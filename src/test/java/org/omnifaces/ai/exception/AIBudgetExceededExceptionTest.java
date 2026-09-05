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
package org.omnifaces.ai.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

/**
 * Pricing carries a currency only when the caller configured one, so the budget message names an amount either way rather than an amount in an unknown unit.
 */
class AIBudgetExceededExceptionTest {

    private static final BigDecimal TOTAL_COST = new BigDecimal("1.50");
    private static final BigDecimal MAX_TOTAL_COST = new BigDecimal("1.00");

    @Test
    void getMessage_withCurrency_namesTheCurrencyOfBothAmounts() {
        var exception = new AIBudgetExceededException(TOTAL_COST, MAX_TOTAL_COST, Currency.getInstance("EUR"));

        assertEquals("Budget exceeded: 1.50 EUR >= cap of 1.00 EUR", exception.getMessage());
    }

    @Test
    void getMessage_withoutCurrency_namesTheAmountsAlone() {
        var exception = new AIBudgetExceededException(TOTAL_COST, MAX_TOTAL_COST, null);

        assertEquals("Budget exceeded: 1.50 >= cap of 1.00", exception.getMessage());
    }

    @Test
    void carriesTheCostsAndTheCurrency() {
        var currency = Currency.getInstance("USD");
        var exception = new AIBudgetExceededException(TOTAL_COST, MAX_TOTAL_COST, currency);

        assertEquals(TOTAL_COST, exception.getTotalCost());
        assertEquals(MAX_TOTAL_COST, exception.getMaxTotalCost());
        assertEquals(currency, exception.getCurrency());
    }

}
