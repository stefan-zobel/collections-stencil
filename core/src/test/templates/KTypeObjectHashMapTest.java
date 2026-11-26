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
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KTypeObjectHashMapTest {

    /**
     * Maps an {@code int} to a key: a bijection with {@code k(0) == 0} that keeps the sign.
//#if K == long
     * The upper 32 bits are used, so that hashing must take them into account.
//#end
     */
    private static KType k(int i) {
//#if K == long
        return ((long) i << 32) | (i & 0xFFFF_FFFFL);
//#else
        return i;
//#end
    }

    @Test
    void basicOperations() {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
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
        assertEquals("b", m.remove(k(1)));
        assertNull(m.remove(k(1)));
        assertFalse(m.containsKey(k(1)));
        assertEquals(2, m.size());
        m.clear();
        assertTrue(m.isEmpty());
        assertNull(m.get(k(-2)));
    }

    @Test
    void zeroKey() {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
        assertFalse(m.containsKey(KZero));
        assertNull(m.put(KZero, "zero"));
        assertTrue(m.containsKey(KZero));
        assertEquals("zero", m.get(KZero));
        assertEquals(1, m.size());
        assertEquals("{0=zero}", m.toString());
        assertEquals("zero", m.remove(KZero));
        assertTrue(m.isEmpty());
    }

    @Test
    void nullValues() {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
        assertNull(m.put(k(5), null));
        assertNull(m.put(KZero, null));
        assertTrue(m.containsKey(k(5)));
        assertTrue(m.containsKey(KZero));
        assertNull(m.getOrDefault(k(5), "default"));
        assertEquals("default", m.getOrDefault(k(6), "default"));
        assertFalse(m.putIfAbsent(k(5), "x"));
        assertEquals(2, m.size());
        Map<KBoxed, String> ref = new HashMap<>();
        ref.put(k(5), null);
        ref.put(KZero, null);
        assertEquals(ref.hashCode(), m.hashCode());
    }

    @Test
    void computeIfAbsentFollowsMapSemantics() {
        for (KType key : new KType[] {KZero, k(7)}) {
            KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
            Map<KBoxed, String> ref = new HashMap<>();
            // absent, function returns null: nothing is stored
            assertEquals(ref.computeIfAbsent(key, x -> null), m.computeIfAbsent(key, x -> null));
            assertFalse(m.containsKey(key));
            // absent: the computed value is stored
            assertEquals(ref.computeIfAbsent(key, x -> "v" + x), m.computeIfAbsent(key, x -> "v" + x));
            assertEquals("v" + key, m.get(key));
            // present: the function is not called
            assertEquals("v" + key, m.computeIfAbsent(key, x -> {
                throw new AssertionError("must not be called");
            }));
            // mapped to null: treated as absent
            m.put(key, null);
            ref.put(key, null);
            assertEquals(ref.computeIfAbsent(key, x -> null), m.computeIfAbsent(key, x -> null));
            assertTrue(m.containsKey(key));
            assertEquals(ref.computeIfAbsent(key, x -> "w"), m.computeIfAbsent(key, x -> "w"));
            assertEquals("w", m.get(key));
            assertEquals(1, m.size());
        }
        assertThrows(NullPointerException.class, () -> new KTypeObjectHashMap<String>().computeIfAbsent(k(1), null));
    }

    @Test
    void computeIfAbsentGroupsValues() {
        KTypeObjectHashMap<List<Integer>> groups = new KTypeObjectHashMap<>(0);
        for (int i = 0; i < 10_000; i++) {
            groups.computeIfAbsent(k(i % 97), x -> new ArrayList<>()).add(i);
        }
        assertEquals(97, groups.size());
        for (int g = 0; g < 97; g++) {
            List<Integer> list = groups.get(k(g));
            assertEquals(g, (int) list.get(0));
            assertEquals((10_000 - g + 96) / 97, list.size());
        }
    }

    @Test
    void indexHandles() {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
        for (KType key : new KType[] {KZero, k(3)}) {
            int i = m.indexOf(key);
            assertFalse(m.indexExists(i));
            m.indexInsert(i, key, "a");
            i = m.indexOf(key);
            assertTrue(m.indexExists(i));
            assertEquals("a", m.indexGet(i));
            assertEquals("a", m.indexReplace(i, "b"));
            assertEquals("b", m.get(key));
            assertEquals("b", m.indexRemove(i));
            assertFalse(m.containsKey(key));
        }
        assertTrue(m.isEmpty());
    }

    @Test
    void extremeKeys() {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
        KType[] keys = {KBoxed.MIN_VALUE, KBoxed.MAX_VALUE, -1, 1, k(-123456), k(1 << 30)};
        for (KType key : keys) {
            m.put(key, "v" + key);
        }
        for (KType key : keys) {
            assertEquals("v" + key, m.get(key));
            assertFalse(m.containsKey(key ^ 2));
        }
        assertEquals(keys.length, m.size());
    }

    @Test
    void equalsAndHashCode() {
        KTypeObjectHashMap<String> a = new KTypeObjectHashMap<>();
        KTypeObjectHashMap<String> b = new KTypeObjectHashMap<>(0, 0.5f);
        Map<KBoxed, String> ref = new HashMap<>();
        assertEquals(a, b);
        for (int i = -1000; i <= 1000; i++) {
            String v = i % 10 == 0 ? null : Integer.toString(i);
            a.put(k(i), v);
            b.put(k(-i), i % 10 == 0 ? null : Integer.toString(-i));
            ref.put(k(i), v);
        }
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(ref.hashCode(), a.hashCode());
        assertEquals(a.hashCode(), b.hashCode());
        b.put(k(1), "other");
        assertNotEquals(a, b);
        b.put(k(1), "1");
        assertEquals(a, b);
        b.put(k(10), "not null");
        assertNotEquals(a, b);
        assertNotEquals(a, ref);
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectHashMap<String>(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectHashMap<String>(10, 0f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectHashMap<String>(10, 1f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectHashMap<String>(10, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectHashMap<String>(Integer.MAX_VALUE));
    }

    @Test
    void ensureCapacityKeepsContent() {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>(0);
        for (int i = 1; i <= 100; i++) {
            m.put(k(i), "v" + i);
        }
        m.ensureCapacity(100_000);
        m.ensureCapacity(10); // no shrinking
        for (int i = 1; i <= 100; i++) {
            assertEquals("v" + i, m.get(k(i)));
        }
    }

    /** Removed values must not stay reachable from the map. */
    @Test
    void removalReleasesValues() throws ReflectiveOperationException {
        KTypeObjectHashMap<Object> m = new KTypeObjectHashMap<>(0);
        SplittableRandom rnd = new SplittableRandom(3);
        for (int n = 0; n < 50_000; n++) {
            KType key = k(rnd.nextInt(2000) - 1000);
            if (rnd.nextBoolean()) {
                m.put(key, new Object());
            } else {
                m.remove(key);
            }
        }
        int i = m.indexOf(k(5));
        if (m.indexExists(i)) {
            m.indexRemove(i);
        }
        m.remove(KZero);
        Field f = KTypeObjectHashMap.class.getDeclaredField("values");
        f.setAccessible(true);
        long referenced = 0;
        for (Object v : (Object[]) f.get(m)) {
            if (v != null) {
                referenced++;
            }
        }
        assertEquals(m.size(), referenced, "one reference per entry, none for removed ones");
        m.clear();
        for (Object v : (Object[]) f.get(m)) {
            assertNull(v);
        }
    }

    @Test
    void valuesAreSharedNotCopied() {
        KTypeObjectHashMap<StringBuilder> m = new KTypeObjectHashMap<>();
        StringBuilder sb = new StringBuilder();
        m.put(k(1), sb);
        assertSame(sb, m.get(k(1)));
    }

    /** Differential test against {@link HashMap}, with removals, growth, and null values. */
    @ParameterizedTest
    @CsvSource({
            "1, 16, 0.75, 1, 200000",
            "2, 5000, 0.97, 1, 200000",
            "3, 20000, 0.5, 4096, 200000",
            "4, 100000, 0.75, 65536, 400000",
    })
    void randomOperationsMatchHashMap(long rngSeed, int keyRange, float loadFactor, int stride, int ops) {
        SplittableRandom rnd = new SplittableRandom(rngSeed);
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>(0, loadFactor);
        Map<KBoxed, String> ref = new HashMap<>();
        for (int n = 0; n < ops; n++) {
            KType key = k((rnd.nextInt(keyRange) - keyRange / 2) * stride); // includes 0 and negatives
            String value = rnd.nextInt(16) == 0 ? null : Integer.toString(rnd.nextInt(1000));
            switch (rnd.nextInt(8)) {
                case 0, 1 -> assertEquals(ref.put(key, value), m.put(key, value));
                case 2 -> assertEquals(ref.remove(key), m.remove(key));
                case 3 -> assertEquals(ref.computeIfAbsent(key, x -> value), m.computeIfAbsent(key, x -> value));
                case 4 -> {
                    boolean absent = !ref.containsKey(key);
                    if (absent) {
                        ref.put(key, value);
                    }
                    assertEquals(absent, m.putIfAbsent(key, value));
                }
                case 5 -> {
                    int idx = m.indexOf(key);
                    assertEquals(ref.containsKey(key), m.indexExists(idx));
                    if (m.indexExists(idx)) {
                        assertEquals(ref.get(key), m.indexGet(idx));
                        if (rnd.nextBoolean()) {
                            assertEquals(ref.put(key, value), m.indexReplace(idx, value));
                        } else {
                            assertEquals(ref.remove(key), m.indexRemove(idx));
                        }
                    } else {
                        ref.put(key, value);
                        m.indexInsert(idx, key, value);
                    }
                }
                case 6 -> assertEquals(ref.containsKey(key), m.containsKey(key));
                default -> assertEquals(ref.getOrDefault(key, "none"), m.getOrDefault(key, "none"));
            }
            assertEquals(ref.size(), m.size());
            if ((n & 0xFFFF) == 0) {
                assertContentEquals(ref, m);
            }
        }
        assertContentEquals(ref, m);
        for (KBoxed key : ref.keySet().toArray(new KBoxed[0])) {
            assertEquals(ref.remove(key), m.remove(key));
        }
        assertTrue(m.isEmpty());
    }

    // ------------------------------------------------------------------
    // Iteration, removeIf, bulk operations
    // ------------------------------------------------------------------

    /** A map and its reference with random content (some null values), including key 0 if {@code withZero}. */
    private static KTypeObjectHashMap<String> fill(float loadFactor, int n, int range, boolean withZero,
                                                   Map<KBoxed, String> ref, long seed) {
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>(0, loadFactor);
        SplittableRandom rnd = new SplittableRandom(seed);
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            if (i != 0 || withZero) {
                String v = i % 11 == 0 ? null : "v" + i;
                m.put(k(i), v);
                ref.put(k(i), v);
            }
        }
        return m;
    }

    private static Map<KBoxed, String> iterate(KTypeObjectHashMap<String> m) {
        Map<KBoxed, String> seen = new HashMap<>();
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            KType key = m.indexKey(i);
            assertFalse(seen.containsKey(key), "visited twice: " + key);
            seen.put(key, m.indexGet(i));
            assertEquals(i, m.indexOf(key));
        }
        return seen;
    }

    @Test
    void iterationVisitsEveryEntryOnce() {
        for (boolean withZero : new boolean[] {false, true}) {
            Map<KBoxed, String> ref = new HashMap<>();
            KTypeObjectHashMap<String> m = fill(0.75f, 3000, 20_000, withZero, ref, 1);
            assertEquals(ref, iterate(m));
        }
        KTypeObjectHashMap<String> m = new KTypeObjectHashMap<>();
        assertEquals(-1, m.firstIndex());
        m.put(KZero, "z");
        assertEquals("z", m.indexGet(m.firstIndex()));
        assertEquals(-1, m.nextIndex(m.firstIndex()));
    }

    /** removeIf against {@link Map#entrySet()}{@code .removeIf}; every entry tested once; removed values released. */
    @ParameterizedTest
    @CsvSource({"0.5, 100", "0.75, 3000", "0.97, 3000", "0.97, 12"})
    void removeIfMatchesHashMap(float loadFactor, int n) throws ReflectiveOperationException {
        for (long seed = 1; seed <= 20; seed++) {
            Map<KBoxed, String> ref = new HashMap<>();
            KTypeObjectHashMap<String> m = fill(loadFactor, n, 4 * n + 10, seed % 2 == 0, ref, seed);
            int modulus = 2 + (int) (seed % 3);
            Map<KBoxed, Integer> calls = new HashMap<>();
            boolean removed = m.removeIf((key, value) -> {
                calls.merge(key, 1, Integer::sum);
                return Math.floorMod(KBoxed.hashCode(key) + java.util.Objects.hashCode(value), modulus) == 0;
            });
            assertEquals(ref.keySet(), calls.keySet(), "every entry is tested");
            for (int c : calls.values()) {
                assertEquals(1, c, "every entry is tested once");
            }
            boolean expected = ref.entrySet().removeIf(e -> Math.floorMod(
                    KBoxed.hashCode(e.getKey()) + java.util.Objects.hashCode(e.getValue()), modulus) == 0);
            assertEquals(expected, removed);
            assertEquals(ref, iterate(m));
            Field f = KTypeObjectHashMap.class.getDeclaredField("values");
            f.setAccessible(true);
            long referenced = 0;
            for (Object v : (Object[]) f.get(m)) {
                if (v != null) {
                    referenced++;
                }
            }
            long nonNull = ref.entrySet().stream().filter(e -> e.getValue() != null && e.getKey() != KZero).count();
            assertEquals(nonNull, referenced, "removed values are released");
        }
    }

    @Test
    void bulkOperationsAndConversions() {
        Map<KBoxed, String> ref = new HashMap<>();
        KTypeObjectHashMap<String> source = fill(0.75f, 2000, 50_000, true, ref, 5);
        KType[] keys = source.keysToArray();
        String[] values = source.valuesToArray(String[]::new);
        assertEquals(ref.size(), keys.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(ref.get(keys[i]), values[i]);
        }
        assertEquals(ref, source.toMap());
        KTypeObjectHashMap<String> copy = new KTypeObjectHashMap<>(source);
        assertEquals(source, copy);
        copy.put(k(99_999), "new");
        assertFalse(source.containsKey(k(99_999)), "copies are independent");
        KTypeObjectHashMap<CharSequence> wider = new KTypeObjectHashMap<>();
        wider.putAll(source);
        wider.putAll(ref);
        assertEquals(ref.size(), wider.size());
        assertEquals(source, wider);
        Map<KBoxed, String> nullKey = new HashMap<>();
        nullKey.put(null, "x");
        assertThrows(NullPointerException.class, () -> new KTypeObjectHashMap<String>().putAll(nullKey));
    }

    private static void assertContentEquals(Map<KBoxed, String> ref, KTypeObjectHashMap<String> m) {
        Map<KBoxed, String> copy = new HashMap<>();
        m.forEach((k, v) -> {
            assertFalse(copy.containsKey(k), "duplicate " + k);
            copy.put(k, v);
        });
        assertEquals(ref, copy);
        assertEquals(ref.hashCode(), m.hashCode());
        for (Map.Entry<KBoxed, String> e : ref.entrySet()) {
            assertEquals(e.getValue(), m.get(e.getKey()));
        }
    }
}
