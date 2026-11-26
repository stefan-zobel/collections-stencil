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
//#-- Growable array of primitive values: the list counterpart of java.util.ArrayList.
//#types K=int|long|double
package net.generated.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.KTypeConsumer;
import java.util.function.KTypePredicate;
import java.util.function.KTypeUnaryOperator;
import java.util.stream.KTypeStream;

/**
 * List of {@code KType} values in a growable array.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> one {@code KType[]} with the elements at positions {@code 0 .. size}.
 *       When it is full, the array grows by half its length (to at least 16). A new list
 *       allocates no array until its first element.</li>
 *   <li><b>Cost:</b> {@link #add(KType)}, {@link #removeLast()}, {@link #get} and {@link #set}
 *       take constant time ({@code add} amortized). Inserting or removing at an index moves the
 *       elements behind it with one {@link System#arraycopy}. {@link #sort()} uses
 *       {@link Arrays#sort(KType[], int, int)}.</li>
 *   <li><b>Index or value:</b> there is no {@code remove(int)}: for an {@code IntArrayList} it
 *       would be unclear whether it removes at an index or a value. {@link #removeAt} takes an
 *       index, {@link #removeValue} a value.</li>
 *   <li><b>Raw access:</b> {@link #elements()} returns the array itself, for code that works on
 *       arrays; {@link #wrap} turns an array into a list without copying it.</li>
//#if K.floating
 *   <li><b>Equality of values:</b> {@link #indexOf}, {@link #contains}, {@link #removeValue}, and
 *       {@link #equals} compare values like {@link Double#equals}: {@code NaN} equals
 *       {@code NaN}, {@code -0.0} does not equal {@code 0.0}, as in a {@code List<Double>}.</li>
//#end
 *   <li><b>Memory:</b> {@code KBoxed.BYTES} bytes per element of capacity;
 *       {@link #trimToSize()} drops the unused part.</li>
 * </ul>
 * A list equals another {@code KTypeArrayList} with the same elements in the same order. The hash
 * code is the one of {@link List#hashCode()} on the boxed elements, so it equals the hash code of
 * {@link #toList()}. This class is not thread-safe.
 */
public final class KTypeArrayList {

    private static final KType[] EMPTY = {};

    private static final int MIN_GROWTH = 16;

    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    /** Elements at {@code buffer[0 .. size)}. */
    private KType[] buffer;
    private int size;

    /** An empty list. It allocates its array when the first element is added. */
    public KTypeArrayList() {
        buffer = EMPTY;
    }

    /**
     * An empty list with room for {@code expectedSize} elements.
     *
     * @param expectedSize the number of elements the list can hold without growing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public KTypeArrayList(int expectedSize) {
        if (expectedSize < 0 || expectedSize > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize must be in [0, " + MAX_CAPACITY + "]: " + expectedSize);
        }
        buffer = expectedSize == 0 ? EMPTY : new KType[expectedSize];
    }

    /**
     * A list with the elements of {@code other}, in the same order.
     *
     * @param other the list to copy
     */
    public KTypeArrayList(KTypeArrayList other) {
        buffer = other.size == 0 ? EMPTY : Arrays.copyOf(other.buffer, other.size);
        size = other.size;
    }

    /**
     * A list with the given elements, in the same order. The list does not share the array.
     *
     * @param values the elements
     * @return a new list
     */
    public static KTypeArrayList of(KType... values) {
        KTypeArrayList list = new KTypeArrayList(values.length);
        System.arraycopy(values, 0, list.buffer, 0, values.length);
        list.size = values.length;
        return list;
    }

    /**
     * A list of the elements {@code a[0 .. size)} that uses {@code a} as its array, without
     * copying it: changes to the list write to {@code a} until the list has to grow.
     *
     * @param a the array
     * @param size the number of elements
     * @return a new list backed by {@code a}
     * @throws IndexOutOfBoundsException if {@code size} is negative or larger than {@code a.length}
     */
    public static KTypeArrayList wrap(KType[] a, int size) {
        Objects.checkFromToIndex(0, size, a.length);
        KTypeArrayList list = new KTypeArrayList();
        list.buffer = a;
        list.size = size;
        return list;
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
     * Whether the list has no elements.
     *
     * @return {@code true} if the list is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * The element at {@code index}.
     *
     * @param index the index
     * @return the element
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size())}
     */
    public KType get(int index) {
        Objects.checkIndex(index, size);
        return buffer[index];
    }

    /**
     * The first element.
     *
     * @return the element at index 0
     * @throws NoSuchElementException if the list is empty
     */
    public KType getFirst() {
        if (size == 0) {
            throw new NoSuchElementException("list is empty");
        }
        return buffer[0];
    }

    /**
     * The last element.
     *
     * @return the element at index {@code size() - 1}
     * @throws NoSuchElementException if the list is empty
     */
    public KType getLast() {
        if (size == 0) {
            throw new NoSuchElementException("list is empty");
        }
        return buffer[size - 1];
    }

    /**
     * Whether the list contains {@code value}.
     *
     * @param value the value
     * @return {@code true} if an element equals {@code value}
     */
    public boolean contains(KType value) {
        return indexOf(value) >= 0;
    }

    /**
     * The index of the first element equal to {@code value}.
     *
     * @param value the value
     * @return the smallest such index, or {@code -1} if there is none
     */
    public int indexOf(KType value) {
        final KType[] a = buffer;
        for (int i = 0, n = size; i < n; i++) {
            if (same(a[i], value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The index of the last element equal to {@code value}.
     *
     * @param value the value
     * @return the largest such index, or {@code -1} if there is none
     */
    public int lastIndexOf(KType value) {
        final KType[] a = buffer;
        for (int i = size - 1; i >= 0; i--) {
            if (same(a[i], value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Searches a sorted list for {@code value} with
     * {@link Arrays#binarySearch(KType[], int, int, KType)}. The result is undefined if the list
     * is not sorted in ascending order.
     *
     * @param value the value
     * @return the index of an element equal to {@code value}; otherwise
     *         {@code -(insertion point) - 1}
     */
    public int binarySearch(KType value) {
        return Arrays.binarySearch(buffer, 0, size, value);
    }

    // ------------------------------------------------------------------
    // Adding
    // ------------------------------------------------------------------

    /**
     * Appends {@code value}.
     *
     * @param value the value
     */
    public void add(KType value) {
        int n = size;
        if (n == buffer.length) {
            grow(n + 1L);
        }
        buffer[n] = value;
        size = n + 1;
    }

    /**
     * Inserts {@code value} at {@code index}; the elements from {@code index} on move one up.
     *
     * @param index the index, from 0 to {@code size()}
     * @param value the value
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size()]}
     */
    public void add(int index, KType value) {
        int n = size;
        Objects.checkIndex(index, n + 1);
        if (n == buffer.length) {
            grow(n + 1L);
        }
        final KType[] a = buffer;
        System.arraycopy(a, index, a, index + 1, n - index);
        a[index] = value;
        size = n + 1;
    }

    /**
     * Appends the values {@code a[from .. to)}.
     *
     * @param a the values
     * @param from the first index, inclusive
     * @param to the last index, exclusive
     * @throws IndexOutOfBoundsException if the range is not within {@code a}
     */
    public void addAll(KType[] a, int from, int to) {
        Objects.checkFromToIndex(from, to, a.length);
        int k = to - from;
        int n = size;
        if (n + (long) k > buffer.length) {
            grow(n + (long) k);
        }
        System.arraycopy(a, from, buffer, n, k);
        size = n + k;
    }

    /**
     * Appends the elements of {@code other}, in their order. {@code other} may be this list.
     *
     * @param other the list whose elements to append
     */
    public void addAll(KTypeArrayList other) {
        int k = other.size;
        int n = size;
        if (n + (long) k > buffer.length) {
            grow(n + (long) k);
        }
        System.arraycopy(other.buffer, 0, buffer, n, k);
        size = n + k;
    }

    /**
     * Inserts the values {@code a[from .. to)} at {@code index}; the elements from {@code index}
     * on move up behind them.
     *
     * @param index the index, from 0 to {@code size()}
     * @param a the values
     * @param from the first index in {@code a}, inclusive
     * @param to the last index in {@code a}, exclusive
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size()]} or the range
     *         is not within {@code a}
     */
    public void addAll(int index, KType[] a, int from, int to) {
        int n = size;
        Objects.checkIndex(index, n + 1);
        Objects.checkFromToIndex(from, to, a.length);
        int k = to - from;
        if (a == buffer) {
            // The values are elements of this list (see elements()): the move below would overwrite them.
            a = Arrays.copyOfRange(a, from, to);
            from = 0;
        }
        if (n + (long) k > buffer.length) {
            grow(n + (long) k);
        }
        final KType[] b = buffer;
        System.arraycopy(b, index, b, index + k, n - index);
        System.arraycopy(a, from, b, index, k);
        size = n + k;
    }

    // ------------------------------------------------------------------
    // Changing and removing
    // ------------------------------------------------------------------

    /**
     * Replaces the element at {@code index}.
     *
     * @param index the index
     * @param value the new value
     * @return the previous element at {@code index}
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size())}
     */
    public KType set(int index, KType value) {
        Objects.checkIndex(index, size);
        final KType[] a = buffer;
        KType old = a[index];
        a[index] = value;
        return old;
    }

    /**
     * Removes the element at {@code index}; the elements behind it move one down.
     *
     * @param index the index
     * @return the removed element
     * @throws IndexOutOfBoundsException if {@code index} is not in {@code [0, size())}
     */
    public KType removeAt(int index) {
        int n = size;
        Objects.checkIndex(index, n);
        final KType[] a = buffer;
        KType old = a[index];
        System.arraycopy(a, index + 1, a, index, n - index - 1);
        size = n - 1;
        return old;
    }

    /**
     * Removes and returns the last element.
     *
     * @return the removed element
     * @throws NoSuchElementException if the list is empty
     */
    public KType removeLast() {
        if (size == 0) {
            throw new NoSuchElementException("list is empty");
        }
        return buffer[--size];
    }

    /**
     * Removes the elements at the indexes {@code from .. to}.
     *
     * @param from the first index, inclusive
     * @param to the last index, exclusive
     * @throws IndexOutOfBoundsException if the range is not within {@code [0, size()]}
     */
    public void removeRange(int from, int to) {
        int n = size;
        Objects.checkFromToIndex(from, to, n);
        final KType[] a = buffer;
        System.arraycopy(a, to, a, from, n - to);
        size = n - (to - from);
    }

    /**
     * Removes the first element equal to {@code value}.
     *
     * @param value the value
     * @return {@code true} if an element was removed
     */
    public boolean removeValue(KType value) {
        int i = indexOf(value);
        if (i < 0) {
            return false;
        }
        removeAt(i);
        return true;
    }

    /**
     * Removes all elements for which {@code predicate} returns {@code true}, in one pass; the
     * other elements keep their order. If {@code predicate} throws, the elements it was not called for
     * stay in the list.
     *
     * @param predicate the condition for removal
     * @return {@code true} if an element was removed
     */
    public boolean removeIf(KTypePredicate predicate) {
        final KType[] a = buffer;
        final int n = size;
        int w = 0;
        int r = 0;
        try {
            for (; r < n; r++) {
                KType v = a[r];
                if (!predicate.test(v)) {
                    a[w++] = v;
                }
            }
        } finally {
            if (r < n) {
                System.arraycopy(a, r, a, w, n - r);
                w += n - r;
            }
            size = w;
        }
        return w != n;
    }

    /**
     * Replaces every element with the result of {@code operator} on it.
     *
     * @param operator the function to apply
     */
    public void replaceAll(KTypeUnaryOperator operator) {
        final KType[] a = buffer;
        for (int i = 0, n = size; i < n; i++) {
            a[i] = operator.applyAsKType(a[i]);
        }
    }

    /**
     * Sets every element to {@code value}.
     *
     * @param value the value
     */
    public void fill(KType value) {
        Arrays.fill(buffer, 0, size, value);
    }

    /**
     * Sorts the elements in ascending order.
//#if K.floating
     * As in {@link Arrays#sort(double[])}, {@code -0.0} comes before {@code 0.0} and
     * {@code NaN} last.
//#end
     */
    public void sort() {
        Arrays.sort(buffer, 0, size);
    }

    /**
     * Sorts the elements at the indexes {@code from .. to} in ascending order.
     *
     * @param from the first index, inclusive
     * @param to the last index, exclusive
     * @throws IndexOutOfBoundsException if the range is not within {@code [0, size()]}
     */
    public void sort(int from, int to) {
        Objects.checkFromToIndex(from, to, size);
        Arrays.sort(buffer, from, to);
    }

    /** Reverses the order of the elements. */
    public void reverse() {
        final KType[] a = buffer;
        for (int i = 0, j = size - 1; i < j; i++, j--) {
            KType t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    /** Removes all elements; the capacity stays. */
    public void clear() {
        size = 0;
    }

    /**
     * Removes the elements from {@code newSize} on.
     *
     * @param newSize the new number of elements
     * @throws IndexOutOfBoundsException if {@code newSize} is not in {@code [0, size()]}
     */
    public void truncate(int newSize) {
        Objects.checkIndex(newSize, size + 1);
        size = newSize;
    }

    /**
     * Grows the array, if needed, to hold {@code expectedSize} elements without growing again.
     *
     * @param expectedSize the number of elements
     */
    public void ensureCapacity(int expectedSize) {
        if (expectedSize > buffer.length) {
            if (expectedSize > MAX_CAPACITY) {
                throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
            }
            buffer = Arrays.copyOf(buffer, expectedSize);
        }
    }

    /** Shrinks the array to the number of elements. */
    public void trimToSize() {
        if (buffer.length != size) {
            buffer = size == 0 ? EMPTY : Arrays.copyOf(buffer, size);
        }
    }

    // ------------------------------------------------------------------
    // Iteration and copies
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for every element, in order.
     *
     * @param action the action
     */
    public void forEach(KTypeConsumer action) {
        final KType[] a = buffer;
        for (int i = 0, n = size; i < n; i++) {
            action.accept(a[i]);
        }
    }

    /**
     * A sequential stream of the elements, in order. The list must not change while the stream
     * runs.
     *
     * @return a stream of the elements
     */
    public KTypeStream stream() {
        return Arrays.stream(buffer, 0, size);
    }

    /**
     * The elements, in order.
     *
     * @return a new array with the elements
     */
    public KType[] toArray() {
        return Arrays.copyOf(buffer, size);
    }

    /**
     * The elements at the indexes {@code from .. to}, in order.
     *
     * @param from the first index, inclusive
     * @param to the last index, exclusive
     * @return a new array with the elements
     * @throws IndexOutOfBoundsException if the range is not within {@code [0, size()]}
     */
    public KType[] toArray(int from, int to) {
        Objects.checkFromToIndex(from, to, size);
        return Arrays.copyOfRange(buffer, from, to);
    }

    /**
     * The array of this list, not a copy: the elements are at positions {@code 0 .. size()}.
     * Writes to these positions change the list. The array is valid until the list grows or
     * shrinks its array ({@link #add(KType)}, {@link #trimToSize()}, and so on); positions from
     * {@code size()} on are unspecified.
     *
     * @return the array of this list
     */
    public KType[] elements() {
        return buffer;
    }

    /**
     * The elements as a new modifiable {@link List}, in order.
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
        return o == this || o instanceof KTypeArrayList other
                && Arrays.equals(buffer, 0, size, other.buffer, 0, other.size);
    }

    /** Same formula as {@link List#hashCode()} on the boxed elements. */
    @Override
    public int hashCode() {
        final KType[] a = buffer;
        int h = 1;
        for (int i = 0, n = size; i < n; i++) {
            h = 31 * h + KBoxed.hashCode(a[i]);
        }
        return h;
    }

    /** The elements in order, as {@code [a, b, c]}. */
    @Override
    public String toString() {
        final KType[] a = buffer;
        StringBuilder sb = new StringBuilder(Math.min(size, 1 << 16) * 8 + 2).append('[');
        for (int i = 0, n = size; i < n; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
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

    /** Grows the array to at least {@code minCapacity}, by half its length or more. */
    private void grow(long minCapacity) {
        if (minCapacity > MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        int len = buffer.length;
        long cap = Math.max(minCapacity, Math.max(len + (len >> 1), MIN_GROWTH));
        buffer = Arrays.copyOf(buffer, (int) Math.min(MAX_CAPACITY, cap));
    }
}
