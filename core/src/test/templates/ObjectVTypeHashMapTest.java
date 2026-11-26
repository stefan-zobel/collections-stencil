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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ObjectVTypeHashMapTest {

    /** A key whose hash codes collide in groups of four: long probe chains. */
    private record Collider(int id) {
        @Override
        public int hashCode() {
            return id >> 2;
        }
    }

    /** Maps an {@code int} to a value. */
    private static VType v(int i) {
        return i;
    }

    /** The {@code i}-th string key, a new object on each call (equal, not identical). */
    private static String s(int i) {
        return "k" + i;
    }

    /** {@code VZero} for {@code null}, otherwise the unboxed value. */
    private static VType orZero(VBoxed b) {
        return b == null ? VZero : b;
    }

    @Test
    void basicOperations() {
        ObjectVTypeHashMap<String> m = new ObjectVTypeHashMap<>();
        assertTrue(m.isEmpty());
        assertEquals(VZero, m.put(s(1), v(10)));
        assertEquals(v(10), m.put(s(1), v(11)));
        assertEquals(VZero, m.put(s(2), v(20)));
        assertEquals(2, m.size());
        assertEquals(v(11), m.get(s(1)));
        assertEquals(v(20), m.get(s(2)));
        assertEquals(VZero, m.get(s(3)));
        assertEquals(v(-1), m.getOrDefault(s(3), v(-1)));
        assertEquals(VZero, m.get(42), "a key of another type is simply absent");
        assertFalse(m.putIfAbsent(s(1), v(99)));
        assertTrue(m.putIfAbsent(s(3), v(30)));
        assertEquals(v(11), m.remove(s(1)));
        assertEquals(VZero, m.remove(s(1)));
        assertFalse(m.containsKey(s(1)));
        assertEquals(2, m.size());
        m.clear();
        assertTrue(m.isEmpty());
        assertEquals(VZero, m.get(s(2)));
    }

    @Test
    void nullKey() {
        ObjectVTypeHashMap<String> m = new ObjectVTypeHashMap<>();
        assertFalse(m.containsKey(null));
        assertEquals(VZero, m.put(null, v(7)));
        assertTrue(m.containsKey(null));
        assertEquals(v(7), m.get(null));
        assertEquals(1, m.size());
        assertEquals("{null=" + v(7) + "}", m.toString());
        assertEquals(v(10), m.addTo(null, v(3)));
        assertFalse(m.putIfAbsent(null, v(1)));
        Map<String, VBoxed> ref = new HashMap<>();
        ref.put(null, v(10));
        assertEquals(ref, m.toMap());
        assertEquals(ref.hashCode(), m.hashCode());
        assertNull(m.indexKey(m.firstIndex()));
        assertEquals(v(10), m.remove(null));
        assertTrue(m.isEmpty());
        assertEquals(-1, m.firstIndex());
    }

    @Test
    void addToCounts() {
        ObjectVTypeHashMap<String> counts = new ObjectVTypeHashMap<>(0);
        Map<String, VBoxed> ref = new HashMap<>();
        SplittableRandom rnd = new SplittableRandom(1);
        for (int n = 0; n < 100_000; n++) {
            String word = s(rnd.nextInt(500));
            VType inc = v(1 + rnd.nextInt(3));
            assertEquals(ref.merge(word, inc, VBoxed::sum), counts.addTo(word, inc));
        }
        assertEquals(ref, counts.toMap());
    }

    @Test
    void indexHandles() {
        ObjectVTypeHashMap<String> m = new ObjectVTypeHashMap<>();
        for (String key : new String[] {null, "a"}) {
            int i = m.indexOf(key);
            assertFalse(m.indexExists(i));
            m.indexInsert(i, key, v(1));
            i = m.indexOf(key);
            assertTrue(m.indexExists(i));
            assertEquals(key, m.indexKey(i));
            assertEquals(v(1), m.indexGet(i));
            assertEquals(v(1), m.indexReplace(i, v(2)));
            assertEquals(v(2), m.get(key));
            assertEquals(v(2), m.indexRemove(i));
            assertFalse(m.containsKey(key));
        }
        assertTrue(m.isEmpty());
    }

    /** Keys whose {@code hashCode()}s differ only in a few bits, or only in their high bits. */
    @Test
    void weakHashCodes() {
        ObjectVTypeHashMap<Integer> m = new ObjectVTypeHashMap<>();
        for (int i = 0; i < 10_000; i++) {
            m.put(i, v(i));
        }
        for (int i = 1; i < 2048; i++) {
            m.put(i << 20, v(-i)); // distinct and positive for i < 2048
        }
        assertEquals(10_000 + 2047, m.size());
        for (int i = 1; i < 10_000; i++) {
            assertEquals(v(i), m.get(i));
        }
        for (int i = 1; i < 2048; i++) {
            assertEquals(v(-i), m.get(i << 20));
        }
    }

    @Test
    void equalsAndHashCode() {
        ObjectVTypeHashMap<String> a = new ObjectVTypeHashMap<>();
        ObjectVTypeHashMap<String> b = new ObjectVTypeHashMap<>(0, 0.5f);
        Map<String, VBoxed> ref = new HashMap<>();
        assertEquals(a, b);
        for (int i = -1000; i <= 1000; i++) {
            a.put(s(i), v(i));
            b.put(s(-i), v(-i));
            ref.put(s(i), v(i));
        }
        a.put(null, v(5));
        b.put(null, v(5));
        ref.put(null, v(5));
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(ref.hashCode(), a.hashCode());
        assertEquals(a.hashCode(), b.hashCode());
        b.put(s(1), v(12345));
        assertNotEquals(a, b);
        b.put(s(1), v(1));
        assertEquals(a, b);
        b.put(null, v(6));
        assertNotEquals(a, b);
        assertNotEquals(a, ref);
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeHashMap<String>(-1));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeHashMap<String>(10, 0f));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeHashMap<String>(10, 1f));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeHashMap<String>(10, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeHashMap<String>(Integer.MAX_VALUE));
    }

    @Test
    void ensureCapacityKeepsContent() {
        ObjectVTypeHashMap<String> m = new ObjectVTypeHashMap<>(0);
        for (int i = 1; i <= 100; i++) {
            m.put(s(i), v(i));
        }
        m.ensureCapacity(100_000);
        m.ensureCapacity(10); // no shrinking
        for (int i = 1; i <= 100; i++) {
            assertEquals(v(i), m.get(s(i)));
        }
    }

    /** Removed keys must not stay reachable from the map. */
    @Test
    void removalReleasesKeys() throws ReflectiveOperationException {
        ObjectVTypeHashMap<Object> m = new ObjectVTypeHashMap<>(0);
        SplittableRandom rnd = new SplittableRandom(3);
        for (int n = 0; n < 50_000; n++) {
            Collider key = new Collider(rnd.nextInt(2000));
            if (rnd.nextBoolean()) {
                m.put(key, v(n));
            } else {
                m.remove(key);
            }
        }
        int i = m.indexOf(new Collider(5));
        if (m.indexExists(i)) {
            m.indexRemove(i);
        }
        assertEquals(m.size(), referencedKeys(m), "one reference per entry, none for removed ones");
        m.clear();
        assertEquals(0, referencedKeys(m));
    }

    /** Number of key references in the table; checks that exactly the slots with a stored hash hold a key. */
    private static long referencedKeys(ObjectVTypeHashMap<?> m) throws ReflectiveOperationException {
        Field kf = ObjectVTypeHashMap.class.getDeclaredField("keys");
        Field hf = ObjectVTypeHashMap.class.getDeclaredField("hashes");
        kf.setAccessible(true);
        hf.setAccessible(true);
        Object[] keys = (Object[]) kf.get(m);
        int[] hashes = (int[]) hf.get(m);
        long referenced = 0;
        for (int i = 0; i < keys.length; i++) {
            assertEquals(hashes[i] != 0, keys[i] != null, "slot " + i);
            if (keys[i] != null) {
                referenced++;
            }
        }
        return referenced;
    }

    /**
     * Differential test against {@link HashMap}, with removals, growth, the null key, keys that
     * are equal but not identical, and colliding hash codes.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 16, 0.75, false, 200000",
            "2, 5000, 0.97, false, 200000",
            "3, 20000, 0.5, false, 200000",
            "4, 100000, 0.75, false, 400000",
            "5, 3000, 0.75, true, 200000",
            "6, 3000, 0.97, true, 200000",
    })
    void randomOperationsMatchHashMap(long rngSeed, int keyRange, float loadFactor, boolean colliding, int ops)
            throws ReflectiveOperationException {
        SplittableRandom rnd = new SplittableRandom(rngSeed);
        ObjectVTypeHashMap<Object> m = new ObjectVTypeHashMap<>(0, loadFactor);
        Map<Object, VBoxed> ref = new HashMap<>();
        for (int n = 0; n < ops; n++) {
            int id = rnd.nextInt(keyRange);
            Object key = id == 0 ? null : colliding ? new Collider(id) : s(id);
            VType value = v(rnd.nextInt(1000) - 500);
            switch (rnd.nextInt(8)) {
                case 0, 1 -> assertEquals(orZero(ref.put(key, value)), m.put(key, value));
                case 2 -> assertEquals(orZero(ref.remove(key)), m.remove(key));
                case 3 -> assertEquals(ref.merge(key, value, VBoxed::sum), m.addTo(key, value));
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
                            assertEquals(orZero(ref.put(key, value)), m.indexReplace(idx, value));
                        } else {
                            assertEquals(orZero(ref.remove(key)), m.indexRemove(idx));
                        }
                    } else {
                        ref.put(key, value);
                        m.indexInsert(idx, key, value);
                    }
                }
                case 6 -> assertEquals(ref.containsKey(key), m.containsKey(key));
                default -> assertEquals(ref.getOrDefault(key, v(-1)), m.getOrDefault(key, v(-1)));
            }
            assertEquals(ref.size(), m.size());
            if ((n & 0xFFFF) == 0) {
                assertContentEquals(ref, m);
            }
        }
        assertContentEquals(ref, m);
        assertEquals(ref.containsKey(null) ? ref.size() - 1 : ref.size(), referencedKeys(m));
        for (Object key : ref.keySet().toArray()) {
            assertEquals(orZero(ref.remove(key)), m.remove(key));
        }
        assertTrue(m.isEmpty());
    }

    // ------------------------------------------------------------------
    // Iteration, removeIf, bulk operations
    // ------------------------------------------------------------------

    /** A map and its reference with random content, including the null key if {@code withNull}. */
    private static ObjectVTypeHashMap<Object> fill(float loadFactor, int n, int range, boolean withNull,
                                                   boolean colliding, Map<Object, VBoxed> ref, long seed) {
        ObjectVTypeHashMap<Object> m = new ObjectVTypeHashMap<>(0, loadFactor);
        SplittableRandom rnd = new SplittableRandom(seed);
        while (ref.size() < n) {
            int i = rnd.nextInt(range);
            if (i != 0 || withNull) {
                Object key = i == 0 ? null : colliding ? new Collider(i) : s(i);
                m.put(key, v(i));
                ref.put(key, v(i));
            }
        }
        return m;
    }

    private static Map<Object, VBoxed> iterate(ObjectVTypeHashMap<Object> m) {
        Map<Object, VBoxed> seen = new HashMap<>();
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            Object key = m.indexKey(i);
            assertFalse(seen.containsKey(key), "visited twice: " + key);
            seen.put(key, m.indexGet(i));
            assertEquals(i, m.indexOf(key));
        }
        return seen;
    }

    @Test
    void iterationVisitsEveryEntryOnce() {
        for (boolean withNull : new boolean[] {false, true}) {
            Map<Object, VBoxed> ref = new HashMap<>();
            ObjectVTypeHashMap<Object> m = fill(0.75f, 3000, 20_000, withNull, false, ref, 1);
            assertEquals(ref, iterate(m));
        }
        ObjectVTypeHashMap<Object> m = new ObjectVTypeHashMap<>();
        assertEquals(-1, m.firstIndex());
        m.put(null, v(1));
        assertEquals(v(1), m.indexGet(m.firstIndex()));
        assertEquals(-1, m.nextIndex(m.firstIndex()));
    }

    /** removeIf against {@link Map#entrySet()}{@code .removeIf}; every entry tested once; removed keys released. */
    @ParameterizedTest
    @CsvSource({"0.5, 100, false", "0.75, 3000, false", "0.97, 3000, false", "0.97, 12, false", "0.97, 3000, true"})
    void removeIfMatchesHashMap(float loadFactor, int n, boolean colliding) throws ReflectiveOperationException {
        for (long seed = 1; seed <= 20; seed++) {
            Map<Object, VBoxed> ref = new HashMap<>();
            ObjectVTypeHashMap<Object> m = fill(loadFactor, n, 4 * n + 10, seed % 2 == 0, colliding, ref, seed);
            int modulus = 2 + (int) (seed % 3);
            Map<Object, Integer> calls = new HashMap<>();
            boolean removed = m.removeIf((key, value) -> {
                calls.merge(Objects.toString(key), 1, Integer::sum);
                return Math.floorMod(Objects.hashCode(key) + VBoxed.hashCode(value), modulus) == 0;
            });
            assertEquals(ref.size(), calls.size(), "every entry is tested");
            for (int c : calls.values()) {
                assertEquals(1, c, "every entry is tested once");
            }
            boolean expected = ref.entrySet().removeIf(e -> Math.floorMod(
                    Objects.hashCode(e.getKey()) + VBoxed.hashCode(e.getValue()), modulus) == 0);
            assertEquals(expected, removed);
            assertEquals(ref, iterate(m));
            assertEquals(ref.containsKey(null) ? ref.size() - 1 : ref.size(), referencedKeys(m),
                    "removed keys are released");
        }
    }

    @Test
    void bulkOperationsAndConversions() {
        Map<Object, VBoxed> ref = new HashMap<>();
        ObjectVTypeHashMap<Object> source = fill(0.75f, 2000, 50_000, true, false, ref, 5);
        Object[] keys = source.keysToArray(Object[]::new);
        VType[] values = source.valuesToArray();
        assertEquals(ref.size(), keys.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(ref.get(keys[i]), values[i]);
        }
        assertEquals(ref, source.toMap());
        ObjectVTypeHashMap<Object> copy = new ObjectVTypeHashMap<>(source);
        assertEquals(source, copy);
        copy.put("new", v(1));
        assertFalse(source.containsKey("new"), "copies are independent");
        ObjectVTypeHashMap<Object> fromMap = new ObjectVTypeHashMap<>();
        fromMap.putAll(ref);
        assertEquals(source, fromMap);
        ObjectVTypeHashMap<CharSequence> narrow = new ObjectVTypeHashMap<>();
        narrow.put("a", v(1));
        ObjectVTypeHashMap<Object> wider = new ObjectVTypeHashMap<>();
        wider.putAll(narrow);
        assertArrayEquals(new Object[] {"a"}, wider.keysToArray(Object[]::new));
        Map<String, VBoxed> nullValue = new HashMap<>();
        nullValue.put("x", null);
        assertThrows(NullPointerException.class, () -> new ObjectVTypeHashMap<String>().putAll(nullValue));
    }

    @Test
    void toStringShowsEntries() {
        ObjectVTypeHashMap<Object> m = new ObjectVTypeHashMap<>();
        assertEquals("{}", m.toString());
        m.put("a", v(1));
        assertEquals("{a=" + v(1) + "}", m.toString());
    }

    private static void assertContentEquals(Map<Object, VBoxed> ref, ObjectVTypeHashMap<Object> m) {
        Map<Object, VBoxed> copy = new HashMap<>();
        m.forEach((k, v) -> {
            assertFalse(copy.containsKey(k), "duplicate " + k);
            copy.put(k, v);
        });
        assertEquals(ref, copy);
        assertEquals(ref.hashCode(), m.hashCode());
        for (Map.Entry<Object, VBoxed> e : ref.entrySet()) {
            assertEquals(e.getValue(), m.get(e.getKey()));
        }
    }
}
