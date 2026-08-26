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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

class ChatPricingTest {

    // =================================================================================================================
    // Constructor tests
    // =================================================================================================================

    @Test
    void new_validPrices_createsRecord() {
        var usd = Currency.getInstance("USD");
        var pricing = new ChatPricing(new BigDecimal("3.00"), new BigDecimal("0.30"), new BigDecimal("15.00"), usd);

        assertEquals(new BigDecimal("3.00"), pricing.inputTokenPrice());
        assertEquals(new BigDecimal("0.30"), pricing.cachedInputTokenPrice());
        assertEquals(new BigDecimal("15.00"), pricing.outputTokenPrice());
        assertEquals(usd, pricing.currency());
    }

    @Test
    void new_nullCachedPriceAllowed() {
        var pricing = new ChatPricing(new BigDecimal("3.00"), null, new BigDecimal("15.00"), null);

        assertNull(pricing.cachedInputTokenPrice());
        assertNull(pricing.currency());
    }

    @Test
    void new_zeroPricesAllowed() {
        var pricing = new ChatPricing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);

        assertEquals(BigDecimal.ZERO, pricing.inputTokenPrice());
        assertEquals(BigDecimal.ZERO, pricing.cachedInputTokenPrice());
        assertEquals(BigDecimal.ZERO, pricing.outputTokenPrice());
    }

    @Test
    void new_nullInputTokenPrice_throwsNPE() {
        var outputTokenPrice = new BigDecimal("15.00");

        assertThrows(NullPointerException.class, () -> new ChatPricing(null, null, outputTokenPrice, null));
    }

    @Test
    void new_nullOutputTokenPrice_throwsNPE() {
        var inputTokenPrice = new BigDecimal("3.00");

        assertThrows(NullPointerException.class, () -> new ChatPricing(inputTokenPrice, null, null, null));
    }

    @Test
    void new_negativeInputTokenPrice_throwsIAE() {
        var inputTokenPrice = new BigDecimal("-1");
        var outputTokenPrice = new BigDecimal("15.00");

        assertThrows(IllegalArgumentException.class, () -> new ChatPricing(inputTokenPrice, null, outputTokenPrice, null));
    }

    @Test
    void new_negativeCachedInputTokenPrice_throwsIAE() {
        var inputTokenPrice = new BigDecimal("3.00");
        var cachedInputTokenPrice = new BigDecimal("-0.1");
        var outputTokenPrice = new BigDecimal("15.00");

        assertThrows(IllegalArgumentException.class, () -> new ChatPricing(inputTokenPrice, cachedInputTokenPrice, outputTokenPrice, null));
    }

    @Test
    void new_negativeOutputTokenPrice_throwsIAE() {
        var inputTokenPrice = new BigDecimal("3.00");
        var outputTokenPrice = new BigDecimal("-15.00");

        assertThrows(IllegalArgumentException.class, () -> new ChatPricing(inputTokenPrice, null, outputTokenPrice, null));
    }

    // =================================================================================================================
    // Factory method tests
    // =================================================================================================================

    @Test
    void of_inputOutput_leavesCachedAndCurrencyNull() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("15.00"));

        assertEquals(new BigDecimal("3.00"), pricing.inputTokenPrice());
        assertNull(pricing.cachedInputTokenPrice());
        assertEquals(new BigDecimal("15.00"), pricing.outputTokenPrice());
        assertNull(pricing.currency());
    }

    @Test
    void of_withCached_leavesCurrencyNull() {
        var pricing = ChatPricing.of(new BigDecimal("3.00"), new BigDecimal("0.30"), new BigDecimal("15.00"));

        assertEquals(new BigDecimal("3.00"), pricing.inputTokenPrice());
        assertEquals(new BigDecimal("0.30"), pricing.cachedInputTokenPrice());
        assertEquals(new BigDecimal("15.00"), pricing.outputTokenPrice());
        assertNull(pricing.currency());
    }

    // =================================================================================================================
    // effectiveCachedInputTokenPrice
    // =================================================================================================================

    @Test
    void effectiveCachedInputTokenPrice_whenSet_returnsCachedPrice() {
        var pricing = new ChatPricing(new BigDecimal("3.00"), new BigDecimal("0.30"), new BigDecimal("15.00"), null);

        assertEquals(new BigDecimal("0.30"), pricing.effectiveCachedInputTokenPrice());
    }

    @Test
    void effectiveCachedInputTokenPrice_whenNull_fallsBackToInputPrice() {
        var pricing = new ChatPricing(new BigDecimal("3.00"), null, new BigDecimal("15.00"), null);

        assertEquals(new BigDecimal("3.00"), pricing.effectiveCachedInputTokenPrice());
    }

    // =================================================================================================================
    // Serialization tests
    // =================================================================================================================

    @Test
    void implementsSerializable() {
        assertTrue(Serializable.class.isAssignableFrom(ChatPricing.class));
    }

    @Test
    void serialization_roundTripsAllFields() throws Exception {
        var original = new ChatPricing(new BigDecimal("3.00"), new BigDecimal("0.30"), new BigDecimal("15.00"), Currency.getInstance("USD"));

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            var deserialized = (ChatPricing) ois.readObject();
            assertEquals(original, deserialized);
        }
    }

    @Test
    void serialization_roundTripsNulls() throws Exception {
        var original = new ChatPricing(new BigDecimal("3.00"), null, new BigDecimal("15.00"), null);

        var baos = new ByteArrayOutputStream();
        try (var oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        try (var ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            var deserialized = (ChatPricing) ois.readObject();
            assertEquals(original, deserialized);
        }
    }

}
