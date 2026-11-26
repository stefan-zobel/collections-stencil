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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import net.generated.example.IntObjectConcurrentHashMap;
import net.generated.example.LongObjectConcurrentHashMap;

/**
 * Calls every benchmark method of the concurrent adapters once, outside JMH and in one thread,
 * and checks that all implementations compute the same results. A functional test of the
 * adapters, not a measurement.
 */
class ConcurrentBenchAdaptersTest {

    private static final List<Class<? extends AbstractConcurrentLongObjectBench<?>>> LONG_ADAPTERS = List.of(
            StencilConcurrentLongObjectBench.class, JdkConcurrentLongObjectBench.class,
            JctoolsConcurrentLongObjectBench.class, SpeigerConcurrentLongObjectBench.class);

    private static final List<Class<? extends AbstractConcurrentIntObjectBench<?>>> INT_ADAPTERS = List.of(
            StencilConcurrentIntObjectBench.class, JdkConcurrentIntObjectBench.class,
            JctoolsConcurrentIntObjectBench.class, SpeigerConcurrentIntObjectBench.class);

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void longAdaptersAgree(KeyDist dist) throws Exception {
        List<Object> expected = null;
        for (Class<? extends AbstractConcurrentLongObjectBench<?>> cls : LONG_ADAPTERS) {
            List<Object> results = runLong(cls.getDeclaredConstructor().newInstance(), dist);
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertEquals("getMiss=0", expected.get(1), "no miss found");
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void intAdaptersAgree(KeyDist dist) throws Exception {
        List<Object> expected = null;
        for (Class<? extends AbstractConcurrentIntObjectBench<?>> cls : INT_ADAPTERS) {
            List<Object> results = runInt(cls.getDeclaredConstructor().newInstance(), dist);
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertEquals("getMiss=0", expected.get(1), "no miss found");
    }

    private static <M> List<Object> runLong(AbstractConcurrentLongObjectBench<M> bench, KeyDist dist) throws Exception {
        bench.size = 1400;
        bench.dist = dist;
        bench.workers = 3;
        bench.setUpData();
        try {
            AbstractConcurrentLongObjectBench.ThreadState t = new AbstractConcurrentLongObjectBench.ThreadState();
            List<Object> results = new ArrayList<>();
            results.add("getHit=" + bench.getHit(t));
            results.add("getMiss=" + bench.getMiss(t));
            results.add("mixed=" + bench.mixed(t));
            results.add("writeHeavy=" + bench.writeHeavy(t));
            results.add("computeIfAbsent=" + bench.computeIfAbsent(t));
            results.add("getHit=" + bench.getHit(t));
            for (int round = 0; round < 3; round++) {
                @SuppressWarnings("unchecked")
                M built = (M) bench.build(null);
                int found = 0;
                for (long[] keys : bench.buildKeySets) {
                    for (long k : keys) {
                        if (bench.get(built, k) != null) {
                            found++;
                        }
                    }
                }
                results.add("build=" + found);
            }
            // key 0 (part of the SEQUENTIAL population) and extreme keys
            M m = bench.create();
            for (long k : new long[] {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 1L << 40}) {
                bench.put(m, k, AbstractConcurrentLongObjectBench.value(k));
            }
            for (long k : new long[] {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 1L << 40}) {
                assertNotNull(bench.get(m, k), bench.getClass().getSimpleName() + " key " + k);
            }
            return results;
        } finally {
            bench.tearDown();
        }
    }

    private static <M> List<Object> runInt(AbstractConcurrentIntObjectBench<M> bench, KeyDist dist) throws Exception {
        bench.size = 1400;
        bench.dist = dist;
        bench.workers = 3;
        bench.setUpData();
        try {
            AbstractConcurrentIntObjectBench.ThreadState t = new AbstractConcurrentIntObjectBench.ThreadState();
            List<Object> results = new ArrayList<>();
            results.add("getHit=" + bench.getHit(t));
            results.add("getMiss=" + bench.getMiss(t));
            results.add("mixed=" + bench.mixed(t));
            results.add("writeHeavy=" + bench.writeHeavy(t));
            results.add("computeIfAbsent=" + bench.computeIfAbsent(t));
            results.add("getHit=" + bench.getHit(t));
            for (int round = 0; round < 3; round++) {
                @SuppressWarnings("unchecked")
                M built = (M) bench.build(null);
                int found = 0;
                for (int[] keys : bench.buildKeySets) {
                    for (int k : keys) {
                        if (bench.get(built, k) != null) {
                            found++;
                        }
                    }
                }
                results.add("build=" + found);
            }
            M m = bench.create();
            for (int k : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 20}) {
                bench.put(m, k, AbstractConcurrentIntObjectBench.value(k));
            }
            for (int k : new int[] {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 20}) {
                assertNotNull(bench.get(m, k), bench.getClass().getSimpleName() + " key " + k);
            }
            return results;
        } finally {
            bench.tearDown();
        }
    }

    @Test
    void deepFootprintCountsBoxedKeysButNotSharedValues() {
        int n = 10_000;
        LongObjectConcurrentHashMap<Object> stencilLong = new LongObjectConcurrentHashMap<>(0, 0.75f, 8);
        IntObjectConcurrentHashMap<Object> stencilInt = new IntObjectConcurrentHashMap<>(0, 0.75f, 8);
        ConcurrentHashMap<Integer, Object> jdk = new ConcurrentHashMap<>();
        for (int i = 0; i < n; i++) {
            Object v = AbstractConcurrentIntObjectBench.value(i);
            stencilLong.put(KeyDist.RANDOM.longKey(i), v);
            stencilInt.put(KeyDist.RANDOM.key(i), v);
            jdk.put(KeyDist.RANDOM.key(i), v);
        }
        double longPerEntry = Footprint.deepBytes(stencilLong, Footprint.Mode.DEEP_BOXED_KEYS) / (double) n;
        double intPerEntry = Footprint.deepBytes(stencilInt, Footprint.Mode.DEEP_BOXED_KEYS) / (double) n;
        double jdkPerEntry = Footprint.deepBytes(jdk, Footprint.Mode.DEEP_BOXED_KEYS) / (double) n;
        // 8 segments of 2048 slots: 12 or 8 bytes per slot at a fill of 0.61, plus the segments.
        assertTrue(longPerEntry > 19 && longPerEntry < 22, "stencil long: " + longPerEntry);
        assertTrue(intPerEntry > 13 && intPerEntry < 15, "stencil int: " + intPerEntry);
        // Node (32) + Integer (16) + 4-byte table slot per 0.5 to 0.75 entries.
        assertTrue(jdkPerEntry > 52 && jdkPerEntry < 56, "jdk: " + jdkPerEntry);
    }
}
