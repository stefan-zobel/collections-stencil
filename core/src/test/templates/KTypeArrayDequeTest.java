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
//#types K=int|long|double
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Differential tests against an {@link ArrayList} that mirrors the deque, so that access by
 * index can be checked too: after every operation, both hold the same elements.
 */
class KTypeArrayDequeTest {

    /** A value: mostly from {@code range} values around zero (with duplicates), sometimes special. */
    private static KType value(SplittableRandom rnd, int range) {
        int r = rnd.nextInt(40);
//#if K == int
        if (r == 0) {
            return Integer.MIN_VALUE;
        }
        return rnd.nextInt(range) - range / 2;
//#elif K == long
        if (r == 0) {
            return Long.MAX_VALUE;
        }
        return (rnd.nextInt(range) - range / 2) * 0x1_0000_0001L;
//#else
        if (r == 0) {
            return Double.NaN;
        }
        if (r == 1) {
            return -0.0;
        }
        return (rnd.nextInt(range) - range / 2) * 0.25;
//#end
    }

    private static void assertSameContent(List<KBoxed> ref, KTypeArrayDeque d, String context) {
        assertEquals(ref.size(), d.size(), context);
        assertEquals(ref.isEmpty(), d.isEmpty(), context);
        assertEquals(ref, d.toList(), context);
        int cap = d.capacity();
        assertTrue(cap >= d.size() && (cap & (cap - 1)) == 0, context + ": capacity " + cap);
        assertTrue(d.head() >= 0 && (cap == 0 ? d.head() == 0 : d.head() < cap), context);
    }

    private static KTypeArrayDeque deque(int... values) {
        KTypeArrayDeque d = new KTypeArrayDeque();
        for (int v : values) {
            d.addLast(v);
        }
        return d;
    }

    /**
     * Random operations; {@code target} is the size around which the deque moves, so that the
     * first element runs around the array many times.
     */
    @ParameterizedTest
    @CsvSource({"8, 5, 1", "50, 40, 2", "1000, 300, 3", "100000, 3000, 4"})
    void randomOperationsMatchAList(int range, int target, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        KTypeArrayDeque d = new KTypeArrayDeque();
        List<KBoxed> ref = new ArrayList<>();
        for (int step = 0; step < 30_000; step++) {
            String context = "step " + step;
            int n = ref.size();
            boolean grow = n < target;
            int op = rnd.nextInt(100);
            if (op < 20) {
                KType v = value(rnd, range);
                if (grow || rnd.nextBoolean()) {
                    d.addLast(v);
                    ref.add(v);
                } else if (n > 0) {
                    assertEquals(ref.removeFirst(), d.removeFirst(), context);
                }
            } else if (op < 40) {
                KType v = value(rnd, range);
                if (grow || rnd.nextBoolean()) {
                    d.addFirst(v);
                    ref.addFirst(v);
                } else if (n > 0) {
                    assertEquals(ref.removeLast(), d.removeLast(), context);
                }
            } else if (op < 52 && n > 0) {
                assertEquals(ref.removeFirst(), d.removeFirst(), context);
            } else if (op < 64 && n > 0) {
                assertEquals(ref.removeLast(), d.removeLast(), context);
            } else if (op < 68) {
                KType[] a = new KType[rnd.nextInt(Math.max(1, target / 4))];
                for (int i = 0; i < a.length; i++) {
                    a[i] = value(rnd, range);
                }
                int from = a.length == 0 ? 0 : rnd.nextInt(a.length);
                d.addAllLast(a, from, a.length);
                for (int i = from; i < a.length; i++) {
                    ref.add(a[i]);
                }
            } else if (op < 71) {
                int k = rnd.nextInt(n + 1);
                d.discardFirst(k);
                ref.subList(0, k).clear();
            } else if (op < 74) {
                int k = rnd.nextInt(n + 1);
                d.discardLast(k);
                ref.subList(n - k, n).clear();
            } else if (op < 80 && n > 0) {
                int i = rnd.nextInt(n);
                KType v = value(rnd, range);
                assertEquals(ref.set(i, v), d.set(i, v), context);
            } else if (op < 82) {
                KType t = value(rnd, range);
                assertEquals(ref.removeIf(x -> x > t), d.removeIf(x -> x > t), context);
            } else if (op < 83) {
                d.trimToSize();
                assertTrue(d.capacity() < 2 * d.size() || d.size() == 0 && d.capacity() == 0, context);
            } else if (op < 84) {
                d.ensureCapacity(n + rnd.nextInt(100));
            } else if (op < 85 && rnd.nextInt(20) == 0) {
                d.clear();
                ref.clear();
            } else {
                KType v = value(rnd, range);
                assertEquals(ref.indexOf(v), d.indexOf(v), context);
                assertEquals(ref.contains(v), d.contains(v), context);
                if (n > 0) {
                    int i = rnd.nextInt(n);
                    assertEquals(ref.get(i), d.get(i), context);
                    assertEquals(ref.getFirst(), d.getFirst(), context);
                    assertEquals(ref.getLast(), d.getLast(), context);
                }
            }
            assertSameContent(ref, d, context);
            if (step % 97 == 0) {
                assertEquals(ref.hashCode(), d.hashCode(), context);
                assertEquals(ref.toString(), d.toString(), context);
                KTypeArrayDeque copy = new KTypeArrayDeque(d);
                assertEquals(0, copy.head(), context);
                assertEquals(d, copy, context);
                assertEquals(copy, d, context);
                assertEquals(d.hashCode(), copy.hashCode(), context);
                assertArrayEquals(d.toArray(), d.stream().toArray(), context);
                List<KBoxed> each = new ArrayList<>();
                d.forEach(each::add);
                assertEquals(ref, each, context);
                List<KBoxed> reversed = new ArrayList<>();
                d.forEachReversed(reversed::add);
                Collections.reverse(reversed);
                assertEquals(ref, reversed, context);
            }
        }
    }

    /** A deque of capacity 16 with the elements {@code base, base + 1, ...} from array index {@code shift} on. */
    private static KTypeArrayDeque wrapped(int n, int shift, int base) {
        KTypeArrayDeque d = new KTypeArrayDeque(16);
        for (int i = 0; i < shift; i++) {
            d.addLast(-1);
        }
        d.discardFirst(shift);
        for (int i = 0; i < n; i++) {
            d.addLast(base + i);
        }
        assertEquals(16, d.capacity());
        assertEquals(shift, d.head());
        return d;
    }

    @Test
    void addAllLastOfADequeAcrossTheEndsOfBothArrays() {
        for (int shiftA = 0; shiftA < 16; shiftA += 3) {
            for (int shiftB = 0; shiftB < 16; shiftB += 3) {
                for (int nB : new int[] {0, 1, 6, 12}) {
                    String context = "shiftA " + shiftA + " shiftB " + shiftB + " nB " + nB;
                    KTypeArrayDeque a = wrapped(4, shiftA, 0);
                    KTypeArrayDeque b = wrapped(nB, shiftB, 100);
                    List<KBoxed> ref = new ArrayList<>(a.toList());
                    List<KBoxed> refB = b.toList();
                    ref.addAll(refB);
                    a.addAllLast(b);
                    assertSameContent(ref, a, context);
                    assertEquals(refB, b.toList(), context + ": the source is unchanged");
                }
            }
        }
        for (int shift = 0; shift < 16; shift++) {
            for (int n : new int[] {0, 5, 8, 11}) {
                String context = "self, shift " + shift + " n " + n;
                KTypeArrayDeque d = wrapped(n, shift, 0);
                List<KBoxed> ref = new ArrayList<>(d.toList());
                ref.addAll(d.toList());
                d.addAllLast(d);
                assertSameContent(ref, d, context);
            }
        }
    }

    @Test
    void ofCopiesTheValuesInOrder() {
        assertTrue(KTypeArrayDeque.of().isEmpty());
        KType[] values = new KType[17];
        for (int i = 0; i < values.length; i++) {
            values[i] = i * 3;
        }
        KTypeArrayDeque d = KTypeArrayDeque.of(values);
        assertArrayEquals(values, d.toArray());
        assertEquals(32, d.capacity());
        values[0] = 99;
        assertEquals(0, d.getFirst(), "the deque does not share the array");
        d.addFirst(7);
        assertEquals(7, d.removeFirst());
    }

    @Test
    void growsWhileTheContentWrapsAround() {
        KTypeArrayDeque d = new KTypeArrayDeque();
        List<KBoxed> ref = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            d.addLast(i);
            ref.add(KBoxed.valueOf(i));
        }
        for (int i = 0; i < 10; i++) {
            assertEquals(ref.removeFirst(), d.removeFirst());
        }
        for (int i = 0; i < 40; i++) {
            KType v = 100 + i;
            if (i % 2 == 0) {
                d.addFirst(v);
                ref.addFirst(v);
            } else {
                d.addLast(v);
                ref.add(v);
            }
        }
        assertEquals(64, d.capacity());
        assertEquals(ref, d.toList());
        d.discardFirst(20);
        ref.subList(0, 20).clear();
        d.trimToSize();
        assertEquals(32, d.capacity());
        assertEquals(ref, d.toList());
        assertEquals(0, d.head());
    }

    @Test
    void capacityIsAPowerOfTwo() {
        assertEquals(0, new KTypeArrayDeque().capacity());
        assertEquals(0, new KTypeArrayDeque(0).capacity());
        assertEquals(1, new KTypeArrayDeque(1).capacity());
        assertEquals(128, new KTypeArrayDeque(100).capacity());
        assertEquals(128, new KTypeArrayDeque(128).capacity());
        KTypeArrayDeque d = new KTypeArrayDeque();
        d.addFirst(1);
        assertEquals(16, d.capacity());
        assertEquals(15, d.head());
        d.ensureCapacity(17);
        assertEquals(32, d.capacity());
        assertEquals(0, d.head());
        KTypeArrayDeque one = new KTypeArrayDeque(1);
        one.addLast(1);
        one.addLast(2);
        assertEquals(16, one.capacity());
        KTypeArrayDeque bulk = new KTypeArrayDeque();
        bulk.addAllLast(new KType[100], 0, 100);
        assertEquals(128, bulk.capacity());
    }

    @Test
    void emptyDeque() {
        KTypeArrayDeque d = new KTypeArrayDeque();
        assertTrue(d.isEmpty());
        assertThrows(NoSuchElementException.class, d::getFirst);
        assertThrows(NoSuchElementException.class, d::getLast);
        assertThrows(NoSuchElementException.class, d::removeFirst);
        assertThrows(NoSuchElementException.class, d::removeLast);
        assertThrows(IndexOutOfBoundsException.class, () -> d.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> d.set(0, 1));
        d.discardFirst(0);
        d.discardLast(0);
        d.addAllLast(new KType[0], 0, 0);
        d.trimToSize();
        assertFalse(d.removeIf(x -> true));
        assertEquals(-1, d.indexOf(0));
        assertEquals("[]", d.toString());
        assertEquals(1, d.hashCode());
        assertEquals(new KTypeArrayDeque(8), d);
        assertEquals(0, d.toArray().length);
        assertEquals(0, d.stream().count());
        assertEquals(0, new KTypeArrayDeque(d).capacity());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeArrayDeque(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeArrayDeque((1 << 30) + 1));
        KTypeArrayDeque d = deque(1, 2, 3);
        assertThrows(IndexOutOfBoundsException.class, () -> d.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> d.get(3));
        assertThrows(IllegalArgumentException.class, () -> d.discardFirst(4));
        assertThrows(IllegalArgumentException.class, () -> d.discardLast(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> d.addAllLast(new KType[2], 1, 3));
        assertThrows(IllegalStateException.class, () -> d.ensureCapacity((1 << 30) + 1));
        assertEquals(deque(1, 2, 3), d);
    }

    @Test
    void discardRemovesFromBothEnds() {
        KTypeArrayDeque d = deque(1, 2, 3, 4, 5, 6);
        d.discardFirst(2);
        assertEquals(deque(3, 4, 5, 6), d);
        d.discardLast(3);
        assertEquals(deque(3), d);
        d.discardFirst(1);
        assertTrue(d.isEmpty());
        d.addLast(7);
        assertEquals(7, d.getFirst());
    }

    @Test
    void removeIfKeepsTheRestWhenTheFilterThrows() {
        KTypeArrayDeque d = new KTypeArrayDeque(16);
        for (int i = 6; i <= 10; i++) {
            d.addLast(i);
        }
        for (int i = 5; i >= 1; i--) {
            d.addFirst(i); // the content now wraps around the end of the array
        }
        assertThrows(IllegalStateException.class, () -> d.removeIf(x -> {
            if (x == 7) {
                throw new IllegalStateException();
            }
            return x % 2 == 0;
        }));
        assertEquals(deque(1, 3, 5, 7, 8, 9, 10), d);
    }

    @Test
    void equalityIgnoresThePositionInTheArray() {
        KTypeArrayDeque a = deque(1, 2, 3);
        KTypeArrayDeque b = new KTypeArrayDeque();
        b.addFirst(3);
        b.addFirst(2);
        b.addFirst(1);
        assertNotEquals(a.head(), b.head());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.set(2, 4);
        assertNotEquals(a, b);
        assertNotEquals(a, a.toList());
        assertNotEquals(a, KTypeArrayList.of(1, 2, 3));
    }
//#if K.floating

    @Test
    void valuesCompareLikeDoubleEquals() {
        KTypeArrayDeque d = new KTypeArrayDeque();
        d.addLast(0.0);
        d.addLast(Double.NaN);
        d.addFirst(-0.0);
        assertEquals(2, d.indexOf(Double.NaN));
        assertEquals(0, d.indexOf(-0.0));
        assertEquals(1, d.indexOf(0.0));
        assertEquals(d.toList().hashCode(), d.hashCode());
        KTypeArrayDeque other = new KTypeArrayDeque();
        other.addLast(0.0);
        other.addLast(0.0);
        other.addLast(Double.NaN);
        assertNotEquals(other, d);
        other.set(0, -0.0);
        assertEquals(other, d);
    }
//#end
}
