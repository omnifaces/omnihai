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
package org.omnifaces.ai.service;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.omnifaces.ai.AIProvider;
import org.omnifaces.ai.model.GenerateVideoOptions;

@EnabledIfEnvironmentVariable(named = XAIServiceVideoGeneratorIT.API_KEY_ENV_NAME, matches = ".+")
class XAIServiceVideoGeneratorIT extends BaseAIServiceVideoGeneratorIT {

    protected static final String API_KEY_ENV_NAME = "XAI_API_KEY";

    @Override
    protected AIProvider getProvider() {
        return AIProvider.XAI;
    }

    @Override
    protected String getApiKeyEnvName() {
        return API_KEY_ENV_NAME;
    }

    @Override
    protected String getModel() {
        return "grok-imagine-video-1.5";
    }

    @Override
    protected GenerateVideoOptions getOptions() {
        return GenerateVideoOptions.newBuilder().resolution("480p").seconds(4).build();
    }

}
