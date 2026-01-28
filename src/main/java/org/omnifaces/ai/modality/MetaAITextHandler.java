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

import java.util.List;

import org.omnifaces.ai.service.MetaAIService;

/**
 * Default text handler for Meta AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see MetaAIService
 */
public class MetaAITextHandler extends OpenAITextHandler {

    @Override
    public List<String> getChatResponseContentPaths() {
        return List.of("choices[0].completion_message.content");
    }
}
