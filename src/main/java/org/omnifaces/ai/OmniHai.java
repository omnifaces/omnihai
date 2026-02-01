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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Provides access to OmniHai application properties.
 *
 * @author Bauke Scholtz
 * @since 1.0
 */
public final class OmniHai {

    private static final Logger logger = Logger.getLogger(OmniHai.class.getPackageName());

    private static String name;
    private static String version;
    private static String url;
    private static String userAgent;

    static {
        var properties = new Properties();

        try (InputStream inputStream = OmniHai.class.getResourceAsStream("/application.properties")) {
            if (inputStream == null) {
                throw new IllegalStateException("application.properties not found on classpath");
            }

            properties.load(inputStream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read application.properties", e);
        }

        name = properties.getProperty("project.name", "OmniHai");
        version = properties.getProperty("project.version", "DEV-SNAPSHOT");
        url = properties.getProperty("project.url", "https://omnihai.org");
        userAgent = String.format("%s %s (%s)", name, version, url);
        logger.info("Using " + userAgent);
    }

    /**
     * Returns the OmniHai brand name.
     * @return The OmniHai brand name.
     */
    public static String name() {
        return name;
    }

    /**
     * Returns the OmniHai version currently used.
     * @return The OmniHai version currently used.
     */
    public static String version() {
        return version;
    }

    /**
     * Returns the OmniHai homepage URL.
     * @return The OmniHai homepage URL.
     */
    public static String url() {
        return url;
    }

    /**
     * Returns the OmniHai user agent string suitable for HTTP {@code User-Agent} header.
     * @return The OmniHai user agent string suitable for HTTP {@code User-Agent} header.
     */
    public static String userAgent() {
        return userAgent;
    }
}
