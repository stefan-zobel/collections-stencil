/*
 * Copyright 2026 The collections-stencil authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.collections.stencil.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import net.generated.example.ObjectIntHashMap;

/**
 * Calls every benchmark method of the {@code String -> int} adapters once, outside JMH, and
 * checks that all implementations compute the same results. This is a functional test of the
 * adapters, not a measurement.
 */
class ObjectIntBenchAdaptersTest {

    private static final List<Class<? extends AbstractObjectIntBench>> ADAPTERS = List.of(
            StencilObjectIntBench.class, FastutilObjectIntBench.class, HppcObjectIntBench.class,
            EclipseObjectIntBench.class, JdkObjectIntBench.class);

    private static final List<String> OPS = List.of("getHit", "getMiss", "count", "churn", "getHit", "count", "churn",
            "getHit", "getMiss");

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void adaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractObjectIntBench> cls : ADAPTERS) {
            AbstractObjectIntBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = dist;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            for (String op : OPS) {
                Method m = cls.getMethod(op);
                results.add(op + "=" + m.invoke(bench));
            }
            Object built = cls.getMethod("build", GcBeforeLargeBuild.class).invoke(bench, (Object) null);
            results.add("build=" + built.getClass().getMethod("size").invoke(built));
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertEquals("getMiss=0", expected.get(1), "no miss found");
        assertEquals("build=1400", expected.getLast());
    }

    @Test
    void deepFootprintExcludesKeysButCountsBoxes() {
        int n = 10_000;
        ObjectIntHashMap<String> stencil = new ObjectIntHashMap<>();
        HashMap<String, Integer> jdk = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String k = KeyDist.RANDOM.stringKey(i);
            stencil.put(k, 1000 + i);
            jdk.put(k, 1000 + i);
        }
        double stencilPerEntry = Footprint.deepBytes(stencil, Footprint.Mode.DEEP_SHARED_KEYS) / (double) n;
        double jdkPerEntry = Footprint.deepBytes(jdk, Footprint.Mode.DEEP_SHARED_KEYS) / (double) n;
        // 4-byte stored hash + 4-byte reference + 4-byte int per slot, 16384 slots for 10000 entries.
        assertTrue(stencilPerEntry > 19 && stencilPerEntry < 20, "stencil: " + stencilPerEntry);
        // Node (32) + Integer (16) + 4-byte table slot per 0.5 to 0.75 entries.
        assertTrue(jdkPerEntry > 52 && jdkPerEntry < 56, "jdk: " + jdkPerEntry);
    }
}
