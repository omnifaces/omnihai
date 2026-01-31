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
 * MIME type detection based on magic bytes.
 * <p>
 * AI API endpoints require a valid MIME type header when uploading file attachments. File extensions cannot be relied
 * upon for MIME type detection as they may be missing, incorrect, or intentionally spoofed. Magic byte detection
 * provides reliable content-based identification without additional dependencies.
 * <p>
 * OmniHai uses {@code byte[]} for file content rather than {@link java.io.File}, {@link java.nio.file.Path}, or
 * {@link java.io.InputStream} because not all environments support filesystem access (e.g., serverless, read-only
 * containers) and streams risk resource leaks if not properly closed.
 */
package org.omnifaces.ai.mime;
