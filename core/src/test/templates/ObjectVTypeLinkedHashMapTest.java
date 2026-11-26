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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Differential tests against {@link LinkedHashMap}: content and iteration order, with object keys. */
class ObjectVTypeLinkedHashMapTest {

    /** A key whose hash codes collide in groups of four: long probe chains. */
    private record Collider(int id) {
        @Override
        public int hashCode() {
            return id >> 2;
        }
    }

    /** A key that counts the calls of {@link #hashCode}. */
    private static final class Counted {
        static int hashCodeCalls;
        final int id;

        Counted(int id) {
            this.id = id;
        }

        @Override
        public int hashCode() {
            hashCodeCalls++;
            return id;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Counted c && c.id == id;
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

    private static VBoxed bv(VType value) {
        return value;
    }

    /** The boxed result of a method that returns {@code VZero} for an absent key. */
    private static VBoxed orZero(VBoxed value) {
        return value != null ? value : bv(VZero);
    }

    /** Compares content and order in both directions, through every way of iterating. */
    private static void assertSameOrder(LinkedHashMap<Object, VBoxed> expected, ObjectVTypeLinkedHashMap<Object> m,
                                        String context) {
        assertEquals(expected.size(), m.size(), context);
        assertEquals(expected.isEmpty(), m.isEmpty(), context);
        List<Map.Entry<Object, VBoxed>> want = new ArrayList<>();
        expected.forEach((key, value) -> want.add(new Entry(key, value)));
        List<Map.Entry<Object, VBoxed>> forward = new ArrayList<>();
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            forward.add(new Entry(m.indexKey(i), bv(m.indexGet(i))));
            assertEquals(i, m.indexOf(m.indexKey(i)), context);
        }
        assertEquals(want, forward, context);
        List<Map.Entry<Object, VBoxed>> each = new ArrayList<>();
        m.forEach((key, value) -> each.add(new Entry(key, bv(value))));
        assertEquals(want, each, context);
        List<Map.Entry<Object, VBoxed>> backward = new ArrayList<>();
        for (int i = m.lastIndex(); i >= 0; i = m.prevIndex(i)) {
            backward.add(new Entry(m.indexKey(i), bv(m.indexGet(i))));
        }
        assertEquals(want.reversed(), backward, context);
        if (want.isEmpty()) {
            assertThrows(NoSuchElementException.class, m::firstKey, context);
            assertThrows(NoSuchElementException.class, m::lastKey, context);
        } else {
            assertEquals(want.getFirst().getKey(), m.firstKey(), context);
            assertEquals(want.getLast().getKey(), m.lastKey(), context);
        }
        Object[] keys = m.keysToArray(Object[]::new);
        VType[] values = m.valuesToArray();
        for (int i = 0; i < keys.length; i++) {
            assertEquals(want.get(i).getKey(), keys[i], context);
            assertEquals(want.get(i).getValue(), bv(values[i]), context);
        }
        assertEquals(expected.hashCode(), m.hashCode(), context);
        assertEquals(expected.toString(), m.toString(), context);
        assertEquals(expected, m.toMap(), context);
        assertEquals(new ArrayList<>(expected.keySet()), new ArrayList<>(m.toMap().keySet()), context);
    }

    /** An entry that allows a {@code null} key (unlike {@link Map#entry}). */
    private record Entry(Object key, VBoxed value) implements Map.Entry<Object, VBoxed> {
        @Override
        public Object getKey() {
            return key;
        }

        @Override
        public VBoxed getValue() {
            return value;
        }

        @Override
        public VBoxed setValue(VBoxed v) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Random operations against {@link LinkedHashMap}, with the null key, keys that are equal but
     * not identical, and colliding hash codes. The mix changes in phases, so that the map grows
     * and shrinks.
     */
    @ParameterizedTest
    @CsvSource({
            "8, 0.75, false",
            "60, 0.75, false",
            "700, 0.75, false",
            "700, 0.9, false",
            "60, 0.3, false",
            "700, 0.75, true",
            "60, 0.9, true",
    })
    void randomOperationsMatchLinkedHashMap(int keyRange, float loadFactor, boolean colliding)
            throws ReflectiveOperationException {
        SplittableRandom rnd = new SplittableRandom(keyRange * 31L + (colliding ? 1 : 0));
        ObjectVTypeLinkedHashMap<Object> m = new ObjectVTypeLinkedHashMap<>(0, loadFactor);
        LinkedHashMap<Object, VBoxed> ref = new LinkedHashMap<>();
        for (int step = 0; step < 30_000; step++) {
            int id = rnd.nextInt(keyRange);
            Object key = id == 0 ? null : colliding ? new Collider(id) : s(id);
            VType value = v(step);
            boolean shrink = (step / 3000) % 2 == 1;
            int op = rnd.nextInt(shrink ? 13 : 22);
            String context = "range " + keyRange + " step " + step + " op " + op + " key " + key;
            switch (op) {
                case 0, 1, 2 -> assertEquals(orZero(ref.remove(key)), bv(m.remove(key)), context);
                case 3 -> {
                    Map.Entry<Object, VBoxed> e = ref.pollLastEntry();
                    if (e == null) {
                        assertThrows(NoSuchElementException.class, () -> m.pollLast(), context);
                    } else {
                        assertEquals(e.getValue(), bv(m.pollLast()), context);
                    }
                }
                case 4 -> {
                    Map.Entry<Object, VBoxed> e = ref.pollFirstEntry();
                    if (rnd.nextBoolean()) {
                        if (e == null) {
                            assertThrows(NoSuchElementException.class, () -> m.pollFirst(), context);
                        } else {
                            assertEquals(e.getValue(), bv(m.pollFirst()), context);
                        }
                    } else {
                        List<Map.Entry<Object, VBoxed>> polled = new ArrayList<>();
                        assertEquals(e != null, m.pollFirst((x, y) -> polled.add(new Entry(x, bv(y)))), context);
                        assertEquals(e == null ? List.of() : List.of(new Entry(e.getKey(), e.getValue())), polled,
                                context);
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
                        assertEquals(ref.keySet().removeIf(x -> Math.floorMod(Objects.hashCode(x), mod) == 0),
                                m.removeIf((x, y) -> Math.floorMod(Objects.hashCode(x), mod) == 0), context);
                    }
                }
                case 11 -> assertEquals(ref.putIfAbsent(key, value) == null, m.putIfAbsent(key, value), context);
                case 12 -> assertEquals(ref.merge(key, v(3), VBoxed::sum), bv(m.addTo(key, v(3))), context);
                default -> assertEquals(orZero(ref.put(key, value)), bv(m.put(key, value)), context);
            }
            if (step % 7 == 0 || ref.size() < 3) {
                assertSameOrder(ref, m, context);
            }
        }
        assertSameOrder(ref, m, "range " + keyRange + " end");
        assertEquals(ref.containsKey(null) ? ref.size() - 1 : ref.size(), referencedKeys(m), "removed keys are released");
        assertEquals(new ObjectVTypeHashMap<>(m), m);
        assertEquals(m, new ObjectVTypeHashMap<>(m));
        m.clear();
        ref.clear();
        assertSameOrder(ref, m, "cleared");
        assertEquals(0, referencedKeys(m));
        m.put(s(5), v(1));
        m.put(null, v(2));
        assertEquals(s(5), m.firstKey());
        assertNull(m.lastKey());
    }

    /** Number of key references in the entry arrays, holes and unused positions included. */
    private static long referencedKeys(ObjectVTypeLinkedHashMap<?> m) throws ReflectiveOperationException {
        Field kf = ObjectVTypeLinkedHashMap.class.getDeclaredField("keys");
        kf.setAccessible(true);
        long referenced = 0;
        for (Object key : (Object[]) kf.get(m)) {
            if (key != null) {
                referenced++;
            }
        }
        return referenced;
    }

    @Test
    void nullKeyHasAPosition() {
        ObjectVTypeLinkedHashMap<String> m = new ObjectVTypeLinkedHashMap<>();
        assertFalse(m.containsKey(null));
        assertEquals(bv(VZero), bv(m.put("a", v(1))));
        assertEquals(bv(VZero), bv(m.put(null, v(2))));
        assertEquals(bv(VZero), bv(m.put("b", v(3))));
        assertArrayEquals(new Object[] {"a", null, "b"}, m.keysToArray(String[]::new));
        assertTrue(m.moveToLast(null));
        assertNull(m.lastKey());
        assertEquals(bv(v(1)), bv(m.putLast("a", v(4))));
        assertArrayEquals(new Object[] {"b", null, "a"}, m.keysToArray(String[]::new));
        assertEquals(bv(v(3)), bv(m.pollFirst()));
        assertNull(m.firstKey());
        assertEquals(bv(v(7)), bv(m.addTo(null, v(5))));
        assertEquals(bv(v(7)), bv(m.pollFirst()));
        assertFalse(m.containsKey(null));
        assertEquals("{a=" + v(4) + "}", m.toString());
    }

    @Test
    void addToKeepsThePositionAndAppendsAbsentKeys() {
        ObjectVTypeLinkedHashMap<String> m = new ObjectVTypeLinkedHashMap<>();
        assertEquals(bv(v(4)), bv(m.addTo(s(1), v(4))));
        assertEquals(bv(v(5)), bv(m.addTo(s(2), v(5))));
        assertEquals(bv(v(7)), bv(m.addTo(s(1), v(3))));
        assertArrayEquals(new Object[] {s(1), s(2)}, m.keysToArray(String[]::new));
        assertArrayEquals(new VType[] {v(7), v(5)}, m.valuesToArray());
    }

    /** Growth, compaction, and removals by position use the stored hashes, not {@code hashCode}. */
    @Test
    void rebuildsAndRemovalsByPositionDoNotCallHashCode() {
        ObjectVTypeLinkedHashMap<Counted> m = new ObjectVTypeLinkedHashMap<>(0);
        Counted.hashCodeCalls = 0;
        for (int i = 0; i < 5000; i++) {
            m.put(new Counted(i), v(i));
        }
        assertEquals(5000, Counted.hashCodeCalls, "one call per insertion, none for growth");
        Counted.hashCodeCalls = 0;
        // A queue: removals at the front, insertions at the end, with compactions.
        for (int i = 5000; i < 30_000; i++) {
            assertEquals(bv(v(i - 5000)), bv(m.pollFirst()));
            m.put(new Counted(i), v(i));
        }
        assertEquals(25_000, Counted.hashCodeCalls, "one call per insertion, none for pollFirst or compaction");
        Counted.hashCodeCalls = 0;
        for (int i = 0; i < 20_000; i++) {
            assertTrue(m.moveToLast(new Counted(25_000 + (i * 7) % 5000)));
        }
        assertEquals(20_000, Counted.hashCodeCalls, "one call per lookup, none for compaction");
        Counted.hashCodeCalls = 0;
        m.ensureCapacity(100_000);
        assertTrue(m.removeIf((key, value) -> key.id % 3 == 0));
        m.indexRemove(m.firstIndex());
        m.pollLast();
        assertEquals(0, Counted.hashCodeCalls, "growth and removals by position");
        for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i)) {
            assertEquals(i, m.indexOf(new Counted(m.indexKey(i).id)));
        }
    }

    @Test
    void growthWithHolesKeepsTheOrder() {
        ObjectVTypeLinkedHashMap<Object> m = new ObjectVTypeLinkedHashMap<>();
        LinkedHashMap<Object, VBoxed> ref = new LinkedHashMap<>();
        for (int i = 1; i <= 50_000; i++) {
            ref.put(s(i), v(i));
            m.put(s(i), v(i));
            if (i % 9 == 8) { // a hole a few entries before the end: few enough holes that the map grows
                assertEquals(ref.remove(s(i - 4)), bv(m.remove(s(i - 4))));
            }
        }
        assertSameOrder(ref, m, "grown");
        m.ensureCapacity(200_000);
        assertSameOrder(ref, m, "after ensureCapacity");
        assertTrue(m.capacity() >= 200_000);
    }

    @Test
    void copiesKeepTheOrder() {
        ObjectVTypeLinkedHashMap<String> m = new ObjectVTypeLinkedHashMap<>();
        for (String key : new String[] {"9", null, "-4", "7", "3"}) {
            m.put(key, v(key == null ? 0 : Integer.parseInt(key)));
        }
        m.remove("7");
        Object[] order = {"9", null, "-4", "3"};
        assertArrayEquals(order, m.keysToArray(String[]::new));
        assertArrayEquals(order, new ObjectVTypeLinkedHashMap<>(m).keysToArray(String[]::new));
        assertEquals(m, new ObjectVTypeLinkedHashMap<>(m));
        ObjectVTypeMap<String> plain = new ObjectVTypeHashMap<>(m);
        assertEquals(plain, m);
        assertEquals(m, plain);
        assertEquals(plain.hashCode(), m.hashCode());
        assertFalse(m.equals(new ObjectVTypeHashMap<String>()));
        plain.put("3", v(4));
        assertNotEquals(plain, m);
        assertNotEquals(m, plain);
        // A copy of an unordered map has the order in which that map's forEach visits its entries.
        List<Object> visited = new ArrayList<>();
        plain.forEach((key, value) -> visited.add(key));
        List<Object> copied = new ArrayList<>();
        new ObjectVTypeLinkedHashMap<>(plain).forEach((key, value) -> copied.add(key));
        assertEquals(visited, copied);
    }

    @Test
    void compactsInsteadOfGrowing() throws ReflectiveOperationException {
        ObjectVTypeLinkedHashMap<Object> m = new ObjectVTypeLinkedHashMap<>(100);
        int capacity = m.capacity();
        // A queue of 50 entries: removals at the front, insertions at the end.
        for (int i = 0; i < 50; i++) {
            m.put(s(i), v(i));
        }
        for (int i = 50; i < 20_000; i++) {
            assertEquals(bv(v(i - 50)), bv(m.pollFirst()));
            m.put(s(i), v(i));
        }
        assertEquals(capacity, m.capacity());
        assertEquals(50, m.size());
        assertEquals(50, referencedKeys(m));
        assertEquals(s(19_950), m.firstKey());
        // Moving entries to the end leaves holes, which compaction closes.
        for (int i = 0; i < 20_000; i++) {
            assertTrue(m.moveToLast(s(19_950 + (i * 7) % 50)));
        }
        assertFalse(m.moveToLast(s(-1)));
        assertEquals(capacity, m.capacity());
        assertEquals(50, m.size());
        assertEquals(50, referencedKeys(m));
        // Removals in the middle; removals at the end give the positions back.
        Object first = m.firstKey();
        Object last = m.lastKey();
        m.removeIf((key, value) -> !key.equals(first) && !key.equals(last));
        assertEquals(2, m.size());
        assertEquals(2, referencedKeys(m));
        assertTrue(m.pollLast((key, value) -> assertEquals(last, key)));
        assertTrue(m.pollLast((key, value) -> assertEquals(first, key)));
        assertFalse(m.pollLast((key, value) -> { }));
        assertFalse(m.pollFirst((key, value) -> { }));
        assertTrue(m.isEmpty());
        assertEquals(0, m.holeCount());
        assertEquals(0, referencedKeys(m));
    }

    /** Keys whose {@code hashCode()}s differ only in a few bits, or only in their high bits. */
    @Test
    void weakHashCodes() {
        ObjectVTypeLinkedHashMap<Integer> m = new ObjectVTypeLinkedHashMap<>();
        for (int i = 0; i < 10_000; i++) {
            m.put(i, v(i));
        }
        for (int i = 1; i < 2048; i++) {
            m.put(i << 20, v(-i)); // distinct and positive for i < 2048
        }
        assertEquals(10_000 + 2047, m.size());
        for (int i = 1; i < 10_000; i++) {
            assertEquals(bv(v(i)), bv(m.get(i)));
        }
        for (int i = 1; i < 2048; i++) {
            assertEquals(bv(v(-i)), bv(m.get(i << 20)));
        }
        assertEquals(Integer.valueOf(0), m.firstKey());
        assertEquals(Integer.valueOf(2047 << 20), m.lastKey());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeLinkedHashMap<String>(-1));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeLinkedHashMap<String>(1, 0f));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeLinkedHashMap<String>(1, 1f));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeLinkedHashMap<String>(1, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ObjectVTypeLinkedHashMap<String>(Integer.MAX_VALUE));
    }
}
