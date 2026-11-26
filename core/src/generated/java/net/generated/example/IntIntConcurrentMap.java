// Generated from template api/KTypeVTypeConcurrentMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

import net.generated.example.function.IntIntConsumer;
import net.generated.example.function.IntIntPredicate;

/**
 * A thread-safe map from {@code int} keys to {@code int} values without boxing, for example
 * counters or sums per key that many threads update.
 * <p>
 * Implementation: {@link IntIntConcurrentHashMap}.
 * <ul>
 *   <li>Every key can be stored, including {@code 0}. Methods that return a value return
 *       {@code 0} if there is no mapping; {@link #containsKey} and {@link #getOrDefault}
 *       tell an absent key from a key that is mapped to {@code 0}.</li>
 *   <li>Single-key operations are atomic. {@link #addTo} adds to the value of a key (inserting
 *       the key if needed) in one step, so no update is lost when threads count the same key.
 *       {@link #merge} and {@link #computeIfAbsent} call their function at most once per call,
 *       while other updates of the same key wait. The function must be short and must not update
 *       this map.</li>
 *   <li>Aggregate operations ({@link #size}, {@link #forEach}, {@link #removeIf},
 *       {@link #clear}, the conversions, {@code equals}, {@code hashCode} and
 *       {@code toString}) are weakly consistent: under concurrent updates they reflect some
 *       state of each part of the map, not one snapshot of the whole map. They never throw
 *       because of concurrent updates.</li>
 * </ul>
 * There are no index handles as in {@link IntIntMap}: an index would be invalid as soon as
 * another thread changes the map. Two concurrent maps are {@linkplain Object#equals equal} if
 * they contain the same mappings, whatever their implementations. The
 * {@linkplain Object#hashCode hash code} is the one of a {@link java.util.Map} with the boxed
 * entries. Use {@link #toMap} to compare with other kinds of maps.
 * <p>
 * Only the classes of this library implement this interface, including the classes that the
 * annotation processor generates. It is not meant to be implemented elsewhere: later versions
 * may add methods to it, also without a default implementation.
 */
public interface IntIntConcurrentMap {

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
    boolean containsKey(int key);

    /**
     * The value of {@code key}, or {@code 0} if the key is absent.
     *
     * @param key the key to look up
     * @return the value, or {@code 0}
     */
    int get(int key);

    /**
     * The value of {@code key}, or {@code defaultValue} if the key is absent.
     *
     * @param key the key to look up
     * @param defaultValue the result for an absent key
     * @return the value, or {@code defaultValue}
     */
    int getOrDefault(int key, int defaultValue);

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /**
     * Maps {@code key} to {@code value}.
     *
     * @param key the key
     * @param value the new value
     * @return the previous value, or {@code 0} if the key was absent
     */
    int put(int key, int value);

    /**
     * Maps {@code key} to {@code value} if the key is absent.
     * <p>
     * Returns a {@code boolean}, not the current value as
     * {@link java.util.concurrent.ConcurrentMap#putIfAbsent} and
     * {@link IntObjectConcurrentMap#putIfAbsent} do: every value, including {@code 0},
     * can be stored, so a returned value could not tell a stored {@code 0} from an absent
     * key. {@link #computeIfAbsent} returns the current value.
     *
     * @param key the key
     * @param value the value to store
     * @return {@code true} if the key was absent and {@code value} was stored
     */
    boolean putIfAbsent(int key, int value);

    /**
     * Replaces the value of {@code key} if the key is present.
     *
     * @param key the key
     * @param value the new value
     * @return {@code true} if the key was present and its value was replaced
     */
    boolean replace(int key, int value);

    /**
     * Replaces the value of {@code key} with {@code newValue} if it is currently equal to
     * {@code expectedValue}.
     *
     * @param key the key
     * @param expectedValue the expected current value
     * @param newValue the new value
     * @return {@code true} if the value was replaced
     */
    boolean replace(int key, int expectedValue, int newValue);

    /**
     * Removes the mapping of {@code key}.
     *
     * @param key the key
     * @return the removed value, or {@code 0} if the key was absent
     */
    int remove(int key);

    /**
     * Removes the mapping of {@code key} if its value is currently equal to {@code value}.
     *
     * @param key the key
     * @param value the expected current value
     * @return {@code true} if the mapping was removed
     */
    boolean remove(int key, int value);

    /**
     * Adds {@code increment} to the value of {@code key}; an absent key is inserted with the
     * value {@code increment}. Atomic: concurrent calls for the same key never lose an update.
     *
     * @param key the key
     * @param increment the value to add
     * @return the new value
     */
    int addTo(int key, int increment);

    /**
     * Stores {@code value} if {@code key} is absent; otherwise replaces the current value with
     * {@code remappingFunction.applyAsInt(current, value)}.
     * <p>
     * Atomic: the function is called at most once, only if the key is present, while other
     * updates of the key wait.
     *
     * @param key the key
     * @param value the value to store or combine
     * @param remappingFunction combines the current value and {@code value}; must not update
     *                          this map
     * @return the new value
     */
    int merge(int key, int value, IntBinaryOperator remappingFunction);

    /**
     * Returns the value of {@code key}; if the key is absent, computes a value with
     * {@code mappingFunction}, stores it, and returns it.
     * <p>
     * Atomic: the function is called at most once, only if the key is absent, and no other
     * thread can map the key meanwhile.
     *
     * @param key the key
     * @param mappingFunction computes the value from the key; must not update this map
     * @return the current or computed value
     */
    int computeIfAbsent(int key, IntUnaryOperator mappingFunction);

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
    void forEach(IntIntConsumer action);

    /**
     * Removes every mapping for which {@code predicate} returns {@code true}. Weakly
     * consistent; a mapping is only removed if its value has not changed since the predicate saw
     * it.
     *
     * @param predicate decides for a key and its value whether to remove the mapping
     * @return {@code true} if any mapping was removed
     */
    boolean removeIf(IntIntPredicate predicate);

    // ------------------------------------------------------------------
    // Bulk operations and conversions
    // ------------------------------------------------------------------

    /**
     * Copies all mappings of {@code other} into this map, replacing the values of existing keys.
     *
     * @param other the map to copy from (any implementation)
     */
    default void putAll(IntIntMap other) {
        ensureCapacity(Math.max(size(), other.size()));
        other.forEach(this::put);
    }

    /**
     * Copies all mappings of {@code other} into this map, replacing the values of existing keys.
     *
     * @param other the map to copy from (any implementation)
     */
    default void putAll(IntIntConcurrentMap other) {
        ensureCapacity(Math.max(size(), other.size()));
        other.forEach(this::put);
    }

    /**
     * Copies all mappings of {@code map} into this map, replacing the values of existing keys.
     *
     * @param map the map to copy from
     * @throws NullPointerException for a {@code null} key or value
     */
    default void putAll(Map<? extends Integer, ? extends Integer> map) {
        ensureCapacity(Math.max(size(), map.size()));
        for (Map.Entry<? extends Integer, ? extends Integer> e : map.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * The keys in a new array. Weakly consistent, like {@link #forEach}.
     *
     * @return a new array with the keys
     */
    default int[] keysToArray() {
        int[][] keys = {new int[Math.max(size(), 16)]};
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
     * The values in a new array. Weakly consistent, like {@link #forEach}; under concurrent
     * updates the order need not match the one of {@link #keysToArray}.
     *
     * @return a new array with the values
     */
    default int[] valuesToArray() {
        int[][] values = {new int[Math.max(size(), 16)]};
        int[] n = {0};
        forEach((k, v) -> {
            if (n[0] == values[0].length) {
                values[0] = Arrays.copyOf(values[0], n[0] * 2);
            }
            values[0][n[0]++] = v;
        });
        return Arrays.copyOf(values[0], n[0]);
    }

    /**
     * A new {@link HashMap} with the mappings of this map, keys and values boxed. Weakly
     * consistent, like {@link #forEach}.
     *
     * @return a new {@link HashMap}
     */
    default Map<Integer, Integer> toMap() {
        Map<Integer, Integer> map = HashMap.newHashMap(size());
        forEach(map::put);
        return map;
    }
}
