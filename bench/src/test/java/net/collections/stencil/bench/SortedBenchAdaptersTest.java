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
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import net.generated.example.LongLongTreeMap;
import net.generated.example.LongObjectTreeMap;
import net.generated.example.LongTreeSet;

import it.unimi.dsi.fastutil.longs.Long2LongRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;

/**
 * Calls every benchmark method of the sorted-map and sorted-set adapters once, outside JMH, and checks that all
 * implementations compute the same results. This is a functional test of the adapters, not a
 * measurement.
 */
class SortedBenchAdaptersTest {

    private static final List<Class<? extends AbstractLongSortedBench>> ADAPTERS = List.of(
            StencilLongTreeBench.class, FastutilLongRBTreeBench.class, FastutilLongAVLTreeBench.class,
            JdkLongTreeBench.class);

    private static final List<Class<? extends AbstractLongSortedBench>> LONG_VALUE_ADAPTERS = List.of(
            StencilLongLongTreeBench.class, FastutilLongLongRBTreeBench.class, FastutilLongLongAVLTreeBench.class,
            SpeigerLongLongRBTreeBench.class, JdkLongLongTreeBench.class);

    private static final List<Class<? extends AbstractLongSortedBench>> SET_ADAPTERS = List.of(
            StencilLongTreeSetBench.class, FastutilLongRBTreeSetBench.class, FastutilLongAVLTreeSetBench.class,
            SpeigerLongRBTreeSetBench.class, JdkLongTreeSetBench.class);

    private static final List<String> OPS = List.of(
            "getHit", "getMiss", "floor", "iterate", "rangeScan", "removeRange", "getHit", "churn", "getHit", "floor");

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void adaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = results(ADAPTERS, dist);
        assertTrue(expected.getFirst().toString().endsWith("=" + AbstractLongSortedBench.OPS), "all hits found");
        assertEquals("getMiss=0", expected.get(1), "no miss found");
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void longValueAdaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = results(LONG_VALUE_ADAPTERS, dist);
        // Every hit adds a value of at least 1024.
        long hitSum = Long.parseLong(expected.getFirst().toString().substring("getHit=".length()));
        assertTrue(hitSum >= 1024L * AbstractLongSortedBench.OPS, "all hits found: " + hitSum);
        assertEquals("getMiss=0", expected.get(1), "no miss found");
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void setAdaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = results(SET_ADAPTERS, dist);
        assertEquals("getHit=" + AbstractLongSortedBench.OPS, expected.getFirst(), "all hits found");
        assertEquals("getMiss=0", expected.get(1), "no miss found");
    }

    /** The results of all operations, which must be the same for every adapter. */
    private static List<Object> results(List<Class<? extends AbstractLongSortedBench>> adapters, KeyDist dist)
            throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractLongSortedBench> cls : adapters) {
            AbstractLongSortedBench bench = cls.getDeclaredConstructor().newInstance();
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
        return expected;
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void deepFootprintCountsNodesAndBoxes(KeyDist dist) {
        int n = 10_000;
        LongObjectTreeMap<Object> tree = new LongObjectTreeMap<>();
        Long2ObjectRBTreeMap<Object> rb = new Long2ObjectRBTreeMap<>();
        for (int i = 0; i < n; i++) {
            long k = dist == KeyDist.RANDOM ? KeyDist.fmix64(i + 1) : i;
            tree.put(k, Integer.valueOf(i & 1023));
            rb.put(k, Integer.valueOf(i & 1023));
        }
        double treePerEntry = Footprint.deepBytes(tree) / (double) n;
        double rbPerEntry = Footprint.deepBytes(rb) / (double) n;
        // B+ tree: 12 bytes of key and value per entry, divided by the leaf fill (50% to 100%).
        assertTrue(treePerEntry > 12 && treePerEntry < 30, "tree: " + treePerEntry);
        // Red-black tree entry: 12-byte header, long key, value, left, right, info: 40 bytes.
        assertTrue(rbPerEntry > 39 && rbPerEntry < 42, "rb: " + rbPerEntry);
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void deepFootprintOfLongValues(KeyDist dist) {
        int n = 10_000;
        LongLongTreeMap tree = new LongLongTreeMap();
        Long2LongRBTreeMap rb = new Long2LongRBTreeMap();
        TreeMap<Long, Long> jdk = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            long k = dist == KeyDist.RANDOM ? KeyDist.fmix64(i + 1) : i + 1024;
            tree.put(k, AbstractLongSortedBench.longValue(i));
            rb.put(k, AbstractLongSortedBench.longValue(i));
            jdk.put(k, AbstractLongSortedBench.longValue(i));
        }
        double treePerEntry = Footprint.deepBytes(tree) / (double) n;
        double rbPerEntry = Footprint.deepBytes(rb) / (double) n;
        double jdkPerEntry = Footprint.deepBytes(jdk) / (double) n;
        // B+ tree: 16 bytes of key and value per entry, divided by the leaf fill (50% to 100%).
        assertTrue(treePerEntry > 16 && treePerEntry < 36, "tree: " + treePerEntry);
        // Red-black tree entry: 12-byte header, long key, long value, left, right, info: 40 bytes.
        assertTrue(rbPerEntry > 39 && rbPerEntry < 50, "rb: " + rbPerEntry);
        // TreeMap entry of 40 bytes plus a boxed key and a boxed value of 24 bytes each.
        assertTrue(jdkPerEntry > 87 && jdkPerEntry < 90, "jdk: " + jdkPerEntry);
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void deepFootprintOfSets(KeyDist dist) {
        int n = 10_000;
        LongTreeSet tree = new LongTreeSet();
        LongRBTreeSet rb = new LongRBTreeSet();
        TreeSet<Long> jdk = new TreeSet<>();
        for (int i = 0; i < n; i++) {
            long k = dist == KeyDist.RANDOM ? KeyDist.fmix64(i + 1) : i + 1024;
            tree.add(k);
            rb.add(k);
            jdk.add(k);
        }
        double treePerEntry = Footprint.deepBytes(tree) / (double) n;
        double rbPerEntry = Footprint.deepBytes(rb) / (double) n;
        double jdkPerEntry = Footprint.deepBytes(jdk) / (double) n;
        // B+ tree: 8 bytes per element, divided by the leaf fill (50% to 100%).
        assertTrue(treePerEntry > 8 && treePerEntry < 18, "tree: " + treePerEntry);
        // Red-black tree entry: 12-byte header, long element, left, right, info: 32 bytes.
        assertTrue(rbPerEntry > 31 && rbPerEntry < 34, "rb: " + rbPerEntry);
        // TreeMap entry of 40 bytes plus a boxed element of 24 bytes; the value is a shared object.
        assertTrue(jdkPerEntry > 63 && jdkPerEntry < 66, "jdk: " + jdkPerEntry);
    }
}
