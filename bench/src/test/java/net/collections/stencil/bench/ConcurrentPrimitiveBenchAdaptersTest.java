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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import net.generated.example.IntIntConcurrentHashMap;
import net.generated.example.LongLongConcurrentHashMap;

/**
 * Calls every benchmark method of the adapters of the thread-safe maps with primitive values
 * once, outside JMH and in one thread, and checks that all implementations compute the same
 * results. A functional test of the adapters, not a measurement.
 */
class ConcurrentPrimitiveBenchAdaptersTest {

    private static final List<Class<? extends AbstractConcurrentLongLongBench<?>>> LONG_ADAPTERS = List.of(
            StencilConcurrentLongLongBench.class, JdkConcurrentLongLongBench.class,
            JdkAdderConcurrentLongLongBench.class, JctoolsConcurrentLongLongBench.class,
            SpeigerConcurrentLongLongBench.class, TrivagoConcurrentLongLongBench.class);

    private static final List<Class<? extends AbstractConcurrentIntIntBench<?>>> INT_ADAPTERS = List.of(
            StencilConcurrentIntIntBench.class, JdkConcurrentIntIntBench.class,
            JdkAdderConcurrentIntIntBench.class, JctoolsConcurrentIntIntBench.class,
            SpeigerConcurrentIntIntBench.class, TrivagoConcurrentIntIntBench.class);

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void longAdaptersAgree(KeyDist dist) throws Exception {
        List<Object> expected = null;
        for (Class<? extends AbstractConcurrentLongLongBench<?>> cls : LONG_ADAPTERS) {
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
        for (Class<? extends AbstractConcurrentIntIntBench<?>> cls : INT_ADAPTERS) {
            List<Object> results = runInt(cls.getDeclaredConstructor().newInstance(), dist);
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertEquals("getMiss=0", expected.get(1), "no miss found");
    }

    /** Runs the benchmark methods; {@code count} is compared through the counts it leaves in the map. */
    private static <M> List<Object> runLong(AbstractConcurrentLongLongBench<M> bench, KeyDist dist) throws Exception {
        bench.size = 1400;
        bench.dist = dist;
        bench.workers = 3;
        bench.setUpData();
        try {
            AbstractConcurrentLongLongBench.ThreadState t = new AbstractConcurrentLongLongBench.ThreadState();
            List<Object> results = new ArrayList<>();
            results.add("getHit=" + bench.getHit(t));
            results.add("getMiss=" + bench.getMiss(t));
            results.add("mixed=" + bench.mixed(t));
            results.add("writeHeavy=" + bench.writeHeavy(t));
            bench.count(t);
            long sum = 0;
            for (long k : bench.keys) {
                sum += bench.get(bench.map(), k);
            }
            results.add("sum after count=" + sum);
            @SuppressWarnings("unchecked")
            M built = (M) bench.build(null);
            long builtSum = 0;
            for (long k : bench.buildKeySets[0]) {
                builtSum += bench.get(built, k);
            }
            results.add("build sum=" + builtSum);
            // key 0 (part of the SEQUENTIAL population) and extreme keys
            M m = bench.create();
            long[] extreme = {0L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 1L << 40};
            for (int i = 0; i < extreme.length; i++) {
                bench.put(m, extreme[i], i + 1);
            }
            for (int i = 0; i < extreme.length; i++) {
                assertEquals(i + 1, bench.get(m, extreme[i]), bench.getClass().getSimpleName() + " key " + extreme[i]);
            }
            return results;
        } finally {
            bench.tearDown();
        }
    }

    private static <M> List<Object> runInt(AbstractConcurrentIntIntBench<M> bench, KeyDist dist) throws Exception {
        bench.size = 1400;
        bench.dist = dist;
        bench.workers = 3;
        bench.setUpData();
        try {
            AbstractConcurrentIntIntBench.ThreadState t = new AbstractConcurrentIntIntBench.ThreadState();
            List<Object> results = new ArrayList<>();
            results.add("getHit=" + bench.getHit(t));
            results.add("getMiss=" + bench.getMiss(t));
            results.add("mixed=" + bench.mixed(t));
            results.add("writeHeavy=" + bench.writeHeavy(t));
            bench.count(t);
            long sum = 0;
            for (int k : bench.keys) {
                sum += bench.get(bench.map(), k);
            }
            results.add("sum after count=" + sum);
            @SuppressWarnings("unchecked")
            M built = (M) bench.build(null);
            long builtSum = 0;
            for (int k : bench.buildKeySets[0]) {
                builtSum += bench.get(built, k);
            }
            results.add("build sum=" + builtSum);
            M m = bench.create();
            int[] extreme = {0, -1, Integer.MIN_VALUE, Integer.MAX_VALUE, 1 << 20};
            for (int i = 0; i < extreme.length; i++) {
                bench.put(m, extreme[i], i + 1);
            }
            for (int i = 0; i < extreme.length; i++) {
                assertEquals(i + 1, bench.get(m, extreme[i]), bench.getClass().getSimpleName() + " key " + extreme[i]);
            }
            return results;
        } finally {
            bench.tearDown();
        }
    }

    @Test
    void deepFootprintCountsBoxes() {
        int n = 10_000;
        LongLongConcurrentHashMap longs = new LongLongConcurrentHashMap(0, 0.75f, 8);
        IntIntConcurrentHashMap ints = new IntIntConcurrentHashMap(0, 0.75f, 8);
        ConcurrentHashMap<Integer, Integer> jdk = new ConcurrentHashMap<>();
        for (int i = 0; i < n; i++) {
            longs.put(KeyDist.RANDOM.longKey(i), i + 1000L);
            ints.put(KeyDist.RANDOM.key(i), i + 1000);
            jdk.put(KeyDist.RANDOM.key(i), i + 1000);
        }
        double longPerEntry = Footprint.deepBytes(longs, Footprint.Mode.DEEP_BOXED_KEYS) / (double) n;
        double intPerEntry = Footprint.deepBytes(ints, Footprint.Mode.DEEP_BOXED_KEYS) / (double) n;
        double jdkPerEntry = Footprint.deepBytes(jdk, Footprint.Mode.DEEP_BOXED_KEYS) / (double) n;
        // 8 segments of 2048 slots: 16 or 8 bytes per slot at a fill of 0.61, plus the segments.
        assertTrue(longPerEntry > 26 && longPerEntry < 28, "stencil long -> long: " + longPerEntry);
        assertTrue(intPerEntry > 13 && intPerEntry < 15, "stencil int -> int: " + intPerEntry);
        // Node (32) + two Integers (2 x 16) + 4-byte table slot per 0.5 to 0.75 entries.
        assertTrue(jdkPerEntry > 68 && jdkPerEntry < 72, "jdk: " + jdkPerEntry);
    }
}
