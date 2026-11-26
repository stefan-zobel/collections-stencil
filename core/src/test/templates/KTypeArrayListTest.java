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
import static org.junit.jupiter.api.Assertions.assertSame;
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

/** Differential tests against {@link ArrayList}: after every operation, both hold the same elements. */
class KTypeArrayListTest {

    /** A value: mostly from {@code range} values around zero (with duplicates), sometimes an extreme. */
    private static KType value(SplittableRandom rnd, int range) {
        int r = rnd.nextInt(40);
//#if K == int
        if (r == 0) {
            return Integer.MIN_VALUE;
        }
        if (r == 1) {
            return Integer.MAX_VALUE;
        }
        return rnd.nextInt(range) - range / 2;
//#elif K == long
        if (r == 0) {
            return Long.MIN_VALUE;
        }
        if (r == 1) {
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
        if (r == 2) {
            return Double.NEGATIVE_INFINITY;
        }
        return (rnd.nextInt(range) - range / 2) * 0.25;
//#end
    }

    private static KType[] values(SplittableRandom rnd, int n, int range) {
        KType[] a = new KType[n];
        for (int i = 0; i < n; i++) {
            a[i] = value(rnd, range);
        }
        return a;
    }

    private static List<KBoxed> boxed(KType[] a, int from, int to) {
        List<KBoxed> list = new ArrayList<>();
        for (int i = from; i < to; i++) {
            list.add(a[i]);
        }
        return list;
    }

    private static void assertSameContent(List<KBoxed> ref, KTypeArrayList l, String context) {
        assertEquals(ref.size(), l.size(), context);
        assertEquals(ref.isEmpty(), l.isEmpty(), context);
        assertEquals(ref, l.toList(), context);
        assertTrue(l.capacity() >= l.size(), context);
    }

    @ParameterizedTest
    @CsvSource({"8, 1", "50, 2", "1000, 3", "100000, 4"})
    void randomOperationsMatchArrayList(int range, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        KTypeArrayList l = new KTypeArrayList();
        List<KBoxed> ref = new ArrayList<>();
        for (int step = 0; step < 20_000; step++) {
            String context = "step " + step;
            int n = ref.size();
            int op = rnd.nextInt(100);
            if (op < 30) {
                KType v = value(rnd, range);
                l.add(v);
                ref.add(v);
            } else if (op < 40) {
                int i = rnd.nextInt(n + 1);
                KType v = value(rnd, range);
                l.add(i, v);
                ref.add(i, v);
            } else if (op < 45) {
                KType[] a = values(rnd, rnd.nextInt(30), range);
                int from = a.length == 0 ? 0 : rnd.nextInt(a.length);
                int to = from + rnd.nextInt(a.length - from + 1);
                l.addAll(a, from, to);
                ref.addAll(boxed(a, from, to));
            } else if (op < 48) {
                KType[] a = values(rnd, rnd.nextInt(30), range);
                int i = rnd.nextInt(n + 1);
                l.addAll(i, a, 0, a.length);
                ref.addAll(i, boxed(a, 0, a.length));
            } else if (op < 49 && n < 2000) {
                l.addAll(l);
                ref.addAll(new ArrayList<>(ref));
            } else if (op < 55 && n > 0) {
                int i = rnd.nextInt(n);
                KType v = value(rnd, range);
                assertEquals(ref.set(i, v), l.set(i, v), context);
            } else if (op < 65 && n > 0) {
                int i = rnd.nextInt(n);
                assertEquals(ref.remove(i), l.removeAt(i), context);
            } else if (op < 72 && n > 0) {
                assertEquals(ref.removeLast(), l.removeLast(), context);
            } else if (op < 75) {
                int from = rnd.nextInt(n + 1);
                int to = from + rnd.nextInt(Math.min(n - from, 20) + 1);
                l.removeRange(from, to);
                ref.subList(from, to).clear();
            } else if (op < 82) {
                KType v = value(rnd, range);
                assertEquals(ref.remove((KBoxed) v), l.removeValue(v), context);
            } else if (op < 84) {
                KType t = value(rnd, range);
                assertEquals(ref.removeIf(x -> x > t), l.removeIf(x -> x > t), context);
            } else if (op < 85) {
                int k = rnd.nextInt(n + 1);
                l.truncate(k);
                ref.subList(k, n).clear();
            } else if (op < 86) {
                l.sort();
                Collections.sort(ref);
            } else if (op < 87 && n > 0) {
                int from = rnd.nextInt(n);
                int to = from + rnd.nextInt(n - from + 1);
                l.sort(from, to);
                Collections.sort(ref.subList(from, to));
            } else if (op < 88) {
                l.reverse();
                Collections.reverse(ref);
            } else if (op < 89) {
                l.replaceAll(x -> x + 1);
                ref.replaceAll(x -> x + 1);
            } else if (op < 90) {
                l.trimToSize();
                assertEquals(l.size(), l.capacity(), context);
            } else if (op < 91 && rnd.nextInt(20) == 0) {
                l.clear();
                ref.clear();
            } else {
                KType v = value(rnd, range);
                assertEquals(ref.indexOf(v), l.indexOf(v), context);
                assertEquals(ref.lastIndexOf(v), l.lastIndexOf(v), context);
                assertEquals(ref.contains(v), l.contains(v), context);
                if (n > 0) {
                    int i = rnd.nextInt(n);
                    assertEquals(ref.get(i), l.get(i), context);
                    assertEquals(ref.getFirst(), l.getFirst(), context);
                    assertEquals(ref.getLast(), l.getLast(), context);
                }
            }
            assertSameContent(ref, l, context);
            if (step % 97 == 0) {
                assertEquals(ref.hashCode(), l.hashCode(), context);
                assertEquals(ref.toString(), l.toString(), context);
                KTypeArrayList copy = new KTypeArrayList(l);
                assertEquals(l, copy, context);
                assertEquals(l.hashCode(), copy.hashCode(), context);
                assertArrayEquals(l.toArray(), l.stream().toArray(), context);
                List<KBoxed> each = new ArrayList<>();
                l.forEach(each::add);
                assertEquals(ref, each, context);
            }
        }
    }

    @Test
    void binarySearchOnASortedList() {
        SplittableRandom rnd = new SplittableRandom(7);
        KTypeArrayList l = KTypeArrayList.of(values(rnd, 500, 300));
        l.sort();
        List<KBoxed> ref = l.toList();
        for (int i = 0; i < 2000; i++) {
            KType v = value(rnd, 300);
            int r = l.binarySearch(v);
            if (ref.contains(v)) {
                assertEquals((KBoxed) v, l.get(r));
            } else {
                assertTrue(r < 0);
                int at = -r - 1;
                assertTrue(at == 0 || KBoxed.compare(l.get(at - 1), v) < 0);
                assertTrue(at == l.size() || KBoxed.compare(l.get(at), v) > 0);
            }
        }
    }

    @Test
    void emptyList() {
        KTypeArrayList l = new KTypeArrayList();
        assertEquals(0, l.capacity());
        assertTrue(l.isEmpty());
        assertThrows(NoSuchElementException.class, l::getFirst);
        assertThrows(NoSuchElementException.class, l::getLast);
        assertThrows(NoSuchElementException.class, l::removeLast);
        assertThrows(IndexOutOfBoundsException.class, () -> l.get(0));
        assertThrows(IndexOutOfBoundsException.class, () -> l.removeAt(0));
        assertEquals("[]", l.toString());
        assertEquals(1, l.hashCode());
        assertEquals(new KTypeArrayList(5), l);
        assertEquals(-1, l.indexOf(0));
        assertFalse(l.removeValue(0));
        assertFalse(l.removeIf(x -> true));
        l.sort();
        l.reverse();
        l.trimToSize();
        assertEquals(0, l.toArray().length);
        assertEquals(0, l.stream().count());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeArrayList(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeArrayList(Integer.MAX_VALUE));
        KTypeArrayList l = KTypeArrayList.of(1, 2, 3);
        assertThrows(IndexOutOfBoundsException.class, () -> l.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> l.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> l.set(3, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> l.add(4, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> l.add(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> l.removeRange(2, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> l.removeRange(2, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> l.truncate(4));
        assertThrows(IndexOutOfBoundsException.class, () -> l.sort(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> l.toArray(1, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> l.addAll(new KType[2], 1, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> l.addAll(4, new KType[2], 0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> KTypeArrayList.wrap(new KType[2], 3));
        assertEquals(3, l.size());
    }

    @Test
    void growthAndCapacity() {
        KTypeArrayList l = new KTypeArrayList();
        l.add(1);
        assertEquals(16, l.capacity());
        for (int i = 1; i < 17; i++) {
            l.add(i);
        }
        assertEquals(24, l.capacity());
        l.trimToSize();
        assertEquals(17, l.capacity());
        l.ensureCapacity(100);
        assertEquals(100, l.capacity());
        l.ensureCapacity(10);
        assertEquals(100, l.capacity());
        assertEquals(17, l.size());
        KTypeArrayList sized = new KTypeArrayList(3);
        assertEquals(3, sized.capacity());
        KTypeArrayList big = new KTypeArrayList();
        big.addAll(new KType[1000], 0, 1000);
        assertEquals(1000, big.capacity());
    }

    @Test
    void wrapAndElementsShareTheArray() {
        KType[] a = {5, 6, 7, 8};
        KTypeArrayList l = KTypeArrayList.wrap(a, 3);
        assertEquals(3, l.size());
        assertSame(a, l.elements());
        l.set(0, 9);
        assertEquals(9, a[0]);
        a[1] = 4;
        assertEquals(4, l.get(1));
        l.add(1);
        assertSame(a, l.elements());
        assertEquals(1, a[3]);
        l.add(2);
        assertFalse(a == l.elements());
        assertEquals(KTypeArrayList.of(9, 4, 7, 1, 2), l);

        KType[] values = {1, 2};
        KTypeArrayList of = KTypeArrayList.of(values);
        values[0] = 3;
        assertEquals(1, of.get(0));
    }

    @Test
    void insertsItsOwnElements() {
        KTypeArrayList l = KTypeArrayList.of(1, 2, 3, 4);
        l.addAll(1, l.elements(), 2, 4);
        assertEquals(KTypeArrayList.of(1, 3, 4, 2, 3, 4), l);
        l.addAll(l);
        assertEquals(12, l.size());
        assertEquals(4, l.getLast());
    }

    @Test
    void removeIfKeepsTheRestWhenTheFilterThrows() {
        KTypeArrayList l = new KTypeArrayList();
        for (int i = 1; i <= 10; i++) {
            l.add(i);
        }
        assertThrows(IllegalStateException.class, () -> l.removeIf(x -> {
            if (x == 7) {
                throw new IllegalStateException();
            }
            return x % 2 == 0;
        }));
        assertEquals(KTypeArrayList.of(1, 3, 5, 7, 8, 9, 10), l);
    }

    @Test
    void equalityNeedsTheSameOrder() {
        KTypeArrayList a = KTypeArrayList.of(1, 2);
        KTypeArrayList b = KTypeArrayList.of(2, 1);
        assertNotEquals(a, b);
        b.reverse();
        assertEquals(a, b);
        assertNotEquals(a, a.toList());
        a.fill(7);
        assertEquals(KTypeArrayList.of(7, 7), a);
    }
//#if K.floating

    @Test
    void valuesCompareLikeDoubleEquals() {
        KTypeArrayList l = KTypeArrayList.of(0.0, Double.NaN, -0.0);
        assertEquals(1, l.indexOf(Double.NaN));
        assertEquals(2, l.indexOf(-0.0));
        assertEquals(0, l.lastIndexOf(0.0));
        assertNotEquals(KTypeArrayList.of(-0.0, Double.NaN, -0.0), l);
        assertEquals(KTypeArrayList.of(0.0, Double.NaN, -0.0), l);
        assertEquals(l.toList().hashCode(), l.hashCode());
        assertTrue(l.removeValue(-0.0));
        assertEquals(KTypeArrayList.of(0.0, Double.NaN), l);
        l.add(-0.0);
        l.sort();
        assertEquals(KTypeArrayList.of(-0.0, 0.0, Double.NaN), l);
    }
//#end
}
