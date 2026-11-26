// Generated from template heap/KTypeHeap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.IntConsumer;

/**
 * Priority queue of {@code int} values: a heap that returns the smallest value first, or the
 * largest with {@code maxFirst}.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> an implicit 4-ary heap in one {@code int[]}: the children of position
 *       {@code i} are at {@code 4i + 1 .. 4i + 4}, its parent at {@code (i - 1) / 4}. The heap has
 *       half the levels of a binary heap, so an insertion moves fewer values, and the four
 *       children of a node, which a removal scans for the smallest, are neighbors in memory. In
 *       benchmarks with {@code long} values, an 8-ary heap was up to 20% faster in the best
 *       case, but twice as slow for any data when the JIT compiled it while the heap was in
 *       ascending order; the 4-ary heap does not depend on that.</li>
 *   <li><b>Order:</b> the array holds a min-heap of encoded values. A max-first heap stores
 *       {@code ~x} instead of {@code x}, which reverses the order exactly and without overflow;
 *       comparisons are always plain {@code <} without a branch on the order.</li>
 *   <li><b>Cost:</b> {@link #push} and {@link #poll} take {@code O(log n)} time, {@link #peek}
 *       constant time, {@link #pushAll} into an empty heap linear time. The array grows by half
 *       its length when full; memory is {@code Integer.BYTES} bytes per value of capacity.</li>
 * </ul>
 * Values of equal priority come out in an unspecified order. Like
 * {@link java.util.PriorityQueue}, a heap is equal only to itself. This class is not
 * thread-safe.
 */
public final class IntHeap {

    /** Default number of values the heap can hold without growing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private final boolean maxFirst;
    /** {@code 0} for a min-first heap, {@code -1} for a max-first heap: the stored value of {@code x} is {@code x ^ flip}. */
    private final int flip;

    /** Encoded values; {@code heap[0 .. size)} is a min-heap. */
    private int[] heap;
    private int size;

    /** An empty min-first heap with room for {@link #DEFAULT_EXPECTED_SIZE} values. */
    public IntHeap() {
        this(DEFAULT_EXPECTED_SIZE, false);
    }

    /**
     * An empty min-first heap with room for {@code expectedSize} values.
     *
     * @param expectedSize the number of values the heap can hold without growing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the
     *         maximum capacity
     */
    public IntHeap(int expectedSize) {
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
    public IntHeap(int expectedSize, boolean maxFirst) {
        if (expectedSize < 0 || expectedSize > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize must be in [0, " + MAX_CAPACITY + "]: " + expectedSize);
        }
        this.maxFirst = maxFirst;
        this.flip = maxFirst ? -1 : 0;
        this.heap = new int[expectedSize];
    }

    /** The stored value of {@code x}. */
    private int encode(int x) {
        return x ^ flip;
    }

    /** The value of a stored value. */
    private int decode(int v) {
        return v ^ flip;
    }

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
    public int peek() {
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
     */
    public void push(int x) {
        int v = encode(x);
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
    public int poll() {
        int n = size;
        if (n == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        final int[] h = heap;
        int top = h[0];
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
     */
    public int pushPoll(int x) {
        int v = encode(x);
        final int[] h = heap;
        if (size == 0 || !(h[0] < v)) {
            return x;
        }
        int top = h[0];
        siftDown(0, v, size);
        return decode(top);
    }

    /**
     * Removes and returns the first value, then adds {@code x}, in one step.
     *
     * @param x the value to add
     * @return the first value before the call
     * @throws NoSuchElementException if the heap is empty
     */
    public int replaceTop(int x) {
        int v = encode(x);
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        int top = heap[0];
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
     */
    public void pushAll(int[] a, int from, int to) {
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
            for (int i = from; i < to; i++) {
                push(a[i]);
            }
            return;
        }
        if (n + k > heap.length) {
            grow(n + k);
        }
        final int[] h = heap;
        for (int i = 0; i < k; i++) {
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
    public void forEach(IntConsumer action) {
        final int[] h = heap;
        for (int i = 0, n = size; i < n; i++) {
            action.accept(decode(h[i]));
        }
    }

    /**
     * The values in an unspecified order.
     *
     * @return a new array with the values
     */
    public int[] toArray() {
        int[] result = new int[size];
        final int[] h = heap;
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
    public int[] toSortedArray() {
        int[] result = Arrays.copyOf(heap, size);
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
    private void siftUp(int i, int v) {
        final int[] h = heap;
        while (i > 0) {
            int p = (i - 1) >>> 2;
            int pv = h[p];
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
    private void siftDown(int i, int v, int n) {
        final int[] h = heap;
        // Positions up to lastParent have at least one child; 4 * lastParent + 4 cannot overflow.
        final int lastParent = (n - 2) >> 2;
        while (i <= lastParent) {
            int c = (i << 2) + 1;
            int end = Math.min(c + 4, n);
            int m = c;
            int mv = h[c];
            // A plain loop over at most four children: its speed does not depend on the data the
            // JIT saw when it compiled the method. With eight children, a queue that the JIT first
            // saw in ascending order (the first child always the smallest) stayed twice as slow for
            // any data; a tournament of pairwise comparisons was slow for random values (runs
            // v22a to v22c and the hold diagnosis).
            for (int j = c + 1; j < end; j++) {
                int jv = h[j];
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
