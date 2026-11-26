// Generated from template api/KTypeVTypeSortedMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.TreeMap;

import net.generated.example.function.IntDoubleConsumer;

/**
 * A {@link IntDoubleMap} whose entries are ordered by key.
 * <p>
 * Implementation: {@link IntDoubleTreeMap} (B+ tree).
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
 * for example to include {@code Integer.MAX_VALUE}. A range with {@code from > to} is rejected
 * with an {@link IllegalArgumentException}, as in {@link NavigableMap#subMap}.
 * <p>
 * <b>Empty maps and keys without a match:</b> no key and no value is reserved, so the methods
 * follow three rules.
 * <ul>
 *   <li>An index handle is {@code -1} ({@link #firstIndex}, {@link #lastIndex},
 *       {@link #floorIndex}, ...). The handles are the exact form and need no default.</li>
 *   <li>A key is the default passed by the caller ({@link #floorKey}, {@link #ceilingKey},
 *       {@link #lowerKey}, {@link #higherKey}); {@link #firstKey}, {@link #lastKey},
 *       {@link #pollFirst()}, and {@link #pollLast()} throw a {@link NoSuchElementException}
 *       for an empty map, and {@link #pollFirst(IntDoubleConsumer)} and
 *       {@link #pollLast(IntDoubleConsumer)} return {@code false} instead and also deliver the
 *       key.</li>
 *   <li>A value is the default passed by the caller ({@link #floorValue},
 *       {@link #ceilingValue}, {@link #lowerValue}, {@link #higherValue}).</li>
 * </ul>
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 */
public interface IntDoubleSortedMap extends IntDoubleMap {

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
    int floorIndex(int key);

    /**
     * Handle of the entry with the least key {@code >= key}, or {@code -1} if there is none.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int ceilingIndex(int key);

    /**
     * Handle of the entry with the greatest key {@code < key}, or {@code -1} if there is none.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int lowerIndex(int key);

    /**
     * Handle of the entry with the least key {@code > key}, or {@code -1} if there is none.
     *
     * @param key the key
     * @return the handle, or {@code -1}
     */
    int higherIndex(int key);

    /**
     * Handle of the entry with the greatest key, or {@code -1} if the map is empty. Together
     * with {@link #prevIndex}, this iterates in descending key order:
     * <pre>{@code
     * for (int i = map.lastIndex(); i >= 0; i = map.prevIndex(i)) {
     *     int key = map.indexKey(i);
     *     double value = map.indexGet(i);
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
    default void forEachInRange(int from, int to, IntDoubleConsumer action) {
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
    void forEachInRange(int from, boolean fromInclusive, int to, boolean toInclusive,
            IntDoubleConsumer action);

    /**
     * Removes all entries with a key in {@code [from, to)}. Returns the number removed.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return the number of removed entries
     * @throws IllegalArgumentException if {@code from > to}
     */
    default int removeRange(int from, int to) {
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
    int removeRange(int from, boolean fromInclusive, int to, boolean toInclusive);

    /**
     * Number of entries with a key in {@code [from, to)}.
     *
     * @param from the lower bound (inclusive)
     * @param to the upper bound (exclusive)
     * @return the number of entries in the range
     * @throws IllegalArgumentException if {@code from > to}
     */
    default int countRange(int from, int to) {
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
    int countRange(int from, boolean fromInclusive, int to, boolean toInclusive);

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
    default IntDoubleSortedMap subMapCopy(int from, int to) {
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
    IntDoubleSortedMap subMapCopy(int from, boolean fromInclusive, int to, boolean toInclusive);

    /**
     * A new {@link TreeMap} with the mappings of this map, keys and values boxed, in key order;
     * the same as {@link #toNavigableMap}.
     *
     * @return a new {@link TreeMap}
     */
    @Override
    default Map<Integer, Double> toMap() {
        return toNavigableMap();
    }

    /**
     * A new {@link TreeMap} with the mappings of this map, keys and values boxed.
     *
     * @return a new {@link TreeMap}
     */
    default NavigableMap<Integer, Double> toNavigableMap() {
        NavigableMap<Integer, Double> map = new TreeMap<>();
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
    default int firstKey() {
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
    default int lastKey() {
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
    default int floorKey(int key, int defaultKey) {
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
    default int ceilingKey(int key, int defaultKey) {
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
    default int lowerKey(int key, int defaultKey) {
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
    default int higherKey(int key, int defaultKey) {
        int i = higherIndex(key);
        return i < 0 ? defaultKey : indexKey(i);
    }

    /**
     * The value of the greatest key {@code <= key}, or {@code defaultValue} if there is none.
     *
     * @param key the key
     * @param defaultValue the value to return if there is none
     * @return the value, or {@code defaultValue}
     */
    default double floorValue(int key, double defaultValue) {
        int i = floorIndex(key);
        return i < 0 ? defaultValue : indexGet(i);
    }

    /**
     * The value of the least key {@code >= key}, or {@code defaultValue} if there is none.
     *
     * @param key the key
     * @param defaultValue the value to return if there is none
     * @return the value, or {@code defaultValue}
     */
    default double ceilingValue(int key, double defaultValue) {
        int i = ceilingIndex(key);
        return i < 0 ? defaultValue : indexGet(i);
    }

    /**
     * The value of the greatest key {@code < key}, or {@code defaultValue} if there is none.
     *
     * @param key the key
     * @param defaultValue the value to return if there is none
     * @return the value, or {@code defaultValue}
     */
    default double lowerValue(int key, double defaultValue) {
        int i = lowerIndex(key);
        return i < 0 ? defaultValue : indexGet(i);
    }

    /**
     * The value of the least key {@code > key}, or {@code defaultValue} if there is none.
     *
     * @param key the key
     * @param defaultValue the value to return if there is none
     * @return the value, or {@code defaultValue}
     */
    default double higherValue(int key, double defaultValue) {
        int i = higherIndex(key);
        return i < 0 ? defaultValue : indexGet(i);
    }

    /**
     * Removes the entry with the least key and returns its value.
     * {@link #pollFirst(IntDoubleConsumer)} returns {@code false} for an empty map instead and also
     * delivers the key.
     *
     * @return the removed value
     * @throws NoSuchElementException if the map is empty
     */
    default double pollFirst() {
        int i = firstIndex();
        if (i < 0) {
            throw new NoSuchElementException();
        }
        return indexRemove(i);
    }

    /**
     * Removes the entry with the greatest key and returns its value.
     * {@link #pollLast(IntDoubleConsumer)} returns {@code false} for an empty map instead and also
     * delivers the key.
     *
     * @return the removed value
     * @throws NoSuchElementException if the map is empty
     */
    default double pollLast() {
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
    default boolean pollFirst(IntDoubleConsumer action) {
        int i = firstIndex();
        if (i < 0) {
            return false;
        }
        int key = indexKey(i);
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
    default boolean pollLast(IntDoubleConsumer action) {
        int i = lastIndex();
        if (i < 0) {
            return false;
        }
        int key = indexKey(i);
        action.accept(key, indexRemove(i));
        return true;
    }
}
