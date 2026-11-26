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
//#-- Implicit 4-ary heap over a plain array. The heap is always a min-heap of the stored values;
//#-- a max-first heap stores encoded values (xor with -1 for integral types, negation for
//#-- double), which reverses the order without a branch in the comparisons.
//#types K=int|long|double
package net.generated.example;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.KTypeConsumer;

/**
 * Priority queue of {@code KType} values: a heap that returns the smallest value first, or the
 * largest with {@code maxFirst}.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> an implicit 4-ary heap in one {@code KType[]}: the children of position
 *       {@code i} are at {@code 4i + 1 .. 4i + 4}, its parent at {@code (i - 1) / 4}. The heap has
 *       half the levels of a binary heap, so an insertion moves fewer values, and the four
 *       children of a node, which a removal scans for the smallest, are neighbors in memory. In
 *       benchmarks with {@code long} values, an 8-ary heap was up to 20% faster in the best
 *       case, but twice as slow for any data when the JIT compiled it while the heap was in
 *       ascending order; the 4-ary heap does not depend on that.</li>
 *   <li><b>Order:</b> the array holds a min-heap of encoded values. A max-first heap stores
//#if K.floating
 *       {@code -x} instead of {@code x}, which reverses the order exactly; comparisons are
 *       always plain {@code <} without a branch on the order.</li>
 *   <li><b>NaN:</b> {@code NaN} has no place in the order and is rejected. {@code -0.0} and
 *       {@code 0.0} have the same priority.</li>
//#else
 *       {@code ~x} instead of {@code x}, which reverses the order exactly and without overflow;
 *       comparisons are always plain {@code <} without a branch on the order.</li>
//#end
 *   <li><b>Cost:</b> {@link #push} and {@link #poll} take {@code O(log n)} time, {@link #peek}
 *       constant time, {@link #pushAll} into an empty heap linear time. The array grows by half
 *       its length when full; memory is {@code KBoxed.BYTES} bytes per value of capacity.</li>
 * </ul>
 * Values of equal priority come out in an unspecified order. Like
 * {@link java.util.PriorityQueue}, a heap is equal only to itself. This class is not
 * thread-safe.
 */
public final class KTypeHeap {

    /** Default number of values the heap can hold without growing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private final boolean maxFirst;
//#if K.floating
    /** {@code 1.0} for a min-first heap, {@code -1.0} for a max-first heap: the stored value of {@code x} is {@code x * sign}. */
    private final double sign;
//#else
    /** {@code 0} for a min-first heap, {@code -1} for a max-first heap: the stored value of {@code x} is {@code x ^ flip}. */
    private final KType flip;
//#end

    /** Encoded values; {@code heap[0 .. size)} is a min-heap. */
    private KType[] heap;
    private int size;

    /** An empty min-first heap with room for {@link #DEFAULT_EXPECTED_SIZE} values. */
    public KTypeHeap() {
        this(DEFAULT_EXPECTED_SIZE, false);
    }

    /**
     * An empty min-first heap with room for {@code expectedSize} values.
     *
     * @param expectedSize the number of values the heap can hold without growing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the
     *         maximum capacity
     */
    public KTypeHeap(int expectedSize) {
        this(expectedSize, false);
    }

    /**
     * An empty heap with room for {@code expectedSize} values.
     *
     * @param expectedSize the number of values the heap can hold without growing
     * @param maxFirst {@code true} to return the largest value first, {@code false} for the smallest
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the
     *         maximum capacity
     */
    public KTypeHeap(int expectedSize, boolean maxFirst) {
        if (expectedSize < 0 || expectedSize > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize must be in [0, " + MAX_CAPACITY + "]: " + expectedSize);
        }
        this.maxFirst = maxFirst;
//#if K.floating
        this.sign = maxFirst ? -1.0 : 1.0;
//#else
        this.flip = maxFirst ? -1 : 0;
//#end
        this.heap = new KType[expectedSize];
    }

//#if K.floating
    /** The stored value of {@code x}; rejects {@code NaN}. */
    private KType encode(KType x) {
        if (x != x) {
            throw new IllegalArgumentException("NaN has no priority");
        }
        return x * sign;
    }

    /** The value of a stored value. */
    private KType decode(KType v) {
        return v * sign;
    }
//#else
    /** The stored value of {@code x}. */
    private KType encode(KType x) {
        return x ^ flip;
    }

    /** The value of a stored value. */
    private KType decode(KType v) {
        return v ^ flip;
    }
//#end

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * The number of values in the heap.
     *
     * @return the number of values
     */
    public int size() {
        return size;
    }

    /**
     * Whether the heap holds no values.
     *
     * @return {@code true} if the heap is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Whether the heap returns the largest value first.
     *
     * @return {@code true} for a max-first heap, {@code false} for a min-first heap
     */
    public boolean isMaxFirst() {
        return maxFirst;
    }

    /**
     * The value that {@link #poll} would return, without removing it.
     *
     * @return the first value: the smallest, or the largest for a max-first heap
     * @throws NoSuchElementException if the heap is empty
     */
    public KType peek() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return decode(heap[0]);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /**
     * Adds a value. Equal values may occur several times.
     *
     * @param x the value
//#if K.floating
     * @throws IllegalArgumentException if {@code x} is {@code NaN}
//#end
     */
    public void push(KType x) {
        KType v = encode(x);
        int n = size;
        if (n == heap.length) {
            grow(n + 1);
        }
        siftUp(n, v);
        size = n + 1;
    }

    /**
     * Removes and returns the first value.
     *
     * @return the first value: the smallest, or the largest for a max-first heap
     * @throws NoSuchElementException if the heap is empty
     */
    public KType poll() {
        int n = size;
        if (n == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        final KType[] h = heap;
        KType top = h[0];
        size = --n;
        if (n > 0) {
            siftDown(0, h[n], n);
        }
        return decode(top);
    }

    /**
     * Adds {@code x}, then removes and returns the first value, in one step: if {@code x} would
     * come first, it is returned at once and the heap does not change. With a heap of the
     * {@code k} largest values so far, {@code pushPoll} keeps the {@code k} largest of a stream.
     *
     * @param x the value to add
     * @return the first value of the heap with {@code x}
//#if K.floating
     * @throws IllegalArgumentException if {@code x} is {@code NaN}
//#end
     */
    public KType pushPoll(KType x) {
        KType v = encode(x);
        final KType[] h = heap;
        if (size == 0 || !(h[0] < v)) {
            return x;
        }
        KType top = h[0];
        siftDown(0, v, size);
        return decode(top);
    }

    /**
     * Removes and returns the first value, then adds {@code x}, in one step.
     *
     * @param x the value to add
     * @return the first value before the call
     * @throws NoSuchElementException if the heap is empty
//#if K.floating
     * @throws IllegalArgumentException if {@code x} is {@code NaN}
//#end
     */
    public KType replaceTop(KType x) {
        KType v = encode(x);
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        KType top = heap[0];
        siftDown(0, v, size);
        return decode(top);
    }

    /**
     * Adds the values {@code a[from .. to)}. Into an empty heap, or when they are at least as
     * many as the values in the heap, it adds them in linear time (bottom-up heap construction).
     *
     * @param a the values
     * @param from the first index, inclusive
     * @param to the last index, exclusive
     * @throws IndexOutOfBoundsException if the range is not within {@code a}
//#if K.floating
     * @throws IllegalArgumentException if a value is {@code NaN}; the heap does not change then
//#end
     */
    public void pushAll(KType[] a, int from, int to) {
        Objects.checkFromToIndex(from, to, a.length);
        int k = to - from;
        if (k == 0) {
            return;
        }
        int n = size;
        if ((long) n + k > MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        if (k < n) {
//#if K.floating
            for (int i = from; i < to; i++) {
                encode(a[i]); // reject NaN before any change
            }
//#end
            for (int i = from; i < to; i++) {
                push(a[i]);
            }
            return;
        }
        if (n + k > heap.length) {
            grow(n + k);
        }
        final KType[] h = heap;
        for (int i = 0; i < k; i++) {
//#if K.floating
            // If encode rejects a value, size has not changed yet: the values are not part of the heap.
//#end
            h[n + i] = encode(a[from + i]);
        }
        n += k;
        size = n;
        for (int i = (n - 2) >> 2; i >= 0; i--) {
            siftDown(i, h[i], n);
        }
    }

    /** Removes all values; the capacity stays. */
    public void clear() {
        size = 0;
    }

    /**
     * Grows the array, if needed, to hold {@code expectedSize} values without growing again.
     *
     * @param expectedSize the number of values
     */
    public void ensureCapacity(int expectedSize) {
        if (expectedSize > heap.length) {
            grow(expectedSize);
        }
    }

    /** Shrinks the array to the number of values. */
    public void trimToSize() {
        if (heap.length != size) {
            heap = Arrays.copyOf(heap, size);
        }
    }

    // ------------------------------------------------------------------
    // Iteration and copies
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for every value, in an unspecified order.
     *
     * @param action the action
     */
    public void forEach(KTypeConsumer action) {
        final KType[] h = heap;
        for (int i = 0, n = size; i < n; i++) {
            action.accept(decode(h[i]));
        }
    }

    /**
     * The values in an unspecified order.
     *
     * @return a new array with the values
     */
    public KType[] toArray() {
        KType[] result = new KType[size];
        final KType[] h = heap;
        for (int i = 0; i < result.length; i++) {
            result[i] = decode(h[i]);
        }
        return result;
    }

    /**
     * The values in the order in which {@link #poll} would return them; the heap does not change.
     *
     * @return a new array with the values, first value first
     */
    public KType[] toSortedArray() {
        KType[] result = Arrays.copyOf(heap, size);
        Arrays.sort(result);
        for (int i = 0; i < result.length; i++) {
            result[i] = decode(result[i]);
        }
        return result;
    }

    /** The values in the order in which {@link #poll} would return them, as {@code [a, b, c]}. */
    @Override
    public String toString() {
        return Arrays.toString(toSortedArray());
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Capacity of the array, for tests. */
    int capacity() {
        return heap.length;
    }

    /** Moves the hole at position {@code i} up until {@code v} fits, and stores {@code v} there. */
    private void siftUp(int i, KType v) {
        final KType[] h = heap;
        while (i > 0) {
            int p = (i - 1) >>> 2;
            KType pv = h[p];
            if (!(v < pv)) {
                break;
            }
            h[i] = pv;
            i = p;
        }
        h[i] = v;
    }

    /**
     * Moves the hole at position {@code i} of a heap of {@code n} values down until {@code v}
     * fits, and stores {@code v} there.
     */
    private void siftDown(int i, KType v, int n) {
        final KType[] h = heap;
        // Positions up to lastParent have at least one child; 4 * lastParent + 4 cannot overflow.
        final int lastParent = (n - 2) >> 2;
        while (i <= lastParent) {
            int c = (i << 2) + 1;
            int end = Math.min(c + 4, n);
            int m = c;
            KType mv = h[c];
            // A plain loop over at most four children: its speed does not depend on the data the
            // JIT saw when it compiled the method. With eight children, a queue that the JIT first
            // saw in ascending order (the first child always the smallest) stayed twice as slow for
            // any data; a tournament of pairwise comparisons was slow for random values (runs
            // v22a to v22c and the hold diagnosis).
            for (int j = c + 1; j < end; j++) {
                KType jv = h[j];
                if (jv < mv) {
                    m = j;
                    mv = jv;
                }
            }
            if (!(mv < v)) {
                break;
            }
            h[i] = mv;
            i = m;
        }
        h[i] = v;
    }

    /** Grows the array to at least {@code minCapacity}, by half its length or more. */
    private void grow(int minCapacity) {
        if (minCapacity > MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        int len = heap.length;
        int cap = (int) Math.min(MAX_CAPACITY, Math.max((long) minCapacity, len + (len >> 1) + 1L));
        heap = Arrays.copyOf(heap, cap);
    }
}
