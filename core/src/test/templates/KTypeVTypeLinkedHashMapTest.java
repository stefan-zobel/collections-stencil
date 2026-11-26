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
class KTypeVTypeLinkedHashMapTest {

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

    private static KBoxed bk(KType key) {
        return key;
    }

    private static VBoxed bv(VType value) {
        return value;
    }

    /** The boxed result of a method that returns {@code VZero} for an absent key. */
    private static VBoxed orZero(VBoxed value) {
        return value != null ? value : bv(VZero);
    }

    /** Compares content and order in both directions, through every way of iterating. */
    private static void assertSameOrder(LinkedHashMap<KBoxed, VBoxed> expected, KTypeVTypeLinkedHashMap m, String context) {
        assertEquals(expected.size(), m.size(), context);
        assertEquals(expected.isEmpty(), m.isEmpty(), context);
        List<Map.Entry<KBoxed, VBoxed>> want = new ArrayList<>(expected.entrySet());
        List<Map.Entry<KBoxed, VBoxed>> forward = new ArrayList<>();
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            forward.add(Map.entry(bk(m.indexKey(i)), bv(m.indexGet(i))));
            assertEquals(i, m.indexOf(m.indexKey(i)), context);
        }
        assertEquals(want, forward, context);
        List<Map.Entry<KBoxed, VBoxed>> each = new ArrayList<>();
        m.forEach((key, value) -> each.add(Map.entry(bk(key), bv(value))));
        assertEquals(want, each, context);
        List<Map.Entry<KBoxed, VBoxed>> backward = new ArrayList<>();
        for (int i = m.lastIndex(); i >= 0; i = m.prevIndex(i)) {
            backward.add(Map.entry(bk(m.indexKey(i)), bv(m.indexGet(i))));
        }
        assertEquals(want.reversed(), backward, context);
        if (want.isEmpty()) {
            assertThrows(NoSuchElementException.class, m::firstKey, context);
            assertThrows(NoSuchElementException.class, m::lastKey, context);
        } else {
            assertEquals(want.getFirst().getKey(), bk(m.firstKey()), context);
            assertEquals(want.getLast().getKey(), bk(m.lastKey()), context);
        }
        KType[] keys = m.keysToArray();
        VType[] values = m.valuesToArray();
        for (int i = 0; i < keys.length; i++) {
            assertEquals(want.get(i).getKey(), bk(keys[i]), context);
            assertEquals(want.get(i).getValue(), bv(values[i]), context);
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
            KTypeVTypeLinkedHashMap m = new KTypeVTypeLinkedHashMap(0, loadFactor);
            LinkedHashMap<KBoxed, VBoxed> ref = new LinkedHashMap<>();
            for (int step = 0; step < 30_000; step++) {
                // Keys around zero, so that key 0 takes part. The mix changes in phases, so that the map grows and shrinks.
                KType key = k(rnd.nextInt(keyRange) - 3);
                VType value = v(step);
                boolean shrink = (step / 3000) % 2 == 1;
                int op = rnd.nextInt(shrink ? 13 : 22);
                String context = "range " + keyRange + " step " + step + " op " + op + " key " + key;
                switch (op) {
                    case 0, 1, 2 -> assertEquals(orZero(ref.remove(key)), bv(m.remove(key)), context);
                    case 3 -> {
                        Map.Entry<KBoxed, VBoxed> e = ref.pollLastEntry();
                        if (e == null) {
                            assertThrows(NoSuchElementException.class, () -> m.pollLast(), context);
                        } else {
                            assertEquals(e.getValue(), bv(m.pollLast()), context);
                        }
                    }
                    case 4 -> {
                        Map.Entry<KBoxed, VBoxed> e = ref.pollFirstEntry();
                        if (rnd.nextBoolean()) {
                            if (e == null) {
                                assertThrows(NoSuchElementException.class, () -> m.pollFirst(), context);
                            } else {
                                assertEquals(e.getValue(), bv(m.pollFirst()), context);
                            }
                        } else {
                            List<Map.Entry<KBoxed, VBoxed>> polled = new ArrayList<>();
                            assertEquals(e != null, m.pollFirst((x, y) -> polled.add(Map.entry(bk(x), bv(y)))), context);
                            assertEquals(e == null ? List.of() : List.of(e), polled, context);
                        }
                    }
                    case 5 -> {
                        boolean present = ref.containsKey(key);
                        if (present) {
                            ref.putLast(key, ref.get(key));
                        }
                        assertEquals(present, m.moveToLast(key), context);
                    }
                    case 6 -> assertEquals(orZero(ref.putLast(key, value)), bv(m.putLast(key, value)), context);
                    case 7 -> {
                        int i = m.indexOf(key);
                        assertEquals(ref.containsKey(key), m.indexExists(i), context);
                        if (m.indexExists(i)) {
                            assertEquals(ref.remove(key), bv(m.indexRemove(i)), context);
                        } else {
                            m.indexInsert(i, key, value);
                            ref.put(key, value);
                        }
                    }
                    case 8 -> {
                        assertEquals(orZero(ref.get(key)), bv(m.get(key)), context);
                        assertEquals(ref.containsKey(key), m.containsKey(key), context);
                        assertEquals(ref.getOrDefault(key, v(77)), bv(m.getOrDefault(key, v(77))), context);
                    }
                    case 9 -> {
                        int i = m.indexOf(key);
                        if (m.indexExists(i)) {
                            assertEquals(ref.put(key, value), bv(m.indexReplace(i, value)), context);
                        }
                    }
                    case 10 -> {
                        if (rnd.nextInt(40) == 0) {
                            int mod = 2 + rnd.nextInt(4);
                            assertEquals(ref.keySet().removeIf(x -> Math.floorMod(x, mod) == 0),
                                    m.removeIf((x, y) -> Math.floorMod(x, mod) == 0), context);
                        }
                    }
                    case 11 -> assertEquals(ref.putIfAbsent(key, value) == null, m.putIfAbsent(key, value), context);
//#if V.numeric
                    case 12 -> {
                        VType sum = m.addTo(key, v(3));
                        ref.put(key, sum);
                        assertEquals(bv(sum), bv(m.get(key)), context);
                    }
//#end
                    default -> assertEquals(orZero(ref.put(key, value)), bv(m.put(key, value)), context);
                }
                if (step % 7 == 0 || ref.size() < 3) {
                    assertSameOrder(ref, m, context);
                }
            }
            assertSameOrder(ref, m, "range " + keyRange + " end");
            assertEquals(new KTypeVTypeHashMap(m), m);
            assertEquals(m, new KTypeVTypeHashMap(m));
            m.clear();
            ref.clear();
            assertSameOrder(ref, m, "cleared");
            m.put(k(5), v(1));
            m.put(KZero, v(2));
            assertEquals(bk(k(5)), bk(m.firstKey()));
            assertEquals(bk(KZero), bk(m.lastKey()));
        }
    }
//#if V.numeric

    @Test
    void addToKeepsThePositionAndAppendsAbsentKeys() {
        KTypeVTypeLinkedHashMap m = new KTypeVTypeLinkedHashMap();
        assertEquals(bv(v(4)), bv(m.addTo(k(1), v(4))));
        assertEquals(bv(v(5)), bv(m.addTo(k(2), v(5))));
        assertEquals(bv(v(7)), bv(m.addTo(k(1), v(3))));
        assertArrayEquals(new KType[] {k(1), k(2)}, m.keysToArray());
        assertArrayEquals(new VType[] {v(7), v(5)}, m.valuesToArray());
    }
//#end

    @Test
    void growthWithHolesKeepsTheOrder() {
        KTypeVTypeLinkedHashMap m = new KTypeVTypeLinkedHashMap();
        LinkedHashMap<KBoxed, VBoxed> ref = new LinkedHashMap<>();
        for (int i = 1; i <= 50_000; i++) {
            ref.put(k(i), v(i));
            m.put(k(i), v(i));
            if (i % 9 == 8) { // a hole a few entries before the end: few enough holes that the map grows
                assertEquals(ref.remove(k(i - 4)), bv(m.remove(k(i - 4))));
            }
        }
        assertSameOrder(ref, m, "grown");
        m.ensureCapacity(200_000);
        assertSameOrder(ref, m, "after ensureCapacity");
        assertTrue(m.capacity() >= 200_000);
    }

    @Test
    void copiesKeepTheOrder() {
        KTypeVTypeLinkedHashMap m = new KTypeVTypeLinkedHashMap();
        for (int i : new int[] {9, 0, -4, 7, 3}) {
            m.put(k(i), v(i));
        }
        m.remove(k(7));
        KType[] order = {k(9), KZero, k(-4), k(3)};
        assertArrayEquals(order, m.keysToArray());
        assertArrayEquals(order, new KTypeVTypeLinkedHashMap(m).keysToArray());
        assertEquals(m, new KTypeVTypeLinkedHashMap(m));
        KTypeVTypeMap plain = new KTypeVTypeHashMap(m);
        assertEquals(plain, m);
        assertEquals(m, plain);
        assertEquals(plain.hashCode(), m.hashCode());
        assertFalse(m.equals(new KTypeVTypeHashMap()));
        // A copy of an unordered map has the order in which that map's forEach visits its entries.
        List<KBoxed> visited = new ArrayList<>();
        plain.forEach((key, value) -> visited.add(key));
        List<KBoxed> copied = new ArrayList<>();
        new KTypeVTypeLinkedHashMap(plain).forEach((key, value) -> copied.add(key));
        assertEquals(visited, copied);
    }

    @Test
    void compactsInsteadOfGrowing() {
        KTypeVTypeLinkedHashMap m = new KTypeVTypeLinkedHashMap(100);
        int capacity = m.capacity();
        // A queue of 50 entries: removals at the front, insertions at the end.
        for (int i = 0; i < 50; i++) {
            m.put(k(i), v(i));
        }
        for (int i = 50; i < 20_000; i++) {
            assertEquals(bv(v(i - 50)), bv(m.pollFirst()));
            m.put(k(i), v(i));
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
        assertTrue(m.isEmpty());
        assertEquals(0, m.holeCount());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeLinkedHashMap(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeLinkedHashMap(1, 0f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeLinkedHashMap(1, 1f));
    }
}
