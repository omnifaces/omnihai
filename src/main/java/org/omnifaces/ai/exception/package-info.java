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

/**
 * Exception hierarchy for AI service operations.
 * <p>
 * All exceptions extend {@link org.omnifaces.ai.exception.AIException}:
 * <ul>
 * <li>{@link org.omnifaces.ai.exception.AIHttpException} - HTTP-level errors (4xx/5xx responses)
 * <ul>
 * <li>{@link org.omnifaces.ai.exception.AIAuthenticationException} - authentication failures (401)</li>
 * <li>{@link org.omnifaces.ai.exception.AIPaymentRequiredException} - payment required (402)</li>
 * <li>{@link org.omnifaces.ai.exception.AIAuthorizationException} - authorization failures (403)</li>
 * <li>{@link org.omnifaces.ai.exception.AIBadRequestException} - malformed requests (400)</li>
 * <li>{@link org.omnifaces.ai.exception.AIEndpointNotFoundException} - endpoint not found (404)</li>
 * <li>{@link org.omnifaces.ai.exception.AIRateLimitExceededException} - rate limit exceeded (429)</li>
 * <li>{@link org.omnifaces.ai.exception.AIServiceUnavailableException} - service unavailable (503)</li>
 * </ul>
 * </li>
 * <li>{@link org.omnifaces.ai.exception.AIResponseException} - response parsing or content errors</li>
 * <li>{@link org.omnifaces.ai.exception.AITokenLimitExceededException} - input/output token limit exceeded</li>
 * <li>{@link org.omnifaces.ai.exception.AIBudgetExceededException} - cumulative cost cap on ChatOptions reached</li>
 * <li>{@link org.omnifaces.ai.exception.AIStreamAbortedException} - stream failed after already emitting tokens</li>
 * </ul>
 */
package org.omnifaces.ai.exception;
