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
//#-- Linear probing over a dense int[] of stored hashes, with parallel arrays of keys and values.
//#-- A stored hash of 0 marks an empty slot; the null key is stored outside the table. Probing
//#-- dereferences a key only when its stored hash is equal, and removal and growth never do.
//#types V=int|long|double
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.function.ObjectVTypeConsumer;
import net.generated.example.function.ObjectVTypePredicate;

/**
 * Open-addressing hash map from object keys to {@code VType} values.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> three parallel arrays: an {@code int[]} of stored hashes, an
 *       {@code Object[]} of keys, and a {@code VType[]} of values. Probing reads only the dense
 *       hash array and dereferences a key (to call {@code equals}) only when its stored hash
 *       equals the hash of the key searched for. Lookups of absent keys therefore almost never
 *       touch a key object, and backward-shift deletion and growth never do.</li>
 *   <li><b>Why stored hashes:</b> without them, every slot on a probe chain costs a
 *       {@code equals} call, which loads the key object (and, for a string, its character
 *       array) from memory, and every entry moved by a removal or a resize costs a
 *       {@code hashCode} call on the key object. That {@link String} caches its hash code does
 *       not help, because reading the cached value loads the string as well. Measured with
 *       string keys: lookups of absent keys more than twice as fast, removals and inserts in a
 *       sliding window about twice as fast, at 4 more bytes per slot.</li>
 *   <li><b>Empty marker:</b> a stored hash of {@code 0} marks an empty slot; a key whose mixed
 *       hash is {@code 0} is stored with hash {@code 1}. The {@code null} key is stored outside
 *       the table ({@link #hasNullKey}, {@link #nullValue}).</li>
 *   <li><b>Hashing:</b> the murmur3 finalizer applied to {@code key.hashCode() ^ seed}; the slot
 *       is taken from the high bits. A nonlinear mixer is needed here: hash codes of keys such
 *       as decimal strings form arithmetic patterns that a purely multiplicative hash keeps,
 *       which gave probe chains of 35 slots instead of 5 for absent keys among 1.4 million
 *       decimal strings. {@code seed} is chosen at random per instance.</li>
 *   <li><b>Key comparison:</b> stored hash first, then by reference, then with
 *       {@code equals}.</li>
 *   <li><b>Collision resolution:</b> linear probing. Removal uses backward-shift deletion, so
 *       there are no tombstones, and clears the key reference.</li>
 *   <li><b>Index handles:</b> {@link #indexOf} and the {@code index*} methods let you do
 *       "find, then read, update or insert" with a single probe and no allocation.</li>
 *   <li><b>Load factor:</b> the default {@value #DEFAULT_LOAD_FACTOR} keeps the table small.
 *       For maximum speed, pass {@code 0.5f}: probe chains get shorter, especially for lookups
 *       of absent keys, at up to twice the memory.</li>
 * </ul>
 * See {@link ObjectVTypeMap} for the contract. This class is not thread-safe. Iteration order
 * is unspecified and differs between instances.
 *
 * @param <K> the type of the keys
 */
public final class ObjectVTypeHashMap<K> implements ObjectVTypeMap<K> {

    /** Default maximum ratio of occupied slots to capacity. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    private static final int MIN_CAPACITY = 4;
    private static final int MAX_CAPACITY = 1 << 30;

    private final float loadFactor;
    private final int seed;

    /** Stored hashes; length is the capacity, a power of two. {@code 0} in empty slots. */
    private int[] hashes;
    /** Keys, parallel to {@link #hashes}; {@code null} in empty slots. */
    private Object[] keys;
    /** Values, parallel to {@link #hashes}; {@code VZero} in empty slots. */
    private VType[] values;
    /** {@code 32 - log2(hashes.length)}. */
    private int shift;
    /** Number of occupied slots in the table (the null key is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasNullKey;
    private VType nullValue;

    /**
     * An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public ObjectVTypeHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public ObjectVTypeHashMap(int expectedSize) {
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
    public ObjectVTypeHashMap(int expectedSize, float loadFactor) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (!(loadFactor > 0f && loadFactor < 1f)) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.seed = ThreadLocalRandom.current().nextInt();
        allocate(capacityFor(expectedSize, loadFactor));
    }

    /**
     * A map with the mappings of {@code other} (any implementation) and the default load factor.
     *
     * @param other the map to copy (any implementation)
     */
    public ObjectVTypeHashMap(ObjectVTypeMap<? extends K> other) {
        this(other.size());
        putAll(other);
    }

    /**
     * The stored hash of a non-null key: the murmur3 finalizer of {@code key.hashCode() ^ seed},
     * never {@code 0}. The key's home slot is {@code hash >>> shift}.
     */
    private static int hash(Object key, int seed) {
        int h = key.hashCode() ^ seed;
        h ^= h >>> 16;
        h *= 0x85EB_CA6B;
        h ^= h >>> 13;
        h *= 0xC2B2_AE35;
        h ^= h >>> 16;
        return h == 0 ? 1 : h;
    }

    /** The key in slot {@code i}. */
    @SuppressWarnings("unchecked")
    private K keyAt(int i) {
        return (K) keys[i];
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public int size() {
        return hasNullKey ? assigned + 1 : assigned;
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsKey(Object key) {
        if (key == null) {
            return hasNullKey;
        }
        return probe(key, hash(key, seed)) >= 0;
    }

    @Override
    public VType get(Object key) {
        return getOrDefault(key, VZero);
    }

    @Override
    public VType getOrDefault(Object key, VType defaultValue) {
        if (key == null) {
            return hasNullKey ? nullValue : defaultValue;
        }
        int i = probe(key, hash(key, seed));
        return i >= 0 ? values[i] : defaultValue;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public VType put(K key, VType value) {
        if (key == null) {
            VType old = hasNullKey ? nullValue : VZero;
            hasNullKey = true;
            nullValue = value;
            return old;
        }
        int h = hash(key, seed);
        int i = probe(key, h);
        if (i >= 0) {
            VType old = values[i];
            values[i] = value;
            return old;
        }
        insertNew(~i, h, key, value);
        return VZero;
    }

    @Override
    public boolean putIfAbsent(K key, VType value) {
        if (key == null) {
            if (hasNullKey) {
                return false;
            }
            hasNullKey = true;
            nullValue = value;
            return true;
        }
        int h = hash(key, seed);
        int i = probe(key, h);
        if (i >= 0) {
            return false;
        }
        insertNew(~i, h, key, value);
        return true;
    }

    @Override
    public VType addTo(K key, VType increment) {
        if (key == null) {
            VType v = hasNullKey ? nullValue + increment : increment;
            hasNullKey = true;
            nullValue = v;
            return v;
        }
        int h = hash(key, seed);
        int i = probe(key, h);
        if (i >= 0) {
            return values[i] += increment;
        }
        insertNew(~i, h, key, increment);
        return increment;
    }

    @Override
    public VType remove(Object key) {
        if (key == null) {
            if (!hasNullKey) {
                return VZero;
            }
            VType old = nullValue;
            hasNullKey = false;
            nullValue = VZero;
            return old;
        }
        int i = probe(key, hash(key, seed));
        if (i < 0) {
            return VZero;
        }
        VType old = values[i];
        deleteAt(i);
        return old;
    }

    @Override
    public void clear() {
        Arrays.fill(hashes, 0);
        Arrays.fill(keys, null);
        Arrays.fill(values, VZero);
        assigned = 0;
        hasNullKey = false;
        nullValue = VZero;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int cap = capacityFor(expectedSize, loadFactor);
        if (cap > hashes.length) {
            rehash(cap);
        }
    }

    // ------------------------------------------------------------------
    // Index handles (one probe for find-then-modify)
    // ------------------------------------------------------------------

    @Override
    public int indexOf(Object key) {
        if (key == null) {
            int z = hashes.length; // virtual slot for the null key
            return hasNullKey ? z : ~z;
        }
        return probe(key, hash(key, seed));
    }

    @Override
    public boolean indexExists(int index) {
        return index >= 0;
    }

    @Override
    public VType indexGet(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == hashes.length ? nullValue : values[index];
    }

    @Override
    public VType indexReplace(int index, VType value) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == hashes.length) {
            VType old = nullValue;
            nullValue = value;
            return old;
        }
        VType old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public void indexInsert(int index, K key, VType value) {
        assert index < 0 : "index exists: " + index;
        int slot = ~index;
        if (slot == hashes.length) {
            assert key == null;
            hasNullKey = true;
            nullValue = value;
            return;
        }
        int h = hash(key, seed);
        assert hashes[slot] == 0 && probe(key, h) == index;
        insertNew(slot, h, key, value);
    }

    @Override
    public VType indexRemove(int index) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == hashes.length) {
            VType old = nullValue;
            hasNullKey = false;
            nullValue = VZero;
            return old;
        }
        VType old = values[index];
        deleteAt(index);
        return old;
    }

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
        return index == hashes.length ? -1 : occupiedFrom(index + 1);
    }

    @Override
    public K indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == hashes.length ? null : keyAt(index);
    }

    /** First occupied slot at or after {@code from}, then the null key's virtual slot, then {@code -1}. */
    private int occupiedFrom(int from) {
        final int[] hs = hashes;
        for (int i = from; i < hs.length; i++) {
            if (hs[i] != 0) {
                return i;
            }
        }
        return hasNullKey ? hs.length : -1;
    }

    @Override
    public boolean removeIf(ObjectVTypePredicate<? super K> predicate) {
        boolean removed = false;
        if (hasNullKey && predicate.test(null, nullValue)) {
            hasNullKey = false;
            nullValue = VZero;
            removed = true;
        }
        final int[] hs = hashes;
        final int mask = hs.length - 1;
        // Start behind an empty slot (there always is one). Backward shifts never move an entry
        // across an empty slot, so they only move entries that come later in this pass, and
        // every entry is tested exactly once.
        int start = 0;
        while (hs[start] != 0) {
            start++;
        }
        for (int i = (start + 1) & mask; i != start; ) {
            if (hs[i] != 0 && predicate.test(keyAt(i), values[i])) {
                deleteAt(i); // a later entry may move into slot i: test it next
                removed = true;
            } else {
                i = (i + 1) & mask;
            }
        }
        return removed;
    }

    @Override
    public void forEach(ObjectVTypeConsumer<? super K> action) {
        if (hasNullKey) {
            action.accept(null, nullValue);
        }
        final int[] hs = hashes;
        final VType[] vs = values;
        for (int i = 0; i < hs.length; i++) {
            if (hs[i] != 0) {
                action.accept(keyAt(i), vs[i]);
            }
        }
    }

    /** Equal to any {@link ObjectVTypeMap} with the same mappings. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ObjectVTypeMap<?> other) || other.size() != size()) {
            return false;
        }
        if (hasNullKey && !containsEntry(other, null, nullValue)) {
            return false;
        }
        final int[] hs = hashes;
        final Object[] ks = keys;
        final VType[] vs = values;
        for (int i = 0; i < hs.length; i++) {
            if (hs[i] != 0 && !containsEntry(other, ks[i], vs[i])) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code map} maps {@code key} to {@code value}. */
    private static boolean containsEntry(ObjectVTypeMap<?> map, Object key, VType value) {
        int i = map.indexOf(key);
//#if V.floating
        return map.indexExists(i) && VBoxed.compare(map.indexGet(i), value) == 0;
//#else
        return map.indexExists(i) && map.indexGet(i) == value;
//#end
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = hasNullKey ? VBoxed.hashCode(nullValue) : 0;
        final int[] hs = hashes;
        final Object[] ks = keys;
        final VType[] vs = values;
        for (int i = 0; i < hs.length; i++) {
            if (hs[i] != 0) {
                h += ks[i].hashCode() ^ VBoxed.hashCode(vs[i]);
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
            sb.append(k == this ? "(this Map)" : k).append('=').append(v);
        });
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * For {@code key != null} with stored hash {@code h}: slot of {@code key} if present,
     * otherwise {@code ~firstEmptySlot}.
     */
    private int probe(Object key, int h) {
        final int[] hs = hashes;
        final int mask = hs.length - 1;
        int i = h >>> shift;
        for (;;) {
            int s = hs[i];
            if (s == 0) {
                return ~i;
            }
            if (s == h) {
                Object k = keys[i];
                if (k == key || key.equals(k)) {
                    return i;
                }
            }
            i = (i + 1) & mask;
        }
    }

    /** First empty slot on the probe path of stored hash {@code h}. */
    private int emptySlot(int h) {
        final int[] hs = hashes;
        final int mask = hs.length - 1;
        int i = h >>> shift;
        while (hs[i] != 0) {
            i = (i + 1) & mask;
        }
        return i;
    }

    /**
     * Puts a new entry (its key is known to be absent) into the empty slot {@code i}, growing
     * first if needed.
     */
    private void insertNew(int i, int h, K key, VType value) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            i = emptySlot(h);
        }
        hashes[i] = h;
        keys[i] = key;
        values[i] = value;
        assigned++;
    }

    /** Backward-shift deletion: moves later entries of the probe chain into the gap. */
    private void deleteAt(int gap) {
        final int[] hs = hashes;
        final Object[] ks = keys;
        final VType[] vs = values;
        final int mask = hs.length - 1;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 1) & mask;
            int s = hs[i];
            if (s == 0) {
                break;
            }
            int ideal = s >>> sh;
            // The entry can move into gap iff gap lies on its probe path ideal -> i (cyclically).
            if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                hs[gap] = s;
                ks[gap] = ks[i];
                vs[gap] = vs[i];
                gap = i;
            }
        }
        hs[gap] = 0;
        ks[gap] = null;
        vs[gap] = VZero;
        assigned--;
    }

    private int nextCapacity() {
        if (hashes.length >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return hashes.length << 1;
    }

    private void rehash(int newCapacity) {
        final int[] oldHashes = hashes;
        final Object[] oldKeys = keys;
        final VType[] oldValues = values;
        allocate(newCapacity);
        final int[] hs = hashes;
        final Object[] ks = keys;
        final VType[] vs = values;
        for (int j = 0; j < oldHashes.length; j++) {
            int s = oldHashes[j];
            if (s != 0) {
                int i = emptySlot(s);
                hs[i] = s;
                ks[i] = oldKeys[j];
                vs[i] = oldValues[j];
            }
        }
    }

    private void allocate(int capacity) {
        hashes = new int[capacity];
        keys = new Object[capacity];
        values = new VType[capacity];
        shift = 32 - Integer.numberOfTrailingZeros(capacity);
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
