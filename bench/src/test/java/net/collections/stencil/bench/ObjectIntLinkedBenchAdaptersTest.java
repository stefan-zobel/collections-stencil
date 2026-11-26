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
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import net.generated.example.ObjectIntLinkedHashMap;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;

/**
 * Calls every benchmark method of the adapters for insertion-ordered maps with {@code String} keys
 * once, outside JMH, and checks that all implementations compute the same results. This is a
 * functional test of the adapters, not a measurement.
 */
class ObjectIntLinkedBenchAdaptersTest {

    private static final List<Class<? extends AbstractObjectIntLinkedBench>> ADAPTERS = List.of(
            StencilObjectIntLinkedBench.class, FastutilObjectIntLinkedBench.class,
            SpeigerObjectIntLinkedBench.class, JdkObjectIntLinkedBench.class);

    /** The lookups come first: the later operations replace keys. Every result depends on the order of the entries. */
    private static final List<String> OPS = List.of(
            "getHit", "getMiss", "iterate", "count", "touch", "iterate", "fifo", "touch", "churn", "fifo", "touch",
            "churn", "count", "iterate", "getMiss");

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void adaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractObjectIntLinkedBench> cls : ADAPTERS) {
            AbstractObjectIntLinkedBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = dist;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            for (String op : OPS) {
                Method m = cls.getMethod(op);
                results.add(op + "=" + m.invoke(bench));
            }
            // The last key after all operations: the newest entry is the same in every implementation.
            bench.hits = new String[0];
            results.add("last=" + cls.getMethod("touch").invoke(bench));
            Object built = cls.getMethod("build", GcBeforeLargeBuild.class).invoke(bench, (Object) null);
            results.add("build=" + built.getClass().getMethod("size").invoke(built));
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertEquals("getMiss=0", expected.get(1), "no miss found");
        assertEquals("getMiss=0", expected.get(14), "no miss found after the updates");
        assertEquals("build=1400", expected.getLast());
    }

    /** The diagnostic variants compute the same {@code getHit} and {@code count} results as the default adapter. */
    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void diagnosticVariantsAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractObjectIntLinkedBench> cls : List.of(StencilObjectIntLinkedBench.class,
                StencilHashObjectIntLinkedBench.class, FastutilHashObjectIntLinkedBench.class,
                StencilShuffledObjectIntLinkedBench.class)) {
            AbstractObjectIntLinkedBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = dist;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            for (String op : List.of("getHit", "count", "count", "getHit")) {
                results.add(op + "=" + cls.getMethod(op).invoke(bench));
            }
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
    }

    /** {@code fifo} and {@code churn} keep the size: the inserted keys are absent. */
    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void replacementsKeepTheSize(KeyDist dist) {
        StencilObjectIntLinkedBench fifo = new StencilObjectIntLinkedBench();
        fifo.size = 1400;
        fifo.dist = dist;
        fifo.setUpData();
        StencilObjectIntLinkedBench churn = new StencilObjectIntLinkedBench();
        churn.size = 1400;
        churn.dist = dist;
        churn.setUpData();
        for (int invocation = 0; invocation < 4; invocation++) {
            assertEquals(1400, fifo.fifo());
            assertEquals(1400, churn.churn());
        }
        assertEquals(0, fifo.getMiss());
        assertEquals(0, churn.getMiss());
    }

    @Test
    void deepFootprintWithoutTheKeys() {
        int n = 10_000;
        ObjectIntLinkedHashMap<String> stencil = new ObjectIntLinkedHashMap<>();
        Object2IntLinkedOpenHashMap<String> fastutil = new Object2IntLinkedOpenHashMap<>();
        LinkedHashMap<String, Integer> jdk = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String k = KeyDist.RANDOM.stringKey(i);
            stencil.put(k, i);
            fastutil.put(k, i);
            jdk.put(k, i);
        }
        // 10,000 entries: 12,288 entry positions of 4 + 4 + 4 bytes and 16,384 index slots of 4 bytes.
        double stencilPerEntry = Footprint.deepBytes(stencil, Footprint.Mode.DEEP_SHARED_KEYS) / (double) n;
        assertTrue(stencilPerEntry > 21 && stencilPerEntry < 22, "stencil: " + stencilPerEntry);
        // 16,384 slots of 4 + 4 + 8 bytes.
        double fastutilPerEntry = Footprint.deepBytes(fastutil, Footprint.Mode.DEEP_SHARED_KEYS) / (double) n;
        assertTrue(fastutilPerEntry > 26 && fastutilPerEntry < 27, "fastutil: " + fastutilPerEntry);
        // A 40-byte node and a 16-byte Integer per entry, 4 bytes per bucket.
        double jdkPerEntry = Footprint.deepBytes(jdk, Footprint.Mode.DEEP_SHARED_KEYS) / (double) n;
        assertTrue(jdkPerEntry > 60 && jdkPerEntry < 64, "jdk: " + jdkPerEntry);
    }
}
