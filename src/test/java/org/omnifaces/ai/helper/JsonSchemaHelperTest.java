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
package org.omnifaces.ai.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.omnifaces.ai.AITextHandler;

class JsonSchemaHelperTest {

    // =================================================================================================================
    // Test records
    // =================================================================================================================

    record SimpleRecord(String name, int age) {}

    @Test
    void buildJsonSchema_simpleRecord() {
        var schema = JsonSchemaHelper.buildJsonSchema(SimpleRecord.class);

        assertEquals("object", schema.getString("type"));

        var properties = schema.getJsonObject("properties");
        assertEquals("string", properties.getJsonObject("name").getString("type"));
        assertEquals("integer", properties.getJsonObject("age").getString("type"));

        var required = schema.getJsonArray("required");
        assertTrue(required.toString().contains("name"));
        assertTrue(required.toString().contains("age"));
    }

    record AllPrimitives(
        boolean boolPrimitive, Boolean boolWrapper,
        byte bytePrimitive, Byte byteWrapper,
        short shortPrimitive, Short shortWrapper,
        int intPrimitive, Integer intWrapper,
        long longPrimitive, Long longWrapper,
        float floatPrimitive, Float floatWrapper,
        double doublePrimitive, Double doubleWrapper,
        char charPrimitive, Character charWrapper
    ) {}

    @Test
    void buildJsonSchema_allPrimitives() {
        var schema = JsonSchemaHelper.buildJsonSchema(AllPrimitives.class);
        var properties = schema.getJsonObject("properties");

        assertEquals("boolean", properties.getJsonObject("boolPrimitive").getString("type"));
        assertEquals("boolean", properties.getJsonObject("boolWrapper").getString("type"));
        assertEquals("integer", properties.getJsonObject("bytePrimitive").getString("type"));
        assertEquals("integer", properties.getJsonObject("byteWrapper").getString("type"));
        assertEquals("integer", properties.getJsonObject("shortPrimitive").getString("type"));
        assertEquals("integer", properties.getJsonObject("shortWrapper").getString("type"));
        assertEquals("integer", properties.getJsonObject("intPrimitive").getString("type"));
        assertEquals("integer", properties.getJsonObject("intWrapper").getString("type"));
        assertEquals("integer", properties.getJsonObject("longPrimitive").getString("type"));
        assertEquals("integer", properties.getJsonObject("longWrapper").getString("type"));
        assertEquals("number", properties.getJsonObject("floatPrimitive").getString("type"));
        assertEquals("number", properties.getJsonObject("floatWrapper").getString("type"));
        assertEquals("number", properties.getJsonObject("doublePrimitive").getString("type"));
        assertEquals("number", properties.getJsonObject("doubleWrapper").getString("type"));
        assertEquals("string", properties.getJsonObject("charPrimitive").getString("type"));
        assertEquals("string", properties.getJsonObject("charWrapper").getString("type"));
    }

    enum Status { PENDING, APPROVED, REJECTED }

    record WithEnum(Status status) {}

    @Test
    void buildJsonSchema_withEnum() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithEnum.class);
        var statusSchema = schema.getJsonObject("properties").getJsonObject("status");

        assertEquals("string", statusSchema.getString("type"));
        var enumValues = statusSchema.getJsonArray("enum");
        assertEquals(3, enumValues.size());
        assertTrue(enumValues.toString().contains("PENDING"));
        assertTrue(enumValues.toString().contains("APPROVED"));
        assertTrue(enumValues.toString().contains("REJECTED"));
    }

    record WithList(List<String> items) {}

    @Test
    void buildJsonSchema_withList() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithList.class);
        var itemsSchema = schema.getJsonObject("properties").getJsonObject("items");

        assertEquals("array", itemsSchema.getString("type"));
        assertEquals("string", itemsSchema.getJsonObject("items").getString("type"));
    }

    record WithArray(int[] numbers) {}

    @Test
    void buildJsonSchema_withArray() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithArray.class);
        var numbersSchema = schema.getJsonObject("properties").getJsonObject("numbers");

        assertEquals("array", numbersSchema.getString("type"));
        assertEquals("integer", numbersSchema.getJsonObject("items").getString("type"));
    }

    record Inner(String value) {}
    record Outer(Inner inner) {}

    @Test
    void buildJsonSchema_nested() {
        var schema = JsonSchemaHelper.buildJsonSchema(Outer.class);
        var innerSchema = schema.getJsonObject("properties").getJsonObject("inner");

        assertEquals("object", innerSchema.getString("type"));
        assertEquals("string", innerSchema.getJsonObject("properties").getJsonObject("value").getString("type"));
    }

    record WithOptional(String required, Optional<String> optional) {}

    @Test
    void buildJsonSchema_withOptional() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithOptional.class);
        var properties = schema.getJsonObject("properties");

        assertEquals("string", properties.getJsonObject("required").getString("type"));
        assertEquals("string", properties.getJsonObject("optional").getString("type"));

        var required = schema.getJsonArray("required");
        assertTrue(required.toString().contains("required"));
        assertFalse(required.toString().contains("optional"));
    }

    record ProductReview(String sentiment, int rating, List<String> pros, List<String> cons) {}

    @Test
    void buildJsonSchema_productReview() {
        var schema = JsonSchemaHelper.buildJsonSchema(ProductReview.class);
        var properties = schema.getJsonObject("properties");

        assertEquals("string", properties.getJsonObject("sentiment").getString("type"));
        assertEquals("integer", properties.getJsonObject("rating").getString("type"));
        assertEquals("array", properties.getJsonObject("pros").getString("type"));
        assertEquals("string", properties.getJsonObject("pros").getJsonObject("items").getString("type"));
        assertEquals("array", properties.getJsonObject("cons").getString("type"));
        assertEquals("string", properties.getJsonObject("cons").getJsonObject("items").getString("type"));
    }

    record WithMap(Map<String, Double> scores) {}

    @Test
    void buildJsonSchema_withMap() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithMap.class);
        var scoresSchema = schema.getJsonObject("properties").getJsonObject("scores");

        assertEquals("object", scoresSchema.getString("type"));
        assertEquals("number", scoresSchema.getJsonObject("additionalProperties").getString("type"));
    }

    @Test
    void moderationResponseSchema() {
        var schema = AITextHandler.MODERATION_RESPONSE_SCHEMA;

        assertEquals("object", schema.getString("type"));
        var scoresSchema = schema.getJsonObject("properties").getJsonObject("scores");
        assertEquals("object", scoresSchema.getString("type"));
        assertEquals("number", scoresSchema.getJsonObject("additionalProperties").getString("type"));
    }

    // =================================================================================================================
    // Test beans
    // =================================================================================================================

    public static class SimpleBean {
        private String name;
        private int age;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    @Test
    void buildJsonSchema_simpleBean() {
        var schema = JsonSchemaHelper.buildJsonSchema(SimpleBean.class);

        assertEquals("object", schema.getString("type"));

        var properties = schema.getJsonObject("properties");
        assertEquals("string", properties.getJsonObject("name").getString("type"));
        assertEquals("integer", properties.getJsonObject("age").getString("type"));

        var required = schema.getJsonArray("required");
        assertTrue(required.toString().contains("name"));
        assertTrue(required.toString().contains("age"));
    }

    public static class BeanWithBoolean {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    @Test
    void buildJsonSchema_beanWithBooleanGetter() {
        var schema = JsonSchemaHelper.buildJsonSchema(BeanWithBoolean.class);
        var properties = schema.getJsonObject("properties");

        assertEquals("boolean", properties.getJsonObject("active").getString("type"));
    }

    public static class BeanWithList {
        private List<Integer> scores;
        public List<Integer> getScores() { return scores; }
        public void setScores(List<Integer> scores) { this.scores = scores; }
    }

    @Test
    void buildJsonSchema_beanWithList() {
        var schema = JsonSchemaHelper.buildJsonSchema(BeanWithList.class);
        var scoresSchema = schema.getJsonObject("properties").getJsonObject("scores");

        assertEquals("array", scoresSchema.getString("type"));
        assertEquals("integer", scoresSchema.getJsonObject("items").getString("type"));
    }
}
