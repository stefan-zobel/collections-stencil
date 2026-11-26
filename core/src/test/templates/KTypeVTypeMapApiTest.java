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
//#types K=int V=boolean|byte|short|char|int|long|float|double
//#types K=long V=boolean|byte|short|char|int|long|float|double
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Iteration, removeIf, bulk operations and conversions of all map implementations. */
class KTypeVTypeMapApiTest {

    enum Impl {
        LINEAR, COMPACT, LINKED, TREE;

        KTypeVTypeMap create(int expectedSize, float loadFactor) {
            return switch (this) {
                case LINEAR -> new KTypeVTypeHashMap(expectedSize, loadFactor);
                case COMPACT -> new KTypeVTypeCompactHashMap(expectedSize, loadFactor);
                case LINKED -> new KTypeVTypeLinkedHashMap(expectedSize, loadFactor);
                case TREE -> new KTypeVTypeTreeMap(expectedSize);
            };
        }

        KTypeVTypeMap copyOf(KTypeVTypeMap other) {
            return switch (this) {
                case LINEAR -> new KTypeVTypeHashMap(other);
                case COMPACT -> new KTypeVTypeCompactHashMap(other);
                case LINKED -> new KTypeVTypeLinkedHashMap(other);
                case TREE -> new KTypeVTypeTreeMap(other);
            };
        }
    }

    /** Maps an {@code int} to a key: a bijection with {@code k(0) == 0} that keeps the sign. */
    private static KType k(int i) {
//#if K == long
        return ((long) i << 32) | (i & 0xFFFF_FFFFL);
//#else
        return i;
//#end
    }

    /** Maps an {@code int} to a value. */
    private static VType v(int i) {
//#if V == boolean
        return (i & 1) != 0;
//#elif V == int|long|float|double
        return i;
//#else
        return (VType) i;
//#end
    }

    /** A map and its reference with random content, including key 0 if {@code withZero}. */
    private static KTypeVTypeMap fill(Impl impl, float loadFactor, int n, int range, boolean withZero,
                                      Map<KBoxed, VBoxed> ref, long seed) {
        KTypeVTypeMap m = impl.create(0, loadFactor);
        SplittableRandom rnd = new SplittableRandom(seed);
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            if (i == 0 && !withZero) {
                continue;
            }
            m.put(k(i), v(i * 7));
            ref.put(k(i), v(i * 7));
        }
        if (withZero) {
            m.put(KZero, v(5));
            ref.put(KZero, v(5));
        }
        return m;
    }

    private static Map<KBoxed, VBoxed> iterate(KTypeVTypeMap m) {
        Map<KBoxed, VBoxed> seen = new HashMap<>();
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            assertTrue(m.indexExists(i));
            KType key = m.indexKey(i);
            assertFalse(seen.containsKey(key), "visited twice: " + key);
            seen.put(key, m.indexGet(i));
            assertEquals(i, m.indexOf(key), "handle of " + key);
        }
        return seen;
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void iterationVisitsEveryEntryOnce(Impl impl) {
        for (boolean withZero : new boolean[] {false, true}) {
            Map<KBoxed, VBoxed> ref = new HashMap<>();
            KTypeVTypeMap m = fill(impl, 0.85f, 3000, 20_000, withZero, ref, 1);
            assertEquals(ref, iterate(m));
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void iterationOfEmptyAndZeroOnlyMaps(Impl impl) {
        KTypeVTypeMap m = impl.create(0, 0.75f);
        assertEquals(-1, m.firstIndex());
        m.put(KZero, v(3));
        int i = m.firstIndex();
        assertTrue(i >= 0);
        assertEquals(KZero, m.indexKey(i));
        assertEquals(v(3), m.indexGet(i));
        assertEquals(-1, m.nextIndex(i));
        m.remove(KZero);
        m.put(k(9), v(4));
        i = m.firstIndex();
        assertEquals(k(9), m.indexKey(i));
        assertEquals(-1, m.nextIndex(i));
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void iterationStopsEarlyAndReplacesValues(Impl impl) {
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        KTypeVTypeMap m = fill(impl, 0.75f, 500, 5000, true, ref, 2);
        int visited = 0;
        for (int i = m.firstIndex(); i >= 0 && visited < 10; i = m.nextIndex(i)) {
            visited++;
        }
        assertEquals(10, visited);
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            m.indexReplace(i, v(1));
        }
        for (VBoxed value : iterate(m).values()) {
            assertEquals(v(1), (VType) value);
        }
        assertEquals(ref.size(), m.size());
    }

    /**
     * removeIf against {@link Map#entrySet()}{@code .removeIf}. High load factors and small
     * tables produce probe chains that wrap around the end of the table; the predicate must
     * see every entry exactly once.
     */
    @ParameterizedTest
    @CsvSource({
            "LINEAR, 0.5, 100", "LINEAR, 0.75, 3000", "LINEAR, 0.97, 3000", "LINEAR, 0.97, 12",
            "COMPACT, 0.5, 100", "COMPACT, 0.85, 3000", "COMPACT, 0.97, 3000", "COMPACT, 0.97, 12",
    })
    void removeIfMatchesHashMap(Impl impl, float loadFactor, int n) {
        for (long seed = 1; seed <= 20; seed++) {
            Map<KBoxed, VBoxed> ref = new HashMap<>();
            KTypeVTypeMap m = fill(impl, loadFactor, n, 4 * n + 10, seed % 2 == 0, ref, seed);
            int modulus = 2 + (int) (seed % 3);
            Map<KBoxed, Integer> calls = new HashMap<>();
            boolean removed = m.removeIf((key, value) -> {
                calls.merge(key, 1, Integer::sum);
                return Math.floorMod(KBoxed.hashCode(key) * 31 + VBoxed.hashCode(value), modulus) == 0;
            });
            assertEquals(ref.keySet(), calls.keySet(), "every entry is tested");
            for (int c : calls.values()) {
                assertEquals(1, c, "every entry is tested once");
            }
            boolean expected = ref.entrySet().removeIf(
                    e -> Math.floorMod(KBoxed.hashCode(e.getKey()) * 31 + VBoxed.hashCode(e.getValue()), modulus) == 0);
            assertEquals(expected, removed);
            assertEquals(ref, iterate(m));
            assertEquals(ref.size(), m.size());
            for (KBoxed key : ref.keySet()) {
                assertTrue(m.containsKey(key));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void removeIfAllAndNone(Impl impl) {
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        KTypeVTypeMap m = fill(impl, 0.75f, 1000, 10_000, true, ref, 3);
        assertFalse(m.removeIf((key, value) -> false));
        assertEquals(ref.size(), m.size());
        assertTrue(m.removeIf((key, value) -> true));
        assertTrue(m.isEmpty());
        assertEquals(-1, m.firstIndex());
        m.put(k(1), v(1));
        assertEquals(v(1), m.get(k(1)));
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void arraysAndConversions(Impl impl) {
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        KTypeVTypeMap m = fill(impl, 0.75f, 700, 5000, true, ref, 4);
        KType[] keys = m.keysToArray();
        VType[] values = m.valuesToArray();
        assertEquals(ref.size(), keys.length);
        assertEquals(ref.size(), values.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(ref.get(keys[i]), (VBoxed) values[i]);
        }
        assertEquals(ref, m.toMap());

        KTypeVTypeMap target = impl.create(0, 0.75f);
        target.put(k(123_456), v(1));
        target.putAll(ref);
        assertEquals(ref.size() + (ref.containsKey(k(123_456)) ? 0 : 1), target.size());
        target.remove(k(123_456));
        Map<KBoxed, VBoxed> expected = new HashMap<>(ref);
        expected.remove(k(123_456));
        assertEquals(expected, target.toMap());

        Map<KBoxed, VBoxed> withNull = new HashMap<>();
        withNull.put(k(1), null);
        assertThrows(NullPointerException.class, () -> impl.create(0, 0.75f).putAll(withNull));
    }

    @Test
    void putAllAndCopyAcrossImplementations() {
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        KTypeVTypeMap source = fill(Impl.LINEAR, 0.75f, 2000, 50_000, true, ref, 5);
        for (Impl impl : Impl.values()) {
            KTypeVTypeMap copy = impl.copyOf(source);
            assertEquals(source, copy);
            assertEquals(ref, copy.toMap());
            KTypeVTypeMap target = impl.create(0, 0.75f);
            target.putAll(copy);
            target.putAll(copy); // idempotent
            assertEquals(source, target);
            copy.put(k(99_999), v(2));
            assertFalse(source.containsKey(k(99_999)), "copies are independent");
        }
        KTypeVTypeMap compact = Impl.COMPACT.copyOf(source);
        KTypeVTypeMap linear = Impl.LINEAR.copyOf(compact);
        assertEquals(compact, linear);
        assertArrayEquals(sorted(compact.keysToArray()), sorted(linear.keysToArray()));
    }

    private static KType[] sorted(KType[] a) {
        java.util.Arrays.sort(a);
        return a;
    }
}
