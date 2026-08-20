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

import static java.lang.Math.min;
import static java.util.Collections.unmodifiableSet;
import static java.util.logging.Level.WARNING;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.omnifaces.ai.service.BaseAIService.HTTP_CLIENT;

import java.net.URI;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;

import org.omnifaces.ai.AIModality;
import org.omnifaces.ai.AIService;

/**
 * Registry of the input and output modalities which an AI provider publishes per model, so that they can be looked up rather than guessed from the model name.
 * <p>
 * Aggregators such as OpenRouter and Hugging Face route hundreds of models of every vendor, whose capabilities the model name does not reveal, and expose an
 * OpenAI-compatible {@code models} listing which states them per model:
 *
 * <pre>
 * {"data": [{"id": "google/gemini-3.7-flash", "architecture": {"input_modalities": ["text", "image", "video"], "output_modalities": ["text"]}}]}
 * </pre>
 * <p>
 * The listing of an endpoint is fetched at most once a day and shared by every service instance on it, so the first lookup blocks on one HTTP request and the
 * rest are served from memory. A listing which could not be obtained is retried after a minute, doubling per consecutive failure up to an hour, and the last
 * known one keeps being served meanwhile, so that a blip costs a minute of guessing while a permanently unreachable listing costs a request an hour rather than
 * one per call.
 *
 * @author Bauke Scholtz
 * @since 1.7
 * @see BaseAIService#supportsModality(AIModality)
 */
final class ModelModalitiesRegistry {

    private static final Logger logger = Logger.getLogger(ModelModalitiesRegistry.class.getPackageName());

    private static final String ARCHITECTURE_PROPERTY = "architecture";
    private static final String ID_PROPERTY = "id";
    private static final String DATA_PROPERTY = "data";
    private static final char MODEL_VARIANT_SEPARATOR = ':';

    private static final String INPUT_MODALITIES_PROPERTY = "input_modalities";
    private static final String OUTPUT_MODALITIES_PROPERTY = "output_modalities";

    /** The modality which a published input modality maps to; a published modality which is absent here, such as {@code text}, maps to none. */
    private static final Map<String, AIModality> INPUT_MODALITIES = Map.of(
        "image", AIModality.IMAGE_ANALYSIS, "audio", AIModality.AUDIO_ANALYSIS, "video", AIModality.VIDEO_ANALYSIS
    );

    /** The modality which a published output modality maps to; a published modality which is absent here, such as {@code text}, maps to none. */
    private static final Map<String, AIModality> OUTPUT_MODALITIES = Map.of(
        "image", AIModality.IMAGE_GENERATION, "audio", AIModality.AUDIO_GENERATION, "video", AIModality.VIDEO_GENERATION
    );

    private static final Map<URI, CachedModels> MODELS = new ConcurrentHashMap<>();
    private static final Duration MODELS_MAX_AGE = Duration.ofDays(1);
    private static final Duration INITIAL_FAILED_MODELS_MAX_AGE = Duration.ofMinutes(1);
    private static final Duration MAX_FAILED_MODELS_MAX_AGE = Duration.ofHours(1);
    private static final int MAX_FAILED_MODELS_BACKOFF_SHIFT = 6;

    private ModelModalitiesRegistry() {
        // Hide constructor.
    }

    /**
     * Returns the modalities which the given AI service publishes for its currently configured model, or empty when the listing cannot be obtained or does not
     * know the model. A model name carrying a variant suffix such as {@code :batch} or {@code :free} falls back to the base name, as the listing enumerates
     * those as separate entries with equal modalities.
     *
     * @param service The AI service to obtain the model listing from.
     * @return The modalities of the currently configured model, or empty if unknown.
     */
    static Optional<Set<AIModality>> findModelModalities(BaseAIService service) {
        return findModelModalities(service.getModelsPaths().stream().map(path -> getModels(service, path)).toList(), service.getModelName());
    }

    /**
     * Returns the modalities which the given listings state for the given model, unioned across them, or empty when none of them knows it. A provider which
     * does not enumerate every model in one listing states one per listing, and a model which appears in several keeps every modality any of them states.
     * <p>
     * A model name carrying a variant suffix such as {@code :batch} or {@code :free} falls back to the base name, as a listing enumerates those as separate
     * entries with equal modalities.
     *
     * @param listings The model listings to consult, in order.
     * @param model The model name to look up.
     * @return The modalities of the model, or empty if no listing knows it.
     */
    static Optional<Set<AIModality>> findModelModalities(List<Map<String, Set<AIModality>>> listings, String model) {
        var modalities = EnumSet.noneOf(AIModality.class);
        var known = false;

        for (var listing : listings) {
            var stated = findModalities(listing, model);

            if (stated != null) {
                modalities.addAll(stated);
                known = true;
            }
        }

        return known ? Optional.of(unmodifiableSet(modalities)) : Optional.empty();
    }

    private static Set<AIModality> findModalities(Map<String, Set<AIModality>> listing, String model) {
        var modalities = listing.get(model);

        if (modalities != null) {
            return modalities;
        }

        var index = model.indexOf(MODEL_VARIANT_SEPARATOR);
        return index > 0 ? listing.get(model.substring(0, index)) : null;
    }

    /**
     * Returns one listing of the given AI service's endpoint, fetching it first if it is absent or expired.
     *
     * @param service The AI service to obtain the model listing from.
     * @param path The path of the model listing.
     * @return The listing, keyed by model name.
     */
    private static Map<String, Set<AIModality>> getModels(BaseAIService service, String path) {
        var uri = service.resolveURI(path);
        var models = MODELS.get(uri);
        return (models == null || models.isExpired() ? refreshModels(uri, service, path) : models).byModelName();
    }

    /**
     * Fetches the listing of the given endpoint and caches it, unless another thread got there first. This blocks the caller, which is deliberate: the listing
     * is reached only from {@link AIService#supportsModality(AIModality)}, which no operation of the library itself consults, so the only party waiting is the
     * one who asked the question, and an answer is worth more to it than a guess. Serializing the fetch keeps a burst of callers to one request, and keeps a
     * failing fetch from discarding a listing which a concurrent one just stored.
     *
     * @param uri The endpoint's model listing URI.
     * @param service The AI service to obtain the model listing from.
     * @param path The path of the model listing.
     * @return The freshly fetched listing, or the one another thread fetched while this one waited.
     */
    private static synchronized CachedModels refreshModels(URI uri, BaseAIService service, String path) {
        var models = MODELS.get(uri);

        if (models != null && !models.isExpired()) {
            return models;
        }

        var refreshed = fetchModels(service, path, models);
        MODELS.put(uri, refreshed);
        return refreshed;
    }

    /**
     * Fetches the model listing, never throwing: a listing which cannot be obtained or parsed yields the last known one, or an empty one when there is none, so
     * that a listing already in hand keeps being served rather than being discarded in favor of guessing from the model name.
     */
    private static CachedModels fetchModels(BaseAIService service, String path, CachedModels previous) {
        try {
            return new CachedModels(parseModels(HTTP_CLIENT.get(service, path).join()), System.nanoTime(), 0);
        }
        catch (Exception e) {
            var lastKnown = previous != null ? previous.byModelName() : Map.<String, Set<AIModality>>of();
            var failures = previous != null ? previous.consecutiveFailures() + 1 : 1;
            logger.log(
                WARNING, e, () -> "Cannot obtain " + path + " from " + service.getName()
                    + (lastKnown.isEmpty() ? "; falling back to model name matching" : "; keeping the last known listing of " + lastKnown.size() + " models")
            );
            return new CachedModels(lastKnown, System.nanoTime(), failures);
        }
    }

    /**
     * Parses the model listing into modalities per model name. A model entry without an {@code architecture} object is skipped, so that a provider which does
     * not publish modalities at all yields an empty listing rather than one claiming every model supports nothing.
     *
     * @param responseJson The model listing response JSON.
     * @return The modalities per model name.
     */
    static Map<String, Set<AIModality>> parseModels(JsonObject responseJson) {
        return responseJson.getJsonArray(DATA_PROPERTY).stream()
            .map(JsonValue::asJsonObject)
            .filter(model -> model.containsKey(ARCHITECTURE_PROPERTY))
            .collect(toUnmodifiableMap(model -> model.getString(ID_PROPERTY), ModelModalitiesRegistry::parseModelModalities, (first, duplicate) -> first));
    }

    private static Set<AIModality> parseModelModalities(JsonObject model) {
        var architecture = model.getJsonObject(ARCHITECTURE_PROPERTY);
        var modalities = EnumSet.noneOf(AIModality.class);
        addModalities(modalities, architecture, INPUT_MODALITIES_PROPERTY, INPUT_MODALITIES);
        addModalities(modalities, architecture, OUTPUT_MODALITIES_PROPERTY, OUTPUT_MODALITIES);
        return unmodifiableSet(modalities);
    }

    private static void addModalities(Set<AIModality> target, JsonObject architecture, String property, Map<String, AIModality> mapping) {
        var published = architecture.getJsonArray(property);

        if (published != null) {
            published.stream().map(modality -> mapping.get(((JsonString) modality).getString())).filter(Objects::nonNull).forEach(target::add);
        }
    }

    /**
     * The model listing of one endpoint as fetched at {@code fetchedNanos}, keyed by model name. A fetch which failed is cached as an empty listing along with
     * the number of consecutive failures, which shortens how long it is held.
     */
    record CachedModels(Map<String, Set<AIModality>> byModelName, long fetchedNanos, int consecutiveFailures) {

        private boolean isExpired() {
            return System.nanoTime() - fetchedNanos >= maxAge().toNanos();
        }

        Duration maxAge() {
            if (consecutiveFailures == 0) {
                return MODELS_MAX_AGE;
            }

            var backoff = INITIAL_FAILED_MODELS_MAX_AGE.multipliedBy(1L << min(consecutiveFailures - 1, MAX_FAILED_MODELS_BACKOFF_SHIFT));
            return backoff.compareTo(MAX_FAILED_MODELS_MAX_AGE) > 0 ? MAX_FAILED_MODELS_MAX_AGE : backoff;
        }

    }

}
