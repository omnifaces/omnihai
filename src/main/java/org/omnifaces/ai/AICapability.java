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

/**
 * Represents a specific capability that an AI service may support.
 * <p>
 * These constants are used to query whether a given {@link AIService} can perform certain types of input processing (analysis/understanding) or output generation.
 * <p>
 * Capabilities are deliberately separated into <em>analysis</em> (understanding/processing input) and <em>generation</em> (producing new content) to allow
 * fine-grained support checks, since many models support one direction but not the other (e.g., vision input but no image output).
 * <p>
 * Not all providers/models support every capability. Callers should use {@link AIService#supportsCapability(AICapability)} or equivalent to check availability
 * before invoking related operations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService#supportsCapability(AICapability)
 */
public enum AICapability {

    /** Ability to analyze, describe, interpret, caption, answer questions about, or extract information from images provided as input (vision / multimodal understanding). */
    IMAGE_ANALYSIS,

    /** Ability to generate new images from text prompts, image-to-image transformations, editing, inpainting, outpainting, or other image creation tasks. */
    IMAGE_GENERATION,

    /** Ability to analyze, transcribe, summarize, classify, detect speakers, understand spoken content, or answer questions about audio input (speech-to-text, audio understanding). */
    AUDIO_ANALYSIS,

    /** Ability to generate synthetic speech, text-to-speech (TTS), voice cloning, or audio continuations from prompts or text. */
    AUDIO_GENERATION,

    /** Ability to analyze, summarize, answer questions about, detect events in, or understand content from video input (frames + optional audio). */
    VIDEO_ANALYSIS,

    /** Ability to generate new video content from text prompts, image-to-video, video editing, continuation, or style transfer. */
    VIDEO_GENERATION
}
