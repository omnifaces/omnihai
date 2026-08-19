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

import java.io.Serializable;

/**
 * Result of a text classification.
 *
 * @param label The label which the AI picked, being one of those it was offered.
 * @param confidence How sure the AI is of the label, from 0.0 to 1.0.
 * @author Bauke Scholtz
 * @since 1.7
 * @see org.omnifaces.ai.AIService#classify(String, java.util.List)
 */
public final record ClassificationResult(String label, double confidence) implements Serializable {
    //
}
