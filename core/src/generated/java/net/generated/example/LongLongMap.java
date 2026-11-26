// Generated from template api/KTypeVTypeMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.HashMap;
import java.util.Map;

import net.generated.example.function.LongLongConsumer;
import net.generated.example.function.LongLongPredicate;

/**
 * A map from {@code long} keys to {@code long} values without boxing.
 * <p>
 * Implementations:
 * <ul>
 *   <li>{@link LongLongHashMap}: linear probing, the general-purpose default.</li>
 *   <li>{@link LongLongCompactHashMap}: tagged blocks, for high load factors and many
 *       lookups of absent keys.</li>
 *   <li>{@link LongLongLinkedHashMap}: iterates in insertion order.</li>
 *   <li>{@link LongLongTreeMap}: a sorted map, see {@link LongLongSortedMap}.</li>
 * </ul>
 * Every key can be stored, including {@code 0}. Methods that return a value return
 * {@code 0L} if there is no mapping. Use {@link #containsKey}, {@link #getOrDefault} or
 * the index handles to tell an absent key from a key that is mapped to {@code 0L}.
 * <p>
 * Two maps are {@linkplain Object#equals equal} if they contain the same mappings, whatever
 * their implementations. The {@linkplain Object#hashCode hash code} is the one of a
 * {@link java.util.Map} with the boxed entries.
 * <p>
 * Implementations are not thread-safe. Iteration order is unspecified unless the
 * implementation specifies it: the plain hash maps iterate in an order that differs between
 * instances, {@link LongLongLinkedHashMap} in insertion order, {@link LongLongTreeMap} in
 * ascending key order.
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 */
public interface LongLongMap {

    /**
     * Number of mappings.
     *
     * @return the number of mappings
     */
    int size();

    /**
     * Whether the map has no mappings.
     *
     * @return {@code true} if the map is empty
     */
    boolean isEmpty();

    /**
     * Whether {@code key} is mapped to a value.
     *
     * @param key the key
     * @return {@code true} if the key is present
     */
    boolean containsKey(long key);

    /**
     * Returns the value mapped to {@code key}, or {@code 0L} if there is none.
     *
     * @param key the key
     * @return the value, or {@code 0L}
     */
    long get(long key);

    /**
     * Returns the value mapped to {@code key}, or {@code defaultValue} if there is none.
     *
     * @param key the key
     * @param defaultValue the value to return for an absent key
     * @return the value, or {@code defaultValue}
     */
    long getOrDefault(long key, long defaultValue);

    /**
     * Associates {@code value} with {@code key}.
     *
     * @param key the key
     * @param value the new value
     * @return the previous value, or {@code 0L} if the key was absent
     */
    long put(long key, long value);

    /**
     * Inserts only if {@code key} is absent.
     *
     * @param key the key
     * @param value the value for an absent key
     * @return {@code true} if inserted, {@code false} if the key was present
     */
    boolean putIfAbsent(long key, long value);

    /**
     * Adds {@code increment} to the value of {@code key}. An absent key counts as
     * {@code 0L}, so it is inserted with value {@code increment}. Returns the
     * <em>new</em> value (like HPPC; fastutil's {@code addTo} returns the old one).
     * The sum wraps around on overflow, as in Java arithmetic.
     *
     * @param key the key
     * @param increment the amount to add
     * @return the new value
     */
    long addTo(long key, long increment);

    /**
     * Removes {@code key}.
     *
     * @param key the key
     * @return the previous value, or {@code 0L} if the key was absent
     */
    long remove(long key);

    /** Removes all mappings. The capacity stays unchanged. */
    void clear();

    /**
     * Makes room for {@code expectedSize} mappings without further resizing.
     *
     * @param expectedSize the number of mappings
     * @throws IllegalArgumentException if {@code expectedSize} is more than the map can hold, or
     *         negative for a sorted map
     */
    void ensureCapacity(int expectedSize);

    /**
     * Returns a handle to {@code key}'s slot. A value {@code >= 0} means the key is
     * present. A negative value is {@code ~insertionSlot} for an absent key.
     * <p>
     * The handle stays valid only until the next structural modification of the map
     * (any insert or remove, including those made through other handles).
     *
     * @param key the key
     * @return a handle, negative if the key is absent
     */
    int indexOf(long key);

    /**
     * Whether a handle obtained from {@link #indexOf} denotes a present key.
     *
     * @param index a handle
     * @return {@code true} if the handle denotes a present key
     */
    boolean indexExists(int index);

    /**
     * Value at a handle obtained from {@link #indexOf} that {@linkplain #indexExists exists}.
     *
     * @param index an existing handle
     * @return the value
     */
    long indexGet(int index);

    /**
     * Replaces the value at an existing handle.
     *
     * @param index an existing handle
     * @param value the new value
     * @return the old value
     */
    long indexReplace(int index, long value);

    /**
     * Inserts {@code key -> value} at a negative handle that {@link #indexOf} returned for
     * this same {@code key}. The table may grow as a result, which invalidates every other
     * handle.
     *
     * @param index a negative handle for {@code key}
     * @param key the key
     * @param value the value
     */
    void indexInsert(int index, long key, long value);

    /**
     * Removes the entry at an existing handle.
     *
     * @param index an existing handle
     * @return the removed value
     */
    long indexRemove(int index);

    /**
     * Calls {@code action} for each entry. The map must not be modified during the call.
     *
     * @param action the action to call with each key and value
     */
    void forEach(LongLongConsumer action);

    /**
     * Removes all entries for which {@code predicate} returns {@code true}. The predicate
     * must not modify the map.
     *
     * @param predicate the test for each key and value
     * @return {@code true} if any entry was removed
     */
    boolean removeIf(LongLongPredicate predicate);

    // ------------------------------------------------------------------
    // Iteration without allocation
    // ------------------------------------------------------------------

    /**
     * Handle of the first entry in iteration order, or {@code -1} if the map is empty.
     * Together with {@link #nextIndex}, {@link #indexKey} and {@link #indexGet}, this iterates
     * without allocation and can stop at any point:
     * <pre>{@code
     * for (int i = map.firstIndex(); i >= 0; i = map.nextIndex(i)) {
     *     long key = map.indexKey(i);
     *     long value = map.indexGet(i);
     * }
     * }</pre>
     * During the iteration, values may be replaced with {@link #indexReplace}; any other
     * modification of the map invalidates the handles.
     *
     * @return the handle of the first entry, or {@code -1}
     */
    int firstIndex();

    /**
     * Handle of the entry after {@code index} in iteration order, or {@code -1} if there is none.
     *
     * @param index an existing handle
     * @return the handle of the next entry, or {@code -1}
     */
    int nextIndex(int index);

    /**
     * The key at a handle obtained from {@link #firstIndex} or {@link #nextIndex}.
     *
     * @param index an existing handle
     * @return the key
     */
    long indexKey(int index);

    // ------------------------------------------------------------------
    // Bulk operations and conversions
    // ------------------------------------------------------------------

    /**
     * Copies all mappings of {@code other} into this map, replacing the values of existing keys.
     *
     * @param other the map to copy from (any implementation)
     */
    default void putAll(LongLongMap other) {
        ensureCapacity(Math.max(size(), other.size()));
        other.forEach(this::put);
    }

    /**
     * Copies all mappings of {@code map} into this map, replacing the values of existing keys.
     *
     * @param map the map to copy from
     * @throws NullPointerException for a {@code null} key or value
     */
    default void putAll(Map<? extends Long, ? extends Long> map) {
        ensureCapacity(Math.max(size(), map.size()));
        for (Map.Entry<? extends Long, ? extends Long> e : map.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * The keys in a new array, in iteration order.
     *
     * @return a new array with the keys
     */
    default long[] keysToArray() {
        long[] keys = new long[size()];
        int n = 0;
        for (int i = firstIndex(); i >= 0; i = nextIndex(i)) {
            keys[n++] = indexKey(i);
        }
        return keys;
    }

    /**
     * The values in a new array, in iteration order (the order of {@link #keysToArray}).
     *
     * @return a new array with the values
     */
    default long[] valuesToArray() {
        long[] values = new long[size()];
        int n = 0;
        for (int i = firstIndex(); i >= 0; i = nextIndex(i)) {
            values[n++] = indexGet(i);
        }
        return values;
    }

    /**
     * A new modifiable map with the mappings of this map, boxed: a {@link HashMap}, or, for a
     * map with a defined order (insertion-ordered or sorted), a map with the same iteration
     * order.
     *
     * @return a new map
     */
    default Map<Long, Long> toMap() {
        Map<Long, Long> map = HashMap.newHashMap(size());
        forEach(map::put);
        return map;
    }
}
