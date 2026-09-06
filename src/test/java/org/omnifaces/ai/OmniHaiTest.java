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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The library identifies itself to every AI provider it calls, so the name, the version and the URL are read from the build rather than hardcoded, and the user
 * agent is assembled from all three.
 */
class OmniHaiTest {

    @Test
    void name_isTheBrandName() {
        assertEquals("OmniHai", OmniHai.name());
    }

    @Test
    void version_isTheBuildVersion() {
        assertTrue(OmniHai.version().matches("\\d++\\.\\d++.*"), OmniHai.version());
    }

    @Test
    void url_isTheProjectUrl() {
        assertTrue(OmniHai.url().startsWith("https://"), OmniHai.url());
    }

    @Test
    void userAgent_namesTheLibraryItsVersionAndItsUrl() {
        assertEquals(OmniHai.name() + " " + OmniHai.version() + " (" + OmniHai.url() + ")", OmniHai.userAgent());
    }

}
