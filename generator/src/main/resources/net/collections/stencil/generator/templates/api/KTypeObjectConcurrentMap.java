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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.KTypeFunction;

import net.generated.example.function.KTypeObjectConsumer;
import net.generated.example.function.KTypeObjectFunction;
import net.generated.example.function.KTypeObjectPredicate;

/**
 * A thread-safe map from {@code KType} keys to object values without boxing the keys.
 * <p>
 * Implementation: {@link KTypeObjectConcurrentHashMap}.
 * <p>
 * The methods follow the contract of {@link java.util.concurrent.ConcurrentMap}:
 * <ul>
 *   <li>Every key can be stored, including {@code 0}. Values must not be {@code null}: methods
 *       that store a value throw {@link NullPointerException} for {@code null}. A method that
 *       returns {@code null} therefore always means "no mapping".</li>
 *   <li>Single-key operations are atomic. {@link #computeIfAbsent}, {@link #computeIfPresent},
 *       {@link #compute} and {@link #merge} call their function at most once per call, while
 *       other updates of the same key wait. The function must be short and must not update
 *       this map.</li>
 *   <li>Aggregate operations ({@link #size}, {@link #forEach}, {@link #removeIf},
 *       {@link #clear}, the conversions, {@code equals}, {@code hashCode} and
 *       {@code toString}) are weakly consistent: under concurrent updates they reflect some
 *       state of each part of the map, not one snapshot of the whole map. They never throw
 *       because of concurrent updates.</li>
 * </ul>
 * There are no index handles as in {@link KTypeObjectMap}: an index would be invalid as soon
 * as another thread changes the map.
 * <p>
 * Two concurrent maps are {@linkplain Object#equals equal} if they contain the same mappings
 * (values compared with {@link Object#equals}), whatever their implementations. The
 * {@linkplain Object#hashCode hash code} is the one of a {@link java.util.Map} with the boxed
 * entries. Use {@link #toMap} to compare with other kinds of maps.
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 *
 * @param <V> the type of the values
 */
public interface KTypeObjectConcurrentMap<V> {

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * The number of mappings. Weakly consistent: under concurrent updates the result is an
     * estimate.
     *
     * @return the number of mappings
     */
    int size();

    /**
     * Whether the map has no mappings. Weakly consistent, like {@link #size}.
     *
     * @return {@code true} if the map is empty
     */
    boolean isEmpty();

    /**
     * Whether {@code key} has a mapping.
     *
     * @param key the key to look up
     * @return {@code true} if the map contains {@code key}
     */
    boolean containsKey(KType key);

    /**
     * The value of {@code key}, or {@code null} if the key is absent.
     *
     * @param key the key to look up
     * @return the value, or {@code null}
     */
    V get(KType key);

    /**
     * The value of {@code key}, or {@code defaultValue} if the key is absent.
     *
     * @param key the key to look up
     * @param defaultValue the result for an absent key (may be {@code null})
     * @return the value, or {@code defaultValue}
     */
    V getOrDefault(KType key, V defaultValue);

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /**
     * Maps {@code key} to {@code value}.
     *
     * @param key the key
     * @param value the new value, not {@code null}
     * @return the previous value, or {@code null} if the key was absent
     */
    V put(KType key, V value);

    /**
     * Maps {@code key} to {@code value} if the key is absent.
     * <p>
     * Returns the current value, as {@link java.util.concurrent.ConcurrentMap#putIfAbsent}
     * does, so that a thread that loses the race gets the winner's value in the same atomic
     * step. This differs from {@link KTypeObjectMap#putIfAbsent}, which returns a
     * {@code boolean}: there, values may be {@code null}, so a returned {@code null} could not
     * tell a stored {@code null} from an absent key. The values of this map are never
     * {@code null}.
     *
     * @param key the key
     * @param value the value to store, not {@code null}
     * @return the current value (unchanged), or {@code null} if the key was absent and
     *         {@code value} was stored
     */
    V putIfAbsent(KType key, V value);

    /**
     * Replaces the value of {@code key} if the key is present.
     * <p>
     * Returns the previous value, as
     * {@link java.util.concurrent.ConcurrentMap#replace(Object, Object)} does; the maps with
     * primitive values return a {@code boolean} instead, because there every value, including
     * {@code 0}, can be stored.
     *
     * @param key the key
     * @param value the new value, not {@code null}
     * @return the previous value, or {@code null} if the key was absent (and nothing changed)
     */
    V replace(KType key, V value);

    /**
     * Replaces the value of {@code key} with {@code newValue} if it is currently equal to
     * {@code oldValue}.
     *
     * @param key the key
     * @param oldValue the expected current value, compared with {@link Object#equals}
     * @param newValue the new value, not {@code null}
     * @return {@code true} if the value was replaced
     */
    boolean replace(KType key, V oldValue, V newValue);

    /**
     * Removes the mapping of {@code key}.
     *
     * @param key the key
     * @return the removed value, or {@code null} if the key was absent
     */
    V remove(KType key);

    /**
     * Removes the mapping of {@code key} if its value is currently equal to {@code value}.
     *
     * @param key the key
     * @param value the expected current value, compared with {@link Object#equals}
     * @return {@code true} if the mapping was removed
     */
    boolean remove(KType key, Object value);

    /**
     * Returns the value of {@code key}; if the key is absent, computes a value with
     * {@code mappingFunction}, stores it unless it is {@code null}, and returns it.
     * <p>
     * Atomic: the function is called at most once, only if the key is absent, and no other
     * thread can map the key meanwhile. Lookups of a present key do not lock.
     *
     * @param key the key
     * @param mappingFunction computes the value from the key; must not update this map
     * @return the current or computed value, or {@code null} if the key is absent and the
     *         function returned {@code null}
     */
    V computeIfAbsent(KType key, KTypeFunction<? extends V> mappingFunction);

    /**
     * If {@code key} is present, computes its new value from the key and the current value.
     * A {@code null} result removes the mapping.
     * <p>
     * Atomic: the function is called at most once, while other updates of the key wait.
     *
     * @param key the key
     * @param remappingFunction computes the new value from the key and the current value;
     *                          must not update this map
     * @return the new value, or {@code null} if the key is absent or was removed
     */
    V computeIfPresent(KType key, KTypeObjectFunction<? super V, ? extends V> remappingFunction);

    /**
     * Computes the new value of {@code key} from the key and the current value ({@code null}
     * if absent). A {@code null} result removes the mapping (or stores nothing).
     * <p>
     * Atomic: the function is called exactly once, while other updates of the key wait.
     *
     * @param key the key
     * @param remappingFunction computes the new value from the key and the current value or
     *                          {@code null}; must not update this map
     * @return the new value, or {@code null} if there is no mapping afterwards
     */
    V compute(KType key, KTypeObjectFunction<? super V, ? extends V> remappingFunction);

    /**
     * Stores {@code value} if {@code key} is absent; otherwise combines the current value and
     * {@code value} with {@code remappingFunction}. A {@code null} result removes the mapping.
     * <p>
     * Atomic: the function is called at most once, only if the key is present, while other
     * updates of the key wait.
     *
     * @param key the key
     * @param value the value to store or combine, not {@code null}
     * @param remappingFunction combines the current value and {@code value}; must not update
     *                          this map
     * @return the new value, or {@code null} if the mapping was removed
     */
    V merge(KType key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);

    /** Removes all mappings. Weakly consistent: not atomic over the whole map. */
    void clear();

    /**
     * Grows the map so that it can hold {@code expectedSize} mappings without resizing.
     *
     * @param expectedSize the number of mappings to make room for
     * @throws IllegalArgumentException if {@code expectedSize} is more than the map can hold
     */
    void ensureCapacity(int expectedSize);

    // ------------------------------------------------------------------
    // Iteration and bulk removal
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for every mapping, in unspecified order. Weakly consistent; the
     * action may update this map.
     *
     * @param action the action to perform on each key and value
     */
    void forEach(KTypeObjectConsumer<? super V> action);

    /**
     * Removes every mapping for which {@code predicate} returns {@code true}. Weakly
     * consistent; a mapping is only removed if its value has not been replaced since the
     * predicate saw it.
     *
     * @param predicate decides for a key and its value whether to remove the mapping
     * @return {@code true} if any mapping was removed
     */
    boolean removeIf(KTypeObjectPredicate<? super V> predicate);

    // ------------------------------------------------------------------
    // Bulk operations and conversions
    // ------------------------------------------------------------------

    /**
     * Copies all mappings of {@code other} into this map, replacing the values of existing keys.
     *
     * @param other the map to copy from (any implementation); its values must not be
     *              {@code null}
     */
    default void putAll(KTypeObjectMap<? extends V> other) {
        ensureCapacity(Math.max(size(), other.size()));
        other.forEach(this::put);
    }

    /**
     * Copies all mappings of {@code other} into this map, replacing the values of existing keys.
     *
     * @param other the map to copy from (any implementation)
     */
    default void putAll(KTypeObjectConcurrentMap<? extends V> other) {
        ensureCapacity(Math.max(size(), other.size()));
        other.forEach(this::put);
    }

    /**
     * Copies all mappings of {@code map} into this map, replacing the values of existing keys.
     *
     * @param map the map to copy from
     * @throws NullPointerException for a {@code null} key or value
     */
    default void putAll(Map<? extends KBoxed, ? extends V> map) {
        ensureCapacity(Math.max(size(), map.size()));
        for (Map.Entry<? extends KBoxed, ? extends V> e : map.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * The keys in a new array. Weakly consistent, like {@link #forEach}.
     *
     * @return a new array with the keys
     */
    default KType[] keysToArray() {
        KType[][] keys = {new KType[Math.max(size(), 16)]};
        int[] n = {0};
        forEach((k, v) -> {
            if (n[0] == keys[0].length) {
                keys[0] = Arrays.copyOf(keys[0], n[0] * 2);
            }
            keys[0][n[0]++] = k;
        });
        return Arrays.copyOf(keys[0], n[0]);
    }

    /**
     * The values in a new array created by {@code generator} (for example
     * {@code String[]::new}). Weakly consistent, like {@link #forEach}; under concurrent updates
     * the order need not match the one of {@link #keysToArray}.
     *
     * @param generator creates an array of the requested length
     * @return an array created by {@code generator}, filled with the values
     */
    default V[] valuesToArray(java.util.function.IntFunction<V[]> generator) {
        Object[][] values = {new Object[Math.max(size(), 16)]};
        int[] n = {0};
        forEach((k, v) -> {
            if (n[0] == values[0].length) {
                values[0] = Arrays.copyOf(values[0], n[0] * 2);
            }
            values[0][n[0]++] = v;
        });
        V[] result = generator.apply(n[0]);
        System.arraycopy(values[0], 0, result, 0, n[0]);
        return result;
    }

    /**
     * A new {@link HashMap} with the mappings of this map, keys boxed. Weakly consistent, like
     * {@link #forEach}.
     *
     * @return a new {@link HashMap}
     */
    default Map<KBoxed, V> toMap() {
        Map<KBoxed, V> map = HashMap.newHashMap(size());
        forEach(map::put);
        return map;
    }
}
