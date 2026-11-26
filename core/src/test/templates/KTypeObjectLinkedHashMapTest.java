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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Differential tests against {@link LinkedHashMap}: content and iteration order. */
class KTypeObjectLinkedHashMapTest {

    /** Maps an {@code int} to a key: a bijection with {@code k(0) == 0} that keeps the sign. */
    private static KType k(int i) {
//#if K == long
        return ((long) i << 32) | (i & 0xFFFF_FFFFL);
//#else
        return i;
//#end
    }

    private static KBoxed bk(KType key) {
        return key;
    }

    /** An entry that may have a {@code null} value. */
    private record Pair(KBoxed key, String value) {
    }

    /** Compares content and order in both directions, through every way of iterating. */
    private static void assertSameOrder(LinkedHashMap<KBoxed, String> expected, KTypeObjectLinkedHashMap<String> m,
                                        String context) {
        assertEquals(expected.size(), m.size(), context);
        assertEquals(expected.isEmpty(), m.isEmpty(), context);
        List<Pair> want = new ArrayList<>();
        expected.forEach((key, value) -> want.add(new Pair(key, value)));
        List<Pair> forward = new ArrayList<>();
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            forward.add(new Pair(m.indexKey(i), m.indexGet(i)));
            assertEquals(i, m.indexOf(m.indexKey(i)), context);
        }
        assertEquals(want, forward, context);
        List<Pair> each = new ArrayList<>();
        m.forEach((key, value) -> each.add(new Pair(key, value)));
        assertEquals(want, each, context);
        List<Pair> backward = new ArrayList<>();
        for (int i = m.lastIndex(); i >= 0; i = m.prevIndex(i)) {
            backward.add(new Pair(m.indexKey(i), m.indexGet(i)));
        }
        assertEquals(want.reversed(), backward, context);
        if (want.isEmpty()) {
            assertThrows(NoSuchElementException.class, m::firstKey, context);
            assertThrows(NoSuchElementException.class, m::lastKey, context);
        } else {
            assertEquals(want.getFirst().key(), bk(m.firstKey()), context);
            assertEquals(want.getLast().key(), bk(m.lastKey()), context);
        }
        KType[] keys = m.keysToArray();
        String[] values = m.valuesToArray(String[]::new);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(want.get(i), new Pair(keys[i], values[i]), context);
        }
        assertEquals(expected.hashCode(), m.hashCode(), context);
        assertEquals(expected.toString(), m.toString(), context);
        assertEquals(expected, m.toMap(), context);
        assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(m.toMap().keySet()), context);
    }

    @ParameterizedTest
    @ValueSource(floats = {0.75f, 0.9f, 0.3f})
    void randomOperationsMatchLinkedHashMap(float loadFactor) {
        for (int keyRange : new int[] {8, 60, 700}) {
            SplittableRandom rnd = new SplittableRandom(keyRange);
            KTypeObjectLinkedHashMap<String> m = new KTypeObjectLinkedHashMap<>(0, loadFactor);
            LinkedHashMap<KBoxed, String> ref = new LinkedHashMap<>();
            for (int step = 0; step < 30_000; step++) {
                // Keys around zero, so that key 0 takes part. The mix changes in phases, so that the map grows and shrinks.
                KType key = k(rnd.nextInt(keyRange) - 3);
                String value = step % 50 == 0 ? null : "v" + step;
                boolean shrink = (step / 3000) % 2 == 1;
                int op = rnd.nextInt(shrink ? 13 : 22);
                String context = "range " + keyRange + " step " + step + " op " + op + " key " + key;
                switch (op) {
                    case 0, 1, 2 -> assertEquals(ref.remove(key), m.remove(key), context);
                    case 3 -> {
                        Map.Entry<KBoxed, String> e = ref.pollLastEntry();
                        if (e == null) {
                            assertThrows(NoSuchElementException.class, () -> m.pollLast(), context);
                        } else {
                            assertEquals(e.getValue(), m.pollLast(), context);
                        }
                    }
                    case 4 -> {
                        Map.Entry<KBoxed, String> e = ref.pollFirstEntry();
                        if (rnd.nextBoolean()) {
                            if (e == null) {
                                assertThrows(NoSuchElementException.class, () -> m.pollFirst(), context);
                            } else {
                                assertEquals(e.getValue(), m.pollFirst(), context);
                            }
                        } else {
                            List<Pair> polled = new ArrayList<>();
                            assertEquals(e != null, m.pollFirst((x, y) -> polled.add(new Pair(x, y))), context);
                            assertEquals(e == null ? List.of() : List.of(new Pair(e.getKey(), e.getValue())), polled, context);
                        }
                    }
                    case 5 -> {
                        boolean present = ref.containsKey(key);
                        if (present) {
                            ref.putLast(key, ref.get(key));
                        }
                        assertEquals(present, m.moveToLast(key), context);
                    }
                    case 6 -> assertEquals(ref.putLast(key, value), m.putLast(key, value), context);
                    case 7 -> {
                        int i = m.indexOf(key);
                        assertEquals(ref.containsKey(key), m.indexExists(i), context);
                        if (m.indexExists(i)) {
                            assertEquals(ref.remove(key), m.indexRemove(i), context);
                        } else {
                            m.indexInsert(i, key, value);
                            ref.put(key, value);
                        }
                    }
                    case 8 -> {
                        assertEquals(ref.get(key), m.get(key), context);
                        assertEquals(ref.containsKey(key), m.containsKey(key), context);
                        assertEquals(ref.getOrDefault(key, "none"), m.getOrDefault(key, "none"), context);
                    }
                    case 9 -> {
                        int i = m.indexOf(key);
                        if (m.indexExists(i)) {
                            assertEquals(ref.put(key, value), m.indexReplace(i, value), context);
                        }
                    }
                    case 10 -> {
                        if (rnd.nextInt(40) == 0) {
                            int mod = 2 + rnd.nextInt(4);
                            assertEquals(ref.keySet().removeIf(x -> Math.floorMod(x, mod) == 0),
                                    m.removeIf((x, y) -> Math.floorMod(x, mod) == 0), context);
                        }
                    }
                    case 11 -> {
                        // java.util.Map.putIfAbsent replaces a null value; this map treats the key as present.
                        boolean absent = !ref.containsKey(key);
                        if (absent) {
                            ref.put(key, value);
                        }
                        assertEquals(absent, m.putIfAbsent(key, value), context);
                    }
                    case 12 -> assertEquals(ref.computeIfAbsent(key, x -> value), m.computeIfAbsent(key, x -> value), context);
                    default -> assertEquals(ref.put(key, value), m.put(key, value), context);
                }
                if (step % 7 == 0 || ref.size() < 3) {
                    assertSameOrder(ref, m, context);
                }
            }
            assertSameOrder(ref, m, "range " + keyRange + " end");
            assertEquals(new KTypeObjectHashMap<>(m), m);
            assertEquals(m, new KTypeObjectHashMap<>(m));
            m.clear();
            ref.clear();
            assertSameOrder(ref, m, "cleared");
            assertNull(m.put(k(5), "a"));
            assertNull(m.put(KZero, "b"));
            assertEquals(bk(k(5)), bk(m.firstKey()));
            assertEquals(bk(KZero), bk(m.lastKey()));
        }
    }

    @Test
    void growthWithHolesKeepsTheOrder() {
        KTypeObjectLinkedHashMap<String> m = new KTypeObjectLinkedHashMap<>();
        LinkedHashMap<KBoxed, String> ref = new LinkedHashMap<>();
        for (int i = 1; i <= 50_000; i++) {
            ref.put(k(i), "v" + i);
            m.put(k(i), "v" + i);
            if (i % 9 == 8) { // a hole a few entries before the end: few enough holes that the map grows
                assertEquals(ref.remove(k(i - 4)), m.remove(k(i - 4)));
            }
        }
        assertSameOrder(ref, m, "grown");
        m.ensureCapacity(200_000);
        assertSameOrder(ref, m, "after ensureCapacity");
        assertTrue(m.capacity() >= 200_000);
    }

    @Test
    void copiesKeepTheOrder() {
        KTypeObjectLinkedHashMap<String> m = new KTypeObjectLinkedHashMap<>();
        for (int i : new int[] {9, 0, -4, 7, 3}) {
            m.put(k(i), "v" + i);
        }
        m.remove(k(7));
        m.put(k(9), null);
        assertTrue(m.containsKey(k(9)));
        assertEquals("{" + k(9) + "=null, 0=v0, " + k(-4) + "=v-4, " + k(3) + "=v3}", m.toString());
        KType[] order = {k(9), KZero, k(-4), k(3)};
        assertArrayEquals(order, m.keysToArray());
        assertArrayEquals(order, new KTypeObjectLinkedHashMap<>(m).keysToArray());
        assertEquals(m, new KTypeObjectLinkedHashMap<>(m));
        KTypeObjectMap<String> plain = new KTypeObjectHashMap<>(m);
        assertEquals(plain, m);
        assertEquals(m, plain);
        assertEquals(plain.hashCode(), m.hashCode());
        assertFalse(m.equals(new KTypeObjectHashMap<String>()));
        // A copy of an unordered map has the order in which that map's forEach visits its entries.
        List<KBoxed> visited = new ArrayList<>();
        plain.forEach((key, value) -> visited.add(key));
        List<KBoxed> copied = new ArrayList<>();
        new KTypeObjectLinkedHashMap<>(plain).forEach((key, value) -> copied.add(key));
        assertEquals(visited, copied);
    }

    @Test
    void compactsInsteadOfGrowing() {
        KTypeObjectLinkedHashMap<String> m = new KTypeObjectLinkedHashMap<>(100);
        int capacity = m.capacity();
        // A queue of 50 entries: removals at the front, insertions at the end.
        for (int i = 0; i < 50; i++) {
            m.put(k(i), "v" + i);
        }
        for (int i = 50; i < 20_000; i++) {
            assertEquals("v" + (i - 50), m.pollFirst());
            m.put(k(i), "v" + i);
        }
        assertEquals(capacity, m.capacity());
        assertEquals(50, m.size());
        assertEquals(bk(k(19_950)), bk(m.firstKey()));
        // Moving entries to the end leaves holes, which compaction closes.
        for (int i = 0; i < 20_000; i++) {
            assertTrue(m.moveToLast(k(19_950 + (i * 7) % 50)));
        }
        assertFalse(m.moveToLast(k(-1)));
        assertEquals(capacity, m.capacity());
        assertEquals(50, m.size());
        // Removals in the middle; removals at the end give the positions back.
        KType first = m.firstKey();
        KType last = m.lastKey();
        m.removeIf((key, value) -> key != first && key != last);
        assertEquals(2, m.size());
        assertTrue(m.pollLast((key, value) -> assertEquals(bk(last), bk(key))));
        assertTrue(m.pollLast((key, value) -> assertEquals(bk(first), bk(key))));
        assertFalse(m.pollLast((key, value) -> { }));
        assertFalse(m.pollFirst((key, value) -> { }));
        assertThrows(NoSuchElementException.class, () -> m.pollFirst());
        assertThrows(NoSuchElementException.class, () -> m.pollLast());
        assertTrue(m.isEmpty());
        assertEquals(0, m.holeCount());
    }

    @Test
    void removedValuesAreNotRetained() throws ReflectiveOperationException {
        KTypeObjectLinkedHashMap<String> m = new KTypeObjectLinkedHashMap<>();
        for (int i = 0; i < 100; i++) {
            m.put(k(i), "v" + i);
        }
        m.remove(k(40));
        m.pollFirst();
        m.pollLast();
        m.moveToLast(k(50));
        m.removeIf((key, value) -> key == k(60));
        java.lang.reflect.Field f = KTypeObjectLinkedHashMap.class.getDeclaredField("values");
        f.setAccessible(true);
        long references = java.util.Arrays.stream((Object[]) f.get(m)).filter(x -> x != null).count();
        assertEquals(m.size(), references);
        m.clear();
        assertEquals(0, java.util.Arrays.stream((Object[]) f.get(m)).filter(x -> x != null).count());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectLinkedHashMap<String>(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectLinkedHashMap<String>(1, 0f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectLinkedHashMap<String>(1, 1f));
        assertThrows(NullPointerException.class, () -> new KTypeObjectLinkedHashMap<String>().computeIfAbsent(k(1), null));
    }
}
