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

import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.KTypeConsumer;

/**
 * A {@link KTypeSet} whose elements are ordered.
 * <p>
 * Implementation: {@link KTypeTreeSet} (B+ tree).
 * <p>
 * Iteration with {@link #firstIndex}/{@link #nextIndex}, {@link #forEach} and {@link #toArray}
 * visits the elements in ascending order, {@link #lastIndex}/{@link #prevIndex} in descending
 * order.
 * <p>
 * Unlike {@link java.util.SortedSet}, this interface has no live views ({@code subSet},
 * {@code headSet}, {@code tailSet}, {@code descendingSet}). Their common uses are covered by
 * direct methods:
 * <ul>
 *   <li>navigation through index handles ({@link #floorIndex}, {@link #ceilingIndex},
 *       {@link #lowerIndex}, {@link #higherIndex}), without allocation;</li>
 *   <li>range operations ({@link #forEachInRange}, {@link #removeRange},
 *       {@link #countRange});</li>
 *   <li>copies ({@link #subSetCopy}, {@link #toNavigableSet}).</li>
 * </ul>
 * Ranges are given as {@code from}/{@code to} elements. The two-element forms are half-open,
 * {@code [from, to)}, as in {@link java.util.SortedSet#subSet}; the forms with
 * {@code fromInclusive}/{@code toInclusive} flags select any of the four bound combinations,
 * for example to include {@code KBoxed.MAX_VALUE}. A range with {@code from > to} is rejected
 * with an {@link IllegalArgumentException}, as in {@link NavigableSet#subSet}.
 * <p>
 * <b>Empty sets and elements without a match:</b> no element value is reserved, so the methods
 * follow two rules.
 * <ul>
 *   <li>An index handle is {@code -1} ({@link #firstIndex}, {@link #lastIndex},
 *       {@link #floorIndex}, ...). The handles are the exact form and need no default.</li>
 *   <li>An element is the default passed by the caller ({@link #floor}, {@link #ceiling},
 *       {@link #lower}, {@link #higher}); {@link #first}, {@link #last}, {@link #pollFirst} and
 *       {@link #pollLast} throw a {@link NoSuchElementException} for an empty set.</li>
 * </ul>
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 */
public interface KTypeSortedSet extends KTypeSet {

    // ------------------------------------------------------------------
    // Navigation through index handles
    // ------------------------------------------------------------------

    /**
     * Handle of the greatest element {@code <= key}, or {@code -1} if there is none. Like all
     * handles, it stays valid only until the next structural modification.
     *
     * @param key the element to search from
     * @return the handle, or {@code -1}
     */
    int floorIndex(KType key);

    /**
     * Handle of the least element {@code >= key}, or {@code -1} if there is none.
     *
     * @param key the element to search from
     * @return the handle, or {@code -1}
     */
    int ceilingIndex(KType key);

    /**
     * Handle of the greatest element {@code < key}, or {@code -1} if there is none.
     *
     * @param key the element to search from
     * @return the handle, or {@code -1}
     */
    int lowerIndex(KType key);

    /**
     * Handle of the least element {@code > key}, or {@code -1} if there is none.
     *
     * @param key the element to search from
     * @return the handle, or {@code -1}
     */
    int higherIndex(KType key);

    /**
     * Handle of the greatest element, or {@code -1} if the set is empty. Together with
     * {@link #prevIndex}, this iterates in descending order:
     * <pre>{@code
     * for (int i = set.lastIndex(); i >= 0; i = set.prevIndex(i)) {
     *     KType key = set.indexKey(i);
     * }
     * }</pre>
     *
     * @return the handle of the last element, or {@code -1}
     */
    int lastIndex();

    /**
     * Handle of the element before {@code index} in ascending order, or {@code -1} if there is none.
     *
     * @param index an existing handle
     * @return the handle of the previous element, or {@code -1}
     */
    int prevIndex(int index);

    // ------------------------------------------------------------------
    // Range operations
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for each element in {@code [from, to)}, in ascending order. The set
     * must not be modified during the call.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @param action the action to call with each element
     * @throws IllegalArgumentException if {@code from > to}
     */
    default void forEachInRange(KType from, KType to, KTypeConsumer action) {
        forEachInRange(from, true, to, false, action);
    }

    /**
     * Calls {@code action} for each element between {@code from} and {@code to}, in ascending
     * order. The set must not be modified during the call.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @param action the action to call with each element
     * @throws IllegalArgumentException if {@code from > to}
     */
    void forEachInRange(KType from, boolean fromInclusive, KType to, boolean toInclusive, KTypeConsumer action);

    /**
     * Removes all elements in {@code [from, to)}. Returns the number removed.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return the number of removed elements
     * @throws IllegalArgumentException if {@code from > to}
     */
    default int removeRange(KType from, KType to) {
        return removeRange(from, true, to, false);
    }

    /**
     * Removes all elements between {@code from} and {@code to}. Returns the number removed.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @return the number of removed elements
     * @throws IllegalArgumentException if {@code from > to}
     */
    int removeRange(KType from, boolean fromInclusive, KType to, boolean toInclusive);

    /**
     * Number of elements in {@code [from, to)}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return the number of elements in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    default int countRange(KType from, KType to) {
        return countRange(from, true, to, false);
    }

    /**
     * Number of elements between {@code from} and {@code to}.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @return the number of elements in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    int countRange(KType from, boolean fromInclusive, KType to, boolean toInclusive);

    // ------------------------------------------------------------------
    // Copies
    // ------------------------------------------------------------------

    /**
     * A new set with the elements in {@code [from, to)}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return a new set with the elements in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    default KTypeSortedSet subSetCopy(KType from, KType to) {
        return subSetCopy(from, true, to, false);
    }

    /**
     * A new set with the elements between {@code from} and {@code to}.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @return a new set with the elements in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    KTypeSortedSet subSetCopy(KType from, boolean fromInclusive, KType to, boolean toInclusive);

    /**
     * A new {@link TreeSet} with the elements of this set, boxed, in ascending order; the same
     * as {@link #toNavigableSet}.
     *
     * @return a new {@link TreeSet}
     */
    @Override
    default Set<KBoxed> toSet() {
        return toNavigableSet();
    }

    /**
     * A new {@link TreeSet} with the elements of this set, boxed.
     *
     * @return a new {@link TreeSet}
     */
    default NavigableSet<KBoxed> toNavigableSet() {
        NavigableSet<KBoxed> set = new TreeSet<>();
        forEach(set::add);
        return set;
    }

    // ------------------------------------------------------------------
    // Convenience methods
    // ------------------------------------------------------------------

    /**
     * The least element.
     *
     * @return the least element
     * @throws NoSuchElementException if the set is empty
     */
    default KType first() {
        int i = firstIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexKey(i);
    }

    /**
     * The greatest element.
     *
     * @return the greatest element
     * @throws NoSuchElementException if the set is empty
     */
    default KType last() {
        int i = lastIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexKey(i);
    }

    /**
     * The greatest element {@code <= key}, or {@code defaultElement} if there is none.
     *
     * @param key the element to search from
     * @param defaultElement the element to return if there is none
     * @return the greatest element {@code <= key}, or {@code defaultElement}
     */
    default KType floor(KType key, KType defaultElement) {
        int i = floorIndex(key);
        return i < 0 ? defaultElement : indexKey(i);
    }

    /**
     * The least element {@code >= key}, or {@code defaultElement} if there is none.
     *
     * @param key the element to search from
     * @param defaultElement the element to return if there is none
     * @return the least element {@code >= key}, or {@code defaultElement}
     */
    default KType ceiling(KType key, KType defaultElement) {
        int i = ceilingIndex(key);
        return i < 0 ? defaultElement : indexKey(i);
    }

    /**
     * The greatest element {@code < key}, or {@code defaultElement} if there is none.
     *
     * @param key the element to search from
     * @param defaultElement the element to return if there is none
     * @return the greatest element {@code < key}, or {@code defaultElement}
     */
    default KType lower(KType key, KType defaultElement) {
        int i = lowerIndex(key);
        return i < 0 ? defaultElement : indexKey(i);
    }

    /**
     * The least element {@code > key}, or {@code defaultElement} if there is none.
     *
     * @param key the element to search from
     * @param defaultElement the element to return if there is none
     * @return the least element {@code > key}, or {@code defaultElement}
     */
    default KType higher(KType key, KType defaultElement) {
        int i = higherIndex(key);
        return i < 0 ? defaultElement : indexKey(i);
    }

    /**
     * Removes and returns the least element.
     *
     * @return the removed element
     * @throws NoSuchElementException if the set is empty
     */
    KType pollFirst();

    /**
     * Removes and returns the greatest element.
     *
     * @return the removed element
     * @throws NoSuchElementException if the set is empty
     */
    KType pollLast();
}
