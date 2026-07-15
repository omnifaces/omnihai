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
package org.omnifaces.ai.exception;

/**
 * Exception thrown when a streaming operation failed after it had already emitted one or more items to its consumer.
 * <p>
 * A resilience decorator cannot re-attempt such an operation, because the new attempt would replay the stream from the start and leave the consumer holding a
 * duplicated prefix. Rather than corrupt the consumer silently, the decorator abandons the operation and throws this exception, with the original failure as
 * its cause.
 * <p>
 * This exception is terminal: it is never retried nor failed over, regardless of the retry or failover predicate in effect. To opt into re-attempting a
 * partially consumed stream, supply an {@code org.omnifaces.ai.service.ResettableConsumer} as token consumer, which is notified before each new attempt so it
 * can discard what it accumulated.
 *
 * @author Bauke Scholtz
 * @since 1.5
 * @see AIException
 */
public class AIStreamAbortedException extends AIException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new stream aborted exception with the specified message and cause.
     *
     * @param message The detail message.
     * @param cause The failure that aborted the stream.
     */
    public AIStreamAbortedException(String message, Throwable cause) {
        super(message, cause);
    }

}
