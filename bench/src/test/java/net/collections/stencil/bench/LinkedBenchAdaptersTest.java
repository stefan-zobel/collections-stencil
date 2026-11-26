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
import net.generated.example.LongObjectLinkedHashMap;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/**
 * Calls every benchmark method of the adapters for insertion-ordered maps once, outside JMH, and
 * checks that all implementations compute the same results. This is a functional test of the
 * adapters, not a measurement.
 */
class LinkedBenchAdaptersTest {

    private static final List<Class<? extends AbstractLongLinkedBench>> ADAPTERS = List.of(
            StencilLongLinkedBench.class, FastutilLongLinkedBench.class, SpeigerLongLinkedBench.class,
            JdkLongLinkedBench.class);

    /** The lookups come first: the later operations replace keys. Every result depends on the order of the entries. */
    private static final List<String> OPS = List.of(
            "getHit", "getMiss", "iterate", "touch", "iterate", "fifo", "touch", "churn", "fifo", "touch", "churn",
            "iterate", "getMiss");

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void adaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractLongLinkedBench> cls : ADAPTERS) {
            AbstractLongLinkedBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = dist;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            for (String op : OPS) {
                Method m = cls.getMethod(op);
                results.add(op + "=" + m.invoke(bench));
            }
            // The first key after all operations: the oldest entry is the same in every implementation.
            bench.hits = new long[0];
            results.add("last=" + cls.getMethod("touch").invoke(bench));
            Object built = cls.getMethod("build", GcBeforeLargeBuild.class).invoke(bench, (Object) null);
            results.add("build=" + built.getClass().getMethod("size").invoke(built));
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertTrue(expected.getFirst().toString().endsWith("=" + AbstractLongLinkedBench.OPS), "all hits found");
        assertEquals("getMiss=0", expected.get(1), "no miss found");
        assertEquals("getMiss=0", expected.get(12), "no miss found after the updates");
        assertEquals("build=1400", expected.getLast());
    }

    @Test
    void deepFootprintCountsNodesAndBoxes() {
        int n = 10_000;
        LongObjectLinkedHashMap<Object> stencil = new LongObjectLinkedHashMap<>();
        Long2ObjectLinkedOpenHashMap<Object> fastutil = new Long2ObjectLinkedOpenHashMap<>();
        LinkedHashMap<Long, Object> jdk = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            long k = KeyDist.fmix64(i + 1);
            Object v = Integer.valueOf(i & 1023);
            stencil.put(k, v);
            fastutil.put(k, v);
            jdk.put(k, v);
        }
        // 10,000 entries: 12,288 entry positions of 12 bytes and 16,384 index slots of 4 bytes.
        double stencilPerEntry = Footprint.deepBytes(stencil) / (double) n;
        assertTrue(stencilPerEntry > 21 && stencilPerEntry < 22, "stencil: " + stencilPerEntry);
        // 16,384 slots of 8 + 4 + 8 bytes.
        double fastutilPerEntry = Footprint.deepBytes(fastutil) / (double) n;
        assertTrue(fastutilPerEntry > 32.5 && fastutilPerEntry < 33.5, "fastutil: " + fastutilPerEntry);
        // A 40-byte node and a 24-byte Long per entry, 4 bytes per bucket.
        double jdkPerEntry = Footprint.deepBytes(jdk) / (double) n;
        assertTrue(jdkPerEntry > 70 && jdkPerEntry < 72, "jdk: " + jdkPerEntry);
    }
}
