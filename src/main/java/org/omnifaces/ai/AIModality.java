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
 * Represents a supported input/output modality of an AI service.
 * <p>
 * Each constant indicates whether a given {@link AIService} can process a specific type of media as input
 * (analysis/understanding) and/or generate that media type as output.
 * <p>
 * The enum deliberately separates <em>analysis</em> (consuming/understanding media) from <em>generation</em> (producing
 * new media) because many models support one direction but not the other — for example, strong vision understanding
 * without image generation, or high-quality text-to-speech without audio transcription.
 * <p>
 * Text is considered the default modality and is implicitly supported by nearly all services; it is therefore not
 * enumerated here. Without the text modality the AI service wouldn't be able to process input and give response in
 * first place.
 * <p>
 * Not every provider or model supports every modality. Callers should use {@link AIService#supports(AIModality)}
 * to check availability before attempting modality-specific operations.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see AIService#supportsCapability(AIModality)
 */
public enum AIModality {

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
