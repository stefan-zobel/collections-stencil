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
//#types K=int|long
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KTypeObjectTreeMapTest {

    private static final int LEAF = KTypeObjectTreeMap.LEAF;
    private static final int INNER = KTypeObjectTreeMap.INNER;

    /**
     * Maps an {@code int} to a key: an order-preserving bijection with {@code k(0) == 0}.
//#if K == long
     * The upper 32 bits are used, so that comparisons must take them into account.
//#end
     */
    private static KType k(int i) {
//#if K == long
        return ((long) i << 32) | (i & 0xFFFF_FFFFL);
//#else
        return i;
//#end
    }

    private static final KType MIN = KBoxed.MIN_VALUE;
    private static final KType MAX = KBoxed.MAX_VALUE;

    private static String value(int i) {
        return i % 11 == 0 ? null : "v" + i;
    }

    @Test
    void basicOperations() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        assertTrue(m.isEmpty());
        assertNull(m.put(k(1), "a"));
        assertEquals("a", m.put(k(1), "b"));
        assertNull(m.put(k(-2), "c"));
        assertEquals(2, m.size());
        assertEquals("b", m.get(k(1)));
        assertEquals("c", m.get(k(-2)));
        assertNull(m.get(k(2)));
        assertEquals("x", m.getOrDefault(k(2), "x"));
        assertFalse(m.putIfAbsent(k(1), "z"));
        assertTrue(m.putIfAbsent(k(3), "d"));
        assertEquals("{" + k(-2) + "=c, " + k(1) + "=b, " + k(3) + "=d}", m.toString());
        assertEquals("b", m.remove(k(1)));
        assertNull(m.remove(k(1)));
        assertFalse(m.containsKey(k(1)));
        assertEquals(2, m.size());
        m.clear();
        assertTrue(m.isEmpty());
        assertNull(m.get(k(-2)));
        assertEquals("{}", m.toString());
        checkInvariants(m);
    }

    @Test
    void extremeKeysAndNullValues() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        m.put(MAX, "max");
        m.put(KZero, null);
        m.put(MIN, "min");
        assertEquals(MIN, m.firstKey());
        assertEquals(MAX, m.lastKey());
        assertTrue(m.containsKey(KZero));
        assertNull(m.getOrDefault(KZero, "default"));
        assertEquals("max", m.floorValue(MAX));
        assertEquals("min", m.ceilingValue(MIN));
        assertEquals(-1, m.lowerIndex(MIN));
        assertEquals(-1, m.higherIndex(MAX));
        assertEquals(3, m.countRange(MIN, true, MAX, true));
        assertEquals(2, m.countRange(MIN, MAX));
        assertEquals(1, m.countRange(MIN, false, MAX, false));
        Map<KBoxed, String> ref = new HashMap<>();
        ref.put(MAX, "max");
        ref.put(KZero, null);
        ref.put(MIN, "min");
        assertEquals(ref.hashCode(), m.hashCode());
        assertEquals(new TreeMap<>(ref), m.toNavigableMap());
        assertNull(m.remove(KZero));
        assertEquals(2, m.size());
    }

    @Test
    void emptyMap() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        assertEquals(-1, m.firstIndex());
        assertEquals(-1, m.lastIndex());
        assertEquals(-1, m.floorIndex(KZero));
        assertEquals(-1, m.ceilingIndex(KZero));
        assertThrows(NoSuchElementException.class, m::firstKey);
        assertThrows(NoSuchElementException.class, m::lastKey);
        assertEquals(k(7), m.floorKey(KZero, k(7)));
        assertThrows(NoSuchElementException.class, () -> m.pollFirst());
        assertThrows(NoSuchElementException.class, () -> m.pollLast());
        assertFalse(m.pollFirst((key, v) -> {
            throw new AssertionError();
        }));
        assertEquals(0, m.countRange(MIN, true, MAX, true));
        assertEquals(0, m.removeRange(MIN, true, MAX, true));
        assertTrue(m.subMapCopy(MIN, MAX).isEmpty());
        assertFalse(m.removeIf((key, v) -> true));
        checkInvariants(m);
    }

    @Test
    void invalidArguments() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectTreeMap<String>(-1));
        assertThrows(IllegalArgumentException.class, () -> m.countRange(k(2), k(1)));
        assertThrows(IllegalArgumentException.class, () -> m.removeRange(k(2), k(1)));
        assertThrows(IllegalArgumentException.class, () -> m.subMapCopy(k(2), k(1)));
        assertThrows(IllegalArgumentException.class, () -> m.forEachInRange(k(2), k(1), (key, v) -> { }));
        assertThrows(NullPointerException.class, () -> m.computeIfAbsent(k(1), null));
        assertThrows(NullPointerException.class, () -> m.removeIf(null));
        // Empty ranges with from == to are valid.
        assertEquals(0, m.countRange(k(1), k(1)));
    }

    @Test
    void computeIfAbsentFollowsMapSemantics() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        Map<KBoxed, String> ref = new HashMap<>();
        KType key = k(7);
        assertEquals(ref.computeIfAbsent(key, x -> null), m.computeIfAbsent(key, x -> null));
        assertFalse(m.containsKey(key));
        assertEquals(ref.computeIfAbsent(key, x -> "v" + x), m.computeIfAbsent(key, x -> "v" + x));
        assertEquals("v" + key, m.computeIfAbsent(key, x -> {
            throw new AssertionError("must not be called");
        }));
        m.put(key, null);
        assertEquals("w", m.computeIfAbsent(key, x -> "w"));
        assertEquals(1, m.size());
    }

    @Test
    void indexHandles() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        int i = m.indexOf(k(3));
        assertFalse(m.indexExists(i));
        m.indexInsert(i, k(3), "a");
        i = m.indexOf(k(3));
        assertTrue(m.indexExists(i));
        assertEquals("a", m.indexGet(i));
        assertEquals("a", m.indexReplace(i, "b"));
        assertEquals(k(3), m.indexKey(i));
        assertEquals("b", m.indexRemove(i));
        assertTrue(m.isEmpty());
        // Appending behind a full leaf through a handle: the handle must stay inside the leaf.
        for (int j = 0; j < 10 * LEAF; j++) {
            i = m.indexOf(k(j));
            assertFalse(m.indexExists(i));
            m.indexInsert(i, k(j), value(j));
        }
        // Inserting in the middle of full leaves, with an unrelated lookup in between.
        for (int j = 0; j < 10 * LEAF; j++) {
            i = m.indexOf(k(-j - 1));
            m.indexOf(k(j));
            m.indexInsert(i, k(-j - 1), value(j));
        }
        assertEquals(20 * LEAF, m.size());
        checkInvariants(m);
        for (int j = -10 * LEAF; j < 10 * LEAF; j++) {
            assertEquals(k(j), m.indexKey(m.indexOf(k(j))));
        }
    }

    @Test
    void ascendingInsertsFillLeaves() throws ReflectiveOperationException {
        int n = 100 * LEAF * INNER / 8 + 17;
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        for (int i = 0; i < n; i++) {
            m.put(k(i), value(i));
        }
        checkInvariants(m);
        int leaves = new Tree(m).reachableLeaves;
        assertEquals((n + LEAF - 1) / LEAF, leaves, "ascending inserts fill leaves completely");
        KTypeObjectTreeMap<String> d = new KTypeObjectTreeMap<>();
        for (int i = n - 1; i >= 0; i--) {
            d.put(k(i), value(i));
        }
        checkInvariants(d);
        assertEquals(m, d);
        int i = 0;
        for (int h = d.firstIndex(); h >= 0; h = d.nextIndex(h), i++) {
            assertEquals(k(i), d.indexKey(h));
        }
        assertEquals(n, i);
        for (int h = d.lastIndex(); h >= 0; h = d.prevIndex(h)) {
            assertEquals(k(--i), d.indexKey(h));
        }
        assertEquals(0, i);
    }

    @Test
    void pollRemovesInOrder() {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        TreeMap<KBoxed, String> ref = new TreeMap<>();
        SplittableRandom rnd = new SplittableRandom(3);
        while (ref.size() < 5000) {
            int i = rnd.nextInt(1_000_000) - 500_000;
            m.put(k(i), value(i));
            ref.put(k(i), value(i));
        }
        while (!ref.isEmpty()) {
            if (ref.size() % 2 == 0) {
                Map.Entry<KBoxed, String> e = ref.pollFirstEntry();
                if (ref.size() % 4 == 0) {
                    assertEquals(e.getValue(), m.pollFirst());
                } else {
                    List<Object> got = new ArrayList<>();
                    assertTrue(m.pollFirst((key, v) -> {
                        got.add(key);
                        got.add(v);
                    }));
                    assertEquals(e.getKey(), got.get(0));
                    assertEquals(e.getValue(), got.get(1));
                }
            } else {
                Map.Entry<KBoxed, String> e = ref.pollLastEntry();
                if (ref.size() % 4 == 1) {
                    assertEquals(e.getValue(), m.pollLast());
                } else {
                    List<Object> got = new ArrayList<>();
                    assertTrue(m.pollLast((key, v) -> {
                        got.add(key);
                        got.add(v);
                    }));
                    assertEquals(e.getKey(), got.get(0));
                    assertEquals(e.getValue(), got.get(1));
                }
            }
            assertEquals(ref.size(), m.size());
            if (ref.size() % 500 == 0) {
                checkInvariants(m);
            }
        }
        checkInvariants(m);
    }

    // ------------------------------------------------------------------
    // Differential test against java.util.TreeMap
    // ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "1, 1000, 200000",
        "2, 30000, 300000",
        "3, 2000000, 400000",
        "4, 300, 100000",
    })
    void randomOperationsMatchTreeMap(long seed, int keyRange, int ops) {
        SplittableRandom rnd = new SplittableRandom(seed);
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        TreeMap<KBoxed, String> ref = new TreeMap<>();
        for (int op = 0; op < ops; op++) {
            int i = rnd.nextInt(keyRange) - keyRange / 2;
            KType key = k(i);
            switch (rnd.nextInt(16)) {
                case 0, 1, 2, 3 -> assertEquals(ref.put(key, value(i)), m.put(key, value(i)));
                case 4, 5 -> assertEquals(ref.remove(key), m.remove(key));
                case 6 -> {
                    boolean absent = !ref.containsKey(key);
                    if (absent) {
                        ref.put(key, "p" + i);
                    }
                    assertEquals(absent, m.putIfAbsent(key, "p" + i));
                }
                case 7 -> assertEquals(ref.computeIfAbsent(key, x -> "c" + x), m.computeIfAbsent(key, x -> "c" + x));
                case 8 -> {
                    int h = m.indexOf(key);
                    assertEquals(ref.containsKey(key), m.indexExists(h));
                    if (m.indexExists(h)) {
                        assertEquals(ref.get(key), m.indexGet(h));
                        if (rnd.nextBoolean()) {
                            assertEquals(ref.put(key, "r" + i), m.indexReplace(h, "r" + i));
                        } else {
                            assertEquals(ref.remove(key), m.indexRemove(h));
                        }
                    } else {
                        ref.put(key, "i" + i);
                        m.indexInsert(h, key, "i" + i);
                    }
                }
                case 9, 10 -> checkNavigation(m, ref, key);
                case 11 -> {
                    if (rnd.nextInt(8) == 0) {
                        if (rnd.nextBoolean()) {
                            Map.Entry<KBoxed, String> e = ref.pollFirstEntry();
                            if (e == null) {
                                assertThrows(NoSuchElementException.class, () -> m.pollFirst());
                            } else {
                                assertEquals(e.getValue(), m.pollFirst());
                            }
                        } else {
                            Map.Entry<KBoxed, String> e = ref.pollLastEntry();
                            if (e == null) {
                                assertThrows(NoSuchElementException.class, () -> m.pollLast());
                            } else {
                                assertEquals(e.getValue(), m.pollLast());
                            }
                        }
                    }
                }
                case 12, 13 -> checkRange(m, ref, rnd, keyRange, false);
                case 14 -> {
                    if (rnd.nextInt(16) == 0) {
                        checkRange(m, ref, rnd, keyRange, true);
                    }
                }
                default -> {
                    assertEquals(ref.containsKey(key), m.containsKey(key));
                    assertEquals(ref.getOrDefault(key, "d"), m.getOrDefault(key, "d"));
                }
            }
            assertEquals(ref.size(), m.size());
            if ((op & 0x3FFF) == 0) {
                checkInvariants(m);
                assertContentEquals(ref, m);
            }
        }
        checkInvariants(m);
        assertContentEquals(ref, m);
        for (KBoxed key : ref.keySet().toArray(new KBoxed[0])) {
            assertEquals(ref.remove(key), m.remove(key));
        }
        assertTrue(m.isEmpty());
        checkInvariants(m);
    }

    @Test
    void largeTreeWithRangeRemovals() throws ReflectiveOperationException {
        SplittableRandom rnd = new SplittableRandom(11);
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        TreeMap<KBoxed, String> ref = new TreeMap<>();
        int range = 50_000_000;
        // Enough entries for three inner levels with randomly filled nodes.
        int n = LEAF * INNER * INNER;
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            m.put(k(i), value(i));
            ref.put(k(i), value(i));
        }
        checkInvariants(m);
        assertTrue(new Tree(m).height >= 3, "height");
        for (int r = 0; r < 120; r++) {
            checkRange(m, ref, rnd, range, true);
            for (int j = 0; j < 2000; j++) {
                int i = rnd.nextInt(range) - range / 2;
                m.put(k(i), value(i));
                ref.put(k(i), value(i));
            }
            if (r % 20 == 0) {
                checkInvariants(m);
            }
        }
        checkInvariants(m);
        assertContentEquals(ref, m);
        // Remove everything in a few large ranges, then refill.
        assertEquals(ref.headMap(k(0)).size(), m.removeRange(MIN, k(0)));
        ref.headMap(k(0)).clear();
        checkInvariants(m);
        assertEquals(ref.size(), m.removeRange(MIN, true, MAX, true));
        ref.clear();
        assertTrue(m.isEmpty());
        checkInvariants(m);
        for (int i = 0; i < 3 * LEAF * INNER; i++) {
            int j = rnd.nextInt(range);
            m.put(k(j), value(j));
            ref.put(k(j), value(j));
        }
        checkInvariants(m);
        assertContentEquals(ref, m);
    }

    private static void checkNavigation(KTypeObjectTreeMap<String> m, TreeMap<KBoxed, String> ref, KType key) {
        checkHandle(m, ref, ref.floorKey(key), m.floorIndex(key));
        checkHandle(m, ref, ref.ceilingKey(key), m.ceilingIndex(key));
        checkHandle(m, ref, ref.lowerKey(key), m.lowerIndex(key));
        checkHandle(m, ref, ref.higherKey(key), m.higherIndex(key));
        KBoxed f = ref.floorKey(key);
        assertEquals(f == null ? k(123) : (KType) f, m.floorKey(key, k(123)));
        KBoxed c = ref.ceilingKey(key);
        assertEquals(c == null ? k(123) : (KType) c, m.ceilingKey(key, k(123)));
        KBoxed lo = ref.lowerKey(key);
        assertEquals(lo == null ? k(123) : (KType) lo, m.lowerKey(key, k(123)));
        KBoxed hi = ref.higherKey(key);
        assertEquals(hi == null ? k(123) : (KType) hi, m.higherKey(key, k(123)));
        assertEquals(f == null ? null : ref.get(f), m.floorValue(key));
        assertEquals(c == null ? null : ref.get(c), m.ceilingValue(key));
        assertEquals(lo == null ? null : ref.get(lo), m.lowerValue(key));
        assertEquals(hi == null ? null : ref.get(hi), m.higherValue(key));
        int h = m.floorIndex(key);
        if (h >= 0) {
            KBoxed next = ref.higherKey(m.indexKey(h));
            checkHandle(m, ref, next, m.nextIndex(h));
            KBoxed prev = ref.lowerKey(m.indexKey(h));
            checkHandle(m, ref, prev, m.prevIndex(h));
        }
        if (!ref.isEmpty()) {
            assertEquals((KType) ref.firstKey(), m.firstKey());
            assertEquals((KType) ref.lastKey(), m.lastKey());
            checkHandle(m, ref, ref.firstKey(), m.firstIndex());
            checkHandle(m, ref, ref.lastKey(), m.lastIndex());
        }
    }

    private static void checkHandle(KTypeObjectTreeMap<String> m, TreeMap<KBoxed, String> ref, KBoxed expected, int h) {
        if (expected == null) {
            assertEquals(-1, h);
        } else {
            assertTrue(h >= 0, "handle expected for " + expected);
            assertEquals((KType) expected, m.indexKey(h));
            assertEquals(ref.get(expected), m.indexGet(h));
        }
    }

    /** Range operations on a random range; with {@code remove}, also {@code removeRange} and {@code subMapCopy}. */
    private static void checkRange(KTypeObjectTreeMap<String> m, TreeMap<KBoxed, String> ref, SplittableRandom rnd,
                                   int keyRange, boolean remove) {
        int a = rnd.nextInt(keyRange) - keyRange / 2;
        int width = rnd.nextInt(8) == 0 ? rnd.nextInt(keyRange / 2 + 1) : rnd.nextInt(keyRange / 50 + 2);
        int b = (int) Math.min((long) a + width, Integer.MAX_VALUE);
        KType from = k(a);
        KType to = k(b);
        boolean fi = rnd.nextBoolean();
        boolean ti = rnd.nextBoolean();
        NavigableMap<KBoxed, String> sub = ref.subMap(from, fi, to, ti);
        assertEquals(sub.size(), m.countRange(from, fi, to, ti));
        if (fi && !ti) {
            assertEquals(sub.size(), m.countRange(from, to));
        }
        List<Object> seen = new ArrayList<>();
        m.forEachInRange(from, fi, to, ti, (key, v) -> {
            seen.add(key);
            seen.add(v);
        });
        List<Object> expected = new ArrayList<>();
        sub.forEach((key, v) -> {
            expected.add(key);
            expected.add(v);
        });
        assertEquals(expected, seen);
        if (remove) {
            KTypeObjectSortedMap<String> copy = m.subMapCopy(from, fi, to, ti);
            checkInvariants((KTypeObjectTreeMap<String>) copy);
            assertEquals(sub, copy.toNavigableMap());
            int n = sub.size();
            assertEquals(n, m.removeRange(from, fi, to, ti));
            sub.clear();
            assertEquals(ref.size(), m.size());
        }
    }

    // ------------------------------------------------------------------
    // removeIf, copies, conversions
    // ------------------------------------------------------------------

    private static KTypeObjectTreeMap<String> fill(int n, int range, TreeMap<KBoxed, String> ref, long seed) {
        KTypeObjectTreeMap<String> m = new KTypeObjectTreeMap<>();
        SplittableRandom rnd = new SplittableRandom(seed);
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            m.put(k(i), value(i));
            ref.put(k(i), value(i));
        }
        return m;
    }

    @ParameterizedTest
    @CsvSource({"12", "100", "5000", "60000"})
    void removeIfMatchesTreeMap(int n) {
        for (long seed = 1; seed <= 6; seed++) {
            TreeMap<KBoxed, String> ref = new TreeMap<>();
            KTypeObjectTreeMap<String> m = fill(n, 4 * n + 10, ref, seed);
            int modulus = 1 + (int) (seed % 4); // 1 removes everything
            Map<KBoxed, Integer> calls = new HashMap<>();
            List<KBoxed> order = new ArrayList<>();
            boolean removed = m.removeIf((key, value) -> {
                calls.merge(key, 1, Integer::sum);
                order.add(key);
                return Math.floorMod(KBoxed.hashCode(key) + java.util.Objects.hashCode(value), modulus) == 0;
            });
            assertEquals(new ArrayList<>(ref.keySet()), order, "every entry is tested once, in key order");
            boolean expected = ref.entrySet().removeIf(e -> Math.floorMod(
                    KBoxed.hashCode(e.getKey()) + java.util.Objects.hashCode(e.getValue()), modulus) == 0);
            assertEquals(expected, removed);
            checkInvariants(m);
            assertContentEquals(ref, m);
            // The tree stays usable.
            for (int i = 0; i < 1000; i++) {
                m.put(k(i * 7), value(i));
                ref.put(k(i * 7), value(i));
            }
            checkInvariants(m);
            assertContentEquals(ref, m);
        }
    }

    @Test
    void removeIfKeepsAConsistentMapWhenThePredicateThrows() {
        TreeMap<KBoxed, String> ref = new TreeMap<>();
        KTypeObjectTreeMap<String> m = fill(5000, 100_000, ref, 9);
        int[] calls = {0};
        assertThrows(IllegalStateException.class, () -> m.removeIf((key, v) -> {
            if (++calls[0] == 3000) {
                throw new IllegalStateException();
            }
            return calls[0] % 3 == 0;
        }));
        int[] n = {0};
        ref.entrySet().removeIf(e -> ++n[0] < 3000 && n[0] % 3 == 0);
        checkInvariants(m);
        assertContentEquals(ref, m);
    }

    @Test
    void copiesAndConversions() {
        TreeMap<KBoxed, String> ref = new TreeMap<>();
        KTypeObjectTreeMap<String> source = fill(20_000, 1_000_000, ref, 5);
        KTypeObjectTreeMap<String> copy = new KTypeObjectTreeMap<>(source);
        checkInvariants(copy);
        assertEquals(source, copy);
        assertEquals(source.hashCode(), copy.hashCode());
        copy.put(k(9_999_999), "new");
        assertFalse(source.containsKey(k(9_999_999)), "copies are independent");
        assertNotEquals(source, copy);

        KTypeObjectHashMap<String> hash = new KTypeObjectHashMap<>(source);
        assertEquals(hash, source);
        assertEquals(source, hash);
        KTypeObjectTreeMap<String> fromHash = new KTypeObjectTreeMap<>(hash);
        checkInvariants(fromHash);
        assertEquals(source, fromHash);
        assertEquals(source.toString(), fromHash.toString());

        assertEquals(ref, source.toNavigableMap());
        assertEquals(ref.toString(), source.toString());
        KType[] keys = source.keysToArray();
        String[] values = source.valuesToArray(String[]::new);
        int i = 0;
        for (Map.Entry<KBoxed, String> e : ref.entrySet()) {
            assertEquals((KType) e.getKey(), keys[i]);
            assertEquals(e.getValue(), values[i++]);
        }
        assertEquals(new HashMap<>(ref), source.toMap());
        assertEquals(new ArrayList<>(source.toNavigableMap().keySet()), new ArrayList<>(source.toMap().keySet()),
                "toMap keeps the key order");

        KTypeObjectTreeMap<CharSequence> wider = new KTypeObjectTreeMap<>();
        wider.putAll(source);
        wider.putAll(ref);
        assertEquals(source, wider);
        checkInvariants(wider);

        KTypeObjectTreeMap<String> sub = source.subMapCopy(k(-100_000), k(100_000));
        checkInvariants(sub);
        assertEquals(ref.subMap(k(-100_000), k(100_000)), sub.toNavigableMap());
        KTypeObjectTreeMap<String> all = source.subMapCopy(MIN, true, MAX, true);
        assertEquals(source, all);
        assertSame(KTypeObjectTreeMap.class, all.getClass());
    }

    @Test
    void clearAndEnsureCapacityKeepContentRules() {
        TreeMap<KBoxed, String> ref = new TreeMap<>();
        KTypeObjectTreeMap<String> m = fill(10_000, 100_000, ref, 2);
        m.ensureCapacity(1_000_000);
        checkInvariants(m);
        assertContentEquals(ref, m);
        m.clear();
        assertTrue(m.isEmpty());
        checkInvariants(m);
        ref.clear();
        for (int i = 0; i < 5000; i++) {
            m.put(k(i * 3), value(i));
            ref.put(k(i * 3), value(i));
        }
        checkInvariants(m);
        assertContentEquals(ref, m);
        KTypeObjectTreeMap<String> presized = new KTypeObjectTreeMap<>(100_000);
        presized.putAll(m);
        assertEquals(m, presized);
        checkInvariants(presized);
    }

    @Test
    void computeIfAbsentGroupsValues() {
        KTypeObjectTreeMap<List<Integer>> groups = new KTypeObjectTreeMap<>();
        for (int i = 0; i < 10_000; i++) {
            groups.computeIfAbsent(k(i % 97), x -> new ArrayList<>()).add(i);
        }
        assertEquals(97, groups.size());
        int g = 0;
        for (int h = groups.firstIndex(); h >= 0; h = groups.nextIndex(h), g++) {
            assertEquals(k(g), groups.indexKey(h));
            assertEquals(g, (int) groups.indexGet(h).get(0));
        }
    }

    private static void assertContentEquals(TreeMap<KBoxed, String> ref, KTypeObjectTreeMap<String> m) {
        List<Object> seen = new ArrayList<>();
        m.forEach((key, v) -> {
            seen.add(key);
            seen.add(v);
        });
        List<Object> expected = new ArrayList<>();
        ref.forEach((key, v) -> {
            expected.add(key);
            expected.add(v);
        });
        assertEquals(expected, seen);
        assertEquals(ref.hashCode(), m.hashCode());
        assertEquals(ref.size(), m.size());
    }

    // ------------------------------------------------------------------
    // Structural invariants (read through reflection)
    // ------------------------------------------------------------------

    /** The fields of a tree and the result of walking it from the root. */
    private static final class Tree {
        final KType[] leafKeys;
        final Object[] leafValues;
        final int[] leafSize;
        final int[] leafNext;
        final int[] leafPrev;
        final int leafCount;
        final int freeLeaf;
        final KType[] innerKeys;
        final int[] innerChildren;
        final int[] innerSize;
        final int innerCount;
        final int freeInner;
        final int root;
        final int height;
        final int firstLeaf;
        final int lastLeaf;
        final int size;
        final List<Integer> leaves = new ArrayList<>();
        final Set<Integer> leafSet = new HashSet<>();
        final Set<Integer> inner = new HashSet<>();
        int reachableLeaves;

        Tree(KTypeObjectTreeMap<?> m) throws ReflectiveOperationException {
            leafKeys = (KType[]) get(m, "leafKeys");
            leafValues = (Object[]) get(m, "leafValues");
            leafSize = (int[]) get(m, "leafSize");
            leafNext = (int[]) get(m, "leafNext");
            leafPrev = (int[]) get(m, "leafPrev");
            leafCount = (int) get(m, "leafCount");
            freeLeaf = (int) get(m, "freeLeaf");
            innerKeys = (KType[]) get(m, "innerKeys");
            innerChildren = (int[]) get(m, "innerChildren");
            innerSize = (int[]) get(m, "innerSize");
            innerCount = (int) get(m, "innerCount");
            freeInner = (int) get(m, "freeInner");
            root = (int) get(m, "root");
            height = (int) get(m, "height");
            firstLeaf = (int) get(m, "firstLeaf");
            lastLeaf = (int) get(m, "lastLeaf");
            size = (int) get(m, "size");
            walk(root, height, false, KZero, false, KZero, true, true);
            reachableLeaves = leaves.size();
        }

        private static Object get(Object o, String name) throws ReflectiveOperationException {
            Field f = KTypeObjectTreeMap.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        }

        private void walk(int node, int level, boolean hasLo, KType lo, boolean hasHi, KType hi,
                          boolean rightmost, boolean isRoot) {
            if (level == 0) {
                int n = leafSize[node];
                assertTrue(n <= LEAF, "leaf size");
                if (!isRoot) {
                    assertTrue(n >= 1, "non-root leaf is empty");
                    if (!rightmost) {
                        assertTrue(n >= LEAF / 4, "leaf underfull: " + n);
                    }
                }
                int base = node * LEAF;
                for (int j = 0; j < n; j++) {
                    KType key = leafKeys[base + j];
                    if (j > 0) {
                        assertTrue(leafKeys[base + j - 1] < key, "leaf keys sorted");
                    }
                    assertTrue(!hasLo || key >= lo, "key below the separator");
                    assertTrue(!hasHi || key < hi, "key above the separator");
                }
                for (int j = n; j < LEAF; j++) {
                    assertNull(leafValues[base + j], "vacant value slot is cleared");
                }
                assertTrue(leafSet.add(node), "leaf reachable twice");
                leaves.add(node);
                return;
            }
            int n = innerSize[node];
            assertTrue(n >= 2 && n <= INNER, "inner node size: " + n);
            if (!isRoot && !rightmost) {
                assertTrue(n >= INNER / 4, "inner node underfull: " + n);
            }
            assertTrue(inner.add(node), "inner node reachable twice");
            int base = node * INNER;
            for (int c = 1; c < n - 1; c++) {
                assertTrue(innerKeys[base + c - 1] < innerKeys[base + c], "separators sorted");
            }
            for (int c = 0; c < n; c++) {
                boolean cHasLo = c > 0 || hasLo;
                KType cLo = c > 0 ? innerKeys[base + c - 1] : lo;
                boolean cHasHi = c < n - 1 || hasHi;
                KType cHi = c < n - 1 ? innerKeys[base + c] : hi;
                walk(innerChildren[base + c], level - 1, cHasLo, cLo, cHasHi, cHi, rightmost && c == n - 1, false);
            }
        }
    }

    private static void checkInvariants(KTypeObjectTreeMap<?> m) {
        Tree t;
        try {
            t = new Tree(m);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        // Leaf chain in both directions equals the leaves in tree order.
        List<Integer> chain = new ArrayList<>();
        int prev = -1;
        for (int leaf = t.firstLeaf; leaf >= 0; leaf = t.leafNext[leaf]) {
            assertEquals(prev, t.leafPrev[leaf], "prev link");
            chain.add(leaf);
            prev = leaf;
            assertTrue(chain.size() <= t.leafCount, "cycle in the leaf chain");
        }
        assertEquals(t.leaves, chain, "leaf chain");
        assertEquals(prev, t.lastLeaf, "last leaf");
        long total = 0;
        for (int leaf : t.leaves) {
            total += t.leafSize[leaf];
        }
        assertEquals(t.size, total, "size");
        assertEquals(m.size(), t.size);
        // Free lists are disjoint from the tree and cover the rest of the pools.
        Set<Integer> free = new HashSet<>();
        for (int leaf = t.freeLeaf; leaf >= 0; leaf = t.leafNext[leaf]) {
            assertFalse(t.leafSet.contains(leaf), "free leaf in the tree");
            assertTrue(free.add(leaf), "free leaf twice");
            for (int j = 0; j < LEAF; j++) {
                assertNull(t.leafValues[leaf * LEAF + j], "free leaf holds a value");
            }
        }
        assertEquals(t.leafCount, t.leaves.size() + free.size(), "leaf pool");
        for (int j = t.leafCount * LEAF; j < t.leafValues.length; j++) {
            assertNull(t.leafValues[j], "unused pool slot holds a value");
        }
        free.clear();
        for (int node = t.freeInner; node >= 0; node = t.innerChildren[node * INNER]) {
            assertFalse(t.inner.contains(node), "free inner node in the tree");
            assertTrue(free.add(node), "free inner node twice");
        }
        assertEquals(t.innerCount, t.inner.size() + free.size(), "inner pool");
    }
}
