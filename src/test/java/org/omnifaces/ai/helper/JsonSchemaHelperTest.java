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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

class JsonSchemaHelperTest {

    // =================================================================================================================
    // Test records
    // =================================================================================================================

    record SimpleRecord(String name, int age) {
    }

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
    ) {
    }

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

    enum Status {
        PENDING,
        APPROVED,
        REJECTED
    }

    record WithEnum(Status status) {
    }

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

    record WithList(List<String> items) {
    }

    @Test
    void buildJsonSchema_withList() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithList.class);
        var itemsSchema = schema.getJsonObject("properties").getJsonObject("items");

        assertEquals("array", itemsSchema.getString("type"));
        assertEquals("string", itemsSchema.getJsonObject("items").getString("type"));
    }

    record WithArray(int[] numbers) {
    }

    @Test
    void buildJsonSchema_withArray() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithArray.class);
        var numbersSchema = schema.getJsonObject("properties").getJsonObject("numbers");

        assertEquals("array", numbersSchema.getString("type"));
        assertEquals("integer", numbersSchema.getJsonObject("items").getString("type"));
    }

    record Inner(String value) {
    }

    record Outer(Inner inner) {
    }

    @Test
    void buildJsonSchema_nested() {
        var schema = JsonSchemaHelper.buildJsonSchema(Outer.class);
        var innerSchema = schema.getJsonObject("properties").getJsonObject("inner");

        assertEquals("object", innerSchema.getString("type"));
        assertEquals("string", innerSchema.getJsonObject("properties").getJsonObject("value").getString("type"));
    }

    record WithOptional(String required, Optional<String> optional) {
    }

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

    record ProductReview(String sentiment, int rating, List<String> pros, List<String> cons) {
    }

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

    record WithMap(Map<String, Double> scores) {
    }

    @Test
    void buildJsonSchema_withMap() {
        var schema = JsonSchemaHelper.buildJsonSchema(WithMap.class);
        var scoresSchema = schema.getJsonObject("properties").getJsonObject("scores");

        assertEquals("object", scoresSchema.getString("type"));
        assertEquals("number", scoresSchema.getJsonObject("additionalProperties").getString("type"));
    }

    // =================================================================================================================
    // Test fromJson - records
    // =================================================================================================================

    @Test
    void fromJson_simpleRecord() {
        var json = "{\"name\":\"John\",\"age\":30}";
        var result = JsonSchemaHelper.fromJson(json, SimpleRecord.class);

        assertEquals("John", result.name());
        assertEquals(30, result.age());
    }

    @Test
    void fromJson_withEnum() {
        var json = "{\"status\":\"APPROVED\"}";
        var result = JsonSchemaHelper.fromJson(json, WithEnum.class);

        assertEquals(Status.APPROVED, result.status());
    }

    @Test
    void fromJson_withList() {
        var json = "{\"items\":[\"a\",\"b\",\"c\"]}";
        var result = JsonSchemaHelper.fromJson(json, WithList.class);

        assertEquals(List.of("a", "b", "c"), result.items());
    }

    @Test
    void fromJson_withArray() {
        var json = "{\"numbers\":[1,2,3]}";
        var result = JsonSchemaHelper.fromJson(json, WithArray.class);

        assertArrayEquals(new int[] { 1, 2, 3 }, result.numbers());
    }

    @Test
    void fromJson_nested() {
        var json = "{\"inner\":{\"value\":\"test\"}}";
        var result = JsonSchemaHelper.fromJson(json, Outer.class);

        assertEquals("test", result.inner().value());
    }

    @Test
    void fromJson_withOptional_present() {
        var json = "{\"required\":\"yes\",\"optional\":\"maybe\"}";
        var result = JsonSchemaHelper.fromJson(json, WithOptional.class);

        assertEquals("yes", result.required());
        assertEquals(Optional.of("maybe"), result.optional());
    }

    @Test
    void fromJson_withOptional_absent() {
        var json = "{\"required\":\"yes\"}";
        var result = JsonSchemaHelper.fromJson(json, WithOptional.class);

        assertEquals("yes", result.required());
        assertEquals(Optional.empty(), result.optional());
    }

    @Test
    void fromJson_withOptional_null() {
        var json = "{\"required\":\"yes\",\"optional\":null}";
        var result = JsonSchemaHelper.fromJson(json, WithOptional.class);

        assertEquals("yes", result.required());
        assertEquals(Optional.empty(), result.optional());
    }

    @Test
    void fromJson_productReview() {
        var json = "{\"sentiment\":\"positive\",\"rating\":5,\"pros\":[\"great quality\",\"fast shipping\"],\"cons\":[\"expensive\"]}";
        var result = JsonSchemaHelper.fromJson(json, ProductReview.class);

        assertEquals("positive", result.sentiment());
        assertEquals(5, result.rating());
        assertEquals(List.of("great quality", "fast shipping"), result.pros());
        assertEquals(List.of("expensive"), result.cons());
    }

    @Test
    void fromJson_withMap() {
        var json = "{\"scores\":{\"hate\":0.1,\"violence\":0.05}}";
        var result = JsonSchemaHelper.fromJson(json, WithMap.class);

        assertEquals(0.1, result.scores().get("hate"), 0.001);
        assertEquals(0.05, result.scores().get("violence"), 0.001);
    }

    // =================================================================================================================
    // Test beans
    // =================================================================================================================

    public static class SimpleBean {

        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

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

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

    }

    @Test
    void buildJsonSchema_beanWithBooleanGetter() {
        var schema = JsonSchemaHelper.buildJsonSchema(BeanWithBoolean.class);
        var properties = schema.getJsonObject("properties");

        assertEquals("boolean", properties.getJsonObject("active").getString("type"));
    }

    public static class BeanWithList {

        private List<Integer> scores;

        public List<Integer> getScores() {
            return scores;
        }

        public void setScores(List<Integer> scores) {
            this.scores = scores;
        }

    }

    @Test
    void buildJsonSchema_beanWithList() {
        var schema = JsonSchemaHelper.buildJsonSchema(BeanWithList.class);
        var scoresSchema = schema.getJsonObject("properties").getJsonObject("scores");

        assertEquals("array", scoresSchema.getString("type"));
        assertEquals("integer", scoresSchema.getJsonObject("items").getString("type"));
    }

    // =================================================================================================================
    // Temporal types
    // =================================================================================================================

    record WithTemporals(Instant instant, LocalDate date, LocalTime time, LocalDateTime dateTime, ZonedDateTime zoned) {
    }

    /**
     * A date carries no time and a time no date, so each states the JSON Schema format it serializes as; anything carrying both halves is a full timestamp.
     */
    @Test
    void buildJsonSchema_temporals_stateTheFormatTheySerializeAs() {
        var properties = JsonSchemaHelper.buildJsonSchema(WithTemporals.class).getJsonObject("properties");

        assertEquals("date-time", properties.getJsonObject("instant").getString("format"));
        assertEquals("date", properties.getJsonObject("date").getString("format"));
        assertEquals("time", properties.getJsonObject("time").getString("format"));
        assertEquals("date-time", properties.getJsonObject("dateTime").getString("format"));
        assertEquals("date-time", properties.getJsonObject("zoned").getString("format"));
    }

    @Test
    void fromJson_temporals_areParsedFromTheirIso8601Text() {
        var parsed = JsonSchemaHelper.fromJson("""
            {"instant":"2026-01-31T10:15:30Z","date":"2026-01-31","time":"10:15:30","dateTime":"2026-01-31T10:15:30",
             "zoned":"2026-01-31T10:15:30+01:00[Europe/Amsterdam]"}
            """, WithTemporals.class);

        assertEquals(Instant.parse("2026-01-31T10:15:30Z"), parsed.instant());
        assertEquals(LocalDate.of(2026, 1, 31), parsed.date());
        assertEquals(LocalTime.of(10, 15, 30), parsed.time());
        assertEquals(LocalDateTime.of(2026, 1, 31, 10, 15, 30), parsed.dateTime());
        assertEquals(ZonedDateTime.parse("2026-01-31T10:15:30+01:00[Europe/Amsterdam]"), parsed.zoned());
    }

    // =================================================================================================================
    // Collections and maps
    // =================================================================================================================

    record WithCollections(Set<String> set, TreeSet<String> sorted, List<String> list) {
    }

    /**
     * A set which does not sort of its own accord keeps the order the AI answered in, as that order carries meaning the AI intended.
     */
    @Test
    void fromJson_collections_keepTheOrderTheirTypeImplies() {
        var parsed = JsonSchemaHelper.fromJson("""
            {"set":["c","a","b"],"sorted":["c","a","b"],"list":["c","a","b"]}
            """, WithCollections.class);

        assertEquals(List.of("c", "a", "b"), List.copyOf(parsed.set()));
        assertEquals(List.of("a", "b", "c"), List.copyOf(parsed.sorted()));
        assertEquals(List.of("c", "a", "b"), parsed.list());
    }

    record WithSortedMap(TreeMap<String, Integer> scores) {
    }

    @Test
    void fromJson_sortedMap_isSorted() {
        var parsed = JsonSchemaHelper.fromJson("{\"scores\":{\"c\":3,\"a\":1}}", WithSortedMap.class);

        assertEquals(List.of("a", "c"), List.copyOf(parsed.scores().keySet()));
    }

    // =================================================================================================================
    // Absent values
    // =================================================================================================================

    record WithPrimitives(boolean flag, char letter, int number, String text) {
    }

    /**
     * The AI may answer with a field missing or null, which a primitive cannot hold, so each falls back to the value its type starts at.
     */
    @Test
    void fromJson_absentPrimitives_fallBackToTheirDefaults() {
        var parsed = JsonSchemaHelper.fromJson("{}", WithPrimitives.class);

        assertFalse(parsed.flag());
        assertEquals('\0', parsed.letter());
        assertEquals(0, parsed.number());
        assertNull(parsed.text());
    }

    // =================================================================================================================
    // Beans
    // =================================================================================================================

    public static class Bean {

        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

    }

    /**
     * A type which is not a record is filled through its setters, so that a caller can hand over a class it already has.
     */
    @Test
    void fromJson_bean_isFilledThroughItsSetters() {
        var parsed = JsonSchemaHelper.fromJson("{\"name\":\"Bauke\",\"age\":42}", Bean.class);

        assertEquals("Bauke", parsed.getName());
        assertEquals(42, parsed.getAge());
    }

    @Test
    void fromJson_bean_ignoresAFieldTheJsonDoesNotState() {
        assertNull(JsonSchemaHelper.fromJson("{\"age\":42}", Bean.class).getName());
    }

    public static class WithoutDefaultConstructor {

        public WithoutDefaultConstructor(String name) {
            // The name is of no interest here; what matters is that there is no constructor to instantiate this with.
        }

    }

    @Test
    void fromJson_typeWithoutADefaultConstructor_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> JsonSchemaHelper.fromJson("{}", WithoutDefaultConstructor.class));
    }

    // =================================================================================================================
    // The remaining number types
    // =================================================================================================================

    record WithNumbers(short small, byte tiny, float fraction, BigInteger big, BigDecimal exact, long count, double rate) {
    }

    /**
     * Every number type the JDK offers is answered as the type the field declares, so that a caller does not have to convert what the AI returned.
     */
    @Test
    void fromJson_everyNumberType_isParsedAsItsOwnType() {
        var parsed = JsonSchemaHelper.fromJson("""
            {"small":1,"tiny":2,"fraction":3.5,"big":4,"exact":5.25,"count":6,"rate":7.5}
            """, WithNumbers.class);

        assertEquals((short) 1, parsed.small());
        assertEquals((byte) 2, parsed.tiny());
        assertEquals(3.5f, parsed.fraction());
        assertEquals(BigInteger.valueOf(4), parsed.big());
        assertEquals(new BigDecimal("5.25"), parsed.exact());
        assertEquals(6L, parsed.count());
        assertEquals(7.5, parsed.rate());
    }

    record WithCharacter(char letter, Character boxed) {
    }

    @Test
    void buildJsonSchema_characters_areStrings() {
        var properties = JsonSchemaHelper.buildJsonSchema(WithCharacter.class).getJsonObject("properties");

        assertEquals("string", properties.getJsonObject("letter").getString("type"));
        assertEquals("string", properties.getJsonObject("boxed").getString("type"));
    }

    // =================================================================================================================
    // Generic array components
    // =================================================================================================================

    record WithArrayOfArrays(List<String[]> rows) {
    }

    /**
     * An element type which is itself an array is resolved to that array type rather than to Object, so the elements land in an array of the right kind.
     */
    @Test
    void fromJson_collectionOfArrays_keepsTheArrayComponentType() {
        var parsed = JsonSchemaHelper.fromJson("{\"rows\":[[\"a\",\"b\"]]}", WithArrayOfArrays.class);

        assertEquals(1, parsed.rows().size());
        assertArrayEquals(new String[] { "a", "b" }, parsed.rows().get(0));
    }

    // =================================================================================================================
    // Recursive and awkward types
    // =================================================================================================================

    record Node(String name, Node child) {
    }

    /**
     * A type which refers to itself would describe itself forever, so the second visit states the bare object and lets the AI fill in what it can.
     */
    @Test
    void buildJsonSchema_recursiveType_statesTheRepeatOnceAsABareObject() {
        var child = JsonSchemaHelper.buildJsonSchema(Node.class).getJsonObject("properties").getJsonObject("child");

        assertEquals("object", child.getString("type"));
        assertFalse(child.containsKey("properties"));
    }

    /**
     * A character is one character of the text the AI answered, not the text itself.
     */
    @Test
    void fromJson_characterValue_isTheFirstCharacterOfTheText() {
        var parsed = JsonSchemaHelper.fromJson("{\"letter\":\"x\",\"boxed\":\"y\"}", WithCharacter.class);

        assertEquals('x', parsed.letter());
        assertEquals('y', parsed.boxed());
    }

    // =================================================================================================================
    // Beans which cannot be filled
    // =================================================================================================================

    public static class ReadOnlyBean {

        public String getName() {
            return "fixed";
        }

    }

    /**
     * A property the AI answered but the type cannot accept is skipped rather than refused, as the rest of the answer is still usable.
     */
    @Test
    void fromJson_beanPropertyWithoutASetter_isSkipped() {
        assertEquals("fixed", JsonSchemaHelper.fromJson("{\"name\":\"answered\"}", ReadOnlyBean.class).getName());
    }

    public static class FailingSetterBean {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            throw new IllegalStateException("cannot set");
        }

    }

    @Test
    void fromJson_beanWhoseSetterThrows_namesTheProperty() {
        var exception = assertThrows(IllegalArgumentException.class, () -> JsonSchemaHelper.fromJson("{\"name\":\"answered\"}", FailingSetterBean.class));

        assertTrue(exception.getMessage().contains("name"));
    }

    // =================================================================================================================
    // Generic array components
    // =================================================================================================================

    record WithGenericArrayComponent(List<String>[] rows) {
    }

    /**
     * An element type which is a generic array is resolved to its array kind, so the elements land in an array rather than in a plain object.
     */
    @Test
    void buildJsonSchema_genericArrayComponent_isDescribedAsAnArray() {
        var rows = JsonSchemaHelper.buildJsonSchema(WithGenericArrayComponent.class).getJsonObject("properties").getJsonObject("rows");

        assertEquals("array", rows.getString("type"));
    }

    // =================================================================================================================
    // Nested generics
    // =================================================================================================================

    record WithNestedCollections(List<List<String>> rows) {
    }

    /**
     * A collection whose elements are themselves generic keeps the kind its elements were declared as, rather than falling back to whatever the JSON states.
     */
    @Test
    void fromJson_collectionOfCollections_keepsTheInnerCollections() {
        var parsed = JsonSchemaHelper.fromJson("{\"rows\":[[\"a\",\"b\"],[\"c\"]]}", WithNestedCollections.class);

        assertEquals(2, parsed.rows().size());
        assertEquals(List.of("a", "b"), parsed.rows().get(0));
        assertEquals(List.of("c"), parsed.rows().get(1));
    }

    record WithArraysOfCollections(List<List<String>[]> rows) {
    }

    /**
     * An element type which is an array of a generic type is resolved to that array kind rather than to a bare object.
     */
    @Test
    void buildJsonSchema_collectionOfGenericArrays_isDescribedAsNestedArrays() {
        var rows = JsonSchemaHelper.buildJsonSchema(WithArraysOfCollections.class).getJsonObject("properties").getJsonObject("rows");

        assertEquals("array", rows.getString("type"));
        assertEquals("array", rows.getJsonObject("items").getString("type"));
    }

    record WithWildcardElements(List<? extends CharSequence> rows) {
    }

    /**
     * An element type stated as a bound rather than a type names no class to describe, so the elements are described as whatever the AI answers with.
     */
    @Test
    void buildJsonSchema_collectionOfABoundedType_describesItsElementsAsUnconstrained() {
        var rows = JsonSchemaHelper.buildJsonSchema(WithWildcardElements.class).getJsonObject("properties").getJsonObject("rows");

        assertEquals("array", rows.getString("type"));
    }

    // =================================================================================================================
    // Beans stating a property which cannot be read
    // =================================================================================================================

    public static class WriteOnlyBean {

        private String name;

        public void setName(String name) {
            this.name = name;
        }

        String peek() {
            return name;
        }

    }

    /**
     * A property the type cannot answer is not part of what the AI is asked for, so it is left out of the schema and still accepted in the answer.
     */
    @Test
    void buildJsonSchema_beanPropertyWithoutAGetter_isNotAskedFor() {
        var schema = JsonSchemaHelper.buildJsonSchema(WriteOnlyBean.class);

        assertFalse(schema.getJsonObject("properties").containsKey("name"));
    }

}
