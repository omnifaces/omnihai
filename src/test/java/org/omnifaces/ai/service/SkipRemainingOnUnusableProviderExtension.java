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

import static java.util.stream.Stream.iterate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.exception.AIAuthenticationException;
import org.omnifaces.ai.exception.AIAuthorizationException;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.exception.AIPaymentRequiredException;
import org.omnifaces.ai.exception.AIRateLimitExceededException;
import org.opentest4j.TestAbortedException;

/**
 * JUnit 5 extension which skips the remaining tests of an AI provider once that provider has shown it cannot serve this run.
 * <p>
 * Some failures state something about the account rather than about the test: an exhausted quota, an unpaid plan, a key which is rejected or lacks the
 * permission. Every later test of that provider fails the same way, so the first one marks the provider and the rest are aborted, which keeps one bad
 * credential from spending minutes of retries and filling the report with identical failures.
 * <p>
 * A failure which a later test could get past, such as a malformed request or a model the endpoint does not host, is deliberately absent: it says nothing about
 * the provider as a whole. So is a service outage, which the very next test may well get past.
 *
 * @see AIServiceIT
 */
public class SkipRemainingOnUnusableProviderExtension implements BeforeEachCallback, TestExecutionExceptionHandler {

    /** The failures which no later test of the same provider can get past within one run. */
    private static final List<Class<? extends AIException>> UNUSABLE_PROVIDER_FAILURES = List.of(
        AIAuthenticationException.class, AIAuthorizationException.class, AIPaymentRequiredException.class, AIRateLimitExceededException.class
    );

    private static final ConcurrentMap<AIProvider, String> UNUSABLE_PROVIDERS = new ConcurrentHashMap<>();

    @Override
    public void beforeEach(ExtensionContext context) {
        var provider = getProvider(context);
        var reason = UNUSABLE_PROVIDERS.get(provider);

        if (reason != null) {
            throw new TestAbortedException(provider + " cannot serve this run (" + reason + "); skipping its remaining tests, we better retry later.");
        }
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        findUnusableProviderFailure(throwable).ifPresent(failure -> UNUSABLE_PROVIDERS.putIfAbsent(getProvider(context), failure));

        throw throwable;
    }

    /**
     * Walks the cause chain rather than only the thrown exception, as an asynchronous operation surfaces its failure wrapped in an {@code ExecutionException}.
     */
    private static Optional<String> findUnusableProviderFailure(Throwable throwable) {
        return iterate(throwable, Objects::nonNull, Throwable::getCause)
            .filter(cause -> UNUSABLE_PROVIDER_FAILURES.stream().anyMatch(type -> type.isInstance(cause)))
            .findFirst()
            .map(cause -> cause.getClass().getSimpleName());
    }

    private static AIProvider getProvider(ExtensionContext context) {
        if (!(context.getRequiredTestInstance() instanceof AIServiceIT instance)) {
            throw new IllegalStateException(SkipRemainingOnUnusableProviderExtension.class.getSimpleName() + " must be used on subclasses of AIServiceIT");
        }

        return instance.getProvider();
    }

}
