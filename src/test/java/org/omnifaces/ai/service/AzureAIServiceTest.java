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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.omnifaces.ai.AIProvider.AZURE;
import static org.omnifaces.ai.service.AzureAIService.OPTION_AZURE_RESOURCE;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AIConfig;

/**
 * Azure serves an OpenAI compatible API on a resource of the customer's own, addressed by deployment name rather than by model name, and keyed by a header of
 * its own rather than by a bearer token.
 */
class AzureAIServiceTest {

    private static final String API_KEY = "test-api-key";
    private static final String MODEL = "gpt-4o";

    @Test
    void endpoint_carryingTheResourcePlaceholder_isFilledInWithTheConfiguredResource() {
        var service = newService(AIConfig.of(AZURE, API_KEY).withModel(MODEL).withProperty(OPTION_AZURE_RESOURCE, "my-resource"));

        assertTrue(service.resolveURI("responses").toString().startsWith("https://my-resource."), service.resolveURI("responses").toString());
    }

    /**
     * An endpoint stated in full names the resource already, so none has to be configured beside it.
     */
    @Test
    void endpoint_statedInFull_needsNoResourceAtAll() {
        var service = newService(AIConfig.of(AZURE, API_KEY).withModel(MODEL).withEndpoint("https://my-own.example.org/"));

        assertEquals(URI.create("https://my-own.example.org/openai/v1/deployments/" + MODEL + "/responses"), service.resolveURI("responses"));
    }

    /**
     * Azure addresses the model by its deployment name in the path, so the path of every request carries it.
     */
    @Test
    void resolveURI_addressesTheDeploymentOfTheConfiguredModel() {
        var service = newService(AIConfig.of(AZURE, API_KEY).withModel(MODEL).withEndpoint("https://my-own.example.org/"));

        assertEquals("https://my-own.example.org/openai/v1/deployments/gpt-4o/files", service.resolveURI("files").toString());
    }

    @Test
    void getRequestHeaders_carryTheApiKeyInAzuresOwnHeader() {
        assertEquals(
            java.util.Map.of("api-key", API_KEY), newService(
                AIConfig.of(AZURE, API_KEY).withModel(MODEL)
                    .withProperty(OPTION_AZURE_RESOURCE, "my-resource")
            ).getRequestHeaders()
        );
    }

    @Test
    void supportsWebSearch_isServedWhateverTheModel() {
        assertTrue(newService(AIConfig.of(AZURE, API_KEY).withModel("gpt-3.5-turbo").withProperty(OPTION_AZURE_RESOURCE, "my-resource")).supportsWebSearch());
    }

    private static AzureAIService newService(AIConfig config) {
        return new AzureAIService(config);
    }

}
