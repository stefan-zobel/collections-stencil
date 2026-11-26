// Generated from template api/KTypeObjectSortedMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import net.generated.example.function.LongObjectConsumer;

/**
 * A {@link LongObjectMap} whose entries are ordered by key.
 * <p>
 * Implementation: {@link LongObjectTreeMap} (B+ tree).
 * <p>
 * Iteration with {@link #firstIndex}/{@link #nextIndex} and {@link #forEach} visits the
 * entries in ascending key order, {@link #lastIndex}/{@link #prevIndex} in descending order.
 * <p>
 * Unlike {@link java.util.SortedMap}, this interface has no live views ({@code subMap},
 * {@code headMap}, {@code tailMap}, {@code descendingMap}). Their common uses are covered by
 * direct methods:
 * <ul>
 *   <li>navigation through index handles ({@link #floorIndex}, {@link #ceilingIndex},
 *       {@link #lowerIndex}, {@link #higherIndex}), without allocation;</li>
 *   <li>range operations ({@link #forEachInRange}, {@link #removeRange},
 *       {@link #countRange});</li>
 *   <li>copies ({@link #subMapCopy}, {@link #toNavigableMap}).</li>
 * </ul>
 * Ranges are given as {@code from}/{@code to} keys. The two-key forms are half-open,
 * {@code [from, to)}, as in {@link java.util.SortedMap#subMap}; the forms with
 * {@code fromInclusive}/{@code toInclusive} flags select any of the four bound combinations,
 * for example to include {@code Long.MAX_VALUE}. A range with {@code from > to} is rejected
 * with an {@link IllegalArgumentException}, as in {@link NavigableMap#subMap}.
 * <p>
 * Values may be {@code null}, as in every {@link LongObjectMap}. {@link #firstKey},
 * {@link #lastKey}, {@link #pollFirst()}, and {@link #pollLast()} throw a
 * {@link NoSuchElementException} for an empty map; {@link #pollFirst(LongObjectConsumer)} and
 * {@link #pollLast(LongObjectConsumer)} return {@code false} instead and also deliver the key.
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 *
 * @param <V> the type of the values
 */
public interface LongObjectSortedMap<V> extends LongObjectMap<V> {

    // ------------------------------------------------------------------
    // Navigation through index handles
    // ------------------------------------------------------------------

    /**
     * Handle of the entry with the greatest key {@code <= key}, or {@code -1} if there is none.
     * Like all handles, it stays valid only until the next structural modification.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int floorIndex(long key);

    /**
     * Handle of the entry with the least key {@code >= key}, or {@code -1} if there is none.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int ceilingIndex(long key);

    /**
     * Handle of the entry with the greatest key {@code < key}, or {@code -1} if there is none.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int lowerIndex(long key);

    /**
     * Handle of the entry with the least key {@code > key}, or {@code -1} if there is none.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int higherIndex(long key);

    /**
     * Handle of the entry with the greatest key, or {@code -1} if the map is empty. Together
     * with {@link #prevIndex}, this iterates in descending key order:
     * <pre>{@code
     * for (int i = map.lastIndex(); i >= 0; i = map.prevIndex(i)) {
     *     long key = map.indexKey(i);
     *     V value = map.indexGet(i);
     * }
     * }</pre>
     *
     * @return the handle of the last entry, or {@code -1}
     */
    int lastIndex();

    /**
     * Handle of the entry before {@code index} in key order, or {@code -1} if there is none.
     *
     * @param index an existing handle
     * @return the handle of the previous entry, or {@code -1}
     */
    int prevIndex(int index);

    // ------------------------------------------------------------------
    // Range operations
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for each entry with a key in {@code [from, to)}, in ascending key
     * order. The map must not be modified during the call.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @param action the action to call with each key and value
     * @throws IllegalArgumentException if {@code from > to}
     */
    default void forEachInRange(long from, long to, LongObjectConsumer<? super V> action) {
        forEachInRange(from, true, to, false, action);
    }

    /**
     * Calls {@code action} for each entry with a key between {@code from} and {@code to}, in
     * ascending key order. The map must not be modified during the call.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @param action the action to call with each key and value
     * @throws IllegalArgumentException if {@code from > to}
     */
    void forEachInRange(long from, boolean fromInclusive, long to, boolean toInclusive,
            LongObjectConsumer<? super V> action);

    /**
     * Removes all entries with a key in {@code [from, to)}. Returns the number removed.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return the number of removed entries
     * @throws IllegalArgumentException if {@code from > to}
     */
    default int removeRange(long from, long to) {
        return removeRange(from, true, to, false);
    }

    /**
     * Removes all entries with a key between {@code from} and {@code to}. Returns the number removed.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @return the number of removed entries
     * @throws IllegalArgumentException if {@code from > to}
     */
    int removeRange(long from, boolean fromInclusive, long to, boolean toInclusive);

    /**
     * Number of entries with a key in {@code [from, to)}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return the number of entries in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    default int countRange(long from, long to) {
        return countRange(from, true, to, false);
    }

    /**
     * Number of entries with a key between {@code from} and {@code to}.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @return the number of entries in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    int countRange(long from, boolean fromInclusive, long to, boolean toInclusive);

    // ------------------------------------------------------------------
    // Copies
    // ------------------------------------------------------------------

    /**
     * A new map with the entries whose keys are in {@code [from, to)}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return a new map with the entries in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    default LongObjectSortedMap<V> subMapCopy(long from, long to) {
        return subMapCopy(from, true, to, false);
    }

    /**
     * A new map with the entries whose keys are between {@code from} and {@code to}.
     *
     * @param from the lower bound
     * @param fromInclusive whether {@code from} is part of the range
     * @param to the upper bound
     * @param toInclusive whether {@code to} is part of the range
     * @return a new map with the entries in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    LongObjectSortedMap<V> subMapCopy(long from, boolean fromInclusive, long to, boolean toInclusive);

    /**
     * A new {@link TreeMap} with the mappings of this map, keys boxed, in key order; the same as
     * {@link #toNavigableMap}.
     *
     * @return a new {@link TreeMap}
     */
    @Override
    default Map<Long, V> toMap() {
        return toNavigableMap();
    }

    /**
     * A new {@link TreeMap} with the mappings of this map, keys boxed.
     *
     * @return a new {@link TreeMap}
     */
    default NavigableMap<Long, V> toNavigableMap() {
        NavigableMap<Long, V> map = new TreeMap<>();
        forEach(map::put);
        return map;
    }

    // ------------------------------------------------------------------
    // Convenience methods
    // ------------------------------------------------------------------

    /**
     * The least key.
     *
     * @return the least key
     * @throws NoSuchElementException if the map is empty
     */
    default long firstKey() {
        int i = firstIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexKey(i);
    }

    /**
     * The greatest key.
     *
     * @return the greatest key
     * @throws NoSuchElementException if the map is empty
     */
    default long lastKey() {
        int i = lastIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexKey(i);
    }

    /**
     * The greatest key {@code <= key}, or {@code defaultKey} if there is none.
     *
     * @param key the key
     * @param defaultKey the key to return if there is none
     * @return the greatest key {@code <= key}, or {@code defaultKey}
     */
    default long floorKey(long key, long defaultKey) {
        int i = floorIndex(key);
        return i < 0 ? defaultKey : indexKey(i);
    }

    /**
     * The least key {@code >= key}, or {@code defaultKey} if there is none.
     *
     * @param key the key
     * @param defaultKey the key to return if there is none
     * @return the least key {@code >= key}, or {@code defaultKey}
     */
    default long ceilingKey(long key, long defaultKey) {
        int i = ceilingIndex(key);
        return i < 0 ? defaultKey : indexKey(i);
    }

    /**
     * The greatest key {@code < key}, or {@code defaultKey} if there is none.
     *
     * @param key the key
     * @param defaultKey the key to return if there is none
     * @return the greatest key {@code < key}, or {@code defaultKey}
     */
    default long lowerKey(long key, long defaultKey) {
        int i = lowerIndex(key);
        return i < 0 ? defaultKey : indexKey(i);
    }

    /**
     * The least key {@code > key}, or {@code defaultKey} if there is none.
     *
     * @param key the key
     * @param defaultKey the key to return if there is none
     * @return the least key {@code > key}, or {@code defaultKey}
     */
    default long higherKey(long key, long defaultKey) {
        int i = higherIndex(key);
        return i < 0 ? defaultKey : indexKey(i);
    }

    /**
     * The value of the greatest key {@code <= key}, or {@code null} if there is none.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    default V floorValue(long key) {
        int i = floorIndex(key);
        return i < 0 ? null : indexGet(i);
    }

    /**
     * The value of the least key {@code >= key}, or {@code null} if there is none.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    default V ceilingValue(long key) {
        int i = ceilingIndex(key);
        return i < 0 ? null : indexGet(i);
    }

    /**
     * The value of the greatest key {@code < key}, or {@code null} if there is none.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    default V lowerValue(long key) {
        int i = lowerIndex(key);
        return i < 0 ? null : indexGet(i);
    }

    /**
     * The value of the least key {@code > key}, or {@code null} if there is none.
     *
     * @param key the key
     * @return the value, or {@code null}
     */
    default V higherValue(long key) {
        int i = higherIndex(key);
        return i < 0 ? null : indexGet(i);
    }

    /**
     * Removes the entry with the least key and returns its value.
     * {@link #pollFirst(LongObjectConsumer)} returns {@code false} for an empty map instead and also
     * delivers the key.
     *
     * @return the removed value, possibly {@code null}
     * @throws NoSuchElementException if the map is empty
     */
    default V pollFirst() {
        int i = firstIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexRemove(i);
    }

    /**
     * Removes the entry with the greatest key and returns its value.
     * {@link #pollLast(LongObjectConsumer)} returns {@code false} for an empty map instead and also
     * delivers the key.
     *
     * @return the removed value, possibly {@code null}
     * @throws NoSuchElementException if the map is empty
     */
    default V pollLast() {
        int i = lastIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexRemove(i);
    }

    /**
     * Removes the entry with the least key and passes its key and value to {@code action}.
     * Returns {@code false} if the map is empty.
     *
     * @param action receives the key and value of the removed entry
     * @return {@code false} if the map is empty
     */
    default boolean pollFirst(LongObjectConsumer<? super V> action) {
        int i = firstIndex();
        if (i < 0) {
            return false;
        }
        long key = indexKey(i);
        action.accept(key, indexRemove(i));
        return true;
    }

    /**
     * Removes the entry with the greatest key and passes its key and value to {@code action}.
     * Returns {@code false} if the map is empty.
     *
     * @param action receives the key and value of the removed entry
     * @return {@code false} if the map is empty
     */
    default boolean pollLast(LongObjectConsumer<? super V> action) {
        int i = lastIndex();
        if (i < 0) {
            return false;
        }
        long key = indexKey(i);
        action.accept(key, indexRemove(i));
        return true;
    }
}
