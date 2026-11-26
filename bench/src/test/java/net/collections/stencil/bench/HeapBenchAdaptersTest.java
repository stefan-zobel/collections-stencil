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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Calls every benchmark method of the priority queue adapters once, outside JMH, and checks that
 * all implementations compute the same results. This is a functional test of the adapters, not
 * a measurement.
 */
class HeapBenchAdaptersTest {

    private static final List<Class<? extends AbstractLongHeapBench>> HEAPS = List.of(
            StencilLongHeapBench.class, FastutilLongHeapBench.class, SpeigerLongHeapBench.class,
            JdkLongHeapBench.class, Arity2LongHeapBench.class, Arity4LongHeapBench.class,
            Arity4BottomUpLongHeapBench.class, Arity8BottomUpLongHeapBench.class,
            Arity4BottomUpLoopLongHeapBench.class, Arity8LongHeapBench.class, Arity16BottomUpLongHeapBench.class,
            Arity4LoopLongHeapBench.class);

    private static final List<Class<? extends AbstractIntLongIndexedHeapBench>> INDEXED = List.of(
            StencilIntLongIndexedHeapBench.class, FastutilIntLongIndexedHeapBench.class,
            JdkIntLongIndexedHeapBench.class);

    private static final List<Class<? extends AbstractLongLongIndexedHeapBench>> LONG_KEYED = List.of(
            StencilLongLongIndexedHeapBench.class, StencilDenseLongLongIndexedHeapBench.class,
            FastutilLongLongIndexedHeapBench.class, JdkLongLongIndexedHeapBench.class);

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void heapAdaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractLongHeapBench> cls : HEAPS) {
            AbstractLongHeapBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = dist;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            // hold changes the queue: twice, so that the second call depends on the first.
            for (String op : List.of("hold", "sort", "hold", "sort")) {
                results.add(op + "=" + cls.getMethod(op).invoke(bench));
            }
            Object built = cls.getMethod("build", GcBeforeLargeBuild.class).invoke(bench, (Object) null);
            results.add("build=" + sizeOf(built));
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertEquals("build=1400", expected.getLast());
    }

    private static int sizeOf(Object queue) throws ReflectiveOperationException {
        if (queue instanceof ExperimentLongHeaps.Base b) {
            return b.size;
        }
        return (Integer) queue.getClass().getMethod("size").invoke(queue);
    }

    @ParameterizedTest
    @EnumSource(KeyDist.class)
    void indexedHeapAdaptersAgree(KeyDist dist) throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractIntLongIndexedHeapBench> cls : INDEXED) {
            AbstractIntLongIndexedHeapBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = dist;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            // dijkstra rotates the source: several calls, and each reuses the queue of the last.
            for (String op : List.of("dijkstra", "dijkstra", "sort", "dijkstra")) {
                results.add(op + "=" + cls.getMethod(op).invoke(bench));
            }
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName() + " " + dist);
            }
        }
        assertTrue(!expected.getFirst().equals(expected.get(1)), "the sources differ");
    }

    @Test
    void longKeyedHeapAdaptersAgree() throws ReflectiveOperationException {
        List<Object> expected = null;
        for (Class<? extends AbstractLongLongIndexedHeapBench> cls : LONG_KEYED) {
            AbstractLongLongIndexedHeapBench bench = cls.getDeclaredConstructor().newInstance();
            bench.size = 1400;
            bench.dist = KeyDist.RANDOM;
            bench.setUpData();
            List<Object> results = new ArrayList<>();
            // Both ops carry state over: the source rotation, and the timers.
            for (String op : List.of("dijkstra", "timers", "dijkstra", "timers", "timers")) {
                results.add(op + "=" + cls.getMethod(op).invoke(bench));
            }
            results.add("live=" + java.util.Arrays.hashCode(bench.live));
            if (expected == null) {
                expected = results;
            } else {
                assertEquals(expected, results, cls.getSimpleName());
            }
        }
        assertTrue(!expected.get(1).equals(expected.get(3)), "the timers move on");
    }

    /** The distance sum of the sparse-key Dijkstra equals a reference computation. */
    @Test
    void longKeyedDijkstraMatchesAReference() {
        StencilLongLongIndexedHeapBench sparse = new StencilLongLongIndexedHeapBench();
        sparse.size = 500;
        sparse.dist = KeyDist.RANDOM;
        sparse.setUpData();
        // Bellman-Ford from node 0 (the first source of the rotation).
        int n = sparse.size;
        long[] d = new long[n];
        java.util.Arrays.fill(d, Long.MAX_VALUE);
        d[0] = 0;
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int u = 0; u < n; u++) {
                if (d[u] == Long.MAX_VALUE) {
                    continue;
                }
                for (int e = sparse.start[u]; e < sparse.start[u + 1]; e++) {
                    long nd = d[u] + sparse.weight[e];
                    if (nd < d[sparse.to[e]]) {
                        d[sparse.to[e]] = nd;
                        changed = true;
                    }
                }
            }
        }
        long sum = 0;
        for (long x : d) {
            if (x != Long.MAX_VALUE) {
                sum += x;
            }
        }
        assertEquals(sum, sparse.dijkstra());
        assertEquals(1L, AbstractLongLongIndexedHeapBench.ODD * AbstractLongLongIndexedHeapBench.ODD_INV);
    }

    /** The distance sum over the reachable nodes equals a reference computation. */
    @Test
    void dijkstraMatchesAReference() {
        StencilIntLongIndexedHeapBench bench = new StencilIntLongIndexedHeapBench();
        bench.size = 500;
        bench.dist = KeyDist.SEQUENTIAL;
        bench.setUpData();
        long fromZero = bench.dijkstra();
        // Bellman-Ford from node 0 (the first source of the rotation).
        int n = bench.size;
        long[] d = new long[n];
        java.util.Arrays.fill(d, Long.MAX_VALUE);
        d[0] = 0;
        for (boolean changed = true; changed; ) {
            changed = false;
            for (int u = 0; u < n; u++) {
                if (d[u] == Long.MAX_VALUE) {
                    continue;
                }
                for (int e = bench.start[u]; e < bench.start[u + 1]; e++) {
                    long nd = d[u] + bench.weight[e];
                    if (nd < d[bench.to[e]]) {
                        d[bench.to[e]] = nd;
                        changed = true;
                    }
                }
            }
        }
        long sum = 0;
        int reached = 0;
        for (long x : d) {
            if (x != Long.MAX_VALUE) {
                sum += x;
                reached++;
            }
        }
        assertTrue(reached > n * 9 / 10, "most nodes are reachable: " + reached);
        assertEquals(sum, fromZero);
    }
}
