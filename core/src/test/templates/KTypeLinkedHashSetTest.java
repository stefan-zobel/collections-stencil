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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Differential tests against {@link LinkedHashSet}: content and iteration order. */
class KTypeLinkedHashSetTest {

    /** Maps an {@code int} to an element: a bijection with {@code k(0) == 0} that keeps the sign. */
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

    /** Compares content and order in both directions, through every way of iterating. */
    private static void assertSameOrder(LinkedHashSet<KBoxed> expected, KTypeLinkedHashSet s, String context) {
        assertEquals(expected.size(), s.size(), context);
        assertEquals(expected.isEmpty(), s.isEmpty(), context);
        List<KBoxed> want = new ArrayList<>(expected);
        List<KBoxed> forward = new ArrayList<>();
        for (int i = s.firstIndex(); i >= 0; i = s.nextIndex(i)) {
            forward.add(s.indexKey(i));
        }
        assertEquals(want, forward, context);
        List<KBoxed> each = new ArrayList<>();
        s.forEach(x -> each.add(x));
        assertEquals(want, each, context);
        List<KBoxed> backward = new ArrayList<>();
        for (int i = s.lastIndex(); i >= 0; i = s.prevIndex(i)) {
            backward.add(s.indexKey(i));
        }
        assertEquals(want.reversed(), backward, context);
        List<KBoxed> array = new ArrayList<>();
        for (KType x : s.toArray()) {
            array.add(x);
        }
        assertEquals(want, array, context);
        if (want.isEmpty()) {
            assertThrows(NoSuchElementException.class, s::first, context);
            assertThrows(NoSuchElementException.class, s::last, context);
            assertThrows(NoSuchElementException.class, s::pollFirst, context);
            assertThrows(NoSuchElementException.class, s::pollLast, context);
        } else {
            assertEquals(want.getFirst(), bk(s.first()), context);
            assertEquals(want.getLast(), bk(s.last()), context);
        }
        assertEquals(expected.hashCode(), s.hashCode(), context);
        assertEquals(expected.toString(), s.toString(), context);
        assertEquals(expected, s.toSet(), context);
        assertEquals(new ArrayList<>(expected), new ArrayList<>(s.toSet()), context);
    }

    @ParameterizedTest
    @ValueSource(floats = {0.75f, 0.9f, 0.3f})
    void randomOperationsMatchLinkedHashSet(float loadFactor) {
        for (int keyRange : new int[] {8, 60, 700}) {
            SplittableRandom rnd = new SplittableRandom(keyRange);
            KTypeLinkedHashSet s = new KTypeLinkedHashSet(0, loadFactor);
            LinkedHashSet<KBoxed> ref = new LinkedHashSet<>();
            for (int step = 0; step < 30_000; step++) {
                // Elements around zero, so that 0 takes part. The mix changes in phases, so that the set grows and shrinks.
                KType key = k(rnd.nextInt(keyRange) - 3);
                boolean shrink = (step / 3000) % 2 == 1;
                int op = rnd.nextInt(shrink ? 9 : 16);
                String context = "range " + keyRange + " step " + step + " op " + op + " key " + key;
                switch (op) {
                    case 0, 1, 2 -> assertEquals(ref.remove(key), s.remove(key), context);
                    case 3 -> {
                        if (!ref.isEmpty()) {
                            assertEquals(ref.removeLast(), bk(s.pollLast()), context);
                        }
                    }
                    case 4 -> {
                        if (!ref.isEmpty()) {
                            assertEquals(ref.removeFirst(), bk(s.pollFirst()), context);
                        }
                    }
                    case 5 -> {
                        boolean present = ref.contains(key);
                        if (present) {
                            ref.addLast(key);
                        }
                        assertEquals(present, s.moveToLast(key), context);
                    }
                    case 6 -> {
                        boolean absent = !ref.contains(key);
                        ref.addLast(key);
                        assertEquals(absent, s.addLast(key), context);
                    }
                    case 7 -> assertEquals(ref.contains(key), s.contains(key), context);
                    case 8 -> {
                        if (rnd.nextInt(40) == 0) {
                            int mod = 2 + rnd.nextInt(4);
                            assertEquals(ref.removeIf(x -> Math.floorMod(x, mod) == 0),
                                    s.removeIf(x -> Math.floorMod(x, mod) == 0), context);
                        }
                    }
                    default -> assertEquals(ref.add(key), s.add(key), context);
                }
                if (step % 7 == 0 || ref.size() < 3) {
                    assertSameOrder(ref, s, context);
                }
            }
            assertSameOrder(ref, s, "range " + keyRange + " end");
            assertEquals(new KTypeHashSet(s), s);
            assertEquals(s, new KTypeHashSet(s));
            s.clear();
            ref.clear();
            assertSameOrder(ref, s, "cleared");
            assertTrue(s.add(k(5)));
            assertTrue(s.add(KZero));
            assertFalse(s.add(k(5)));
            assertArrayEquals(new KType[] {k(5), KZero}, s.toArray());
        }
    }

    @Test
    void growthWithHolesKeepsTheOrder() {
        KTypeLinkedHashSet s = new KTypeLinkedHashSet();
        LinkedHashSet<KBoxed> ref = new LinkedHashSet<>();
        for (int i = 1; i <= 50_000; i++) {
            ref.add(k(i));
            s.add(k(i));
            if (i % 9 == 8) { // a hole a few elements before the end: few enough holes that the set grows
                assertEquals(ref.remove(k(i - 4)), s.remove(k(i - 4)));
            }
        }
        assertSameOrder(ref, s, "grown");
        s.ensureCapacity(200_000);
        assertSameOrder(ref, s, "after ensureCapacity");
        assertTrue(s.capacity() >= 200_000);
    }

    @Test
    void copiesKeepTheOrder() {
        KTypeLinkedHashSet s = new KTypeLinkedHashSet();
        for (int i : new int[] {9, 0, -4, 7, 3}) {
            s.add(k(i));
        }
        s.remove(k(7));
        KType[] order = {k(9), KZero, k(-4), k(3)};
        assertArrayEquals(order, s.toArray());
        assertArrayEquals(order, new KTypeLinkedHashSet(s).toArray());
        assertEquals("[" + k(9) + ", 0, " + k(-4) + ", " + k(3) + "]", s.toString());
        assertEquals(s, new KTypeLinkedHashSet(s));
        KTypeSet plain = new KTypeHashSet(s);
        assertEquals(plain, s);
        assertEquals(s, plain);
        assertEquals(plain.hashCode(), s.hashCode());
        assertFalse(s.equals(new KTypeHashSet()));
        // A copy of an unordered set has that set's iteration order.
        assertArrayEquals(plain.toArray(), new KTypeLinkedHashSet(plain).toArray());
    }

    @Test
    void compactsInsteadOfGrowing() {
        KTypeLinkedHashSet s = new KTypeLinkedHashSet(100);
        int capacity = s.capacity();
        // A queue of 50 elements: removals at the front, insertions at the end.
        for (int i = 0; i < 50; i++) {
            s.add(k(i));
        }
        for (int i = 50; i < 20_000; i++) {
            assertEquals(bk(k(i - 50)), bk(s.pollFirst()));
            s.add(k(i));
        }
        assertEquals(capacity, s.capacity());
        assertEquals(50, s.size());
        assertEquals(bk(k(19_950)), bk(s.first()));
        // Moving elements to the end leaves holes, which compaction closes.
        for (int i = 0; i < 20_000; i++) {
            assertTrue(s.moveToLast(k(19_950 + (i * 7) % 50)));
            assertFalse(s.addLast(k(19_950 + (i * 11) % 50)));
        }
        assertFalse(s.moveToLast(k(-1)));
        assertEquals(capacity, s.capacity());
        assertEquals(50, s.size());
        // Removals in the middle; removals at the end give the positions back.
        KType first = s.first();
        KType last = s.last();
        s.removeIf(x -> x != first && x != last);
        assertEquals(2, s.size());
        assertEquals(bk(last), bk(s.pollLast()));
        assertEquals(bk(first), bk(s.pollLast()));
        assertTrue(s.isEmpty());
        assertEquals(0, s.holeCount());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeLinkedHashSet(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeLinkedHashSet(1, 0f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeLinkedHashSet(1, 1f));
    }
}
