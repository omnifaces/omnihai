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

import org.omnifaces.ai.service.AzureAIService;

/**
 * Default text handler for Azure AI service.
 *
 * @author Bauke Scholtz
 * @since 1.3
 * @see AzureAIService
 */
public class AzureAITextHandler extends OpenAITextHandler {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new instance of this AI handler.
     */
    public AzureAITextHandler() {
        //
    }

    /**
     * @see <a href="https://learn.microsoft.com/en-us/azure/foundry/openai/how-to/web-search">Web search tool</a>
     */
    @Override
    protected String getWebSearchToolName() {
        return "web_search_preview";
    }

}
