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

import static java.lang.System.lineSeparator;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A class which carries static members alone is not meant to be instantiated, and hides a constructor which throws so that reflection cannot make one either.
 * <p>
 * The classes are discovered rather than listed, so that one added later is held to the same contract without anyone remembering to add it here.
 */
class UtilityClassesTest {

    @Test
    void everyUtilityClass_refusesToBeInstantiated() throws Exception {
        var utilityClasses = findUtilityClasses();
        var failures = new ArrayList<String>();

        assertFalse(utilityClasses.isEmpty(), "the scan found no utility class at all, so it proves nothing");

        for (var type : utilityClasses) {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);

            try {
                constructor.newInstance();
                failures.add(type.getName() + ": can be instantiated");
            }
            catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof AssertionError)) {
                    failures.add(type.getName() + ": threw " + e.getCause());
                }
            }
        }

        if (!failures.isEmpty()) {
            fail("Instantiable utility classes:" + lineSeparator() + failures.stream().sorted().collect(joining(lineSeparator())));
        }
    }

    private static List<Class<?>> findUtilityClasses() throws Exception {
        var root = Path.of(OmniHai.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        try (var files = Files.walk(root)) {
            return files.filter(file -> file.toString().endsWith(".class"))
                .map(file -> root.relativize(file).toString().replace(File.separatorChar, '.').replaceAll("\\.class$", ""))
                .<Class<?>>map(UtilityClassesTest::load)
                .filter(UtilityClassesTest::isUtilityClass)
                .toList();
        }
    }

    /**
     * Loads without initializing, as the scan visits every class of the library and only the ones it goes on to instantiate should run their static blocks.
     */
    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, UtilityClassesTest.class.getClassLoader());
        }
        catch (ClassNotFoundException | LinkageError e) {
            throw new AssertionError("Cannot load " + name, e);
        }
    }

    private static boolean isUtilityClass(Class<?> type) {
        if (type.isEnum() || type.isRecord() || type.isInterface() || type.isAnonymousClass() || Modifier.isPrivate(type.getModifiers())) {
            return false;
        }

        var constructors = type.getDeclaredConstructors();

        if (constructors.length != 1 || !Modifier.isPrivate(constructors[0].getModifiers()) || constructors[0].getParameterCount() != 0) {
            return false;
        }

        var methods = type.getDeclaredMethods();
        return methods.length > 0 && stream(methods).allMatch(method -> Modifier.isStatic(method.getModifiers()));
    }

}
