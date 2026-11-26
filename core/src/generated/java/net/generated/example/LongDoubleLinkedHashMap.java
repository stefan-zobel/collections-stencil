// Generated from template linked/KTypeVTypeLinkedHashMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.function.LongDoubleConsumer;
import net.generated.example.function.LongDoublePredicate;

/**
 * Hash map from {@code long} keys to {@code double} values that iterates in insertion order.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Entries:</b> a {@code long[]} of keys and a parallel {@code double[]} of values, in
 *       insertion order. A new entry is appended, so iteration is a sequential scan of both
 *       arrays.</li>
 *   <li><b>Index table:</b> an {@code int[]} with linear probing. A slot holds the position of
 *       an entry plus one in its low bits and hash bits (a tag) in the remaining high bits;
 *       {@code 0} is an empty slot. A lookup compares the tag before it reads a key, so a
 *       lookup of an absent key usually reads only the index table. A lookup of a present key
 *       reads the index table and then the entry.</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed} with a per-instance seed. The
 *       highest bits of the product select the home slot, the bits below them form the tag.</li>
 *   <li><b>Removal</b> leaves a hole in the entry arrays and a tombstone in the index table;
 *       entries do not move. Holes inside the entry range are marked in a bitmap that is
 *       allocated with the first such hole; a hole at either end just shrinks the range. When
 *       the entry arrays are full, they are compacted in place if at least a quarter of them is
 *       unused, and grow otherwise. Both rebuild the index table, which removes the
 *       tombstones.</li>
 *   <li><b>Order:</b> {@link #put} on a present key keeps the position of its entry.
 *       {@link #putLast} and {@link #moveToLast} make an entry the last one (for example for a
 *       least-recently-used cache); {@link #pollFirst} and {@link #pollLast} remove at the
 *       ends.</li>
 *   <li><b>Every key</b> is stored in the entry arrays; key {@code 0} needs no special case.</li>
 *   <li><b>Index handles</b> are entry positions. Removals do not move entries, but the
 *       contract of {@link LongDoubleMap} still applies: a handle is valid until the next
 *       insertion, removal, or {@link #moveToLast}.</li>
 *   <li><b>Load factor:</b> the maximum ratio of entry positions to index slots. The default
 *       {@value #DEFAULT_LOAD_FACTOR} keeps the index table small.</li>
 * </ul>
 * Memory: one key and one value of their exact types per entry position and 4 bytes per index
 * slot, without a link per entry.
 * <p>
 * See {@link LongDoubleMap} for the contract. Two maps are equal if they contain the same
 * mappings, whatever their order. This class is not thread-safe.
 */
public final class LongDoubleLinkedHashMap implements LongDoubleMap {

    /** Default maximum ratio of entries (holes included) to index slots. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_SLOTS = 8;
    private static final int MAX_SLOTS = 1 << 30;
    /** Index slot of a removed entry: position part {@code 0}, so it never denotes an entry. */
    private static final int TOMBSTONE = Integer.MIN_VALUE;

    private final float loadFactor;
    private final long seed;

    /** Keys of the entries; positions {@code [head, end)} are in use. */
    private long[] keys;
    /** Values, parallel to {@link #keys}. */
    private double[] values;
    /** Index table; its length is a power of two. */
    private int[] index;
    /** {@code log2(index.length)}: the number of low bits of a slot that hold the position plus one. */
    private int bits;
    /** {@code 64 - bits}. */
    private int shift;
    /** Position of the first entry (if the map is not empty). */
    private int head;
    /** Position behind the last entry. */
    private int end;
    /** Number of mappings. */
    private int size;
    /** Number of index slots that are not empty: one per entry, plus the tombstones. */
    private int occupied;
    /** Bit {@code e} is set if position {@code e} in {@code (head, end)} is a hole; {@code 0.0} without holes. */
    private long[] holes;

    /** An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings. */
    public LongDoubleLinkedHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public LongDoubleLinkedHashMap(int expectedSize) {
        this(expectedSize, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings at the given load factor.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @param loadFactor the maximum fill of the index table, in {@code (0, 1)}
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large, or
     *         {@code loadFactor} is not in {@code (0, 1)}
     */
    public LongDoubleLinkedHashMap(int expectedSize, float loadFactor) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (!(loadFactor > 0f && loadFactor < 1f)) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.seed = ThreadLocalRandom.current().nextLong();
        allocate(slotsFor(expectedSize, loadFactor));
    }

    /**
     * A map with the mappings of {@code other} in its iteration order.
     *
     * @param other the map to copy (any implementation)
     */
    public LongDoubleLinkedHashMap(LongDoubleMap other) {
        this(other.size());
        putAll(other);
    }

    private static long hash(long key, long seed) {
        return (key ^ seed) * PHI;
    }

    /** The tag of a hash in the high bits of a slot (the bits below those that select the home slot). */
    private static int tag(long hash, int bits) {
        return (int) (hash >>> 32) << bits;
    }

    /** The entry position in a slot, {@code -1} for a tombstone. */
    private int position(int slot) {
        return (slot & (index.length - 1)) - 1;
    }

    private boolean isHole(int e) {
        final long[] h = holes;
        return h != null && (h[e >>> 6] & (1L << e)) != 0;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean containsKey(long key) {
        return find(key) >= 0;
    }

    @Override
    public double get(long key) {
        int e = find(key);
        return e >= 0 ? values[e] : 0.0;
    }

    @Override
    public double getOrDefault(long key, double defaultValue) {
        int e = find(key);
        return e >= 0 ? values[e] : defaultValue;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public double put(long key, double value) {
        int s = probe(key);
        if (s >= 0) {
            int e = position(index[s]);
            double old = values[e];
            values[e] = value;
            return old;
        }
        insertNew(~s, key, value);
        return 0.0;
    }

    @Override
    public boolean putIfAbsent(long key, double value) {
        int s = probe(key);
        if (s >= 0) {
            return false;
        }
        insertNew(~s, key, value);
        return true;
    }

    @Override
    public double addTo(long key, double increment) {
        int s = probe(key);
        if (s >= 0) {
            int e = position(index[s]);
            double v = values[e] + increment;
            values[e] = v;
            return v;
        }
        insertNew(~s, key, increment);
        return increment;
    }

    @Override
    public double remove(long key) {
        int s = probe(key);
        if (s < 0) {
            return 0.0;
        }
        int e = position(index[s]);
        double old = values[e];
        delete(s, e);
        return old;
    }

    @Override
    public void clear() {
        Arrays.fill(index, 0);
        if (holes != null) {
            Arrays.fill(holes, 0L);
        }
        head = 0;
        end = 0;
        size = 0;
        occupied = 0;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int slots = slotsFor(expectedSize, loadFactor);
        if (slots > index.length) {
            rebuild(slots);
        }
    }

    // ------------------------------------------------------------------
    // Insertion order
    // ------------------------------------------------------------------

    /**
     * Associates {@code value} with {@code key} and makes the entry the last one in iteration
     * order, whether or not the key was present.
     *
     * @param key the key
     * @param value the new value
     * @return the previous value, or {@code 0.0} if the key was absent
     */
    public double putLast(long key, double value) {
        int s = probe(key);
        if (s < 0) {
            insertNew(~s, key, value);
            return 0.0;
        }
        int e = position(index[s]);
        double old = values[e];
        values[e] = value;
        moveToLast(s, e, key);
        return old;
    }

    /**
     * Makes the entry of {@code key} the last one in iteration order.
     *
     * @param key the key
     * @return {@code false} if the key is absent
     */
    public boolean moveToLast(long key) {
        int s = probe(key);
        if (s < 0) {
            return false;
        }
        moveToLast(s, position(index[s]), key);
        return true;
    }

    private void moveToLast(int s, int e, long key) {
        if (e == end - 1) {
            return;
        }
        if (end == keys.length) {
            makeRoom();
            s = probe(key);
            e = position(index[s]);
            if (e == end - 1) {
                return;
            }
        }
        double v = values[e];
        drop(e);
        int at = end++;
        keys[at] = key;
        values[at] = v;
        index[s] = (index[s] & -index.length) | (at + 1);
    }

    /**
     * The key of the first entry in iteration order.
     *
     * @return the first key
     * @throws NoSuchElementException if the map is empty
     */
    public long firstKey() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return keys[head];
    }

    /**
     * The key of the last entry in iteration order.
     *
     * @return the last key
     * @throws NoSuchElementException if the map is empty
     */
    public long lastKey() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return keys[end - 1];
    }

    /**
     * Removes the first entry and returns its value.
     *
     * @return the removed value
     * @throws NoSuchElementException if the map is empty
     */
    public double pollFirst() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return indexRemove(head);
    }

    /**
     * Removes the last entry and returns its value.
     *
     * @return the removed value
     * @throws NoSuchElementException if the map is empty
     */
    public double pollLast() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return indexRemove(end - 1);
    }

    /**
     * Removes the first entry and passes its key and value to {@code action}.
     *
     * @param action receives the key and value of the removed entry
     * @return {@code false} if the map is empty
     */
    public boolean pollFirst(LongDoubleConsumer action) {
        if (size == 0) {
            return false;
        }
        long key = keys[head];
        action.accept(key, indexRemove(head));
        return true;
    }

    /**
     * Removes the last entry and passes its key and value to {@code action}.
     *
     * @param action receives the key and value of the removed entry
     * @return {@code false} if the map is empty
     */
    public boolean pollLast(LongDoubleConsumer action) {
        if (size == 0) {
            return false;
        }
        long key = keys[end - 1];
        action.accept(key, indexRemove(end - 1));
        return true;
    }

    /**
     * Handle of the last entry in iteration order, or {@code -1} if the map is empty.
     *
     * @return the handle of the last entry, or {@code -1}
     */
    public int lastIndex() {
        return size == 0 ? -1 : end - 1;
    }

    /**
     * Handle of the entry before {@code index} in iteration order, or {@code -1} if there is none.
     *
     * @param index an existing handle
     * @return the handle of the previous entry, or {@code -1}
     */
    public int prevIndex(int index) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        int e = index - 1;
        if (holes != null) {
            while (e > head && isHole(e)) {
                e--;
            }
        }
        return e >= head ? e : -1;
    }

    // ------------------------------------------------------------------
    // Index handles
    // ------------------------------------------------------------------

    @Override
    public int indexOf(long key) {
        int s = probe(key);
        return s >= 0 ? position(index[s]) : s;
    }

    @Override
    public boolean indexExists(int index) {
        return index >= 0;
    }

    @Override
    public double indexGet(int index) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        return values[index];
    }

    @Override
    public double indexReplace(int index, double value) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        double old = values[index];
        values[index] = value;
        return old;
    }

    @Override
    public void indexInsert(int index, long key, double value) {
        assert index < 0 && probe(key) == index : "not the handle of this absent key: " + index;
        insertNew(~index, key, value);
    }

    @Override
    public double indexRemove(int index) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        double old = values[index];
        delete(probe(keys[index]), index);
        return old;
    }

    // ------------------------------------------------------------------
    // Iteration and bulk removal
    // ------------------------------------------------------------------

    @Override
    public int firstIndex() {
        return size == 0 ? -1 : head;
    }

    @Override
    public int nextIndex(int index) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        int e = index + 1;
        if (holes != null) {
            final int last = end - 1;
            while (e < last && isHole(e)) {
                e++;
            }
        }
        return e < end ? e : -1;
    }

    @Override
    public long indexKey(int index) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        return keys[index];
    }

    @Override
    public boolean removeIf(LongDoublePredicate predicate) {
        boolean removed = false;
        // Removals do not move entries; they may advance head and lower end.
        for (int e = head; e < end; e++) {
            if (!isHole(e) && predicate.test(keys[e], values[e])) {
                delete(probe(keys[e]), e);
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public void forEach(LongDoubleConsumer action) {
        final long[] ks = keys;
        final double[] vs = values;
        final long[] h = holes;
        final int to = end;
        if (h == null) {
            for (int e = head; e < to; e++) {
                action.accept(ks[e], vs[e]);
            }
        } else {
            for (int e = head; e < to; e++) {
                if ((h[e >>> 6] & (1L << e)) == 0) {
                    action.accept(ks[e], vs[e]);
                }
            }
        }
    }

    /**
     * A new {@link LinkedHashMap} with the mappings of this map, boxed, in iteration order.
     *
     * @return a new {@link LinkedHashMap}
     */
    @Override
    public Map<Long, Double> toMap() {
        Map<Long, Double> map = LinkedHashMap.newLinkedHashMap(size);
        forEach(map::put);
        return map;
    }

    /** Equal to any {@link LongDoubleMap} with the same mappings, whatever the order. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LongDoubleMap other) || other.size() != size) {
            return false;
        }
        for (int e = head; e < end; e++) {
            if (!isHole(e)) {
                int i = other.indexOf(keys[e]);
                if (!other.indexExists(i) || Double.compare(other.indexGet(i), values[e]) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = 0;
        for (int e = head; e < end; e++) {
            if (!isHole(e)) {
                h += Long.hashCode(keys[e]) ^ Double.hashCode(values[e]);
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size, 1 << 16) * 12 + 2).append('{');
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

    /** Entry position of {@code key}, or {@code -1} if absent. */
    private int find(long key) {
        final int[] ix = index;
        final long[] ks = keys;
        final int mask = ix.length - 1;
        final int b = bits;
        long h = hash(key, seed);
        int tag = tag(h, b);
        int i = (int) (h >>> shift);
        for (int s; (s = ix[i]) != 0; i = (i + 1) & mask) {
            if ((s ^ tag) >>> b == 0) {
                int e = (s & mask) - 1;
                if (e >= 0 && ks[e] == key) {
                    return e;
                }
            }
        }
        return -1;
    }

    /**
     * Index slot of {@code key} if present, otherwise {@code ~slot} of the slot for its
     * insertion: the first tombstone on the probe path, or the empty slot that ends it.
     */
    private int probe(long key) {
        final int[] ix = index;
        final long[] ks = keys;
        final int mask = ix.length - 1;
        final int b = bits;
        long h = hash(key, seed);
        int tag = tag(h, b);
        int i = (int) (h >>> shift);
        int free = -1;
        for (int s; (s = ix[i]) != 0; i = (i + 1) & mask) {
            if ((s ^ tag) >>> b == 0) {
                int e = (s & mask) - 1;
                if (e >= 0 && ks[e] == key) {
                    return i;
                }
            }
            if (s == TOMBSTONE && free < 0) {
                free = i;
            }
        }
        return ~(free >= 0 ? free : i);
    }

    /** Appends an absent key; {@code s} is its insertion slot from {@link #probe}. */
    private void insertNew(int s, long key, double value) {
        // A position that a removal at the end gave back has left its tombstone behind, so the
        // index table can fill up before the entry arrays do.
        if (end == keys.length || (index[s] == 0 && occupied == keys.length)) {
            makeRoom();
            s = ~probe(key);
        }
        if (index[s] == 0) {
            occupied++;
        }
        int at = end++;
        keys[at] = key;
        values[at] = value;
        index[s] = tag(hash(key, seed), bits) | (at + 1);
        size++;
    }

    /** Removes the entry at position {@code e}, whose index slot is {@code s}. */
    private void delete(int s, int e) {
        index[s] = TOMBSTONE;
        size--;
        drop(e);
    }

    /** Takes position {@code e} out of the entry range: shrinks the range at its ends, marks a hole inside. */
    private void drop(int e) {
        if (e == head) {
            int h = e + 1;
            while (h < end && isHole(h)) {
                h++;
            }
            head = h;
        } else if (e == end - 1) {
            int t = e;
            final long[] hs = holes;
            if (hs != null) {
                while (t - 1 > head && (hs[(t - 1) >>> 6] & (1L << (t - 1))) != 0) {
                    t--;
                    hs[t >>> 6] &= ~(1L << t); // the position is appended to again
                }
            }
            end = t;
        } else {
            if (holes == null) {
                holes = new long[(keys.length + 63) >>> 6];
            }
            holes[e >>> 6] |= 1L << e;
        }
    }

    /** The entry arrays are full: compacts them if a quarter is unused, grows otherwise. */
    private void makeRoom() {
        int capacity = keys.length;
        if (capacity - size >= Math.max(1, capacity >>> 2)) {
            rebuild(index.length);
        } else {
            if (index.length >= MAX_SLOTS) {
                throw new IllegalStateException("Maximum capacity exceeded: " + capacity);
            }
            rebuild(index.length << 1);
        }
    }

    /** Moves the entries to the front of entry arrays for an index table of {@code slots} slots, and rebuilds the table. */
    private void rebuild(int slots) {
        final long[] oldKeys = keys;
        final double[] oldValues = values;
        final int from = head;
        final int to = end;
        final boolean inPlace = slots == index.length;
        if (inPlace) {
            Arrays.fill(index, 0);
        } else {
            allocate(slots);
        }
        final long[] ks = keys;
        final double[] vs = values;
        int n = 0;
        for (int e = from; e < to; e++) {
            if (!isHole(e)) {
                ks[n] = oldKeys[e];
                vs[n] = oldValues[e];
                n++;
            }
        }
        if (inPlace) {
            if (holes != null) {
                Arrays.fill(holes, 0L);
            }
        } else {
            holes = null;
        }
        head = 0;
        end = n;
        occupied = n;
        final int[] ix = index;
        final int mask = ix.length - 1;
        final int b = bits;
        final int sh = shift;
        final long sd = seed;
        for (int e = 0; e < n; e++) {
            long h = hash(ks[e], sd);
            int i = (int) (h >>> sh);
            while (ix[i] != 0) {
                i = (i + 1) & mask;
            }
            ix[i] = tag(h, b) | (e + 1);
        }
    }

    /** New arrays; {@link #holes} is still the bitmap of the old ones. */
    private void allocate(int slots) {
        int capacity = capacity(slots, loadFactor);
        keys = new long[capacity];
        values = new double[capacity];
        index = new int[slots];
        bits = Integer.numberOfTrailingZeros(slots);
        shift = 64 - bits;
    }

    /** Number of entry positions for an index table of {@code slots} slots (at least one slot stays empty). */
    private static int capacity(int slots, float loadFactor) {
        return Math.min(slots - 1, Math.max(1, (int) (slots * (double) loadFactor)));
    }

    /** Index slots (a power of two) for {@code expectedSize} entries. */
    static int slotsFor(int expectedSize, float loadFactor) {
        long needed = (long) Math.ceil(expectedSize / (double) loadFactor) + 1;
        if (needed > MAX_SLOTS) {
            throw new IllegalArgumentException("expectedSize too large: " + expectedSize);
        }
        int slots = needed <= MIN_SLOTS ? MIN_SLOTS : Integer.highestOneBit((int) needed - 1) << 1;
        while (capacity(slots, loadFactor) < expectedSize && slots < MAX_SLOTS) {
            slots <<= 1;
        }
        return slots;
    }

    /** Number of entry positions (for tests). */
    int capacity() {
        return keys.length;
    }

    /** Number of positions in the entry range that are holes (for tests). */
    int holeCount() {
        return end - head - size;
    }
}
