// Generated from template api/KTypeSet.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;
import java.util.stream.LongStream;

/**
 * A set of {@code long} elements without boxing.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link LongHashSet}: linear probing, the general-purpose default. With load factor
 *       {@code 0.5}, the fastest choice if memory allows.</li>
 *   <li>{@link LongCompactHashSet}: tagged blocks of one cache line, for high load factors
 *       and many lookups of absent elements.</li>
 *   <li>{@link LongLinkedHashSet}: iterates in insertion order.</li>
 *   <li>{@link LongTreeSet}: a B+ tree, iterates in ascending order (see {@link LongSortedSet}).</li>
 * </ul>
 * Every value can be stored, including {@code 0}.
 * <p>
 * Two sets are {@linkplain Object#equals equal} if they contain the same elements, whatever
 * their implementations. The {@linkplain Object#hashCode hash code} is the one of a
 * {@link java.util.Set} with the boxed elements.
 * <p>
 * Implementations are not thread-safe. Iteration order is unspecified unless the
 * implementation specifies it: the plain hash sets iterate in an order that differs between
 * instances, {@link LongLinkedHashSet} in insertion order, {@link LongTreeSet} in ascending
 * order.
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 */
public interface LongSet {

    /**
     * Number of elements.
     *
     * @return the number of elements
     */
    int size();

    /**
     * Whether the set has no elements.
     *
     * @return {@code true} if the set is empty
     */
    boolean isEmpty();

    /**
     * Whether {@code key} is an element.
     *
     * @param key the element
     * @return {@code true} if it is an element
     */
    boolean contains(long key);

    /**
     * Adds {@code key}. Returns {@code true} if it was absent.
     *
     * @param key the element
     * @return {@code true} if it was absent
     */
    boolean add(long key);

    /**
     * Removes {@code key}. Returns {@code true} if it was present.
     *
     * @param key the element
     * @return {@code true} if it was present
     */
    boolean remove(long key);

    /** Removes all elements. The capacity stays unchanged. */
    void clear();

    /**
     * Makes room for {@code expectedSize} elements without further resizing.
     *
     * @param expectedSize the number of elements
     * @throws IllegalArgumentException if {@code expectedSize} is more than the set can hold, or
     *         negative for a sorted set
     */
    void ensureCapacity(int expectedSize);

    /**
     * Calls {@code action} for each element. The set must not be modified during the call.
     *
     * @param action the action to call with each element
     */
    void forEach(LongConsumer action);

    /**
     * The elements in a new array, in iteration order.
     *
     * @return a new array with the elements
     */
    long[] toArray();

    /**
     * A sequential stream of the elements, in iteration order. The stream reads a copy (see
     * {@link #toArray}), so later changes to the set do not affect it.
     *
     * @return a stream of the elements
     */
    default LongStream stream() {
        return Arrays.stream(toArray());
    }

    /**
     * Removes all elements for which {@code predicate} returns {@code true}. Returns
     * {@code true} if any element was removed. The predicate must not modify the set.
     *
     * @param predicate the test for each element
     * @return {@code true} if any element was removed
     */
    boolean removeIf(LongPredicate predicate);

    // ------------------------------------------------------------------
    // Iteration without allocation
    // ------------------------------------------------------------------

    /**
     * Handle of the first element in iteration order, or {@code -1} if the set is empty.
     * Together with {@link #nextIndex} and {@link #indexKey}, this iterates without allocation and
     * can stop at any point:
     * <pre>{@code
     * for (int i = set.firstIndex(); i >= 0; i = set.nextIndex(i)) {
     *     long key = set.indexKey(i);
     * }
     * }</pre>
     * Any modification of the set invalidates the handles.
     *
     * @return the handle of the first element, or {@code -1}
     */
    int firstIndex();

    /**
     * Handle of the element after {@code index} in iteration order, or {@code -1} if there is none.
     *
     * @param index an existing handle
     * @return the handle of the next element, or {@code -1}
     */
    int nextIndex(int index);

    /**
     * The element at a handle obtained from {@link #firstIndex} or {@link #nextIndex}.
     *
     * @param index an existing handle
     * @return the element
     */
    long indexKey(int index);

    // ------------------------------------------------------------------
    // Bulk operations and conversions
    // ------------------------------------------------------------------

    /**
     * Adds all elements of {@code other}. Returns {@code true} if this set changed.
     *
     * @param other the set to add from (any implementation)
     * @return {@code true} if this set changed
     */
    default boolean addAll(LongSet other) {
        ensureCapacity(Math.max(size(), other.size()));
        int before = size();
        other.forEach(this::add);
        return size() != before;
    }

    /**
     * Adds all elements of {@code elements}. Returns {@code true} if this set changed.
     *
     * @param elements the elements to add
     * @return {@code true} if this set changed
     * @throws NullPointerException for a {@code null} element
     */
    default boolean addAll(Collection<? extends Long> elements) {
        ensureCapacity(Math.max(size(), elements.size()));
        int before = size();
        for (Long e : elements) {
            add(e);
        }
        return size() != before;
    }

    /**
     * A new modifiable set with the elements of this set, boxed: a {@link HashSet}, or, for a
     * set with a defined order (insertion-ordered or sorted), a set with the same iteration
     * order.
     *
     * @return a new set
     */
    default Set<Long> toSet() {
        Set<Long> set = HashSet.newHashSet(size());
        forEach(set::add);
        return set;
    }
}
