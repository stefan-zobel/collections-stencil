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
//#types V=int|long|double
package net.generated.example;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntFunction;

import net.generated.example.function.ObjectVTypeConsumer;
import net.generated.example.function.ObjectVTypePredicate;

/**
 * A map from object keys to {@code VType} values without boxing the values, for example a
 * counter per key:
 * <pre>{@code
 * ObjectVTypeMap<String> counts = new ObjectVTypeHashMap<>();
 * for (String word : words) {
 *     counts.addTo(word, 1);
 * }
 * }</pre>
 * Implementations: {@link ObjectVTypeHashMap} (linear probing), {@link ObjectVTypeLinkedHashMap}
 * (iterates in insertion order).
 * <p>
 * Keys are compared with {@link Object#equals} and hashed with {@link Object#hashCode}, as in
 * {@link java.util.HashMap}. A key must not change in a way that affects {@code equals} or
 * {@code hashCode} while it is in the map. The {@code null} key is allowed. Methods that look
 * up a key accept any object, like {@link java.util.Map#get}.
 * <p>
 * Methods that return a value return {@code VZero} if there is no mapping. Use
 * {@link #containsKey}, {@link #getOrDefault} or the index handles to tell an absent key from
 * a key that is mapped to {@code VZero}.
//#if V.floating
 * <p>
 * Values are compared like {@link VBoxed#equals}: {@code NaN} equals {@code NaN}, and
 * {@code 0.0} differs from {@code -0.0}.
//#end
 * <p>
 * Two maps are {@linkplain Object#equals equal} if they contain the same mappings, whatever
 * their implementations. The {@linkplain Object#hashCode hash code} is the one of a
 * {@link java.util.Map} with the boxed entries.
 * <p>
 * Implementations are not thread-safe. Iteration order is unspecified unless the
 * implementation specifies it: {@link ObjectVTypeHashMap} iterates in an order that differs
 * between instances, {@link ObjectVTypeLinkedHashMap} in insertion order.
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 *
 * @param <K> the type of the keys
 */
public interface ObjectVTypeMap<K> {

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
     * @param key the key, may be {@code null}
     * @return {@code true} if the key is present
     */
    boolean containsKey(Object key);

    /**
     * Returns the value mapped to {@code key}, or {@code VZero} if there is none.
     *
     * @param key the key, may be {@code null}
     * @return the value, or {@code VZero}
     */
    VType get(Object key);

    /**
     * Returns the value mapped to {@code key}, or {@code defaultValue} if there is none.
     *
     * @param key the key, may be {@code null}
     * @param defaultValue the value to return for an absent key
     * @return the value, or {@code defaultValue}
     */
    VType getOrDefault(Object key, VType defaultValue);

    /**
     * Associates {@code value} with {@code key}.
     *
     * @param key the key, may be {@code null}
     * @param value the new value
     * @return the previous value, or {@code VZero} if the key was absent
     */
    VType put(K key, VType value);

    /**
     * Inserts only if {@code key} is absent.
     *
     * @param key the key, may be {@code null}
     * @param value the value for an absent key
     * @return {@code true} if inserted, {@code false} if the key was present
     */
    boolean putIfAbsent(K key, VType value);

    /**
     * Adds {@code increment} to the value of {@code key}. An absent key counts as
     * {@code VZero}, so it is inserted with value {@code increment}. Returns the
     * <em>new</em> value (like HPPC; fastutil's {@code addTo} returns the old one).
//#if V.integral
     * The sum wraps around on overflow, as in Java arithmetic.
//#end
     *
     * @param key the key, may be {@code null}
     * @param increment the amount to add
     * @return the new value
     */
    VType addTo(K key, VType increment);

    /**
     * Removes {@code key}.
     *
     * @param key the key, may be {@code null}
     * @return the previous value, or {@code VZero} if the key was absent
     */
    VType remove(Object key);

    /** Removes all mappings. The capacity stays unchanged. */
    void clear();

    /**
     * Makes room for {@code expectedSize} mappings without further resizing.
     *
     * @param expectedSize the number of mappings
     * @throws IllegalArgumentException if {@code expectedSize} is more than the map can hold
     */
    void ensureCapacity(int expectedSize);

    /**
     * Returns a handle to {@code key}'s slot. A value {@code >= 0} means the key is
     * present. A negative value is {@code ~insertionSlot} for an absent key.
     * <p>
     * The handle stays valid only until the next structural modification of the map
     * (any insert or remove, including those made through other handles).
     *
     * @param key the key, may be {@code null}
     * @return a handle, negative if the key is absent
     */
    int indexOf(Object key);

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
    VType indexGet(int index);

    /**
     * Replaces the value at an existing handle.
     *
     * @param index an existing handle
     * @param value the new value
     * @return the old value
     */
    VType indexReplace(int index, VType value);

    /**
     * Inserts {@code key -> value} at a negative handle that {@link #indexOf} returned for
     * this same {@code key}. The table may grow as a result, which invalidates every other
     * handle.
     *
     * @param index a negative handle for {@code key}
     * @param key the key
     * @param value the value
     */
    void indexInsert(int index, K key, VType value);

    /**
     * Removes the entry at an existing handle.
     *
     * @param index an existing handle
     * @return the removed value
     */
    VType indexRemove(int index);

    /**
     * Calls {@code action} for each entry. The map must not be modified during the call.
     *
     * @param action the action to call with each key and value
     */
    void forEach(ObjectVTypeConsumer<? super K> action);

    /**
     * Removes all entries for which {@code predicate} returns {@code true}. The predicate
     * must not modify the map.
     *
     * @param predicate the test for each key and value
     * @return {@code true} if any entry was removed
     */
    boolean removeIf(ObjectVTypePredicate<? super K> predicate);

    // ------------------------------------------------------------------
    // Iteration without allocation
    // ------------------------------------------------------------------

    /**
     * Handle of the first entry in iteration order, or {@code -1} if the map is empty.
     * Together with {@link #nextIndex}, {@link #indexKey} and {@link #indexGet}, this iterates
     * without allocation and can stop at any point:
     * <pre>{@code
     * for (int i = map.firstIndex(); i >= 0; i = map.nextIndex(i)) {
     *     K key = map.indexKey(i);
     *     VType value = map.indexGet(i);
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
     * @return the key, possibly {@code null}
     */
    K indexKey(int index);

    // ------------------------------------------------------------------
    // Bulk operations and conversions
    // ------------------------------------------------------------------

    /**
     * Copies all mappings of {@code other} into this map, replacing the values of existing keys.
     *
     * @param other the map to copy from (any implementation)
     */
    default void putAll(ObjectVTypeMap<? extends K> other) {
        ensureCapacity(Math.max(size(), other.size()));
        other.forEach(this::put);
    }

    /**
     * Copies all mappings of {@code map} into this map, replacing the values of existing keys.
     *
     * @param map the map to copy from
     * @throws NullPointerException for a {@code null} value
     */
    default void putAll(Map<? extends K, ? extends VBoxed> map) {
        ensureCapacity(Math.max(size(), map.size()));
        for (Map.Entry<? extends K, ? extends VBoxed> e : map.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * The keys in a new array created by {@code generator} (for example {@code String[]::new}),
     * in iteration order.
     *
     * @param generator creates an array of the requested length
     * @return the array created by {@code generator}, filled with the keys
     */
    default K[] keysToArray(IntFunction<K[]> generator) {
        K[] keys = generator.apply(size());
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
    default VType[] valuesToArray() {
        VType[] values = new VType[size()];
        int n = 0;
        for (int i = firstIndex(); i >= 0; i = nextIndex(i)) {
            values[n++] = indexGet(i);
        }
        return values;
    }

    /**
     * A new modifiable map with the mappings of this map, values boxed: a {@link HashMap}, or,
     * for an insertion-ordered map, a map with the same iteration order.
     *
     * @return a new map
     */
    default Map<K, VBoxed> toMap() {
        Map<K, VBoxed> map = HashMap.newHashMap(size());
        forEach(map::put);
        return map;
    }
}
