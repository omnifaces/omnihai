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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.exception.AIApiRateLimitExceededException;
import org.opentest4j.TestAbortedException;

/**
 * JUnit 5 extension that fails fast on AI API rate limit errors.
 * <p>
 * When an {@link AIApiRateLimitExceededException} is thrown during a test, this extension marks the provider as rate-limited and aborts (skips) all
 * subsequent tests for this provider to prevent unnecessary API calls and wasted time / quota.
 *
 * @see AIApiRateLimitExceededException
 */
public class FailFastOnRateLimitExtension implements BeforeEachCallback, TestExecutionExceptionHandler {

    private static final ConcurrentMap<AIProvider, AtomicBoolean> RATE_LIMIT_HITS = new ConcurrentHashMap<>();

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        var provider = getProvider(context);
        if (RATE_LIMIT_HITS.computeIfAbsent(provider, p -> new AtomicBoolean(false)).get()) {
            throw new TestAbortedException("Rate limit hit for " + provider + "; skipping remaining tests for this provider, we better retry later.");
        }
    }

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
        if (throwable instanceof AIApiRateLimitExceededException) {
            RATE_LIMIT_HITS.computeIfAbsent(getProvider(context), p -> new AtomicBoolean(false)).set(true);
        }

        throw throwable;
    }

    private AIProvider getProvider(ExtensionContext context) {
        if (!(context.getRequiredTestInstance() instanceof AIServiceIT instance)) {
            throw new IllegalStateException("FailFastOnRateLimitExtension must be used on subclasses of AIServiceIT");
        }

        return instance.getProvider();
    }
}
