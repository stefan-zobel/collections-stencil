// Generated from template linear/KTypeVTypeHashMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.function.IntShortConsumer;
import net.generated.example.function.IntShortPredicate;

/**
 * Open-addressing hash map from {@code int} keys to {@code short} values.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Packed layout:</b> each slot is a single {@code long}. The key is in the low
 *       32 bits and the value in the high bits. A lookup touches exactly one array
 *       element, so key and value always share a cache line.</li>
 *   <li><b>Empty marker:</b> a slot whose key bits are {@code 0} is empty. Key {@code 0}
 *       itself is stored outside the table ({@link #hasZeroKey}, {@link #zeroValue}).</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed}. The slot is taken from the
 *       <em>high</em> bits of the 64-bit product, and {@code seed} is chosen at random
 *       per instance. The per-instance seed prevents the quadratic clustering that occurs
 *       when you copy one linear-probing map into another in slot order.</li>
 *   <li><b>Collision resolution:</b> linear probing. Removal uses backward-shift
 *       deletion, so there are no tombstones.</li>
 *   <li><b>Index handles:</b> {@link #indexOf} and the {@code index*} methods let you
 *       do "find, then read, update or insert" with a single hash computation and no
 *       allocation.</li>
 *   <li><b>Load factor:</b> the default {@value #DEFAULT_LOAD_FACTOR} keeps the table small.
 *       For maximum speed, pass {@code 0.5f}: probe chains get shorter, especially for lookups
 *       of absent keys, at up to twice the memory.</li>
 * </ul>
 * See {@link IntShortMap} for the contract. This class is not thread-safe. Iteration order
 * is unspecified and differs between instances.
 */
public final class IntShortHashMap implements IntShortMap {

    /** Default maximum ratio of occupied slots to capacity. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_CAPACITY = 4;
    private static final int MAX_CAPACITY = 1 << 30;
    private static final long KEY_BITS = 0xFFFF_FFFFL;

    private final float loadFactor;
    private final int seed;

    /** Packed slots; length is a power of two. */
    private long[] table;
    /** {@code 64 - log2(table.length)}. */
    private int shift;
    /** Number of occupied slots in {@link #table} (the zero key is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;
    private short zeroValue;

    /**
     * An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public IntShortHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public IntShortHashMap(int expectedSize) {
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
    public IntShortHashMap(int expectedSize, float loadFactor) {
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
    public IntShortHashMap(IntShortMap other) {
        this(other.size());
        putAll(other);
    }

    // ------------------------------------------------------------------
    // Packing / hashing (the type-specific core)
    // ------------------------------------------------------------------

    private static long pack(int key, short value) {
        return ((long) value << 32) | (key & KEY_BITS);
    }

    private static int keyOf(long entry) {
        return (int) entry;
    }

    private static short valueOf(long entry) {
        return (short) (entry >>> 32);
    }

    private static long withValue(long entry, short value) {
        return (entry & KEY_BITS) | ((long) value << 32);
    }

    /** Fibonacci hashing: high bits of {@code (key ^ seed) * PHI}. Result is in {@code [0, 2^(64 - shift))}. */
    private static int mix(int key, int seed, int shift) {
        return (int) (((key ^ seed) * PHI) >>> shift);
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
    public boolean containsKey(int key) {
        if (key == 0) {
            return hasZeroKey;
        }
        return probe(key) >= 0;
    }

    @Override
    public short get(int key) {
        return getOrDefault(key, (short) 0);
    }

    @Override
    public short getOrDefault(int key, short defaultValue) {
        if (key == 0) {
            return hasZeroKey ? zeroValue : defaultValue;
        }
        final long[] tab = table;
        final int mask = tab.length - 1;
        int i = mix(key, seed, shift) & mask;
        long e;
        int k;
        while ((k = keyOf(e = tab[i])) != key) {
            if (k == 0) {
                return defaultValue;
            }
            i = (i + 1) & mask;
        }
        return valueOf(e);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public short put(int key, short value) {
        if (key == 0) {
            short old = hasZeroKey ? zeroValue : (short) 0;
            hasZeroKey = true;
            zeroValue = value;
            return old;
        }
        int i = probe(key);
        if (i >= 0) {
            long e = table[i];
            table[i] = withValue(e, value);
            return valueOf(e);
        }
        insertNew(~i, pack(key, value));
        return (short) 0;
    }

    @Override
    public boolean putIfAbsent(int key, short value) {
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
        insertNew(~i, pack(key, value));
        return true;
    }

    @Override
    public short addTo(int key, short increment) {
        if (key == 0) {
            short v = hasZeroKey ? (short) (zeroValue + increment) : increment;
            hasZeroKey = true;
            zeroValue = v;
            return v;
        }
        int i = probe(key);
        if (i >= 0) {
            long e = table[i];
            short v = (short) (valueOf(e) + increment);
            table[i] = withValue(e, v);
            return v;
        }
        insertNew(~i, pack(key, increment));
        return increment;
    }

    @Override
    public short remove(int key) {
        if (key == 0) {
            if (!hasZeroKey) {
                return (short) 0;
            }
            short old = zeroValue;
            hasZeroKey = false;
            zeroValue = (short) 0;
            return old;
        }
        int i = probe(key);
        if (i < 0) {
            return (short) 0;
        }
        short old = valueOf(table[i]);
        deleteAt(i);
        return old;
    }

    @Override
    public void clear() {
        Arrays.fill(table, 0L);
        assigned = 0;
        hasZeroKey = false;
        zeroValue = (short) 0;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int cap = capacityFor(expectedSize, loadFactor);
        if (cap > table.length) {
            rehash(cap);
        }
    }

    // ------------------------------------------------------------------
    // Index handles (one hash computation for find-then-modify)
    // ------------------------------------------------------------------

    @Override
    public int indexOf(int key) {
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
    public short indexGet(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? zeroValue : valueOf(table[index]);
    }

    @Override
    public short indexReplace(int index, short value) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == table.length) {
            short old = zeroValue;
            zeroValue = value;
            return old;
        }
        long e = table[index];
        table[index] = withValue(e, value);
        return valueOf(e);
    }

    @Override
    public void indexInsert(int index, int key, short value) {
        assert index < 0 : "index exists: " + index;
        int slot = ~index;
        if (slot == table.length) {
            assert key == 0;
            hasZeroKey = true;
            zeroValue = value;
            return;
        }
        assert key != 0 && keyOf(table[slot]) == 0 && probe(key) == index;
        insertNew(slot, pack(key, value));
    }

    @Override
    public short indexRemove(int index) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == table.length) {
            short old = zeroValue;
            hasZeroKey = false;
            zeroValue = (short) 0;
            return old;
        }
        short old = valueOf(table[index]);
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
        return index == table.length ? -1 : occupiedFrom(index + 1);
    }

    @Override
    public int indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? 0 : keyOf(table[index]);
    }

    /** First occupied slot at or after {@code from}, then the zero key's virtual slot, then {@code -1}. */
    private int occupiedFrom(int from) {
        final long[] tab = table;
        for (int i = from; i < tab.length; i++) {
            if (tab[i] != 0L) {
                return i;
            }
        }
        return hasZeroKey ? tab.length : -1;
    }

    @Override
    public boolean removeIf(IntShortPredicate predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(0, zeroValue)) {
            hasZeroKey = false;
            zeroValue = (short) 0;
            removed = true;
        }
        final long[] tab = table;
        final int mask = tab.length - 1;
        // Start behind an empty slot (there always is one). Backward shifts never move an entry
        // across an empty slot, so they only move entries that come later in this pass, and
        // every entry is tested exactly once.
        int start = 0;
        while (tab[start] != 0L) {
            start++;
        }
        for (int i = (start + 1) & mask; i != start; ) {
            long e = tab[i];
            if (e != 0L && predicate.test(keyOf(e), valueOf(e))) {
                deleteAt(i); // a later entry may move into slot i: test it next
                removed = true;
            } else {
                i = (i + 1) & mask;
            }
        }
        return removed;
    }

    @Override
    public void forEach(IntShortConsumer action) {
        if (hasZeroKey) {
            action.accept(0, zeroValue);
        }
        for (long e : table) {
            int k = keyOf(e);
            if (k != 0) {
                action.accept(k, valueOf(e));
            }
        }
    }

    /** Equal to any {@link IntShortMap} with the same mappings. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntShortMap other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !containsEntry(other, 0, zeroValue)) {
            return false;
        }
        for (long e : table) {
            int k = keyOf(e);
            if (k != 0 && !containsEntry(other, k, valueOf(e))) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code map} maps {@code key} to {@code value}. */
    private static boolean containsEntry(IntShortMap map, int key, short value) {
        int i = map.indexOf(key);
        return map.indexExists(i) && map.indexGet(i) == value;
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = hasZeroKey ? zeroValue : 0;
        for (long e : table) {
            int k = keyOf(e);
            if (k != 0) {
                h += k ^ valueOf(e);
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size(), 1 << 16) * 8 + 2).append('{');
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

    /** For {@code key != 0}: slot of {@code key} if present, otherwise {@code ~firstEmptySlot}. */
    private int probe(int key) {
        final long[] tab = table;
        final int mask = tab.length - 1;
        int i = mix(key, seed, shift) & mask;
        for (;;) {
            int k = keyOf(tab[i]);
            if (k == key) {
                return i;
            }
            if (k == 0) {
                return ~i;
            }
            i = (i + 1) & mask;
        }
    }

    /** Puts a new entry (its key is known to be absent) into the empty {@code slot}, growing first if needed. */
    private void insertNew(int slot, long entry) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            slot = ~probe(keyOf(entry));
        }
        table[slot] = entry;
        assigned++;
    }

    /** Backward-shift deletion: moves later entries of the probe chain into the gap. */
    private void deleteAt(int gap) {
        final long[] tab = table;
        final int mask = tab.length - 1;
        final int s = seed;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 1) & mask;
            long e = tab[i];
            int k = keyOf(e);
            if (k == 0) {
                break;
            }
            int ideal = mix(k, s, sh) & mask;
            // e can move into gap iff gap lies on its probe path ideal -> i (cyclically).
            if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                tab[gap] = e;
                gap = i;
            }
        }
        tab[gap] = 0L;
        assigned--;
    }

    private int nextCapacity() {
        if (table.length >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return table.length << 1;
    }

    private void rehash(int newCapacity) {
        final long[] old = table;
        allocate(newCapacity);
        final long[] tab = table;
        final int mask = tab.length - 1;
        final int s = seed;
        final int sh = shift;
        for (long e : old) {
            int k = keyOf(e);
            if (k != 0) {
                int i = mix(k, s, sh) & mask;
                while (keyOf(tab[i]) != 0) {
                    i = (i + 1) & mask;
                }
                tab[i] = e;
            }
        }
    }

    private void allocate(int capacity) {
        table = new long[capacity];
        shift = 64 - Integer.numberOfTrailingZeros(capacity);
        maxFill = maxFill(capacity, loadFactor);
    }

    private static int maxFill(int capacity, float loadFactor) {
        // Always keep at least one empty slot so that probing terminates.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * (double) loadFactor)));
    }

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
