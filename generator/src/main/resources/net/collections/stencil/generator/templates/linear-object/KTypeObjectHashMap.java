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
//#-- Linear probing over a primitive key array with a parallel Object[] of values. A Java
//#-- array cannot hold both primitive keys and references, so the interleaved layout of the
//#-- primitive templates is not available; probing reads only the dense key array.
//#types K=int|long
package net.generated.example;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.KTypeFunction;

import net.generated.example.function.KTypeObjectConsumer;
import net.generated.example.function.KTypeObjectPredicate;

/**
 * Open-addressing hash map from {@code KType} keys to object values.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> a {@code KType[]} of keys and an {@code Object[]} of values; slot
 *       {@code s} is {@code keys[s]} and {@code values[s]}. Probing reads only the dense key
 *       array.</li>
 *   <li><b>Empty marker:</b> a slot whose key is {@code 0} is empty. Key {@code 0} itself is
 *       stored outside the table ({@link #hasZeroKey}, {@link #zeroValue}).</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed}. The slot is taken from the
 *       <em>high</em> bits of the 64-bit product, which depend on all bits of the key, and
 *       {@code seed} is chosen at random per instance.</li>
 *   <li><b>Collision resolution:</b> linear probing. Removal uses backward-shift deletion, so
 *       there are no tombstones, and clears the value reference.</li>
 *   <li><b>Index handles:</b> {@link #indexOf} and the {@code index*} methods let you do
 *       "find, then read, update or insert" with a single hash computation and no
 *       allocation.</li>
 *   <li><b>Load factor:</b> the default {@value #DEFAULT_LOAD_FACTOR} keeps the table small.
 *       For maximum speed, pass {@code 0.5f}: probe chains get shorter, especially for lookups
 *       of absent keys, at up to twice the memory.</li>
 * </ul>
 * See {@link KTypeObjectMap} for the contract. This class is not thread-safe. Iteration order
 * is unspecified and differs between instances.
 *
 * @param <V> the type of the values
 */
public final class KTypeObjectHashMap<V> implements KTypeObjectMap<V> {

    /** Default maximum ratio of occupied slots to capacity. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_CAPACITY = 4;
    private static final int MAX_CAPACITY = 1 << 30;

    private final float loadFactor;
    private final long seed;

    /** Keys; length is the capacity, a power of two. */
    private KType[] keys;
    /** Values, parallel to {@link #keys}; {@code null} in empty slots. */
    private Object[] values;
    /** {@code 64 - log2(keys.length)}. */
    private int shift;
    /** Number of occupied slots in {@link #keys} (the zero key is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;
    private V zeroValue;

    /**
     * An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public KTypeObjectHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public KTypeObjectHashMap(int expectedSize) {
        this(expectedSize, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings at the given load factor.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @param loadFactor the maximum fill of the table, in {@code (0, 1)}
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large, or
     *         {@code loadFactor} is not in {@code (0, 1)}
     */
    public KTypeObjectHashMap(int expectedSize, float loadFactor) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (!(loadFactor > 0f && loadFactor < 1f)) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.seed = ThreadLocalRandom.current().nextLong();
        allocate(capacityFor(expectedSize, loadFactor));
    }

    /**
     * A map with the mappings of {@code other} (any implementation) and the default load factor.
     *
     * @param other the map to copy (any implementation)
     */
    public KTypeObjectHashMap(KTypeObjectMap<? extends V> other) {
        this(other.size());
        putAll(other);
    }

    /**
     * Fibonacci hashing: high bits of {@code (key ^ seed) * PHI}. Result is the key's home
     * slot, in {@code [0, 2^(64 - shift))}.
     */
    private static int mix(long key, long seed, int shift) {
        return (int) (((key ^ seed) * PHI) >>> shift);
    }

    /** The value in slot {@code i}. */
    @SuppressWarnings("unchecked")
    private V valueAt(int i) {
        return (V) values[i];
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public int size() {
        return hasZeroKey ? assigned + 1 : assigned;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(KType key) {
        if (key == 0) {
            return hasZeroKey;
        }
        return probe(key) >= 0;
    }

    @Override
    public V get(KType key) {
        return getOrDefault(key, null);
    }

    @Override
    public V getOrDefault(KType key, V defaultValue) {
        if (key == 0) {
            return hasZeroKey ? zeroValue : defaultValue;
        }
        final KType[] ks = keys;
        final int mask = ks.length - 1;
        int i = mix(key, seed, shift);
        KType k;
        while ((k = ks[i]) != key) {
            if (k == 0) {
                return defaultValue;
            }
            i = (i + 1) & mask;
        }
        return valueAt(i);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public V put(KType key, V value) {
        if (key == 0) {
            V old = hasZeroKey ? zeroValue : null;
            hasZeroKey = true;
            zeroValue = value;
            return old;
        }
        int i = probe(key);
        if (i >= 0) {
            V old = valueAt(i);
            values[i] = value;
            return old;
        }
        insertNew(~i, key, value);
        return null;
    }

    @Override
    public boolean putIfAbsent(KType key, V value) {
        if (key == 0) {
            if (hasZeroKey) {
                return false;
            }
            hasZeroKey = true;
            zeroValue = value;
            return true;
        }
        int i = probe(key);
        if (i >= 0) {
            return false;
        }
        insertNew(~i, key, value);
        return true;
    }

    @Override
    public V computeIfAbsent(KType key, KTypeFunction<? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        if (key == 0) {
            if (hasZeroKey && zeroValue != null) {
                return zeroValue;
            }
            V v = mappingFunction.apply(key);
            if (v != null) {
                hasZeroKey = true;
                zeroValue = v;
            }
            return v;
        }
        int i = probe(key);
        if (i >= 0) {
            V old = valueAt(i);
            if (old != null) {
                return old;
            }
            V v = mappingFunction.apply(key);
            if (v != null) {
                values[i] = v;
            }
            return v;
        }
        V v = mappingFunction.apply(key);
        if (v != null) {
            insertNew(~i, key, v);
        }
        return v;
    }

    @Override
    public V remove(KType key) {
        if (key == 0) {
            if (!hasZeroKey) {
                return null;
            }
            V old = zeroValue;
            hasZeroKey = false;
            zeroValue = null;
            return old;
        }
        int i = probe(key);
        if (i < 0) {
            return null;
        }
        V old = valueAt(i);
        deleteAt(i);
        return old;
    }

    @Override
    public void clear() {
        Arrays.fill(keys, KZero);
        Arrays.fill(values, null);
        assigned = 0;
        hasZeroKey = false;
        zeroValue = null;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int cap = capacityFor(expectedSize, loadFactor);
        if (cap > keys.length) {
            rehash(cap);
        }
    }

    // ------------------------------------------------------------------
    // Index handles (one hash computation for find-then-modify)
    // ------------------------------------------------------------------

    @Override
    public int indexOf(KType key) {
        if (key == 0) {
            int z = keys.length; // virtual slot for the zero key
            return hasZeroKey ? z : ~z;
        }
        return probe(key);
    }

    @Override
    public boolean indexExists(int index) {
        return index >= 0;
    }

    @Override
    public V indexGet(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == keys.length ? zeroValue : valueAt(index);
    }

    @Override
    public V indexReplace(int index, V value) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == keys.length) {
            V old = zeroValue;
            zeroValue = value;
            return old;
        }
        V old = valueAt(index);
        values[index] = value;
        return old;
    }

    @Override
    public void indexInsert(int index, KType key, V value) {
        assert index < 0 : "index exists: " + index;
        int slot = ~index;
        if (slot == keys.length) {
            assert key == 0;
            hasZeroKey = true;
            zeroValue = value;
            return;
        }
        assert key != 0 && keys[slot] == 0 && probe(key) == index;
        insertNew(slot, key, value);
    }

    @Override
    public V indexRemove(int index) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == keys.length) {
            V old = zeroValue;
            hasZeroKey = false;
            zeroValue = null;
            return old;
        }
        V old = valueAt(index);
        deleteAt(index);
        return old;
    }

    // ------------------------------------------------------------------
    // Bulk / Object methods
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Iteration and bulk removal
    // ------------------------------------------------------------------

    @Override
    public int firstIndex() {
        return occupiedFrom(0);
    }

    @Override
    public int nextIndex(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == keys.length ? -1 : occupiedFrom(index + 1);
    }

    @Override
    public KType indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == keys.length ? KZero : keys[index];
    }

    /** First occupied slot at or after {@code from}, then the zero key's virtual slot, then {@code -1}. */
    private int occupiedFrom(int from) {
        final KType[] ks = keys;
        for (int i = from; i < ks.length; i++) {
            if (ks[i] != 0) {
                return i;
            }
        }
        return hasZeroKey ? ks.length : -1;
    }

    @Override
    public boolean removeIf(KTypeObjectPredicate<? super V> predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(KZero, zeroValue)) {
            hasZeroKey = false;
            zeroValue = null;
            removed = true;
        }
        final KType[] ks = keys;
        final int mask = ks.length - 1;
        // Start behind an empty slot (there always is one). Backward shifts never move an entry
        // across an empty slot, so they only move entries that come later in this pass, and
        // every entry is tested exactly once.
        int start = 0;
        while (ks[start] != 0) {
            start++;
        }
        for (int i = (start + 1) & mask; i != start; ) {
            KType k = ks[i];
            if (k != 0 && predicate.test(k, valueAt(i))) {
                deleteAt(i); // a later entry may move into slot i: test it next
                removed = true;
            } else {
                i = (i + 1) & mask;
            }
        }
        return removed;
    }

    @Override
    public void forEach(KTypeObjectConsumer<? super V> action) {
        if (hasZeroKey) {
            action.accept(KZero, zeroValue);
        }
        final KType[] ks = keys;
        for (int i = 0; i < ks.length; i++) {
            KType k = ks[i];
            if (k != 0) {
                action.accept(k, valueAt(i));
            }
        }
    }

    /** Equal to any {@link KTypeObjectMap} with the same mappings. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KTypeObjectMap<?> other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !containsEntry(other, KZero, zeroValue)) {
            return false;
        }
        final KType[] ks = keys;
        for (int i = 0; i < ks.length; i++) {
            KType k = ks[i];
            if (k != 0 && !containsEntry(other, k, values[i])) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code map} maps {@code key} to a value equal to {@code value}. */
    private static boolean containsEntry(KTypeObjectMap<?> map, KType key, Object value) {
        int i = map.indexOf(key);
        return map.indexExists(i) && Objects.equals(map.indexGet(i), value);
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = hasZeroKey ? Objects.hashCode(zeroValue) : 0;
        final KType[] ks = keys;
        for (int i = 0; i < ks.length; i++) {
            KType k = ks[i];
            if (k != 0) {
                h += KBoxed.hashCode(k) ^ Objects.hashCode(values[i]);
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size(), 1 << 16) * 16 + 2).append('{');
        forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v == this ? "(this Map)" : v);
        });
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** For {@code key != 0}: slot of {@code key} if present, otherwise {@code ~firstEmptySlot}. */
    private int probe(KType key) {
        final KType[] ks = keys;
        final int mask = ks.length - 1;
        int i = mix(key, seed, shift);
        for (;;) {
            KType k = ks[i];
            if (k == key) {
                return i;
            }
            if (k == 0) {
                return ~i;
            }
            i = (i + 1) & mask;
        }
    }

    /** Puts a new entry (its key is known to be absent) into the empty slot {@code i}, growing first if needed. */
    private void insertNew(int i, KType key, V value) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            i = ~probe(key);
        }
        keys[i] = key;
        values[i] = value;
        assigned++;
    }

    /** Backward-shift deletion: moves later entries of the probe chain into the gap. */
    private void deleteAt(int gap) {
        final KType[] ks = keys;
        final Object[] vs = values;
        final int mask = ks.length - 1;
        final long s = seed;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 1) & mask;
            KType k = ks[i];
            if (k == 0) {
                break;
            }
            int ideal = mix(k, s, sh);
            // The entry can move into gap iff gap lies on its probe path ideal -> i (cyclically).
            if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                ks[gap] = k;
                vs[gap] = vs[i];
                gap = i;
            }
        }
        ks[gap] = KZero;
        vs[gap] = null;
        assigned--;
    }

    private int nextCapacity() {
        if (keys.length >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return keys.length << 1;
    }

    private void rehash(int newCapacity) {
        final KType[] oldKeys = keys;
        final Object[] oldValues = values;
        allocate(newCapacity);
        final KType[] ks = keys;
        final Object[] vs = values;
        final int mask = ks.length - 1;
        final long s = seed;
        final int sh = shift;
        for (int j = 0; j < oldKeys.length; j++) {
            KType k = oldKeys[j];
            if (k != 0) {
                int i = mix(k, s, sh);
                while (ks[i] != 0) {
                    i = (i + 1) & mask;
                }
                ks[i] = k;
                vs[i] = oldValues[j];
            }
        }
    }

    private void allocate(int capacity) {
        keys = new KType[capacity];
        values = new Object[capacity];
        shift = 64 - Integer.numberOfTrailingZeros(capacity);
        maxFill = maxFill(capacity, loadFactor);
    }

    private static int maxFill(int capacity, float loadFactor) {
        // Always keep at least one empty slot so that probing terminates.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * (double) loadFactor)));
    }

    /** Capacity in slots (a power of two) for {@code expectedSize} entries. */
    static int capacityFor(int expectedSize, float loadFactor) {
        long needed = (long) Math.ceil(expectedSize / (double) loadFactor) + 1;
        if (needed > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize too large: " + expectedSize);
        }
        int cap = needed <= MIN_CAPACITY ? MIN_CAPACITY : Integer.highestOneBit((int) needed - 1) << 1;
        // make sure maxFill(cap) >= expectedSize despite rounding
        while (maxFill(cap, loadFactor) < expectedSize && cap < MAX_CAPACITY) {
            cap <<= 1;
        }
        return cap;
    }
}
