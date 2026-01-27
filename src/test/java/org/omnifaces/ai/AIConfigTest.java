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

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.exception.AIException;
import org.omnifaces.ai.modality.OpenAITextHandler;
import org.omnifaces.ai.model.ChatInput;
import org.omnifaces.ai.model.ChatOptions;
import org.omnifaces.ai.model.GenerateImageOptions;
import org.omnifaces.ai.model.ModerationOptions;
import org.omnifaces.ai.model.ModerationResult;
import org.omnifaces.ai.service.OpenAIService;

class AIConfigTest {

    // =================================================================================================================
    // Compact constructor — provider validation
    // =================================================================================================================

    @Test
    void constructor_nullProvider_shouldThrow() {
        assertThrows(NullPointerException.class, () -> new AIConfig(null, "key", null, null, null, null, emptyMap()));
    }

    @Test
    void constructor_emptyProvider_shouldThrow() {
        assertThrows(NullPointerException.class, () -> new AIConfig("", "key", null, null, null, null, emptyMap()));
    }

    @Test
    void constructor_blankProvider_shouldThrow() {
        assertThrows(NullPointerException.class, () -> new AIConfig("   ", "key", null, null, null, null, emptyMap()));
    }

    @Test
    void constructor_providerIsStripped() {
        var config = new AIConfig("  OPENAI  ", null, null, null, null, null, emptyMap());
        assertEquals("OPENAI", config.provider());
    }

    // =================================================================================================================
    // Compact constructor — stripping and null normalization
    // =================================================================================================================

    @Test
    void constructor_apiKeyIsStripped() {
        var config = new AIConfig("OPENAI", "  key  ", null, null, null, null, emptyMap());
        assertEquals("key", config.apiKey());
    }

    @Test
    void constructor_blankApiKeyBecomesNull() {
        var config = new AIConfig("OPENAI", "   ", null, null, null, null, emptyMap());
        assertNull(config.apiKey());
    }

    @Test
    void constructor_emptyApiKeyBecomesNull() {
        var config = new AIConfig("OPENAI", "", null, null, null, null, emptyMap());
        assertNull(config.apiKey());
    }

    @Test
    void constructor_modelIsStripped() {
        var config = new AIConfig("OPENAI", null, "  gpt-5  ", null, null, null, emptyMap());
        assertEquals("gpt-5", config.model());
    }

    @Test
    void constructor_blankModelBecomesNull() {
        var config = new AIConfig("OPENAI", null, "   ", null, null, null, emptyMap());
        assertNull(config.model());
    }

    @Test
    void constructor_endpointIsStripped() {
        var config = new AIConfig("OPENAI", null, null, "  https://api.test.com  ", null, null, emptyMap());
        assertEquals("https://api.test.com", config.endpoint());
    }

    @Test
    void constructor_blankEndpointBecomesNull() {
        var config = new AIConfig("OPENAI", null, null, "   ", null, null, emptyMap());
        assertNull(config.endpoint());
    }

    @Test
    void constructor_promptIsStripped() {
        var config = new AIConfig("OPENAI", null, null, null, "  You are helpful  ", null, emptyMap());
        assertEquals("You are helpful", config.prompt());
    }

    @Test
    void constructor_blankPromptBecomesNull() {
        var config = new AIConfig("OPENAI", null, null, null, "   ", null, emptyMap());
        assertNull(config.prompt());
    }

    // =================================================================================================================
    // Compact constructor — strategy normalization
    // =================================================================================================================

    @Test
    void constructor_nullStrategyBecomesDefault() {
        var config = new AIConfig("OPENAI", null, null, null, null, null, emptyMap());
        assertNotNull(config.strategy());
        assertNull(config.strategy().textHandler());
        assertNull(config.strategy().imageHandler());
    }

    @Test
    void constructor_nonNullStrategyIsPreserved() {
        var strategy = new AIStrategy(OpenAITextHandler.class, null);
        var config = new AIConfig("OPENAI", null, null, null, null, strategy, emptyMap());
        assertEquals(strategy, config.strategy());
        assertEquals(OpenAITextHandler.class, config.strategy().textHandler());
    }

    // =================================================================================================================
    // Compact constructor — properties normalization
    // =================================================================================================================

    @Test
    void constructor_nullPropertiesBecomesEmptyMap() {
        var config = new AIConfig("OPENAI", null, null, null, null, null, null);
        assertNotNull(config.properties());
        assertTrue(config.properties().isEmpty());
    }

    @Test
    void constructor_propertiesAreStripped() {
        var props = Map.of("  key  ", "  value  ");
        var config = new AIConfig("OPENAI", null, null, null, null, null, props);
        assertEquals("value", config.properties().get("key"));
    }

    @Test
    void constructor_blankPropertyKeysAreFiltered() {
        var props = new HashMap<String, String>();
        props.put("   ", "value");
        props.put("valid", "data");
        var config = new AIConfig("OPENAI", null, null, null, null, null, props);
        assertEquals(1, config.properties().size());
        assertEquals("data", config.properties().get("valid"));
    }

    @Test
    void constructor_blankPropertyValuesAreFiltered() {
        var props = new HashMap<String, String>();
        props.put("key", "   ");
        props.put("valid", "data");
        var config = new AIConfig("OPENAI", null, null, null, null, null, props);
        assertEquals(1, config.properties().size());
        assertEquals("data", config.properties().get("valid"));
    }

    @Test
    void constructor_propertiesMapIsUnmodifiable() {
        var props = Map.of("key", "value");
        var config = new AIConfig("OPENAI", null, null, null, null, null, props);
        assertThrows(UnsupportedOperationException.class, () -> config.properties().put("new", "entry"));
    }

    // =================================================================================================================
    // Factory methods — of(AIProvider, String)
    // =================================================================================================================

    @Test
    void ofProvider_setsProviderAndApiKey() {
        var config = AIConfig.of(AIProvider.OPENAI, "my-key");
        assertEquals("OPENAI", config.provider());
        assertEquals("my-key", config.apiKey());
        assertNull(config.model());
        assertNull(config.endpoint());
        assertNull(config.prompt());
        assertNull(config.strategy().textHandler());
        assertNull(config.strategy().imageHandler());
        assertTrue(config.properties().isEmpty());
    }

    @Test
    void ofProvider_nullApiKey_isAllowed() {
        var config = AIConfig.of(AIProvider.OLLAMA, null);
        assertEquals("OLLAMA", config.provider());
        assertNull(config.apiKey());
    }

    @Test
    void ofProvider_nullProvider_shouldThrow() {
        assertThrows(NullPointerException.class, () -> AIConfig.of((AIProvider) null, "key"));
    }

    // =================================================================================================================
    // Factory methods — of(Class)
    // =================================================================================================================

    @Test
    void ofClass_setsProviderToClassName() {
        var config = AIConfig.of(OpenAIService.class);
        assertEquals(OpenAIService.class.getName(), config.provider());
        assertNull(config.apiKey());
    }

    @Test
    void ofClass_nullClass_shouldThrow() {
        assertThrows(NullPointerException.class, () -> AIConfig.of((Class<? extends AIService>) null));
    }

    // =================================================================================================================
    // Factory methods — of(Class, String)
    // =================================================================================================================

    @Test
    void ofClassAndApiKey_setsProviderAndApiKey() {
        var config = AIConfig.of(OpenAIService.class, "my-key");
        assertEquals(OpenAIService.class.getName(), config.provider());
        assertEquals("my-key", config.apiKey());
    }

    // =================================================================================================================
    // Static utility methods
    // =================================================================================================================

    @Test
    void createPropertyKey_prependsPrefix() {
        assertEquals("org.omnifaces.ai.AZURE_RESOURCE", AIConfig.createPropertyKey("AZURE_RESOURCE"));
    }

    @Test
    void acceptsPropertyKey_matchingPrefix_returnsTrue() {
        assertTrue(AIConfig.acceptsPropertyKey("org.omnifaces.ai.SOME_KEY"));
    }

    @Test
    void acceptsPropertyKey_nonMatchingPrefix_returnsFalse() {
        assertEquals(false, AIConfig.acceptsPropertyKey("com.example.KEY"));
    }

    @Test
    void acceptsPropertyKey_exactPrefix_returnsTrue() {
        assertTrue(AIConfig.acceptsPropertyKey("org.omnifaces.ai."));
    }

    // =================================================================================================================
    // withXxx methods — immutability
    // =================================================================================================================

    @Test
    void withModel_returnsNewInstanceWithUpdatedModel() {
        var original = AIConfig.of(AIProvider.OPENAI, "key");
        var modified = original.withModel("gpt-5");
        assertNotSame(original, modified);
        assertNull(original.model());
        assertEquals("gpt-5", modified.model());
        assertEquals(original.provider(), modified.provider());
        assertEquals(original.apiKey(), modified.apiKey());
    }

    @Test
    void withEndpoint_returnsNewInstanceWithUpdatedEndpoint() {
        var original = AIConfig.of(AIProvider.OPENAI, "key");
        var modified = original.withEndpoint("https://custom.api.com/v1");
        assertNotSame(original, modified);
        assertNull(original.endpoint());
        assertEquals("https://custom.api.com/v1", modified.endpoint());
    }

    @Test
    void withPrompt_returnsNewInstanceWithUpdatedPrompt() {
        var original = AIConfig.of(AIProvider.OPENAI, "key");
        var modified = original.withPrompt("You are helpful");
        assertNotSame(original, modified);
        assertNull(original.prompt());
        assertEquals("You are helpful", modified.prompt());
    }

    @Test
    void withStrategy_returnsNewInstanceWithUpdatedStrategy() {
        var original = AIConfig.of(AIProvider.OPENAI, "key");
        var strategy = new AIStrategy(OpenAITextHandler.class, null);
        var modified = original.withStrategy(strategy);
        assertNotSame(original, modified);
        assertNull(original.strategy().textHandler());
        assertEquals(OpenAITextHandler.class, modified.strategy().textHandler());
    }

    @Test
    void withProperties_returnsNewInstanceWithUpdatedProperties() {
        var original = AIConfig.of(AIProvider.OPENAI, "key");
        var modified = original.withProperties(Map.of("k", "v"));
        assertNotSame(original, modified);
        assertTrue(original.properties().isEmpty());
        assertEquals("v", modified.properties().get("k"));
    }

    @Test
    void withProperty_addsPropertyToNewInstance() {
        var original = AIConfig.of(AIProvider.OPENAI, "key");
        var modified = original.withProperty("myKey", "myValue");
        assertNotSame(original, modified);
        assertTrue(original.properties().isEmpty());
        assertEquals("myValue", modified.properties().get("myKey"));
    }

    @Test
    void withProperty_blankKey_shouldThrow() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertThrows(IllegalArgumentException.class, () -> config.withProperty("   ", "value"));
    }

    @Test
    void withProperty_blankValue_shouldThrow() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertThrows(IllegalArgumentException.class, () -> config.withProperty("key", "   "));
    }

    @Test
    void withProperty_nullKey_shouldThrow() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertThrows(IllegalArgumentException.class, () -> config.withProperty(null, "value"));
    }

    @Test
    void withProperty_nullValue_shouldThrow() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertThrows(IllegalArgumentException.class, () -> config.withProperty("key", null));
    }

    @Test
    void withProperty_stripsKeyAndValue() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withProperty("  myKey  ", "  myValue  ");
        assertEquals("myValue", config.properties().get("myKey"));
    }

    @Test
    void withoutProperty_removesProperty() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withProperty("a", "1").withProperty("b", "2");
        var modified = config.withoutProperty("a");
        assertNotSame(config, modified);
        assertEquals("1", config.properties().get("a"));
        assertNull(modified.properties().get("a"));
        assertEquals("2", modified.properties().get("b"));
    }

    @Test
    void withoutProperty_nonExistentKey_isNoOp() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        var modified = config.withoutProperty("nonexistent");
        assertEquals(config, modified);
    }

    // =================================================================================================================
    // withXxx methods — chaining
    // =================================================================================================================

    @Test
    void withXxx_chainingPreservesAllFields() {
        var strategy = new AIStrategy(OpenAITextHandler.class, null);
        var config = AIConfig.of(AIProvider.ANTHROPIC, "my-key")
                .withModel("claude-sonnet-4-5")
                .withEndpoint("https://custom.api.com")
                .withPrompt("Be helpful")
                .withStrategy(strategy)
                .withProperty("extra", "data");

        assertEquals("ANTHROPIC", config.provider());
        assertEquals("my-key", config.apiKey());
        assertEquals("claude-sonnet-4-5", config.model());
        assertEquals("https://custom.api.com", config.endpoint());
        assertEquals("Be helpful", config.prompt());
        assertEquals(strategy, config.strategy());
        assertEquals("data", config.properties().get("extra"));
    }

    // =================================================================================================================
    // withXxx methods — blank values normalize to null
    // =================================================================================================================

    @Test
    void withModel_blankBecomesNull() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withModel("gpt-5").withModel("   ");
        assertNull(config.model());
    }

    @Test
    void withEndpoint_blankBecomesNull() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withEndpoint("https://x.com").withEndpoint("");
        assertNull(config.endpoint());
    }

    @Test
    void withPrompt_blankBecomesNull() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withPrompt("hello").withPrompt("   ");
        assertNull(config.prompt());
    }

    @Test
    void withStrategy_nullResetsToDefault() {
        var strategy = new AIStrategy(OpenAITextHandler.class, null);
        var config = AIConfig.of(AIProvider.OPENAI, "key").withStrategy(strategy).withStrategy(null);
        assertNotNull(config.strategy());
        assertNull(config.strategy().textHandler());
    }

    // =================================================================================================================
    // property() — core property lookup
    // =================================================================================================================

    @Test
    void property_providerKey_returnsProvider() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertEquals("OPENAI", config.property(AIConfig.PROPERTY_PROVIDER));
    }

    @Test
    void property_apiKeyKey_returnsApiKey() {
        var config = AIConfig.of(AIProvider.OPENAI, "my-secret");
        assertEquals("my-secret", config.property(AIConfig.PROPERTY_API_KEY));
    }

    @Test
    void property_modelKey_returnsModel() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withModel("gpt-5");
        assertEquals("gpt-5", config.property(AIConfig.PROPERTY_MODEL));
    }

    @Test
    void property_endpointKey_returnsEndpoint() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withEndpoint("https://api.test.com");
        assertEquals("https://api.test.com", config.property(AIConfig.PROPERTY_ENDPOINT));
    }

    @Test
    void property_promptKey_returnsPrompt() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withPrompt("Be concise");
        assertEquals("Be concise", config.property(AIConfig.PROPERTY_PROMPT));
    }

    @Test
    void property_coreKey_returnsNullWhenUnset() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertNull(config.property(AIConfig.PROPERTY_MODEL));
    }

    // =================================================================================================================
    // property() — properties map overrides core properties
    // =================================================================================================================

    @Test
    void property_propertiesMapOverridesProvider() {
        var config = AIConfig.of(AIProvider.OPENAI, "key")
                .withProperty(AIConfig.PROPERTY_PROVIDER, "OVERRIDDEN");
        assertEquals("OVERRIDDEN", config.property(AIConfig.PROPERTY_PROVIDER));
    }

    @Test
    void property_propertiesMapOverridesApiKey() {
        var config = AIConfig.of(AIProvider.OPENAI, "original")
                .withProperty(AIConfig.PROPERTY_API_KEY, "overridden");
        assertEquals("overridden", config.property(AIConfig.PROPERTY_API_KEY));
    }

    @Test
    void property_propertiesMapOverridesModel() {
        var config = AIConfig.of(AIProvider.OPENAI, "key")
                .withModel("original")
                .withProperty(AIConfig.PROPERTY_MODEL, "overridden");
        assertEquals("overridden", config.property(AIConfig.PROPERTY_MODEL));
    }

    @Test
    void property_propertiesMapOverridesEndpoint() {
        var config = AIConfig.of(AIProvider.OPENAI, "key")
                .withEndpoint("https://original.com")
                .withProperty(AIConfig.PROPERTY_ENDPOINT, "https://overridden.com");
        assertEquals("https://overridden.com", config.property(AIConfig.PROPERTY_ENDPOINT));
    }

    @Test
    void property_propertiesMapOverridesPrompt() {
        var config = AIConfig.of(AIProvider.OPENAI, "key")
                .withPrompt("original")
                .withProperty(AIConfig.PROPERTY_PROMPT, "overridden");
        assertEquals("overridden", config.property(AIConfig.PROPERTY_PROMPT));
    }

    // =================================================================================================================
    // property() — custom / non-core keys
    // =================================================================================================================

    @Test
    void property_customKey_returnsFromPropertiesMap() {
        var config = AIConfig.of(AIProvider.OPENAI, "key").withProperty("org.omnifaces.ai.AZURE_RESOURCE", "myResource");
        assertEquals("myResource", config.property("org.omnifaces.ai.AZURE_RESOURCE"));
    }

    @Test
    void property_unknownKey_returnsNull() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertNull(config.property("nonexistent.key"));
    }

    // =================================================================================================================
    // require()
    // =================================================================================================================

    @Test
    void require_existingProperty_returnsValue() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        assertEquals("OPENAI", config.require(AIConfig.PROPERTY_PROVIDER));
    }

    @Test
    void require_missingProperty_shouldThrow() {
        var config = AIConfig.of(AIProvider.OPENAI, "key");
        var ex = assertThrows(IllegalStateException.class, () -> config.require(AIConfig.PROPERTY_MODEL));
        assertTrue(ex.getMessage().contains(AIConfig.PROPERTY_MODEL));
    }

    @Test
    void require_nullCoreProperty_shouldThrow() {
        var config = AIConfig.of(AIProvider.OLLAMA, null);
        assertThrows(IllegalStateException.class, () -> config.require(AIConfig.PROPERTY_API_KEY));
    }

    // =================================================================================================================
    // resolveProvider()
    // =================================================================================================================

    @Test
    void resolveProvider_knownProvider_returnsEnum() {
        var config = AIConfig.of(AIProvider.ANTHROPIC, "key");
        assertEquals(AIProvider.ANTHROPIC, config.resolveProvider());
    }

    @Test
    void resolveProvider_allBuiltInProviders() {
        for (var provider : AIProvider.values()) {
            if (provider == AIProvider.CUSTOM) {
                continue;
            }
            var config = AIConfig.of(provider, "key");
            assertEquals(provider, config.resolveProvider());
        }
    }

    @Test
    void resolveProvider_customClass_returnsCUSTOM() {
        var config = AIConfig.of(OpenAIService.class, "key");
        assertEquals(AIProvider.CUSTOM, config.resolveProvider());
    }

    @Test
    void resolveProvider_unknownString_returnsCUSTOM() {
        var config = new AIConfig("com.example.MyService", null, null, null, null, null, emptyMap());
        assertEquals(AIProvider.CUSTOM, config.resolveProvider());
    }

    @Test
    void resolveProvider_caseInsensitive() {
        var config = new AIConfig("openai", null, null, null, null, null, emptyMap());
        assertEquals(AIProvider.OPENAI, config.resolveProvider());
    }

    // =================================================================================================================
    // createService()
    // =================================================================================================================

    @Test
    void createService_builtInProvider_createsCorrectServiceType() {
        var config = AIConfig.of(AIProvider.OPENAI, "test-key");
        var service = config.createService();
        assertInstanceOf(OpenAIService.class, service);
    }

    @Test
    void createService_allBuiltInProviders() {
        for (var provider : AIProvider.values()) {
            if (provider == AIProvider.CUSTOM) {
                continue;
            }
            var apiKey = provider.isApiKeyRequired() ? "test-key" : null;
            var config = AIConfig.of(provider, apiKey);

            if (provider == AIProvider.AZURE) {
                config = config.withProperty("org.omnifaces.ai.AZURE_RESOURCE", "test-resource");
            }

            var finalConfig = config;
            var service = assertDoesNotThrow(() -> finalConfig.createService(), "Failed to create service for provider " + provider);
            assertNotNull(service, "Service was null for provider " + provider);
            assertInstanceOf(AIService.class, service);
        }
    }

    @Test
    void createService_customClassNotImplementingAIService_shouldThrow() {
        var config = new AIConfig(String.class.getName(), null, null, null, null, null, emptyMap());
        assertThrows(IllegalArgumentException.class, config::createService);
    }

    @Test
    void createService_nonExistentClass_shouldThrow() {
        var config = new AIConfig("com.nonexistent.FakeService", null, null, null, null, null, emptyMap());
        assertThrows(IllegalStateException.class, config::createService);
    }

    @Test
    void createService_customClassWithoutAIConfigConstructor_shouldThrow() {
        var config = new AIConfig(NoConstructorService.class.getName(), null, null, null, null, null, emptyMap());
        assertThrows(IllegalArgumentException.class, config::createService);
    }

    // =================================================================================================================
    // Property constants
    // =================================================================================================================

    @Test
    void propertyConstants_haveCorrectPrefix() {
        assertTrue(AIConfig.PROPERTY_PROVIDER.startsWith("org.omnifaces.ai."));
        assertTrue(AIConfig.PROPERTY_API_KEY.startsWith("org.omnifaces.ai."));
        assertTrue(AIConfig.PROPERTY_MODEL.startsWith("org.omnifaces.ai."));
        assertTrue(AIConfig.PROPERTY_ENDPOINT.startsWith("org.omnifaces.ai."));
        assertTrue(AIConfig.PROPERTY_PROMPT.startsWith("org.omnifaces.ai."));
    }

    @Test
    void propertyConstants_areAcceptedByAcceptsPropertyKey() {
        assertTrue(AIConfig.acceptsPropertyKey(AIConfig.PROPERTY_PROVIDER));
        assertTrue(AIConfig.acceptsPropertyKey(AIConfig.PROPERTY_API_KEY));
        assertTrue(AIConfig.acceptsPropertyKey(AIConfig.PROPERTY_MODEL));
        assertTrue(AIConfig.acceptsPropertyKey(AIConfig.PROPERTY_ENDPOINT));
        assertTrue(AIConfig.acceptsPropertyKey(AIConfig.PROPERTY_PROMPT));
    }

    // =================================================================================================================
    // Stub for custom service test
    // =================================================================================================================

    /** A stub that implements AIService but lacks the required AIConfig constructor. */
    public static class NoConstructorService implements AIService {
        private static final long serialVersionUID = 1L;
        // No AIConfig constructor — createService should fail.
        @Override public String getProviderName() { return null; }
        @Override public String getModelName() { return null; }
        @Override public String getChatPrompt() { return null; }
        @Override public boolean supportsModality(AIModality modality) { return false; }
        @Override public CompletableFuture<String> chatAsync(ChatInput input, ChatOptions options) throws AIException { return null; }
        @Override public CompletableFuture<String> summarizeAsync(String text, int maxWords) throws AIException { return null; }
        @Override public CompletableFuture<List<String>> extractKeyPointsAsync(String text, int maxPoints) throws AIException { return null; }
        @Override public CompletableFuture<String> detectLanguageAsync(String text) throws AIException { return null; }
        @Override public CompletableFuture<String> translateAsync(String text, String sourceLang, String targetLang) throws AIException { return null; }
        @Override public CompletableFuture<ModerationResult> moderateContentAsync(String content, ModerationOptions options) throws AIException { return null; }
        @Override public CompletableFuture<String> analyzeImageAsync(byte[] image, String prompt) throws AIException { return null; }
        @Override public CompletableFuture<String> generateAltTextAsync(byte[] image) throws AIException { return null; }
        @Override public CompletableFuture<byte[]> generateImageAsync(String prompt, GenerateImageOptions options) throws AIException { return null; }
    }
}
