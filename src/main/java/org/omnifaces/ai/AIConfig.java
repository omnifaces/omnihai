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
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.omnifaces.ai.helper.TextHelper.isBlank;
import static org.omnifaces.ai.helper.TextHelper.stripToNull;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for AI services.
 * <p>
 * This record holds the configuration properties needed to create an {@link AIService} instance.
 * The core properties are {@code provider}, {@code apiKey}, {@code model}, {@code endpoint}, and {@code prompt}.
 * Additional provider-specific properties can be stored in the {@code properties} map.
 * <p>
 * Use the static {@code of(...)} factory methods to create instances, and the {@code withXxx(...)} methods to create modified copies.
 *
 * @see AIProvider
 * @see AIService
 * @author Bauke Scholtz
 * @since 1.0
 */
public record AIConfig(String provider, String apiKey, String model, String endpoint, String prompt, Map<String, String> properties) implements Serializable {

    // Just future-proofing potential Jakarta/MicroProfile config.
    private static final String PROPERTY_PREFIX = "org.omnifaces.ai.";

    /** Configuration property key for the AI provider: {@value}. */
    public static final String PROPERTY_PROVIDER = PROPERTY_PREFIX + "PROVIDER";

    /** Configuration property key for the API key: {@value}. */
    public static final String PROPERTY_API_KEY = PROPERTY_PREFIX + "API_KEY";

    /** Configuration property key for the AI model: {@value}. */
    public static final String PROPERTY_MODEL = PROPERTY_PREFIX + "MODEL";

    /** Configuration property key for the API endpoint: {@value}. */
    public static final String PROPERTY_ENDPOINT = PROPERTY_PREFIX + "ENDPOINT";

    /** Configuration property key for the AI chat prompt: {@value}. */
    public static final String PROPERTY_PROMPT = PROPERTY_PREFIX + "PROMPT";

    public AIConfig {
        provider = stripToNull(provider);
        apiKey = stripToNull(apiKey);
        model = stripToNull(model);
        endpoint = stripToNull(endpoint);
        prompt = stripToNull(prompt);
        properties = properties == null ? emptyMap() : properties.entrySet().stream()
                .filter(e -> !isBlank(e.getKey()) && !isBlank(e.getValue()))
                .collect(toUnmodifiableMap(e -> e.getKey().strip(), e -> e.getValue().strip()));
    }

    /**
     * Creates a new configuration with the specified properties.
     *
     * @param provider The AI provider name or custom service class name.
     * @param apiKey The API key.
     * @param model The AI model name.
     * @param endpoint The API endpoint URL.
     * @param prompt The AI chat prompt.
     * @return A new configuration instance.
     */
    public static AIConfig of(String provider, String apiKey, String model, String endpoint, String prompt) {
        return new AIConfig(provider, apiKey, model, endpoint, prompt, emptyMap());
    }

    /**
     * Creates a new configuration with the specified properties.
     *
     * @param provider The AI provider.
     * @param apiKey The API key.
     * @param model The AI model name.
     * @param endpoint The API endpoint URL.
     * @param prompt The AI chat prompt.
     * @return A new configuration instance.
     */
    public static AIConfig of(AIProvider provider, String apiKey, String model, String endpoint, String prompt) {
        return of(provider.name(), apiKey, model, endpoint, prompt);
    }

    /**
     * Creates a new configuration with the specified properties.
     *
     * @param provider The AI provider name or custom service class name.
     * @param apiKey The API key.
     * @param model The AI model name.
     * @param endpoint The API endpoint URL.
     * @return A new configuration instance.
     */
    public static AIConfig of(String provider, String apiKey, String model, String endpoint) {
        return of(provider, apiKey, model, endpoint, null);
    }

    /**
     * Creates a new configuration with the specified properties.
     *
     * @param provider The AI provider.
     * @param apiKey The API key.
     * @param model The AI model name.
     * @param endpoint The API endpoint URL.
     * @return A new configuration instance.
     */
    public static AIConfig of(AIProvider provider, String apiKey, String model, String endpoint) {
        return of(provider.name(), apiKey, model, endpoint);
    }

    /**
     * Creates a new configuration with the specified properties and default endpoint.
     *
     * @param provider The AI provider name or custom service class name.
     * @param apiKey The API key.
     * @param model The AI model name.
     * @return A new configuration instance.
     */
    public static AIConfig of(String provider, String apiKey, String model) {
        return of(provider, apiKey, model, null);
    }

    /**
     * Creates a new configuration with the specified properties and default endpoint.
     *
     * @param provider The AI provider.
     * @param apiKey The API key.
     * @param model The AI model name.
     * @return A new configuration instance.
     */
    public static AIConfig of(AIProvider provider, String apiKey, String model) {
        return of(provider.name(), apiKey, model);
    }

    /**
     * Creates a new configuration with the specified properties and default model and endpoint.
     *
     * @param provider The AI provider name or custom service class name.
     * @param apiKey The API key.
     * @return A new configuration instance.
     */
    public static AIConfig of(String provider, String apiKey) {
        return of(provider, apiKey, null);
    }

    /**
     * Creates a new configuration with the specified properties and default model and endpoint.
     *
     * @param provider The AI provider.
     * @param apiKey The API key.
     * @return A new configuration instance.
     */
    public static AIConfig of(AIProvider provider, String apiKey) {
        return of(provider.name(), apiKey);
    }

    /**
     * Creates a full property key by prepending the OmniAI property prefix.
     *
     * @param suffix The property name suffix (e.g., "AZURE_RESOURCE").
     * @return The full property key (e.g., "org.omnifaces.ai.AZURE_RESOURCE").
     */
    public static String createPropertyKey(String suffix) {
        return PROPERTY_PREFIX + suffix;
    }

    /**
     * Checks whether the given key is an OmniAI configuration property key.
     *
     * @param key The property key to check.
     * @return {@code true} if the key starts with the OmniAI property prefix.
     */
    public static boolean acceptsPropertyKey(String key) {
        return key.startsWith(PROPERTY_PREFIX);
    }

    /**
     * Returns a copy of this configuration with the specified model.
     *
     * @param model The AI model name.
     * @return A new configuration instance with the updated model.
     */
    public AIConfig withModel(String model) {
        return new AIConfig(provider, apiKey, model, endpoint, prompt, properties);
    }

    /**
     * Returns a copy of this configuration with the specified endpoint.
     *
     * @param endpoint The API endpoint URL.
     * @return A new configuration instance with the updated endpoint.
     */
    public AIConfig withEndpoint(String endpoint) {
        return new AIConfig(provider, apiKey, model, endpoint, prompt, properties);
    }

    /**
     * Returns a copy of this configuration with the specified properties map.
     *
     * @param properties The properties map.
     * @return A new configuration instance with the updated properties.
     */
    public AIConfig withProperties(Map<String, String> properties) {
        return new AIConfig(provider, apiKey, model, endpoint, prompt, properties);
    }

    /**
     * Returns a copy of this configuration with the specified property added.
     *
     * @param key The property key.
     * @param value The property value.
     * @return A new configuration instance with the property added.
     * @throws IllegalArgumentException If key or value is blank.
     */
    public AIConfig withProperty(String key, String value) {
        if (isBlank(key) || isBlank(value)) {
            throw new IllegalArgumentException("key or value cannot be blank");
        }
        var newProperties = new HashMap<String, String>(properties);
        newProperties.put(key.strip(), value.strip());
        return withProperties(newProperties);
    }

    /**
     * Returns a copy of this configuration with the specified property removed.
     *
     * @param key The property key to remove.
     * @return A new configuration instance with the property removed.
     */
    public AIConfig withoutProperty(String key) {
        var newProperties = new HashMap<String, String>(properties);
        newProperties.remove(key);
        return withProperties(newProperties);
    }

    /**
     * Returns the value of the specified configuration property.
     * <p>
     * For core properties ({@link #PROPERTY_PROVIDER}, {@link #PROPERTY_API_KEY}, {@link #PROPERTY_MODEL}, {@link #PROPERTY_ENDPOINT}),
     * the corresponding record field takes precedence over the properties map.
     *
     * @param key The property key.
     * @return The property value, or {@code null} if not found.
     */
    public String property(String key) {
        return switch (key) {
            case PROPERTY_PROVIDER -> coalesce(provider(), key);
            case PROPERTY_API_KEY -> coalesce(apiKey(), key);
            case PROPERTY_MODEL -> coalesce(model(), key);
            case PROPERTY_ENDPOINT -> coalesce(endpoint(), key);
            case PROPERTY_PROMPT -> coalesce(prompt(), key);
            default -> properties().get(key);
        };
    }

    private String coalesce(String field, String key) {
        return field != null ? field : properties().get(key);
    }

    /**
     * Returns the value of the specified configuration property, throwing an illegal state exception if not found.
     *
     * @param key The property key.
     * @return The property value.
     * @throws IllegalStateException If the property is not found.
     */
    public String require(String key) {
        return ofNullable(property(key)).orElseThrow(() -> new IllegalStateException(key + " property is required"));
    }

    /**
     * Resolves the configured provider to an {@link AIProvider} enum value.
     * <p>
     * If the provider string matches a known {@link AIProvider} name, that enum value is returned.
     * Otherwise, {@link AIProvider#CUSTOM} is returned, indicating a custom service class name.
     *
     * @return The resolved AI provider.
     * @throws IllegalStateException If the provider property is not configured.
     */
    public AIProvider resolveProvider() {
        return ofNullable(AIProvider.of(require(PROPERTY_PROVIDER))).orElse(AIProvider.CUSTOM);
    }

    /**
     * Creates a new AI service instance based on this configuration.
     *
     * @return The AI service instance.
     * @throws IllegalStateException If the provider is not configured or the service cannot be created.
     * @throws IllegalArgumentException If a custom provider class does not implement {@link AIService} or does not have a public constructor taking {@link AIConfig}.
     */
    public AIService createService() {
        var provider = resolveProvider();

        if (provider.getServiceClass() != null) {
            try {
                return provider.getServiceClass().getDeclaredConstructor(AIConfig.class).newInstance(this);
            }
            catch (Exception e) {
                throw new IllegalStateException("Cannot create AI service for provider " + provider(), e);
            }
        }
        else {
            return createCustomService();
        }
    }

    /**
     * Creates a custom AI service instance based on this configuration.
     */
    private AIService createCustomService() {
        Class<?> serviceClass;

        try {
            serviceClass = Class.forName(provider());
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot load custom AI service class " + provider(), e);
        }

        if (!AIService.class.isAssignableFrom(serviceClass)) {
            throw new IllegalArgumentException("Cannot create custom AI service class " + provider() + ", because it does not implement " + AIService.class.getName());
        }

        try {
            return (AIService) serviceClass.getDeclaredConstructor(AIConfig.class).newInstance(this);
        }
        catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Cannot create custom AI service class " + provider() + ", because it does not have a constructor taking " + AIConfig.class.getName());
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot create custom AI service class " + provider(), e);
        }
    }

}
