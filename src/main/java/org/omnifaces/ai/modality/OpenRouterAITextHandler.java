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
package org.omnifaces.ai.modality;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.ChatOptions.ReasoningEffort;
import org.omnifaces.ai.service.AIServiceWrapper;
import org.omnifaces.ai.service.OpenRouterAIService;

/**
 * Default text handler for OpenRouter AI service.
 *
 * @author Bauke Scholtz
 * @since 1.3
 * @see OpenRouterAIService
 */
public class OpenRouterAITextHandler extends OpenAITextHandler {

    private static final long serialVersionUID = 1L;

    /**
     * @see <a href="https://openrouter.ai/docs/guides/features/plugins/web-search">Web Search API Reference</a>
     */
    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        if (options.useWebSearch()) {
            return super.buildChatPayload(new AIServiceWrapper(service) {

                private static final long serialVersionUID = 1L;

                @Override
                public String getModelName() {
                    return super.getModelName() + ":online";
                }

            }, input, appendWebSearchLocationToPromptIfNecessary(options.withWebSearch(null)), streaming);
        }
        else {
            return super.buildChatPayload(service, input, options, streaming);
        }
    }

    /**
     * OpenRouter accepts a video attachment as a dedicated {@code video_url} content part, rather than as a generic file, and takes it as a data URI.
     *
     * @see <a href="https://openrouter.ai/docs/guides/overview/multimodal/videos">Video Inputs API Reference</a>
     */
    @Override
    protected void addInlineFileContent(AIService service, JsonArrayBuilder content, Attachment file, boolean supportsResponsesApi) {
        if (file.mimeType().isVideo()) {
            content.add(
                Json.createObjectBuilder()
                    .add("type", "video_url")
                    .add("video_url", Json.createObjectBuilder().add("url", file.toDataUri()))
            );
        }
        else {
            super.addInlineFileContent(service, content, file, supportsResponsesApi);
        }
    }

    /**
     * OpenRouter accepts {@code "none"} as a valid effort value regardless of the routed model, and transparently translates the effort string to whatever
     * shape the underlying model needs (percentages of {@code max_tokens} for budget-based providers like Anthropic / Gemini / Qwen). So we do not need the
     * GPT-5-specific {@code AUTO}/{@code NONE} to {@code MEDIUM} fold that the OpenAI base applies.
     *
     * @see <a href="https://openrouter.ai/docs/guides/best-practices/reasoning-tokens">Reasoning Tokens API Reference</a>
     */
    @Override
    protected ReasoningEffort getEffectiveReasoningEffort(AIService service, ChatOptions options) {
        if (!service.supportsReasoningEffort()) {
            return ReasoningEffort.NONE;
        }

        var effort = options.getReasoningEffort();
        return effort == ReasoningEffort.AUTO ? ReasoningEffort.NONE : effort;
    }

    /**
     * OpenRouter always expects the effort under a nested {@code reasoning.effort} object on the Chat Completions endpoint (not the flat
     * {@code reasoning_effort} field that pure OpenAI Chat Completions uses).
     */
    @Override
    protected void addReasoningEffort(AIService service, JsonObjectBuilder payload, ReasoningEffort effort, boolean supportsResponsesApi) {
        payload.add("reasoning", Json.createObjectBuilder().add("effort", effort.name().toLowerCase()));
    }

}
