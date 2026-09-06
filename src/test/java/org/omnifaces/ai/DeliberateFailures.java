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
package org.omnifaces.ai;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Keeps the log readable where a test provokes a failure the library reports at WARNING with a stack trace, which is the behavior under test rather than a
 * problem to look into.
 * <p>
 * A logger holds one filter, so every test which drops something from the same logger has to drop it through here: setting a filter of its own would silently
 * discard the one another test installed, and whichever ran last would decide whose warnings are shown. What is dropped accumulates and is never taken back, so
 * a test running beside one of these keeps every warning of its own.
 */
public final class DeliberateFailures {

    private static final Map<String, Set<Predicate<LogRecord>>> DROPPED = new ConcurrentHashMap<>();

    private DeliberateFailures() {
        throw new AssertionError();
    }

    /**
     * Drops the records of the given logger which the given predicate accepts.
     *
     * @param loggerName The name of the logger to drop from.
     * @param deliberate Answers whether the record is one a test provoked on purpose.
     */
    public static void drop(String loggerName, Predicate<LogRecord> deliberate) {
        var dropped = DROPPED.computeIfAbsent(loggerName, name -> ConcurrentHashMap.newKeySet());
        dropped.add(deliberate);
        Logger.getLogger(loggerName).setFilter(logRecord -> dropped.stream().noneMatch(predicate -> predicate.test(logRecord)));
    }

    /**
     * Drops the records of the given logger whose message carries any of the given fragments.
     *
     * @param loggerName The name of the logger to drop from.
     * @param messageFragments The message fragments of the records to drop.
     */
    public static void dropMessagesContaining(String loggerName, String... messageFragments) {
        for (var fragment : messageFragments) {
            drop(loggerName, logRecord -> String.valueOf(logRecord.getMessage()).contains(fragment));
        }
    }

}
