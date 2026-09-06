/*
 * Copyright OmniFaces
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.omnifaces.ai.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIModality.AUDIO_ANALYSIS;
import static org.omnifaces.ai.AIModality.IMAGE_GENERATION;
import static org.omnifaces.ai.AIProvider.HUGGINGFACE;
import static org.omnifaces.ai.service.ModelModalitiesRegistry.MODELS;

import java.lang.Thread.State;
import java.time.Duration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;
import org.omnifaces.ai.DeliberateFailures;
import org.omnifaces.ai.service.LoopbackHttpServer.Answer;
import org.omnifaces.ai.service.ModelModalitiesRegistry.CachedModels;

/**
 * How long a model listing is held before it is fetched again, and what is served while a fetch keeps failing. The age of a listing is stated when it is cached
 * rather than measured against a clock the test moves, so a listing older than a day is one which was cached with an older timestamp.
 */
class ModelModalitiesRegistryLoopbackTest {

    private static final String MODEL = "acme/painter";

    /**
     * A listing which cannot be obtained is logged as a warning, which one of these tests provokes on purpose. The filter is installed once and never changed,
     * so a test running beside this one keeps every warning of its own.
     */
    @BeforeAll
    static void dropTheWarningsOfTheDeliberatelyFailingFetch() {
        DeliberateFailures.dropMessagesContaining(ModelModalitiesRegistry.class.getPackageName(), "Cannot obtain");
    }

    private LoopbackHttpServer server;
    private HuggingFaceAIService service;

    @BeforeEach
    void startServer() {
        server = LoopbackHttpServer.start();
        service = new HuggingFaceAIService(AIConfig.of(HUGGINGFACE, "test-api-key").withModel(MODEL).withEndpoint(server.endpoint()));
    }

    @AfterEach
    void stopServer() {
        MODELS.remove(service.resolveURI(service.getModelsPaths().get(0)));
        server.close();
    }

    /**
     * A listing which is still fresh is served from memory, so a burst of questions costs one request.
     */
    @Test
    void listingWhichIsStillFresh_isServedWithoutAskingAgain() {
        server.answer(Answer.ofJson(listingStating("image")));

        assertTrue(service.supportsModality(IMAGE_GENERATION));
        assertTrue(service.supportsModality(IMAGE_GENERATION));

        assertEquals(1, server.requestCount());
    }

    /**
     * A listing older than a day is fetched again, so a model whose modalities the provider changed is not read from a stale answer forever.
     */
    @Test
    void listingOlderThanADay_isFetchedAgain() {
        cache(IMAGE_GENERATION, Duration.ofDays(2), 0);
        server.answer(Answer.ofJson(listingStating("audio")));

        assertTrue(service.supportsModality(AUDIO_ANALYSIS), "the listing which was fetched again is what the modalities are read from");
        assertFalse(service.supportsModality(IMAGE_GENERATION), "the stale listing is gone rather than merged into the new one");
        assertEquals(1, server.requestCount());
    }

    /**
     * A fetch which fails keeps the last known listing being served, so a provider which is briefly unreachable costs no capability.
     */
    @Test
    void fetchWhichFails_keepsServingTheLastKnownListing() {
        cache(IMAGE_GENERATION, Duration.ofDays(2), 0);
        server.answer(Answer.ofStatus(503, "{\"error\":{\"message\":\"listing unavailable\"}}"));

        assertTrue(service.supportsModality(IMAGE_GENERATION), "the listing in hand is worth more than guessing from the model name");
        assertEquals(1, server.requestCount());
        assertEquals(Set.of(IMAGE_GENERATION), cached().byModelName().get(MODEL), "the listing which was in hand is what is still cached");
        assertEquals(1, cached().consecutiveFailures());
    }

    /**
     * A fetch which keeps failing is retried ever less often, so a listing which nobody serves any more costs a request an hour rather than one per question.
     */
    @Test
    void fetchWhichKeepsFailing_isRetriedEverLessOften() {
        cache(IMAGE_GENERATION, Duration.ofMinutes(2), 1);
        server.answer(Answer.ofStatus(503, "{\"error\":{\"message\":\"listing unavailable\"}}"));

        service.supportsModality(IMAGE_GENERATION);

        assertEquals(2, cached().consecutiveFailures());
        assertEquals(Duration.ofMinutes(2), cached().maxAge(), "the wait doubles per consecutive failure");
    }

    private void cache(org.omnifaces.ai.AIModality modality, Duration age, int consecutiveFailures) {
        MODELS.put(
            service.resolveURI(service.getModelsPaths().get(0)),
            new CachedModels(Map.of(MODEL, Set.of(modality)), System.nanoTime() - age.toNanos(), consecutiveFailures)
        );
    }

    private CachedModels cached() {
        return MODELS.get(service.resolveURI(service.getModelsPaths().get(0)));
    }

    private static String listingStating(String modality) {
        return "{\"data\":[{\"id\":\"" + MODEL + "\",\"architecture\":{\"input_modalities\":[\"text\",\"" + modality
            + "\"],\"output_modalities\":[\"text\",\"" + modality + "\"]}}]}";
    }

    /**
     * A burst of callers costs one request: the fetch is serialized, and whoever waited on it is served what the one who fetched put there rather than fetching
     * again.
     */
    @Test
    void listingWhichTwoCallersAskForAtOnce_isFetchedOnce() throws Exception {
        cache(IMAGE_GENERATION, Duration.ofDays(2), 0);
        server.answer(Answer.ofJson(listingStating("audio")));
        server.holdNextAnswer();

        var fetching = ask();
        server.awaitHeldRequest();

        var waiting = ask();
        awaitBlocked(waiting.getKey());

        server.releaseHeldAnswer();

        assertTrue(fetching.getValue().get(5, SECONDS), "the caller which fetched reads the listing it fetched");
        assertTrue(waiting.getValue().get(5, SECONDS), "the caller which waited reads the same listing");
        assertEquals(1, server.requestCount());
    }

    /** Asks the service on a thread of its own, so that two callers can be in the same question at once. */
    private Entry<Thread, CompletableFuture<Boolean>> ask() {
        var answer = new CompletableFuture<Boolean>();
        var caller = new Thread(() -> answer.complete(service.supportsModality(AUDIO_ANALYSIS)), "asking");
        caller.start();
        return Map.entry(caller, answer);
    }

    /** Waits until the caller is waiting on the fetch rather than making one, which is what this test is about. */
    private static void awaitBlocked(Thread caller) {
        var deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();

        while (caller.getState() != State.BLOCKED) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("The second caller never waited on the fetch, so this states nothing");
            }

            Thread.onSpinWait();
        }
    }

}
