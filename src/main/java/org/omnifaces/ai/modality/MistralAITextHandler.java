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

import java.util.HashMap;
import java.util.Map;

import org.omnifaces.ai.AIService;
import org.omnifaces.ai.model.ChatInput.Attachment;
import org.omnifaces.ai.service.MistralAIService;

/**
 * Default text handler for Mistral AI service.
 *
 * @author Bauke Scholtz
 * @since 1.0
 * @see MistralAIService
 */
public class MistralAITextHandler extends OpenAITextHandler {

    private static final long serialVersionUID = 1L;

    @Override
    protected Map<String, String> getFileUploadMetadata(AIService service, Attachment file) {
        var metadata = new HashMap<>(super.getFileUploadMetadata(service, file));
        metadata.put("purpose", "ocr");
        return metadata;
    }
}
