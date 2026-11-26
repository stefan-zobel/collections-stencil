//#-- Copyright 2026 The collections-stencil authors
//#--
//#-- Licensed under the Apache License, Version 2.0 (the "License");
//#-- you may not use this file except in compliance with the License.
//#-- You may obtain a copy of the License at
//#--
//#--     https://www.apache.org/licenses/LICENSE-2.0
//#--
//#-- Unless required by applicable law or agreed to in writing, software
//#-- distributed under the License is distributed on an "AS IS" BASIS,
//#-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//#-- See the License for the specific language governing permissions and
//#-- limitations under the License.
//#--
//#types V=int|long|double
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Differential tests against a map from ids to priorities, and Dijkstra's algorithm against a simple reference. */
class IntVTypeIndexedHeapTest {

    /** A priority: mostly from {@code range} values around zero (with duplicates), sometimes an extreme. */
    private static VType priority(SplittableRandom rnd, int range) {
        int r = rnd.nextInt(40);
//#if V == int
        if (r == 0) {
            return Integer.MIN_VALUE;
        }
        if (r == 1) {
            return Integer.MAX_VALUE;
        }
        return rnd.nextInt(range) - range / 2;
//#elif V == long
        if (r == 0) {
            return Long.MIN_VALUE;
        }
        if (r == 1) {
            return Long.MAX_VALUE;
        }
        return (rnd.nextInt(range) - range / 2) * 0x1_0000_0001L;
//#else
        if (r == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        if (r == 1) {
            return Double.POSITIVE_INFINITY;
        }
        if (r == 2) {
            return -0.0;
        }
        return (rnd.nextInt(range) - range / 2) * 0.25;
//#end
    }

    /** Whether {@code a} comes before {@code b} (same comparison as the heap: {@code -0.0 == 0.0}). */
    private static boolean before(VType a, VType b, boolean maxFirst) {
        return maxFirst ? a > b : a < b;
    }

    /** The first priority of the reference. */
    private static VType first(Map<Integer, VBoxed> ref, boolean maxFirst) {
        VType best = VZero;
        boolean found = false;
        for (VBoxed p : ref.values()) {
            if (!found || before(p, best, maxFirst)) {
                best = p;
                found = true;
            }
        }
        return best;
    }

    private static void assertSameContent(Map<Integer, VBoxed> ref, IntVTypeIndexedHeap h, String context) {
        assertEquals(ref.size(), h.size(), context);
        assertEquals(ref.isEmpty(), h.isEmpty(), context);
        Map<Integer, VBoxed> each = new HashMap<>();
        h.forEach((id, p) -> assertEquals(null, each.put(id, p), context + ": id twice"));
        assertEquals(ref.keySet(), each.keySet(), context);
        for (Map.Entry<Integer, VBoxed> e : ref.entrySet()) {
            VType want = e.getValue();
            assertTrue(want == each.get(e.getKey()), context + " id " + e.getKey());
            assertTrue(h.contains(e.getKey()), context);
            assertTrue(e.getValue() == h.priority(e.getKey()), context);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "false, 8, 50, 1",
        "false, 1000, 2000, 2",
        "false, 1000000, 100000, 3",
        "true, 8, 50, 4",
        "true, 1000, 2000, 5",
        "true, 1000000, 100000, 6",
    })
    void randomOperationsMatchAMap(boolean maxFirst, int range, int idRange, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap(rnd.nextInt(4), maxFirst);
        assertEquals(maxFirst, h.isMaxFirst());
        Map<Integer, VBoxed> ref = new HashMap<>();
        for (int step = 0; step < 30_000; step++) {
            String context = "step " + step;
            // Mostly ids of a small working set, so that updates hit present ids.
            int id = rnd.nextInt(10) == 0 ? rnd.nextInt(idRange) : rnd.nextInt(Math.min(idRange, 64));
            VType p = priority(rnd, range);
            int op = rnd.nextInt(1000);
            if (op < 300) {
                h.put(id, p);
                ref.put(id, p);
            } else if (op < 600) {
                VBoxed old = ref.get(id);
                boolean better = old == null || before(p, old, maxFirst);
                assertEquals(better, h.putIfBetter(id, p), context + " putIfBetter");
                if (better) {
                    ref.put(id, p);
                }
            } else if (op < 800) {
                if (ref.isEmpty()) {
                    assertThrows(NoSuchElementException.class, h::poll, context);
                } else {
                    VType want = first(ref, maxFirst);
                    VType got = h.peekPriority();
                    assertTrue(want == got, context + " peekPriority: expected " + want + ", got " + got);
                    int top = h.peekId();
                    assertEquals(top, h.poll(), context);
                    VBoxed removed = ref.remove(top);
                    assertTrue(removed != null && removed == want, context + " polled id " + top);
                }
            } else if (op < 950) {
                assertEquals(ref.remove(id) != null, h.remove(id), context + " remove");
            } else if (op < 952) {
                h.clear();
                ref.clear();
            } else if (op < 953) {
                if (rnd.nextBoolean()) {
                    h.trimToSize();
                } else {
                    h.ensureCapacity(rnd.nextInt(idRange));
                }
            } else {
                assertSameContent(ref, h, context);
            }
            assertEquals(ref.size(), h.size(), context);
            assertEquals(ref.containsKey(id), h.contains(id), context);
            VBoxed want = ref.get(id);
            VType got = h.priorityOrDefault(id, VZero);
            assertTrue(want == null ? got == VZero : want == got, context + " priorityOrDefault");
        }
        assertSameContent(ref, h, "end");
        while (!ref.isEmpty()) {
            VType want = first(ref, maxFirst);
            assertTrue(want == h.peekPriority(), "drain");
            assertTrue(ref.remove(h.poll()) == want, "drain");
        }
        assertTrue(h.isEmpty());
    }

    /** A random directed graph in compressed rows: the edges of node u are {@code to[start[u] .. start[u + 1])}. */
    private record Graph(int[] start, int[] to, VType[] weight) {

        static Graph random(int n, int degree, long seed) {
            SplittableRandom rnd = new SplittableRandom(seed);
            int[] start = new int[n + 1];
            int[] to = new int[n * degree];
            VType[] weight = new VType[n * degree];
            for (int u = 0; u < n; u++) {
                start[u + 1] = (u + 1) * degree;
                for (int j = u * degree; j < (u + 1) * degree; j++) {
                    to[j] = rnd.nextInt(n);
                    weight[j] = 1 + rnd.nextInt(1000);
                }
            }
            return new Graph(start, to, weight);
        }
    }

    /** Dijkstra's algorithm with the indexed heap; {@code -1} for unreachable nodes. */
    private static VType[] dijkstra(Graph g, int source, IntVTypeIndexedHeap h) {
        int n = g.start().length - 1;
        VType[] dist = new VType[n];
        boolean[] done = new boolean[n];
        java.util.Arrays.fill(dist, -1);
        h.put(source, VZero);
        while (!h.isEmpty()) {
            VType d = h.peekPriority();
            int u = h.poll();
            dist[u] = d;
            done[u] = true;
            for (int j = g.start()[u]; j < g.start()[u + 1]; j++) {
                int v = g.to()[j];
                if (!done[v]) {
                    h.putIfBetter(v, d + g.weight()[j]);
                }
            }
        }
        return dist;
    }

    /** Dijkstra's algorithm in O(n^2) without a heap. */
    private static VType[] reference(Graph g, int source) {
        int n = g.start().length - 1;
        VType[] dist = new VType[n];
        boolean[] reached = new boolean[n];
        boolean[] done = new boolean[n];
        dist[source] = VZero;
        reached[source] = true;
        for (;;) {
            int u = -1;
            for (int v = 0; v < n; v++) {
                if (reached[v] && !done[v] && (u < 0 || dist[v] < dist[u])) {
                    u = v;
                }
            }
            if (u < 0) {
                break;
            }
            done[u] = true;
            for (int j = g.start()[u]; j < g.start()[u + 1]; j++) {
                int v = g.to()[j];
                VType nd = dist[u] + g.weight()[j];
                if (!reached[v] || nd < dist[v]) {
                    dist[v] = nd;
                    reached[v] = true;
                }
            }
        }
        for (int v = 0; v < n; v++) {
            if (!reached[v]) {
                dist[v] = -1;
            }
        }
        return dist;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void dijkstraFindsTheShortestPaths(int degree) {
        Graph g = Graph.random(800, degree, degree);
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap(0);
        VType[] want = reference(g, 0);
        assertArrayEquals(want, dijkstra(g, 0, h));
        assertTrue(h.isEmpty());
        // the same heap again, from another source
        assertArrayEquals(reference(g, 17), dijkstra(g, 17, h));
    }

    @Test
    void emptyHeapAndAbsentIds() {
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap();
        assertTrue(h.isEmpty());
        assertThrows(NoSuchElementException.class, h::peekId);
        assertThrows(NoSuchElementException.class, h::peekPriority);
        assertThrows(NoSuchElementException.class, h::poll);
        assertThrows(NoSuchElementException.class, () -> h.priority(3));
        assertThrows(NoSuchElementException.class, () -> h.priority(-3));
        assertThrows(NoSuchElementException.class, () -> h.priority(1 << 20));
        assertEquals(7, h.priorityOrDefault(1 << 20, 7));
        assertFalse(h.contains(-1));
        assertFalse(h.contains(1 << 20));
        assertFalse(h.remove(-1));
        assertFalse(h.remove(1 << 20));
        assertEquals("{}", h.toString());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new IntVTypeIndexedHeap(-1));
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap();
        assertThrows(IllegalArgumentException.class, () -> h.put(-1, VZero));
        assertThrows(IllegalArgumentException.class, () -> h.putIfBetter(Integer.MIN_VALUE, VZero));
        assertThrows(IllegalArgumentException.class, () -> h.put(Integer.MAX_VALUE, VZero));
        assertThrows(IllegalArgumentException.class, () -> h.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> h.ensureCapacity(Integer.MAX_VALUE));
//#if V.floating
        assertThrows(IllegalArgumentException.class, () -> h.put(1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> h.putIfBetter(1, Double.NaN));
//#end
        assertTrue(h.isEmpty());
    }

    @Test
    void updatesMoveEntriesBothWays() {
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap(4);
        for (int id = 0; id < 100; id++) {
            h.put(id, id * 10);
        }
        h.put(99, -5); // up
        assertEquals(99, h.peekId());
        h.put(99, 2000); // down
        assertEquals(0, h.peekId());
        assertFalse(h.putIfBetter(0, 5), "not better");
        assertTrue(h.putIfBetter(50, -1));
        assertEquals(50, h.poll());
        assertEquals(0, h.peekPriority());
        List<Integer> order = new ArrayList<>();
        while (!h.isEmpty()) {
            order.add(h.poll());
        }
        assertEquals(99, order.size());
        assertEquals(99, order.getLast());
    }

    @Test
    void clearTakesTheSizeAndKeepsLargeIds() {
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap(0, true);
        h.put(1_000_000, 1);
        h.put(3, 2);
        assertEquals("{3=" + VBoxed.valueOf(2) + ", 1000000=" + VBoxed.valueOf(1) + "}", h.toString());
        h.clear();
        assertTrue(h.isEmpty());
        assertFalse(h.contains(1_000_000));
        assertFalse(h.contains(3));
        h.put(1_000_000, 4);
        assertEquals(1_000_000, h.peekId());
        assertEquals(1, h.size());
    }

    @Test
    void capacityChangesKeepTheContent() {
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap(0);
        h.trimToSize();
        assertTrue(h.isEmpty());
        h.ensureCapacity(10_000);
        Map<Integer, VBoxed> ref = new HashMap<>();
        for (int id = 0; id < 5_000; id += 3) {
            VType p = id % 101;
            h.put(id, p);
            ref.put(id, p);
        }
        for (int id = 0; id < 5_000; id += 3) {
            if (id % 7 != 0 || id > 2_000) {
                h.remove(id);
                ref.remove(id);
            }
        }
        h.trimToSize();
        assertSameContent(ref, h, "after trimToSize");
        assertFalse(h.contains(4_998), "an id above the largest id in the heap");
        h.put(9_999, 1);
        ref.put(9_999, VBoxed.valueOf(1));
        h.ensureCapacity(20_000);
        assertSameContent(ref, h, "after ensureCapacity");
        while (!h.isEmpty()) {
            h.poll();
        }
        h.trimToSize();
        h.put(0, 1);
        assertEquals(0, h.peekId());
    }
}
