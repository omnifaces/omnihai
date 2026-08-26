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
package org.omnifaces.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.model.ModerationOptions.Category;

class ModerationOptionsTest {

    // =================================================================================================================
    // Preset instances tests
    // =================================================================================================================

    @Test
    void preset_default() {
        var options = ModerationOptions.DEFAULT;

        assertNotNull(options);
        assertEquals(Category.OPENAI_SUPPORTED_CATEGORY_NAMES, options.getCategories());
        assertEquals(ModerationOptions.DEFAULT_THRESHOLD, options.getThreshold());
    }

    @Test
    void preset_strict_hasLowerThreshold() {
        assertEquals(ModerationOptions.STRICT_THRESHOLD, ModerationOptions.STRICT.getThreshold());
    }

    @Test
    void preset_lenient_hasHigherThreshold() {
        assertEquals(ModerationOptions.LENIENT_THRESHOLD, ModerationOptions.LENIENT.getThreshold());
    }

    // =================================================================================================================
    // Category enum tests
    // =================================================================================================================

    @Test
    void category_names() {
        assertEquals("sexual", Category.SEXUAL.getName());
        assertEquals("harassment", Category.HARASSMENT.getName());
        assertEquals("hate", Category.HATE.getName());
        assertEquals("illicit", Category.ILLICIT.getName());
        assertEquals("self-harm", Category.SELF_HARM.getName());
        assertEquals("violence", Category.VIOLENCE.getName());
        assertEquals("pii", Category.PII.getName());
        assertEquals("spam", Category.SPAM.getName());
        assertEquals("profanity", Category.PROFANITY.getName());
    }

    @Test
    void category_openAISupported() {
        assertTrue(Category.SEXUAL.isOpenAISupported());
        assertTrue(Category.HARASSMENT.isOpenAISupported());
        assertTrue(Category.HATE.isOpenAISupported());
        assertTrue(Category.ILLICIT.isOpenAISupported());
        assertTrue(Category.SELF_HARM.isOpenAISupported());
        assertTrue(Category.VIOLENCE.isOpenAISupported());
        assertFalse(Category.PII.isOpenAISupported());
        assertFalse(Category.SPAM.isOpenAISupported());
        assertFalse(Category.PROFANITY.isOpenAISupported());
    }

    @Test
    void category_allCategoryNames() {
        var allNames = Category.ALL_CATEGORY_NAMES;

        assertEquals(9, allNames.size());
        assertTrue(allNames.contains("sexual"));
        assertTrue(allNames.contains("pii"));
    }

    @Test
    void category_openAISupportedCategoryNames() {
        var openAINames = Category.OPENAI_SUPPORTED_CATEGORY_NAMES;

        assertEquals(6, openAINames.size());
        assertTrue(openAINames.contains("sexual"));
        assertFalse(openAINames.contains("pii"));
    }

    @Test
    void category_allCategoryNames_isImmutable() {
        assertThrows(
            UnsupportedOperationException.class,
            () -> Category.ALL_CATEGORY_NAMES.add("new-category")
        );
    }

    // =================================================================================================================
    // Builder tests - categories
    // =================================================================================================================

    @Test
    void builder_categories_single() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.HATE)
            .build();

        assertEquals(Set.of("hate"), options.getCategories());
    }

    @Test
    void builder_categories_multiple() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.HATE, Category.VIOLENCE, Category.HARASSMENT)
            .build();

        assertEquals(3, options.getCategories().size());
        assertTrue(options.getCategories().contains("hate"));
        assertTrue(options.getCategories().contains("violence"));
    }

    @Test
    void builder_categories_empty_throwsException() {
        var builder = ModerationOptions.newBuilder().categories();

        var exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertEquals("Categories cannot be empty", exception.getMessage());
    }

    // =================================================================================================================
    // Builder tests - addCategories
    // =================================================================================================================

    @Test
    void builder_addCategories_validCustom() {
        var options = ModerationOptions.newBuilder()
            .addCategories("custom-category")
            .build();

        assertTrue(options.getCategories().contains("custom-category"));
    }

    @Test
    void builder_addCategories_convertedToLowercase() {
        var options = ModerationOptions.newBuilder()
            .addCategories("UPPERCASE", "MixedCase")
            .build();

        assertTrue(options.getCategories().contains("uppercase"));
        assertTrue(options.getCategories().contains("mixedcase"));
    }

    @Test
    void builder_addCategories_invalidFormat_throwsException() {
        var builder = ModerationOptions.newBuilder();

        var exception = assertThrows(
            IllegalArgumentException.class,
            () -> builder.addCategories("category123")
        );
        assertTrue(exception.getMessage().contains("may only contain alphabetic characters or hyphens"));
    }

    @Test
    void builder_addCategories_null_throwsException() {
        var builder = ModerationOptions.newBuilder();

        assertThrows(
            NullPointerException.class,
            () -> builder.addCategories((String) null)
        );
    }

    // =================================================================================================================
    // Builder tests - threshold
    // =================================================================================================================

    @Test
    void builder_threshold_atBoundaries() {
        assertEquals(0.0, ModerationOptions.newBuilder().threshold(0.0).build().getThreshold());
        assertEquals(1.0, ModerationOptions.newBuilder().threshold(1.0).build().getThreshold());
    }

    @Test
    void builder_threshold_belowZero_throwsException() {
        var builder = ModerationOptions.newBuilder();

        var exception = assertThrows(IllegalArgumentException.class, () -> builder.threshold(-0.1));
        assertEquals("Threshold must be between 0.0 and 1.0", exception.getMessage());
    }

    @Test
    void builder_threshold_aboveOne_throwsException() {
        var builder = ModerationOptions.newBuilder();

        assertThrows(IllegalArgumentException.class, () -> builder.threshold(1.1));
    }

    // =================================================================================================================
    // Builder tests - chaining
    // =================================================================================================================

    @Test
    void builder_chaining_allOptions() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.HATE, Category.VIOLENCE)
            .threshold(0.3)
            .build();

        assertEquals(2, options.getCategories().size());
        assertEquals(0.3, options.getThreshold());
    }

    @Test
    void builder_categoriesOverwrite() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.HATE)
            .categories(Category.VIOLENCE, Category.HARASSMENT)
            .build();

        assertEquals(2, options.getCategories().size());
        assertFalse(options.getCategories().contains("hate"));
    }

    @Test
    void builder_addCategoriesAccumulates() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.HATE)
            .addCategories("custom-one")
            .addCategories("custom-two")
            .build();

        assertEquals(3, options.getCategories().size());
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(ModerationOptions.class));
    }

    @Test
    void serialization_preservesAllFields() throws Exception {
        var original = ModerationOptions.newBuilder()
            .categories(Category.HATE, Category.VIOLENCE)
            .threshold(0.3)
            .build();

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        var bais = new ByteArrayInputStream(baos.toByteArray());
        try (var ois = new ObjectInputStream(bais)) {
            var deserialized = (ModerationOptions) ois.readObject();

            assertEquals(original.getCategories(), deserialized.getCategories());
            assertEquals(original.getThreshold(), deserialized.getThreshold());
        }
    }

    // =================================================================================================================
    // Immutability tests
    // =================================================================================================================

    @Test
    void categories_isImmutable() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.HATE)
            .build();

        var categories = options.getCategories();

        assertThrows(UnsupportedOperationException.class, () -> categories.add("new-category"));
    }

    @Test
    void categories_areSorted() {
        var options = ModerationOptions.newBuilder()
            .categories(Category.VIOLENCE, Category.HATE, Category.SEXUAL)
            .build();

        var categories = options.getCategories().toArray(new String[0]);
        assertEquals("hate", categories[0]);
        assertEquals("sexual", categories[1]);
        assertEquals("violence", categories[2]);
    }

    // =================================================================================================================
    // List overloads
    // =================================================================================================================

    @Test
    void categories_listAndVarargs_areEquivalent() {
        var fromList = ModerationOptions.newBuilder().categories(List.of(Category.HATE, Category.VIOLENCE)).build();
        var fromVarargs = ModerationOptions.newBuilder().categories(Category.HATE, Category.VIOLENCE).build();

        assertEquals(fromVarargs.getCategories(), fromList.getCategories());
    }

    @Test
    void addCategories_listAndVarargs_areEquivalent() {
        var fromList = ModerationOptions.newBuilder().categories().addCategories(List.of("spoilers", "off-topic")).build();
        var fromVarargs = ModerationOptions.newBuilder().categories().addCategories("spoilers", "off-topic").build();

        assertEquals(fromVarargs.getCategories(), fromList.getCategories());
    }

    @Test
    void addCategories_listWithIllegalCharacters_throwsException() {
        var builder = ModerationOptions.newBuilder();
        var invalid = List.of("not valid");

        assertThrows(IllegalArgumentException.class, () -> builder.addCategories(invalid));
    }

}
