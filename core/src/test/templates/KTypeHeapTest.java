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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Differential tests against {@link PriorityQueue}: every value comes out in priority order. */
class KTypeHeapTest {

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
            return Double.NEGATIVE_INFINITY;
        }
        if (r == 1) {
            return Double.POSITIVE_INFINITY;
        }
        if (r == 2) {
            return -0.0;
        }
        if (r == 3) {
            return -Double.MIN_VALUE;
        }
        return (rnd.nextInt(range) - range / 2) * 0.25;
//#end
    }

    /** Same priority ({@code -0.0} and {@code 0.0} are the same priority). */
    private static void assertSamePriority(KBoxed expected, KType actual, String context) {
        assertTrue(expected == actual, context + ": expected " + expected + ", got " + actual);
    }

    private static PriorityQueue<KBoxed> reference(boolean maxFirst) {
        return maxFirst ? new PriorityQueue<>(Comparator.reverseOrder()) : new PriorityQueue<>();
    }

    private static List<KBoxed> sorted(KType[] values, boolean maxFirst) {
        List<KBoxed> list = new ArrayList<>();
        for (KType x : values) {
            list.add(x);
        }
        list.sort(maxFirst ? Comparator.reverseOrder() : Comparator.naturalOrder());
        return list;
    }

    private static void assertSameContent(PriorityQueue<KBoxed> ref, KTypeHeap h, boolean maxFirst, String context) {
        assertEquals(ref.size(), h.size(), context);
        assertEquals(ref.isEmpty(), h.isEmpty(), context);
        KType[] ordered = h.toSortedArray();
        List<KBoxed> want = new ArrayList<>(ref);
        want.sort(maxFirst ? Comparator.reverseOrder() : Comparator.naturalOrder());
        assertEquals(want.size(), ordered.length, context);
        for (int i = 0; i < ordered.length; i++) {
            assertSamePriority(want.get(i), ordered[i], context + " sorted[" + i + "]");
        }
        List<KBoxed> each = new ArrayList<>();
        h.forEach(x -> each.add(x));
        assertEquals(sorted(h.toArray(), maxFirst), sorted(toPrimitive(each), maxFirst), context);
        assertEquals(Arrays.toString(ordered), h.toString(), context);
    }

    private static KType[] toPrimitive(List<KBoxed> list) {
        KType[] a = new KType[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    @ParameterizedTest
    @CsvSource({
        "false, 8, 1",
        "false, 1000, 2",
        "false, 1000000, 3",
        "true, 8, 4",
        "true, 1000, 5",
        "true, 1000000, 6",
    })
    void randomOperationsMatchPriorityQueue(boolean maxFirst, int range, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        KTypeHeap h = new KTypeHeap(rnd.nextInt(4), maxFirst);
        assertEquals(maxFirst, h.isMaxFirst());
        PriorityQueue<KBoxed> ref = reference(maxFirst);
        for (int step = 0; step < 30_000; step++) {
            String context = "step " + step;
            int op = rnd.nextInt(1000);
            if (op < 450) {
                KType x = value(rnd, range);
                h.push(x);
                ref.add(x);
            } else if (op < 750) {
                if (ref.isEmpty()) {
                    assertThrows(NoSuchElementException.class, h::poll, context);
                } else {
                    assertSamePriority(ref.poll(), h.poll(), context + " poll");
                }
            } else if (op < 850) {
                KType x = value(rnd, range);
                ref.add(x);
                assertSamePriority(ref.poll(), h.pushPoll(x), context + " pushPoll");
            } else if (op < 930) {
                KType x = value(rnd, range);
                if (ref.isEmpty()) {
                    assertThrows(NoSuchElementException.class, () -> h.replaceTop(x), context);
                } else {
                    KBoxed top = ref.poll();
                    ref.add(x);
                    assertSamePriority(top, h.replaceTop(x), context + " replaceTop");
                }
            } else if (op < 990) {
                KType[] a = new KType[rnd.nextInt(rnd.nextInt(10) == 0 ? 2000 : 40)];
                for (int i = 0; i < a.length; i++) {
                    a[i] = value(rnd, range);
                }
                int from = a.length == 0 ? 0 : rnd.nextInt(a.length + 1);
                int to = from + rnd.nextInt(a.length - from + 1);
                h.pushAll(a, from, to);
                for (int i = from; i < to; i++) {
                    ref.add(a[i]);
                }
            } else if (op < 993) {
                h.clear();
                ref.clear();
            } else {
                assertSameContent(ref, h, maxFirst, context);
            }
            assertEquals(ref.size(), h.size(), context);
            if (!ref.isEmpty()) {
                assertSamePriority(ref.peek(), h.peek(), context + " peek");
            }
        }
        assertSameContent(ref, h, maxFirst, "end");
        while (!ref.isEmpty()) {
            assertSamePriority(ref.poll(), h.poll(), "drain");
        }
        assertTrue(h.isEmpty());
    }

    /** Growth over many levels, and bottom-up construction of a large heap. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void largeHeapsComeOutSorted(boolean maxFirst) {
        SplittableRandom rnd = new SplittableRandom(7);
        KType[] values = new KType[200_000];
        for (int i = 0; i < values.length; i++) {
            values[i] = value(rnd, 1 << 30);
        }
        List<KBoxed> want = sorted(values, maxFirst);
        KTypeHeap pushed = new KTypeHeap(0, maxFirst);
        for (KType x : values) {
            pushed.push(x);
        }
        KTypeHeap built = new KTypeHeap(0, maxFirst);
        built.pushAll(values, 0, values.length);
        for (int i = 0; i < want.size(); i++) {
            assertSamePriority(want.get(i), pushed.poll(), "pushed " + i);
            assertSamePriority(want.get(i), built.poll(), "built " + i);
        }
        assertTrue(pushed.isEmpty());
        assertTrue(built.isEmpty());
    }

    @Test
    void extremesKeepTheirOrder() {
//#if K == int
        KType[] values = {0, -1, 1, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE + 1, Integer.MAX_VALUE - 1};
        KType[] ascending = {Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 0, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE};
//#elif K == long
        KType[] values = {0, -1, 1, Long.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE + 1, Long.MAX_VALUE - 1};
        KType[] ascending = {Long.MIN_VALUE, Long.MIN_VALUE + 1, -1, 0, 1, Long.MAX_VALUE - 1, Long.MAX_VALUE};
//#else
        KType[] values = {0.5, -1, 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -Double.MAX_VALUE, Double.MAX_VALUE};
        KType[] ascending = {Double.NEGATIVE_INFINITY, -Double.MAX_VALUE, -1, 0.5, 1, Double.MAX_VALUE, Double.POSITIVE_INFINITY};
//#end
        KTypeHeap min = new KTypeHeap();
        KTypeHeap max = new KTypeHeap(4, true);
        for (KType x : values) {
            min.push(x);
            max.push(x);
        }
        assertArrayEquals(ascending, min.toSortedArray());
        for (int i = 0; i < ascending.length; i++) {
            assertEquals(ascending[i], min.poll());
            assertEquals(ascending[ascending.length - 1 - i], max.poll());
        }
    }

    @Test
    void emptyHeap() {
        KTypeHeap h = new KTypeHeap();
        assertTrue(h.isEmpty());
        assertThrows(NoSuchElementException.class, h::peek);
        assertThrows(NoSuchElementException.class, h::poll);
        assertThrows(NoSuchElementException.class, () -> h.replaceTop(1));
        assertEquals(3, h.pushPoll(3));
        assertTrue(h.isEmpty());
        assertEquals(0, h.toArray().length);
        assertEquals(0, h.toSortedArray().length);
        assertEquals("[]", h.toString());
        h.pushAll(new KType[3], 1, 1);
        assertTrue(h.isEmpty());
    }

    @Test
    void pushPollKeepsTheLargestOfAStream() {
        SplittableRandom rnd = new SplittableRandom(11);
        KType[] stream = new KType[10_000];
        KTypeHeap top = new KTypeHeap(100); // min-first: the smallest of the 100 largest comes out
        for (int i = 0; i < stream.length; i++) {
            stream[i] = value(rnd, 1 << 20);
            if (top.size() < 100) {
                top.push(stream[i]);
            } else {
                top.pushPoll(stream[i]);
            }
        }
        List<KBoxed> all = sorted(stream, true);
        List<KBoxed> want = new ArrayList<>(all.subList(0, 100));
        Collections.reverse(want);
        KType[] got = top.toSortedArray();
        for (int i = 0; i < 100; i++) {
            assertSamePriority(want.get(i), got[i], "top " + i);
        }
    }

    @Test
    void toStringAndArraysInPriorityOrder() {
        KTypeHeap max = new KTypeHeap(0, true);
        max.push(3);
        max.push(1);
        max.push(2);
        KType[] want = {3, 2, 1};
        assertArrayEquals(want, max.toSortedArray());
        assertEquals(Arrays.toString(want), max.toString());
        KType[] any = max.toArray();
        Arrays.sort(any);
        assertArrayEquals(new KType[] {1, 2, 3}, any);
        assertEquals(3, max.size(), "the copies do not change the heap");
    }

    @Test
    void capacity() {
        KTypeHeap h = new KTypeHeap(0);
        assertEquals(0, h.capacity());
        h.ensureCapacity(100);
        assertTrue(h.capacity() >= 100);
        for (int i = 0; i < 10; i++) {
            h.push(i);
        }
        h.trimToSize();
        assertEquals(10, h.capacity());
        h.push(42);
        assertEquals(11, h.size());
        h.clear();
        h.trimToSize();
        assertEquals(0, h.capacity());
        h.push(5);
        assertEquals(5, h.peek());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeHeap(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeHeap(Integer.MAX_VALUE, false));
        KTypeHeap h = new KTypeHeap();
        KType[] a = new KType[4];
        assertThrows(IndexOutOfBoundsException.class, () -> h.pushAll(a, 3, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> h.pushAll(a, -1, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> h.pushAll(a, 0, 5));
        assertTrue(h.isEmpty());
    }
//#if K.floating

    @Test
    void rejectsNaN() {
        KTypeHeap h = new KTypeHeap();
        assertThrows(IllegalArgumentException.class, () -> h.push(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> h.pushPoll(Double.NaN));
        h.push(1);
        h.push(2);
        h.push(3);
        assertThrows(IllegalArgumentException.class, () -> h.replaceTop(Double.NaN));
        // fewer values than in the heap: one by one, all checked first
        assertThrows(IllegalArgumentException.class, () -> h.pushAll(new double[] {0, Double.NaN}, 0, 2));
        // at least as many: bottom-up construction
        assertThrows(IllegalArgumentException.class, () -> h.pushAll(new double[] {0, 5, 6, Double.NaN}, 0, 4));
        assertArrayEquals(new double[] {1, 2, 3}, h.toSortedArray());
    }

    @Test
    void zerosHaveTheSamePriority() {
        KTypeHeap h = new KTypeHeap();
        h.push(0.0);
        h.push(-0.0);
        h.push(-1);
        assertEquals(-1, h.poll());
        double a = h.poll();
        double b = h.poll();
        assertEquals(0, Double.compare(-0.0, Math.min(a, b)), "both zeros come back exactly");
        assertEquals(0, Double.compare(0.0, Math.max(a, b)));
    }
//#end
}
