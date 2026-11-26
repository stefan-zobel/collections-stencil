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

/** Differential tests against a map from keys to priorities, and Dijkstra's algorithm against the dense indexed heap. */
class LongVTypeIndexedHeapTest {

    /** Keys that stress the table: zero, extremes, and keys that differ only in their high bits. */
    private static final long[] SPECIAL_KEYS = {
        0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1,
        1L << 32, 2L << 32, 3L << 32, 1L << 62, -(1L << 32), 0x8000_0000L, 0xFFFF_FFFFL,
    };

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

    /** A key: mostly from a small working set (with the special keys), sometimes any {@code long}. */
    private static long key(SplittableRandom rnd, long[] workingSet) {
        return rnd.nextInt(10) == 0 ? rnd.nextLong() : workingSet[rnd.nextInt(workingSet.length)];
    }

    private static long[] workingSet(SplittableRandom rnd, int n) {
        long[] keys = new long[n];
        for (int i = 0; i < n; i++) {
            keys[i] = i < SPECIAL_KEYS.length ? SPECIAL_KEYS[i] : rnd.nextLong();
        }
        return keys;
    }

    /** Whether {@code a} comes before {@code b} (same comparison as the heap: {@code -0.0 == 0.0}). */
    private static boolean before(VType a, VType b, boolean maxFirst) {
        return maxFirst ? a > b : a < b;
    }

    /** The first priority of the reference. */
    private static VType first(Map<Long, VBoxed> ref, boolean maxFirst) {
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

    private static void assertSameContent(Map<Long, VBoxed> ref, LongVTypeIndexedHeap h, String context) {
        h.checkInvariants();
        assertEquals(ref.size(), h.size(), context);
        assertEquals(ref.isEmpty(), h.isEmpty(), context);
        Map<Long, VBoxed> each = new HashMap<>();
        h.forEach((key, p) -> assertEquals(null, each.put(key, p), context + ": key twice"));
        assertEquals(ref.keySet(), each.keySet(), context);
        for (Map.Entry<Long, VBoxed> e : ref.entrySet()) {
            VType want = e.getValue();
            assertTrue(want == each.get(e.getKey()), context + " key " + e.getKey());
            assertTrue(h.contains(e.getKey()), context);
            assertTrue(want == h.priority(e.getKey()), context);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "false, 8, 16, 0, 1",
        "false, 1000, 64, 1, 2",
        "false, 1000000, 2000, 16, 3",
        "true, 8, 16, 0, 4",
        "true, 1000, 64, 1, 5",
        "true, 1000000, 2000, 16, 6",
    })
    void randomOperationsMatchAMap(boolean maxFirst, int range, int workingSetSize, int expectedSize, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        long[] keys = workingSet(rnd, workingSetSize);
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(expectedSize, maxFirst);
        assertEquals(maxFirst, h.isMaxFirst());
        Map<Long, VBoxed> ref = new HashMap<>();
        for (int step = 0; step < 30_000; step++) {
            String context = "step " + step;
            long key = key(rnd, keys);
            VType p = priority(rnd, range);
            int op = rnd.nextInt(1000);
            if (op < 300) {
                h.put(key, p);
                ref.put(key, p);
            } else if (op < 600) {
                VBoxed old = ref.get(key);
                boolean better = old == null || before(p, old, maxFirst);
                assertEquals(better, h.putIfBetter(key, p), context + " putIfBetter");
                if (better) {
                    ref.put(key, p);
                }
            } else if (op < 800) {
                if (ref.isEmpty()) {
                    assertThrows(NoSuchElementException.class, h::poll, context);
                } else {
                    VType want = first(ref, maxFirst);
                    VType got = h.peekPriority();
                    assertTrue(want == got, context + " peekPriority: expected " + want + ", got " + got);
                    long top = h.peekKey();
                    assertEquals(top, h.poll(), context);
                    VBoxed removed = ref.remove(top);
                    assertTrue(removed != null && removed == want, context + " polled key " + top);
                }
            } else if (op < 950) {
                assertEquals(ref.remove(key) != null, h.remove(key), context + " remove");
            } else if (op < 952) {
                h.clear();
                ref.clear();
            } else if (op < 953) {
                if (rnd.nextBoolean()) {
                    h.trimToSize();
                } else {
                    h.ensureCapacity(rnd.nextInt(4 * workingSetSize + 1));
                }
            } else {
                assertSameContent(ref, h, context);
            }
            assertEquals(ref.size(), h.size(), context);
            assertEquals(ref.containsKey(key), h.contains(key), context);
            VBoxed want = ref.get(key);
            VType got = h.priorityOrDefault(key, VZero);
            assertTrue(want == null ? got == VZero : want == got, context + " priorityOrDefault");
            if (step % 97 == 0) {
                h.checkInvariants();
            }
        }
        assertSameContent(ref, h, "end");
        while (!ref.isEmpty()) {
            VType want = first(ref, maxFirst);
            assertTrue(want == h.peekPriority(), "drain");
            assertTrue(ref.remove(h.poll()) == want, "drain");
        }
        assertTrue(h.isEmpty());
        h.checkInvariants();
    }

    @Test
    void growsAndShrinksThroughManyTableSizes() {
        SplittableRandom rnd = new SplittableRandom(11);
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(0);
        Map<Long, VBoxed> ref = new HashMap<>();
        // Keys that are multiples of 2^40 differ only in their high bits.
        for (int i = 0; i < 20_000; i++) {
            long key = (i % 2 == 0) ? (long) i << 40 : rnd.nextLong();
            VType p = priority(rnd, 1 << 20);
            h.put(key, p);
            ref.put(key, p);
        }
        assertSameContent(ref, h, "grown");
        // Remove every other key in table order: many backward shifts.
        List<Long> all = new ArrayList<>(ref.keySet());
        for (int i = 0; i < all.size(); i += 2) {
            assertTrue(h.remove(all.get(i)));
            ref.remove(all.get(i));
        }
        assertSameContent(ref, h, "removed");
        for (int i = 0; i < 5_000; i++) {
            VType want = first(ref, false);
            assertTrue(want == h.peekPriority());
            assertTrue(ref.remove(h.poll()) == want);
        }
        assertSameContent(ref, h, "polled");
    }

    /** The timeout pattern: a steady number of keys, deadlines pushed back, the earliest expiring. */
    @Test
    void rescheduledTimersExpireInDeadlineOrder() {
        SplittableRandom rnd = new SplittableRandom(12);
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap();
        List<Long> live = new ArrayList<>();
        Map<Long, Integer> index = new HashMap<>();
        VType now = VZero;
        for (int i = 0; i < 1000; i++) {
            long key = rnd.nextLong();
            h.put(key, now + 1 + rnd.nextInt(1000));
            index.put(key, live.size());
            live.add(key);
        }
        for (int step = 0; step < 50_000; step++) {
            if (rnd.nextInt(4) != 0) {
                long key = live.get(rnd.nextInt(live.size()));
                h.put(key, now + 1 + rnd.nextInt(1000));
            } else {
                VType deadline = h.peekPriority();
                assertTrue(deadline >= now, "deadlines come in order");
                now = deadline;
                long expired = h.poll();
                int at = index.remove(expired);
                long fresh = rnd.nextLong();
                h.put(fresh, now + 1 + rnd.nextInt(1000));
                live.set(at, fresh);
                index.put(fresh, at);
            }
            assertEquals(1000, h.size());
        }
        h.checkInvariants();
        for (long key : live) {
            assertTrue(h.contains(key));
        }
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

    /** Odd multiplier that maps node numbers to sparse 64-bit keys, and its inverse modulo 2^64. */
    private static final long ODD = 0x9E37_79B9_7F4A_7C15L;
    private static final long ODD_INV = inverse(ODD);

    private static long inverse(long a) {
        long x = a; // Newton's iteration: correct to 3, 6, 12, 24, 48, 96 bits
        for (int i = 0; i < 5; i++) {
            x *= 2 - a * x;
        }
        return x;
    }

    /** Dijkstra's algorithm with sparse keys {@code u * ODD}; {@code -1} for unreachable nodes. */
    private static VType[] dijkstra(Graph g, int source, LongVTypeIndexedHeap h) {
        int n = g.start().length - 1;
        VType[] dist = new VType[n];
        boolean[] done = new boolean[n];
        java.util.Arrays.fill(dist, -1);
        h.put(source * ODD, VZero);
        while (!h.isEmpty()) {
            VType d = h.peekPriority();
            int u = (int) (h.poll() * ODD_INV);
            dist[u] = d;
            done[u] = true;
            for (int j = g.start()[u]; j < g.start()[u + 1]; j++) {
                int v = g.to()[j];
                if (!done[v]) {
                    h.putIfBetter(v * ODD, d + g.weight()[j]);
                }
            }
        }
        return dist;
    }

    /** The same with the dense indexed heap, which has its own reference test. */
    private static VType[] denseDijkstra(Graph g, int source) {
        int n = g.start().length - 1;
        IntVTypeIndexedHeap h = new IntVTypeIndexedHeap(n);
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

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 8})
    void dijkstraWithSparseKeysMatchesTheDenseHeap(int degree) {
        assertEquals(1L, ODD * ODD_INV);
        Graph g = Graph.random(3000, degree, degree);
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(0);
        assertArrayEquals(denseDijkstra(g, 0), dijkstra(g, 0, h));
        assertTrue(h.isEmpty());
        h.checkInvariants();
        // the same heap again, from another source
        assertArrayEquals(denseDijkstra(g, 17), dijkstra(g, 17, h));
    }

    @Test
    void emptyHeapAndAbsentKeys() {
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap();
        assertTrue(h.isEmpty());
        assertThrows(NoSuchElementException.class, h::peekKey);
        assertThrows(NoSuchElementException.class, h::peekPriority);
        assertThrows(NoSuchElementException.class, h::poll);
        for (long key : SPECIAL_KEYS) {
            assertThrows(NoSuchElementException.class, () -> h.priority(key));
            assertEquals(7, h.priorityOrDefault(key, 7));
            assertFalse(h.contains(key));
            assertFalse(h.remove(key));
        }
        assertEquals("{}", h.toString());
        h.clear();
        h.checkInvariants();
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new LongVTypeIndexedHeap(-1));
        assertThrows(IllegalArgumentException.class, () -> new LongVTypeIndexedHeap(Integer.MAX_VALUE));
        LongVTypeIndexedHeap sized = new LongVTypeIndexedHeap();
        assertThrows(IllegalArgumentException.class, () -> sized.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> sized.ensureCapacity(Integer.MAX_VALUE));
//#if V.floating
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap();
        assertThrows(IllegalArgumentException.class, () -> h.put(1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> h.putIfBetter(1, Double.NaN));
        assertTrue(h.isEmpty());
        h.put(2, -0.0);
        assertFalse(h.putIfBetter(2, 0.0), "-0.0 and 0.0 are the same priority");
//#end
    }

    @Test
    void zeroAndExtremeKeysAreOrdinaryKeys() {
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(0);
        VType p = VZero;
        for (long key : SPECIAL_KEYS) {
            h.put(key, p);
            p = p + 1;
        }
        h.checkInvariants();
        assertEquals(SPECIAL_KEYS.length, h.size());
        assertTrue(h.contains(0L));
        assertTrue(0 == h.priority(0L));
        assertTrue(h.remove(0L));
        assertFalse(h.contains(0L));
        assertEquals(-1L, h.peekKey());
        for (int i = 1; i < SPECIAL_KEYS.length; i++) {
            assertEquals(SPECIAL_KEYS[i], h.poll());
        }
        assertTrue(h.isEmpty());
    }

    @Test
    void updatesMoveEntriesBothWays() {
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(4);
        for (long k = 0; k < 100; k++) {
            h.put(k << 33, (int) (k * 10));
        }
        h.put(99L << 33, -5); // up
        assertEquals(99L << 33, h.peekKey());
        h.put(99L << 33, 2000); // down
        assertEquals(0L, h.peekKey());
        assertFalse(h.putIfBetter(0L, 5), "not better");
        assertTrue(h.putIfBetter(50L << 33, -1));
        assertEquals(50L << 33, h.poll());
        assertTrue(0 == h.peekPriority());
        List<Long> order = new ArrayList<>();
        while (!h.isEmpty()) {
            order.add(h.poll());
        }
        assertEquals(99, order.size());
        assertEquals(99L << 33, order.getLast());
    }

    @Test
    void clearKeepsTheCapacityAndAllowsReuse() {
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(0, true);
        h.put(-1_000_000_000_000L, 1);
        h.put(3L, 2);
        assertEquals("{3=" + VBoxed.valueOf(2) + ", -1000000000000=" + VBoxed.valueOf(1) + "}", h.toString());
        assertEquals(2, h.size(), "toString leaves the heap unchanged");
        h.clear();
        assertTrue(h.isEmpty());
        h.checkInvariants();
        assertFalse(h.contains(-1_000_000_000_000L));
        assertFalse(h.contains(3L));
        h.put(-1_000_000_000_000L, 4);
        assertEquals(-1_000_000_000_000L, h.peekKey());
        assertEquals(1, h.size());
        h.checkInvariants();
    }

    @Test
    void capacityChangesKeepTheContent() {
        LongVTypeIndexedHeap h = new LongVTypeIndexedHeap(0);
        h.trimToSize();
        h.checkInvariants();
        h.ensureCapacity(10_000);
        h.checkInvariants();
        Map<Long, VBoxed> ref = new HashMap<>();
        for (long i = 0; i < 5_000; i++) {
            long key = i * 0x9E3779B97F4A7C15L;
            VType p = (int) (i % 101);
            h.put(key, p);
            ref.put(key, p);
        }
        for (long i = 0; i < 5_000; i++) {
            if (i % 7 != 0) {
                long key = i * 0x9E3779B97F4A7C15L;
                h.remove(key);
                ref.remove(key);
            }
        }
        h.trimToSize();
        assertSameContent(ref, h, "after trimToSize");
        for (long i = 5_000; i < 6_000; i++) {
            h.put(i, 1);
            ref.put(i, VBoxed.valueOf(1));
        }
        h.ensureCapacity(50_000);
        assertSameContent(ref, h, "after ensureCapacity");
        while (!h.isEmpty()) {
            h.poll();
        }
        h.trimToSize();
        h.checkInvariants();
        h.put(0L, 1);
        assertEquals(0L, h.peekKey());
        h.checkInvariants();
    }
}
