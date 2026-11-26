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
//#-- Ring buffer of primitive values with a power-of-two capacity: the counterpart of
//#-- java.util.ArrayDeque, with constant-time access by index.
//#types K=int|long|double
package net.generated.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.KTypeConsumer;
import java.util.function.KTypePredicate;
import java.util.stream.KTypeStream;

/**
 * Double-ended queue of {@code KType} values in a ring buffer: it adds and removes at both ends
 * in constant time and reads and writes any element by its index.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> a ring buffer: one {@code KType[]} whose length is a power of two, the
 *       index of the first element, and the size. Element {@code i} is at
 *       {@code (head + i) & (length - 1)}, without a branch or a division. When the array is
 *       full, it doubles; the elements then start at position 0 again. A new deque allocates
 *       no array until its first element.</li>
 *   <li><b>Cost:</b> {@link #addFirst}, {@link #addLast}, {@link #removeFirst()},
 *       {@link #removeLast()}, {@link #get}, and {@link #set} take constant time ({@code add}
 *       amortized); {@link #discardFirst} and {@link #discardLast} remove any number of
 *       elements in constant time.</li>
 *   <li><b>Names:</b> the methods at the ends have the names of
 *       {@link java.util.SequencedCollection}; there are no aliases such as {@code offer},
 *       {@code poll}, {@code push}, or {@code pop}, so each call names its end.</li>
//#if K.floating
 *   <li><b>Equality of values:</b> {@link #indexOf}, {@link #contains}, and {@link #equals}
 *       compare values like {@link Double#equals}: {@code NaN} equals {@code NaN},
 *       {@code -0.0} does not equal {@code 0.0}.</li>
//#end
 *   <li><b>Memory:</b> {@code KBoxed.BYTES} bytes per element of capacity; the capacity is the
 *       next power of two of the largest size so far, so up to twice the size.
 *       {@link #trimToSize()} shrinks it to the next power of two of the size. The capacity is
 *       at most 2<sup>30</sup>.</li>
 * </ul>
 * Unlike {@link java.util.ArrayDeque}, a deque equals another {@code KTypeArrayDeque} with the
 * same elements in the same order; the hash code is the one of {@link List#hashCode()} on the
 * boxed elements. This class is not thread-safe.
 */
public final class KTypeArrayDeque {

    private static final KType[] EMPTY = {};

    private static final int MIN_CAPACITY = 16;

    private static final int MAX_CAPACITY = 1 << 30;

    /** Ring buffer; its length is 0 or a power of two. */
    private KType[] buffer;
    /** Index of the first element in {@code buffer}; 0 while {@code buffer} is empty. */
    private int head;
    private int size;

    /** An empty deque. It allocates its array when the first element is added. */
    public KTypeArrayDeque() {
        buffer = EMPTY;
    }

    /**
     * An empty deque with room for {@code expectedSize} elements.
     *
     * @param expectedSize the number of elements the deque can hold without growing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than 2<sup>30</sup>
     */
    public KTypeArrayDeque(int expectedSize) {
        if (expectedSize < 0 || expectedSize > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize must be in [0, " + MAX_CAPACITY + "]: " + expectedSize);
        }
        buffer = expectedSize == 0 ? EMPTY : new KType[powerOfTwo(expectedSize)];
    }

    /**
     * A deque with the elements of {@code other}, in the same order.
     *
     * @param other the deque to copy
     */
    public KTypeArrayDeque(KTypeArrayDeque other) {
        int n = other.size;
        buffer = n == 0 ? EMPTY : new KType[powerOfTwo(n)];
        other.copyTo(buffer);
        size = n;
    }

    /**
     * A deque with the given elements, in the same order; {@code values[0]} is the first. The
     * deque does not share the array.
     *
     * @param values the elements
     * @return a new deque
     * @throws IllegalArgumentException if there are more than 2<sup>30</sup> values
     */
    public static KTypeArrayDeque of(KType... values) {
        KTypeArrayDeque deque = new KTypeArrayDeque(values.length);
        deque.append(values, 0, values.length);
        return deque;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * The number of elements.
     *
     * @return the number of elements
     */
    public int size() {
        return size;
    }

    /**
     * Whether the deque has no elements.
     *
     * @return {@code true} if the deque is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * The element at {@code index}, counted from the first element.
     *
     * @param index the index
     * @return the element
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size())}
     */
    public KType get(int index) {
        Objects.checkIndex(index, size);
        final KType[] b = buffer;
        return b[(head + index) & (b.length - 1)];
    }

    /**
     * The first element.
     *
     * @return the first element
     * @throws NoSuchElementException if the deque is empty
     */
    public KType getFirst() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        return buffer[head];
    }

    /**
     * The last element.
     *
     * @return the last element
     * @throws NoSuchElementException if the deque is empty
     */
    public KType getLast() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        final KType[] b = buffer;
        return b[(head + size - 1) & (b.length - 1)];
    }

    /**
     * Whether the deque contains {@code value}.
     *
     * @param value the value
     * @return {@code true} if an element equals {@code value}
     */
    public boolean contains(KType value) {
        return indexOf(value) >= 0;
    }

    /**
     * The index of the first element equal to {@code value}, counted from the first element.
     *
     * @param value the value
     * @return the smallest such index, or {@code -1} if there is none
     */
    public int indexOf(KType value) {
        final KType[] b = buffer;
        final int m = b.length - 1;
        final int h = head;
        for (int i = 0, n = size; i < n; i++) {
            if (same(b[(h + i) & m], value)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Adding
    // ------------------------------------------------------------------

    /**
     * Adds {@code value} before the first element.
     *
     * @param value the value
     * @throws IllegalStateException if the deque already holds 2<sup>30</sup> elements
     */
    public void addFirst(KType value) {
        int n = size;
        if (n == buffer.length) {
            grow(n + 1L);
        }
        final KType[] b = buffer;
        int h = (head - 1) & (b.length - 1);
        b[h] = value;
        head = h;
        size = n + 1;
    }

    /**
     * Adds {@code value} after the last element.
     *
     * @param value the value
     * @throws IllegalStateException if the deque already holds 2<sup>30</sup> elements
     */
    public void addLast(KType value) {
        int n = size;
        if (n == buffer.length) {
            grow(n + 1L);
        }
        final KType[] b = buffer;
        b[(head + n) & (b.length - 1)] = value;
        size = n + 1;
    }

    /**
     * Adds the values {@code a[from .. to)} after the last element, in their order.
     *
     * @param a the values
     * @param from the first index, inclusive
     * @param to the last index, exclusive
     * @throws IndexOutOfBoundsException if the range is not within {@code a}
     * @throws IllegalStateException if the deque would hold more than 2<sup>30</sup> elements
     */
    public void addAllLast(KType[] a, int from, int to) {
        Objects.checkFromToIndex(from, to, a.length);
        int k = to - from;
        if (size + (long) k > buffer.length) {
            grow(size + (long) k);
        }
        append(a, from, k);
    }

    /**
     * Adds the elements of {@code other} after the last element, in their order.
     * {@code other} may be this deque.
     *
     * @param other the deque whose elements to add
     * @throws IllegalStateException if the deque would hold more than 2<sup>30</sup> elements
     */
    public void addAllLast(KTypeArrayDeque other) {
        // Read the source before growing: if other is this deque, the old array keeps the
        // elements, and without growth the free slots that receive them do not overlap them.
        final KType[] ob = other.buffer;
        final int oh = other.head;
        final int k = other.size;
        if (size + (long) k > buffer.length) {
            grow(size + (long) k);
        }
        int first = Math.min(k, ob.length - oh);
        append(ob, oh, first);
        append(ob, 0, k - first);
    }

    /** Copies {@code a[from .. from + k)} behind the last element; the buffer has room for them. */
    private void append(KType[] a, int from, int k) {
        int n = size;
        final KType[] b = buffer;
        int tail = (head + n) & (b.length - 1);
        int first = Math.min(k, b.length - tail);
        System.arraycopy(a, from, b, tail, first);
        System.arraycopy(a, from + first, b, 0, k - first);
        size = n + k;
    }

    // ------------------------------------------------------------------
    // Changing and removing
    // ------------------------------------------------------------------

    /**
     * Replaces the element at {@code index}, counted from the first element.
     *
     * @param index the index
     * @param value the new value
     * @return the previous element at {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size())}
     */
    public KType set(int index, KType value) {
        Objects.checkIndex(index, size);
        final KType[] b = buffer;
        int i = (head + index) & (b.length - 1);
        KType old = b[i];
        b[i] = value;
        return old;
    }

    /**
     * Removes and returns the first element.
     *
     * @return the removed element
     * @throws NoSuchElementException if the deque is empty
     */
    public KType removeFirst() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        final KType[] b = buffer;
        int h = head;
        KType v = b[h];
        head = (h + 1) & (b.length - 1);
        size--;
        return v;
    }

    /**
     * Removes and returns the last element.
     *
     * @return the removed element
     * @throws NoSuchElementException if the deque is empty
     */
    public KType removeLast() {
        if (size == 0) {
            throw new NoSuchElementException("deque is empty");
        }
        final KType[] b = buffer;
        int n = --size;
        return b[(head + n) & (b.length - 1)];
    }

    /**
     * Removes the first {@code count} elements, in constant time.
     *
     * @param count the number of elements to remove
     * @throws IllegalArgumentException if {@code count} is negative or larger than {@link #size()}
     */
    public void discardFirst(int count) {
        checkCount(count);
        head = (head + count) & (buffer.length - 1);
        size -= count;
    }

    /**
     * Removes the last {@code count} elements, in constant time.
     *
     * @param count the number of elements to remove
     * @throws IllegalArgumentException if {@code count} is negative or larger than {@link #size()}
     */
    public void discardLast(int count) {
        checkCount(count);
        size -= count;
    }

    /**
     * Removes all elements for which {@code predicate} returns {@code true}, in one pass; the
     * other elements keep their order. If {@code predicate} throws, the elements it was not called for
     * stay in the deque.
     *
     * @param predicate the condition for removal
     * @return {@code true} if an element was removed
     */
    public boolean removeIf(KTypePredicate predicate) {
        final KType[] b = buffer;
        final int m = b.length - 1;
        final int h = head;
        final int n = size;
        int w = 0;
        int r = 0;
        try {
            for (; r < n; r++) {
                KType v = b[(h + r) & m];
                if (!predicate.test(v)) {
                    b[(h + w++) & m] = v;
                }
            }
        } finally {
            for (; r < n; r++) {
                b[(h + w++) & m] = b[(h + r) & m];
            }
            size = w;
        }
        return w != n;
    }

    /** Removes all elements; the capacity stays. */
    public void clear() {
        head = 0;
        size = 0;
    }

    /**
     * Grows the array, if needed, to hold {@code expectedSize} elements without growing again.
     *
     * @param expectedSize the number of elements
     * @throws IllegalStateException if {@code expectedSize} is larger than 2<sup>30</sup>
     */
    public void ensureCapacity(int expectedSize) {
        if (expectedSize > buffer.length) {
            resize(capacityFor(expectedSize));
        }
    }

    /** Shrinks the array to the next power of two of the number of elements. */
    public void trimToSize() {
        int cap = size == 0 ? 0 : powerOfTwo(size);
        if (cap != buffer.length) {
            resize(cap);
        }
    }

    // ------------------------------------------------------------------
    // Iteration and copies
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for every element, from the first to the last.
     *
     * @param action the action
     */
    public void forEach(KTypeConsumer action) {
        final KType[] b = buffer;
        final int h = head;
        final int n = size;
        int first = Math.min(n, b.length - h);
        for (int i = h, end = h + first; i < end; i++) {
            action.accept(b[i]);
        }
        for (int i = 0, end = n - first; i < end; i++) {
            action.accept(b[i]);
        }
    }

    /**
     * Calls {@code action} for every element, from the last to the first.
     *
     * @param action the action
     */
    public void forEachReversed(KTypeConsumer action) {
        final KType[] b = buffer;
        final int m = b.length - 1;
        final int h = head;
        for (int i = size - 1; i >= 0; i--) {
            action.accept(b[(h + i) & m]);
        }
    }

    /**
     * A sequential stream of the elements, from the first to the last. The deque must not
     * change while the stream runs.
     *
     * @return a stream of the elements
     */
    public KTypeStream stream() {
        final KType[] b = buffer;
        final int h = head;
        final int n = size;
        int first = Math.min(n, b.length - h);
        KTypeStream front = Arrays.stream(b, h, h + first);
        return first == n ? front : KTypeStream.concat(front, Arrays.stream(b, 0, n - first));
    }

    /**
     * The elements, from the first to the last.
     *
     * @return a new array with the elements
     */
    public KType[] toArray() {
        KType[] result = new KType[size];
        copyTo(result);
        return result;
    }

    /**
     * The elements as a new modifiable {@link List}, from the first to the last.
     *
     * @return a new list of boxed elements
     */
    public List<KBoxed> toList() {
        List<KBoxed> list = new ArrayList<>(size);
        forEach(list::add);
        return list;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof KTypeArrayDeque other) || other.size != size) {
            return false;
        }
        final KType[] a = buffer;
        final KType[] b = other.buffer;
        final int ma = a.length - 1;
        final int mb = b.length - 1;
        for (int i = 0, n = size; i < n; i++) {
            if (!same(a[(head + i) & ma], b[(other.head + i) & mb])) {
                return false;
            }
        }
        return true;
    }

    /** Same formula as {@link List#hashCode()} on the boxed elements. */
    @Override
    public int hashCode() {
        final KType[] b = buffer;
        final int m = b.length - 1;
        int h = 1;
        for (int i = 0, n = size; i < n; i++) {
            h = 31 * h + KBoxed.hashCode(b[(head + i) & m]);
        }
        return h;
    }

    /** The elements from the first to the last, as {@code [a, b, c]}. */
    @Override
    public String toString() {
        final KType[] b = buffer;
        final int m = b.length - 1;
        StringBuilder sb = new StringBuilder(Math.min(size, 1 << 16) * 8 + 2).append('[');
        for (int i = 0, n = size; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(b[(head + i) & m]);
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Capacity of the array, for tests. */
    int capacity() {
        return buffer.length;
    }

    /** Index of the first element in the array, for tests. */
    int head() {
        return head;
    }

//#if K.floating
    /** Whether two values are equal like {@link Double#equals}: bitwise, so NaN equals NaN. */
    private static boolean same(KType a, KType b) {
        return KBoxed.doubleToLongBits(a) == KBoxed.doubleToLongBits(b);
    }
//#else
    /** Whether two values are equal. */
    private static boolean same(KType a, KType b) {
        return a == b;
    }
//#end

    private void checkCount(int count) {
        if (count < 0 || count > size) {
            throw new IllegalArgumentException("count must be in [0, " + size + "]: " + count);
        }
    }

    /** The smallest power of two not below {@code n}, for {@code 1 <= n <= 2^30}. */
    private static int powerOfTwo(int n) {
        return n <= 1 ? 1 : Integer.highestOneBit(n - 1) << 1;
    }

    /** The capacity for {@code n} elements: a power of two, at least {@link #MIN_CAPACITY}. */
    private static int capacityFor(long n) {
        if (n > MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return powerOfTwo(Math.max((int) n, MIN_CAPACITY));
    }

    /** Grows the array to hold at least {@code minCapacity} elements; it at least doubles. */
    private void grow(long minCapacity) {
        resize(capacityFor(Math.max(minCapacity, 2L * buffer.length)));
    }

    /** Moves the elements to a new array of {@code capacity}, starting at position 0. */
    private void resize(int capacity) {
        KType[] b = capacity == 0 ? EMPTY : new KType[capacity];
        copyTo(b);
        buffer = b;
        head = 0;
    }

    /** Copies the elements, from the first to the last, to the start of {@code dst}. */
    private void copyTo(KType[] dst) {
        final KType[] b = buffer;
        final int h = head;
        final int n = size;
        int first = Math.min(n, b.length - h);
        System.arraycopy(b, h, dst, 0, first);
        System.arraycopy(b, 0, dst, first, n - first);
    }
}
