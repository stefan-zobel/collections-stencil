// Generated from template linear-wide/KTypeVTypeHashMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.function.LongFloatConsumer;
import net.generated.example.function.LongFloatPredicate;

/**
 * Open-addressing hash map from {@code long} keys to {@code float} values.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Interleaved layout:</b> one {@code long[]} holds the key of slot {@code s} at index
 *       {@code 2s} and the value bits at {@code 2s + 1}. The pair is 16-byte aligned, so key
 *       and value always share a cache line and a lookup costs one memory access.</li>
 *   <li><b>Empty marker:</b> a slot whose key is {@code 0} is empty. Key {@code 0} itself is
 *       stored outside the table ({@link #hasZeroKey}, {@link #zeroValue}).</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed}. The slot is taken from the
 *       <em>high</em> bits of the 64-bit product, which depend on all bits of the key, and
 *       {@code seed} is chosen at random per instance.</li>
 *   <li><b>Collision resolution:</b> linear probing. Removal uses backward-shift
 *       deletion, so there are no tombstones.</li>
 *   <li><b>Index handles:</b> {@link #indexOf} and the {@code index*} methods let you
 *       do "find, then read, update or insert" with a single hash computation and no
 *       allocation.</li>
 *   <li><b>Load factor:</b> the default {@value #DEFAULT_LOAD_FACTOR} keeps the table small.
 *       For maximum speed, pass {@code 0.5f}: probe chains get shorter, especially for lookups
 *       of absent keys, at up to twice the memory.</li>
 * </ul>
 * See {@link LongFloatMap} for the contract. This class is not thread-safe. Iteration order
 * is unspecified and differs between instances.
 */
public final class LongFloatHashMap implements LongFloatMap {

    /** Default maximum ratio of occupied slots to capacity. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_CAPACITY = 4;
    /** In slots; the table has twice as many words. */
    private static final int MAX_CAPACITY = 1 << 29;

    private final float loadFactor;
    private final long seed;

    /** Interleaved keys and value bits; length is twice the capacity, a power of two. */
    private long[] table;
    /** {@code 64 - log2(table.length)}. */
    private int shift;
    /** Number of occupied slots in {@link #table} (the zero key is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;
    private float zeroValue;

    /**
     * An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public LongFloatHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public LongFloatHashMap(int expectedSize) {
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
    public LongFloatHashMap(int expectedSize, float loadFactor) {
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
    public LongFloatHashMap(LongFloatMap other) {
        this(other.size());
        putAll(other);
    }

    // ------------------------------------------------------------------
    // Value encoding / hashing (the type-specific core)
    // ------------------------------------------------------------------

    /** The value as stored in the value word. */
    private static long bits(float value) {
        return Float.floatToRawIntBits(value);
    }

    /** The value stored in a value word. */
    private static float valueOf(long bits) {
        return Float.intBitsToFloat((int) bits);
    }

    /**
     * Fibonacci hashing: high bits of {@code (key ^ seed) * PHI}, cleared lowest bit. Result
     * is the even table index of the key's home slot, in {@code [0, 2^(64 - shift))}.
     */
    private static int mix(long key, long seed, int shift) {
        return (int) (((key ^ seed) * PHI) >>> shift) & ~1;
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
    public boolean containsKey(long key) {
        if (key == 0) {
            return hasZeroKey;
        }
        return probe(key) >= 0;
    }

    @Override
    public float get(long key) {
        return getOrDefault(key, 0.0f);
    }

    @Override
    public float getOrDefault(long key, float defaultValue) {
        if (key == 0) {
            return hasZeroKey ? zeroValue : defaultValue;
        }
        final long[] tab = table;
        final int mask = tab.length - 2;
        int i = mix(key, seed, shift);
        long k;
        while ((k = tab[i]) != key) {
            if (k == 0) {
                return defaultValue;
            }
            i = (i + 2) & mask;
        }
        return valueOf(tab[i + 1]);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public float put(long key, float value) {
        if (key == 0) {
            float old = hasZeroKey ? zeroValue : 0.0f;
            hasZeroKey = true;
            zeroValue = value;
            return old;
        }
        int i = probe(key);
        if (i >= 0) {
            final long[] tab = table;
            float old = valueOf(tab[i + 1]);
            tab[i + 1] = bits(value);
            return old;
        }
        insertNew(~i, key, bits(value));
        return 0.0f;
    }

    @Override
    public boolean putIfAbsent(long key, float value) {
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
        insertNew(~i, key, bits(value));
        return true;
    }

    @Override
    public float addTo(long key, float increment) {
        if (key == 0) {
            float v = hasZeroKey ? zeroValue + increment : increment;
            hasZeroKey = true;
            zeroValue = v;
            return v;
        }
        int i = probe(key);
        if (i >= 0) {
            final long[] tab = table;
            float v = valueOf(tab[i + 1]) + increment;
            tab[i + 1] = bits(v);
            return v;
        }
        insertNew(~i, key, bits(increment));
        return increment;
    }

    @Override
    public float remove(long key) {
        if (key == 0) {
            if (!hasZeroKey) {
                return 0.0f;
            }
            float old = zeroValue;
            hasZeroKey = false;
            zeroValue = 0.0f;
            return old;
        }
        int i = probe(key);
        if (i < 0) {
            return 0.0f;
        }
        float old = valueOf(table[i + 1]);
        deleteAt(i);
        return old;
    }

    @Override
    public void clear() {
        Arrays.fill(table, 0L);
        assigned = 0;
        hasZeroKey = false;
        zeroValue = 0.0f;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int cap = capacityFor(expectedSize, loadFactor);
        if (cap > table.length >>> 1) {
            rehash(cap);
        }
    }

    // ------------------------------------------------------------------
    // Index handles (one hash computation for find-then-modify)
    // ------------------------------------------------------------------

    @Override
    public int indexOf(long key) {
        if (key == 0) {
            int z = table.length; // virtual slot for the zero key
            return hasZeroKey ? z : ~z;
        }
        return probe(key);
    }

    @Override
    public boolean indexExists(int index) {
        return index >= 0;
    }

    @Override
    public float indexGet(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? zeroValue : valueOf(table[index + 1]);
    }

    @Override
    public float indexReplace(int index, float value) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == table.length) {
            float old = zeroValue;
            zeroValue = value;
            return old;
        }
        float old = valueOf(table[index + 1]);
        table[index + 1] = bits(value);
        return old;
    }

    @Override
    public void indexInsert(int index, long key, float value) {
        assert index < 0 : "index exists: " + index;
        int slot = ~index;
        if (slot == table.length) {
            assert key == 0;
            hasZeroKey = true;
            zeroValue = value;
            return;
        }
        assert key != 0 && table[slot] == 0 && probe(key) == index;
        insertNew(slot, key, bits(value));
    }

    @Override
    public float indexRemove(int index) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == table.length) {
            float old = zeroValue;
            hasZeroKey = false;
            zeroValue = 0.0f;
            return old;
        }
        float old = valueOf(table[index + 1]);
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
        return index == table.length ? -1 : occupiedFrom(index + 2);
    }

    @Override
    public long indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? 0L : table[index];
    }

    /** First occupied key index at or after {@code from}, then the zero key's virtual slot, then {@code -1}. */
    private int occupiedFrom(int from) {
        final long[] tab = table;
        for (int i = from; i < tab.length; i += 2) {
            if (tab[i] != 0L) {
                return i;
            }
        }
        return hasZeroKey ? tab.length : -1;
    }

    @Override
    public boolean removeIf(LongFloatPredicate predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(0L, zeroValue)) {
            hasZeroKey = false;
            zeroValue = 0.0f;
            removed = true;
        }
        final long[] tab = table;
        final int mask = tab.length - 2;
        // Start behind an empty slot (there always is one). Backward shifts never move an entry
        // across an empty slot, so they only move entries that come later in this pass, and
        // every entry is tested exactly once.
        int start = 0;
        while (tab[start] != 0L) {
            start += 2;
        }
        for (int i = (start + 2) & mask; i != start; ) {
            long k = tab[i];
            if (k != 0L && predicate.test(k, valueOf(tab[i + 1]))) {
                deleteAt(i); // a later entry may move into slot i: test it next
                removed = true;
            } else {
                i = (i + 2) & mask;
            }
        }
        return removed;
    }

    @Override
    public void forEach(LongFloatConsumer action) {
        if (hasZeroKey) {
            action.accept(0, zeroValue);
        }
        final long[] tab = table;
        for (int i = 0; i < tab.length; i += 2) {
            long k = tab[i];
            if (k != 0) {
                action.accept(k, valueOf(tab[i + 1]));
            }
        }
    }

    /** Equal to any {@link LongFloatMap} with the same mappings. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LongFloatMap other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !containsEntry(other, 0, zeroValue)) {
            return false;
        }
        final long[] tab = table;
        for (int i = 0; i < tab.length; i += 2) {
            long k = tab[i];
            if (k != 0 && !containsEntry(other, k, valueOf(tab[i + 1]))) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code map} maps {@code key} to {@code value}. */
    private static boolean containsEntry(LongFloatMap map, long key, float value) {
        int i = map.indexOf(key);
        return map.indexExists(i) && Float.compare(map.indexGet(i), value) == 0;
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = hasZeroKey ? Float.hashCode(zeroValue) : 0;
        final long[] tab = table;
        for (int i = 0; i < tab.length; i += 2) {
            long k = tab[i];
            if (k != 0) {
                h += Long.hashCode(k) ^ Float.hashCode(valueOf(tab[i + 1]));
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size(), 1 << 16) * 12 + 2).append('{');
        forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v);
        });
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** For {@code key != 0}: index of {@code key} if present, otherwise {@code ~indexOfFirstEmptySlot}. */
    private int probe(long key) {
        final long[] tab = table;
        final int mask = tab.length - 2;
        int i = mix(key, seed, shift);
        for (;;) {
            long k = tab[i];
            if (k == key) {
                return i;
            }
            if (k == 0) {
                return ~i;
            }
            i = (i + 2) & mask;
        }
    }

    /** Puts a new entry (its key is known to be absent) into the empty slot at index {@code i}, growing first if needed. */
    private void insertNew(int i, long key, long valueBits) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            i = ~probe(key);
        }
        final long[] tab = table;
        tab[i] = key;
        tab[i + 1] = valueBits;
        assigned++;
    }

    /** Backward-shift deletion: moves later entries of the probe chain into the gap. */
    private void deleteAt(int gap) {
        final long[] tab = table;
        final int mask = tab.length - 2;
        final long s = seed;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 2) & mask;
            long k = tab[i];
            if (k == 0) {
                break;
            }
            int ideal = mix(k, s, sh);
            // The entry can move into gap iff gap lies on its probe path ideal -> i (cyclically).
            if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                tab[gap] = k;
                tab[gap + 1] = tab[i + 1];
                gap = i;
            }
        }
        tab[gap] = 0L;
        tab[gap + 1] = 0L;
        assigned--;
    }

    private int nextCapacity() {
        int capacity = table.length >>> 1;
        if (capacity >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return capacity << 1;
    }

    private void rehash(int newCapacity) {
        final long[] old = table;
        allocate(newCapacity);
        final long[] tab = table;
        final int mask = tab.length - 2;
        final long s = seed;
        final int sh = shift;
        for (int j = 0; j < old.length; j += 2) {
            long k = old[j];
            if (k != 0) {
                int i = mix(k, s, sh);
                while (tab[i] != 0) {
                    i = (i + 2) & mask;
                }
                tab[i] = k;
                tab[i + 1] = old[j + 1];
            }
        }
    }

    /** Allocates a table for {@code capacity} slots. */
    private void allocate(int capacity) {
        table = new long[capacity << 1];
        shift = 64 - Integer.numberOfTrailingZeros(capacity << 1);
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
