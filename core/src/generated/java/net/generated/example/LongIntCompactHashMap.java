// Generated from template compact-wide/KTypeVTypeCompactHashMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.function.LongIntConsumer;
import net.generated.example.function.LongIntPredicate;

/**
 * Open-addressing hash map from {@code long} keys to {@code int} values for high load
 * factors: blocks with one-byte tags, in the style of F14 and SwissTable.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Blocks:</b> the table is a {@code long[]} of blocks with sixteen words (128 bytes,
 *       two cache lines). Word 0 is the block's <em>control word</em>, words 1-7 are the keys
 *       of its seven slots, words 9-15 their value bits (the value of the key at index
 *       {@code i} is at {@code i + 8}). Word 8 is unused.</li>
 *   <li><b>Control word:</b> bytes 0-6 hold one tag per slot ({@code 0} = empty, otherwise
 *       {@code 0x80} plus seven hash bits). Byte 7 is the block's overflow counter.</li>
 *   <li><b>Lookup:</b> one SWAR comparison of the control word with the key's tag yields the
 *       candidate slots. A miss usually reads only the first cache line of one block, a hit
 *       one key and its value in the two lines of the same 128-byte block.</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed} with a per-instance seed. The
 *       highest bits of the product select the home block, the next seven bits form the tag.</li>
 *   <li><b>Overflow:</b> if the home block is full, the entry goes to the first block with a
 *       free slot along a per-key sequence of blocks: first the neighbor block, then
 *       double hashing. Every block passed on the way increments its saturating 8-bit counter.
 *       A lookup stops at the first block whose counter is zero. Removal walks the same
 *       sequence and decrements the counters, so there are no tombstones.</li>
 *   <li><b>Key 0</b> is stored outside the table; an empty slot holds key {@code 0}.</li>
 *   <li><b>Index handles</b> as in {@link LongIntHashMap}.</li>
 * </ul>
 * Memory: 128 bytes per seven slots; at the default load factor about the same as
 * {@link LongIntHashMap} at its default load factor.
 * <p>
 * See {@link LongIntMap} for the contract. This class is not thread-safe. Iteration order
 * is unspecified and differs between instances.
 */
public final class LongIntCompactHashMap implements LongIntMap {

    /** Default maximum ratio of entries to slots. */
    public static final float DEFAULT_LOAD_FACTOR = 0.85f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    private static final int SLOTS_PER_BLOCK = 7;
    private static final int BLOCK_SHIFT = 4; // sixteen words per block
    /** Distance from a key word to its value word. */
    private static final int VALUE_OFFSET = 8;
    /**
     * Block {@code b} starts at array index {@code 16 * b + PAD}. With the usual 16-byte array
     * header, index {@code 14} is 128-byte aligned whenever the array object is, which holds
     * for large arrays (G1 allocates them at region boundaries). Then every block occupies
     * exactly one aligned pair of cache lines. The alignment affects only speed.
     */
    private static final int PAD = 14;
    private static final int MIN_BLOCKS = 2;
    private static final int MAX_BLOCKS = 1 << 26;
    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    /** Multiplier for the overflow step (odd). */
    private static final long STEP_MULTIPLIER = 0xC2B2_AE3D_27D4_EB4FL;
    /** {@code 0x01} in every byte. */
    private static final long BYTES_ONE = 0x0101_0101_0101_0101L;
    /** High bit of the seven tag bytes (byte 7 is the counter and never matches). */
    private static final long TAG_HIGH_BITS = 0x0080_8080_8080_8080L;
    private static final int TAG_FLAG = 0x80;
    private static final int COUNTER_SHIFT = 56;
    private static final long COUNTER_ONE = 1L << COUNTER_SHIFT;
    /** A saturated counter is never decremented again (until the next rehash). */
    private static final int COUNTER_SATURATED = 0xFF;

    private final float loadFactor;
    private final long seed;

    /** Blocks; length is {@code 16 * blocks + 16}, see {@link #PAD}. */
    private long[] table;
    private int blockMask;
    /** {@code 64 - log2(blocks)}: the product's high bits select the block. */
    private int blockShift;
    /** Number of entries in {@link #table} (the zero key is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;
    private int zeroValue;

    /**
     * An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public LongIntCompactHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty map with room for {@code expectedSize} mappings and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public LongIntCompactHashMap(int expectedSize) {
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
    public LongIntCompactHashMap(int expectedSize, float loadFactor) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (!(loadFactor > 0f && loadFactor < 1f)) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.seed = ThreadLocalRandom.current().nextLong();
        allocate(blocksFor(expectedSize, loadFactor));
    }

    /**
     * A map with the mappings of {@code other} (any implementation) and the default load factor.
     *
     * @param other the map to copy (any implementation)
     */
    public LongIntCompactHashMap(LongIntMap other) {
        this(other.size());
        putAll(other);
    }

    // ------------------------------------------------------------------
    // Value encoding / hashing (the type-specific core)
    // ------------------------------------------------------------------

    /** The value as stored in the value word. */
    private static long bits(int value) {
        return value;
    }

    /** The value stored in a value word. */
    private static int valueOf(long bits) {
        return (int) bits;
    }

    private static long hash(long key, long seed) {
        return (key ^ seed) * PHI;
    }

    /** The tag (seven hash bits below the block bits, plus the flag) in every byte. */
    private static long tagPattern(long hash, int blockShift) {
        return tag(hash, blockShift) * BYTES_ONE;
    }

    private static int tag(long hash, int blockShift) {
        return ((int) (hash >>> (blockShift - 7)) & 0x7F) | TAG_FLAG;
    }

    /**
     * Odd block step of the overflow sequence; callers mask it with {@link #blockMask}.
     * The sequence of a key is {@code home, home + 1, home + 1 + step, home + 1 + 2 * step, ...}:
     * the first overflow goes to the neighbor block, later ones are spread by double hashing
     * so that chains stay short. With an odd step, the sequence reaches every block within
     * {@code blocks} steps after the home block.
     */
    private static int step(long key, long seed) {
        return (int) (((key ^ seed) * STEP_MULTIPLIER) >>> 33) | 1;
    }

    /**
     * High bit set in each of the bytes 0-6 of {@code control} that equals the corresponding
     * byte of {@code pattern}. Exact for the lowest match; a higher byte may be reported
     * falsely after a match (borrow), which the key comparison filters out.
     */
    private static long matches(long control, long pattern) {
        long x = control ^ pattern;
        return (x - BYTES_ONE) & ~x & TAG_HIGH_BITS;
    }

    /** Array index of the key of the slot of the lowest byte flagged in {@code bits}. */
    private static int slotIndex(int base, long bits) {
        return base + 1 + (Long.numberOfTrailingZeros(bits) >>> 3);
    }

    private static int blockBase(int block) {
        return (block << BLOCK_SHIFT) + PAD;
    }

    private static int counter(long control) {
        return (int) (control >>> COUNTER_SHIFT);
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
        return find(key) >= 0;
    }

    @Override
    public int get(long key) {
        return getOrDefault(key, 0);
    }

    @Override
    public int getOrDefault(long key, int defaultValue) {
        if (key == 0) {
            return hasZeroKey ? zeroValue : defaultValue;
        }
        int i = find(key);
        return i >= 0 ? valueOf(table[i + VALUE_OFFSET]) : defaultValue;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public int put(long key, int value) {
        if (key == 0) {
            int old = hasZeroKey ? zeroValue : 0;
            hasZeroKey = true;
            zeroValue = value;
            return old;
        }
        int i = probe(key);
        if (i >= 0) {
            final long[] tab = table;
            int old = valueOf(tab[i + VALUE_OFFSET]);
            tab[i + VALUE_OFFSET] = bits(value);
            return old;
        }
        insertNew(~i, key, bits(value));
        return 0;
    }

    @Override
    public boolean putIfAbsent(long key, int value) {
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
    public int addTo(long key, int increment) {
        if (key == 0) {
            int v = hasZeroKey ? zeroValue + increment : increment;
            hasZeroKey = true;
            zeroValue = v;
            return v;
        }
        int i = probe(key);
        if (i >= 0) {
            final long[] tab = table;
            int v = valueOf(tab[i + VALUE_OFFSET]) + increment;
            tab[i + VALUE_OFFSET] = bits(v);
            return v;
        }
        insertNew(~i, key, bits(increment));
        return increment;
    }

    @Override
    public int remove(long key) {
        if (key == 0) {
            if (!hasZeroKey) {
                return 0;
            }
            int old = zeroValue;
            hasZeroKey = false;
            zeroValue = 0;
            return old;
        }
        int i = find(key);
        if (i < 0) {
            return 0;
        }
        int old = valueOf(table[i + VALUE_OFFSET]);
        removeAt(i, key);
        return old;
    }

    @Override
    public void clear() {
        Arrays.fill(table, 0L);
        assigned = 0;
        hasZeroKey = false;
        zeroValue = 0;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int blocks = blocksFor(expectedSize, loadFactor);
        if (blocks > blockMask + 1) {
            rehash(blocks);
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
    public int indexGet(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? zeroValue : valueOf(table[index + VALUE_OFFSET]);
    }

    @Override
    public int indexReplace(int index, int value) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == table.length) {
            int old = zeroValue;
            zeroValue = value;
            return old;
        }
        int old = valueOf(table[index + VALUE_OFFSET]);
        table[index + VALUE_OFFSET] = bits(value);
        return old;
    }

    @Override
    public void indexInsert(int index, long key, int value) {
        assert index < 0 : "index exists: " + index;
        int slot = ~index;
        if (slot == table.length) {
            assert key == 0;
            hasZeroKey = true;
            zeroValue = value;
            return;
        }
        assert key != 0 && table[slot] == 0L && probe(key) == index;
        insertNew(slot, key, bits(value));
    }

    @Override
    public int indexRemove(int index) {
        assert index >= 0 : "index does not exist: " + index;
        if (index == table.length) {
            int old = zeroValue;
            hasZeroKey = false;
            zeroValue = 0;
            return old;
        }
        int old = valueOf(table[index + VALUE_OFFSET]);
        removeAt(index, table[index]);
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
    public long indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? 0L : table[index];
    }

    /**
     * First occupied key index at array index {@code from} or later (control words, value
     * words and padding are skipped), then the zero key's virtual slot, then {@code -1}.
     */
    private int occupiedFrom(int from) {
        final long[] tab = table;
        for (int i = from; i < tab.length; i++) {
            int lane = (i - PAD) & ((1 << BLOCK_SHIFT) - 1);
            if (lane >= 1 && lane <= SLOTS_PER_BLOCK && tab[i] != 0L) {
                return i;
            }
        }
        return hasZeroKey ? tab.length : -1;
    }

    @Override
    public boolean removeIf(LongIntPredicate predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(0L, zeroValue)) {
            hasZeroKey = false;
            zeroValue = 0;
            removed = true;
        }
        final long[] tab = table;
        for (int b = 0; b <= blockMask; b++) {
            int base = blockBase(b);
            for (int i = base + 1; i <= base + SLOTS_PER_BLOCK; i++) {
                long k = tab[i];
                if (k != 0L && predicate.test(k, valueOf(tab[i + VALUE_OFFSET]))) {
                    removeAt(i, k); // entries never move: no slot is skipped
                    removed = true;
                }
            }
        }
        return removed;
    }

    @Override
    public void forEach(LongIntConsumer action) {
        if (hasZeroKey) {
            action.accept(0, zeroValue);
        }
        final long[] tab = table;
        for (int base = PAD; base < tab.length - 2; base += 1 << BLOCK_SHIFT) {
            for (int i = base + 1; i <= base + SLOTS_PER_BLOCK; i++) {
                long k = tab[i];
                if (k != 0) {
                    action.accept(k, valueOf(tab[i + VALUE_OFFSET]));
                }
            }
        }
    }

    /** Equal to any {@link LongIntMap} with the same mappings. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LongIntMap other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !containsEntry(other, 0, zeroValue)) {
            return false;
        }
        final long[] tab = table;
        for (int base = PAD; base < tab.length - 2; base += 1 << BLOCK_SHIFT) {
            for (int i = base + 1; i <= base + SLOTS_PER_BLOCK; i++) {
                long k = tab[i];
                if (k != 0 && !containsEntry(other, k, valueOf(tab[i + VALUE_OFFSET]))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether {@code map} maps {@code key} to {@code value}. */
    private static boolean containsEntry(LongIntMap map, long key, int value) {
        int i = map.indexOf(key);
        return map.indexExists(i) && map.indexGet(i) == value;
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = hasZeroKey ? Integer.hashCode(zeroValue) : 0;
        final long[] tab = table;
        for (int base = PAD; base < tab.length - 2; base += 1 << BLOCK_SHIFT) {
            for (int i = base + 1; i <= base + SLOTS_PER_BLOCK; i++) {
                long k = tab[i];
                if (k != 0) {
                    h += Long.hashCode(k) ^ Integer.hashCode(valueOf(tab[i + VALUE_OFFSET]));
                }
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

    /**
     * For {@code key != 0}: array index of {@code key}, or {@code -1} if absent. The
     * overflow walk stays inline: a call on this path makes the compiler spill the
     * caller's loop state.
     */
    private int find(long key) {
        final long[] tab = table;
        final int sh = blockShift;
        long h = hash(key, seed);
        long pattern = tagPattern(h, sh);
        int b = (int) (h >>> sh);
        int base = blockBase(b);
        long control = tab[base];
        for (long m = matches(control, pattern); m != 0; m &= m - 1) {
            int i = slotIndex(base, m);
            if (tab[i] == key) {
                return i;
            }
        }
        if (counter(control) == 0) {
            return -1;
        }
        final int mask = blockMask;
        final int st = step(key, seed);
        int d = 1; // first overflow step: the neighbor block
        for (int n = mask + 1; n > 0; n--) { // the sequence reaches every block within this bound
            b = (b + d) & mask;
            d = st;
            base = blockBase(b);
            control = tab[base];
            for (long m = matches(control, pattern); m != 0; m &= m - 1) {
                int i = slotIndex(base, m);
                if (tab[i] == key) {
                    return i;
                }
            }
            if (counter(control) == 0) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * For {@code key != 0}: array index of {@code key} if present, otherwise {@code ~index}
     * of the first free slot along the key's block sequence.
     * <p>
     * Only the home block is handled here. The method must stay small enough for the
     * compiler to inline it into {@code put}/{@code addTo}; the rare rest is in
     * {@link #probeOverflow}.
     */
    private int probe(long key) {
        final long[] tab = table;
        final int sh = blockShift;
        long h = hash(key, seed);
        long pattern = tagPattern(h, sh);
        int base = blockBase((int) (h >>> sh));
        long control = tab[base];
        for (long m = matches(control, pattern); m != 0; m &= m - 1) {
            int i = slotIndex(base, m);
            if (tab[i] == key) {
                return i;
            }
        }
        long empty = matches(control, 0L);
        if (counter(control) == 0 && empty != 0) {
            return ~slotIndex(base, empty);
        }
        return probeOverflow(key, h, base, empty);
    }

    /** Rest of {@link #probe}: the home block has an overflow chain or no free slot. */
    private int probeOverflow(long key, long h, int homeBase, long homeEmpty) {
        final long[] tab = table;
        final int mask = blockMask;
        final int st = step(key, seed);
        int d = 1; // first overflow step: the neighbor block
        final long pattern = tagPattern(h, blockShift);
        int free = homeEmpty != 0 ? slotIndex(homeBase, homeEmpty) : -1;
        int b = (homeBase - PAD) >>> BLOCK_SHIFT;
        long control = tab[homeBase];
        for (int n = mask + 1; n > 0 && counter(control) != 0; n--) {
            b = (b + d) & mask;
            d = st;
            int base = blockBase(b);
            control = tab[base];
            for (long m = matches(control, pattern); m != 0; m &= m - 1) {
                int i = slotIndex(base, m);
                if (tab[i] == key) {
                    return i;
                }
            }
            if (free < 0) {
                long empty = matches(control, 0L);
                if (empty != 0) {
                    free = slotIndex(base, empty);
                }
            }
        }
        // Absent. Without a free slot up to the end of the chain, continue along the sequence.
        return ~(free >= 0 ? free : firstFreeSlot(key, h));
    }

    /** First free slot along the key's block sequence (there is always one). */
    private int firstFreeSlot(long key, long h) {
        final long[] tab = table;
        final int mask = blockMask;
        final int st = step(key, seed);
        int d = 1; // first overflow step: the neighbor block
        int b = (int) (h >>> blockShift);
        for (;;) {
            int base = blockBase(b);
            long empty = matches(tab[base], 0L);
            if (empty != 0) {
                return slotIndex(base, empty);
            }
            b = (b + d) & mask;
            d = st;
        }
    }

    /**
     * Inserts an absent key into the free {@code slot} found by {@link #probe}: writes the
     * tag, the key and the value, and increments the counters of all blocks between the home
     * block and the slot's block (rare).
     */
    private void insertNew(int slot, long key, long valueBits) {
        if (assigned >= maxFill) {
            growAndPlace(key, valueBits);
        } else {
            final long[] tab = table;
            final int sh = blockShift;
            long h = hash(key, seed);
            int target = (slot - PAD) >>> BLOCK_SHIFT;
            int base = blockBase(target);
            tab[base] |= (long) tag(h, sh) << ((slot - base - 1) << 3);
            tab[slot] = key;
            tab[slot + VALUE_OFFSET] = valueBits;
            int home = (int) (h >>> sh);
            if (home != target) {
                adjustCounters(key, home, target, COUNTER_ONE);
            }
        }
        assigned++;
    }

    private void growAndPlace(long key, long valueBits) {
        rehash(nextBlocks());
        place(key, valueBits);
    }

    /**
     * Stores an absent key in the first free slot along its sequence and increments the
     * counters of the full blocks it passes. Used for growth and rehashing.
     */
    private void place(long key, long valueBits) {
        final long[] tab = table;
        final int sh = blockShift;
        long h = hash(key, seed);
        int b = (int) (h >>> sh);
        int base = blockBase(b);
        long empty = matches(tab[base], 0L);
        if (empty == 0) {
            final int mask = blockMask;
            final int st = step(key, seed);
            int d = 1; // first overflow step: the neighbor block
            do {
                if (counter(tab[base]) != COUNTER_SATURATED) {
                    tab[base] += COUNTER_ONE;
                }
                b = (b + d) & mask;
                d = st;
                base = blockBase(b);
                empty = matches(tab[base], 0L);
            } while (empty == 0);
        }
        int slot = slotIndex(base, empty);
        tab[base] |= (long) tag(h, sh) << ((slot - base - 1) << 3);
        tab[slot] = key;
        tab[slot + VALUE_OFFSET] = valueBits;
    }

    /**
     * Clears the slot whose key is at array index {@code i}. If the entry overflowed,
     * decrements the counters of all blocks between the home block and the slot's block.
     */
    private void removeAt(int i, long key) {
        final long[] tab = table;
        int target = (i - PAD) >>> BLOCK_SHIFT;
        int base = blockBase(target);
        tab[base] &= ~(0xFFL << ((i - base - 1) << 3));
        tab[i] = 0L;
        tab[i + VALUE_OFFSET] = 0L;
        assigned--;
        int home = (int) (hash(key, seed) >>> blockShift);
        if (home != target) {
            adjustCounters(key, home, target, -COUNTER_ONE);
        }
    }

    /**
     * Adds {@code delta} (plus or minus one counter unit) to the counters of the blocks
     * from {@code home} (inclusive) to {@code target} (exclusive) along the key's sequence.
     * Saturated counters stay unchanged.
     */
    private void adjustCounters(long key, int home, int target, long delta) {
        final long[] tab = table;
        final int mask = blockMask;
        final int st = step(key, seed);
        int d = 1; // first overflow step: the neighbor block
        int b = home;
        do {
            int cb = blockBase(b);
            if (counter(tab[cb]) != COUNTER_SATURATED) {
                tab[cb] += delta;
            }
            b = (b + d) & mask;
            d = st;
        } while (b != target);
    }

    private int nextBlocks() {
        if (blockMask + 1 >= MAX_BLOCKS) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_BLOCKS * SLOTS_PER_BLOCK);
        }
        return (blockMask + 1) << 1;
    }

    private void rehash(int newBlocks) {
        final long[] old = table;
        allocate(newBlocks);
        for (int base = PAD; base < old.length - 2; base += 1 << BLOCK_SHIFT) {
            for (int i = base + 1; i <= base + SLOTS_PER_BLOCK; i++) {
                long k = old[i];
                if (k != 0) {
                    place(k, old[i + VALUE_OFFSET]);
                }
            }
        }
    }

    private void allocate(int blocks) {
        table = new long[(blocks << BLOCK_SHIFT) + (1 << BLOCK_SHIFT)];
        blockMask = blocks - 1;
        blockShift = 64 - Integer.numberOfTrailingZeros(blocks);
        maxFill = maxFill(blocks, loadFactor);
    }

    private static int maxFill(int blocks, float loadFactor) {
        // Always keep at least one free slot so that insertion terminates.
        int slots = blocks * SLOTS_PER_BLOCK;
        return Math.min(slots - 1, Math.max(1, (int) (slots * (double) loadFactor)));
    }

    static int blocksFor(int expectedSize, float loadFactor) {
        long slots = (long) Math.ceil(expectedSize / (double) loadFactor) + 1;
        long needed = (slots + SLOTS_PER_BLOCK - 1) / SLOTS_PER_BLOCK;
        if (needed > MAX_BLOCKS) {
            throw new IllegalArgumentException("expectedSize too large: " + expectedSize);
        }
        int blocks = needed <= MIN_BLOCKS ? MIN_BLOCKS : Integer.highestOneBit((int) needed - 1) << 1;
        while (maxFill(blocks, loadFactor) < expectedSize && blocks < MAX_BLOCKS) {
            blocks <<= 1;
        }
        return blocks;
    }

    /** Number of blocks (for tests). */
    int blockCount() {
        return blockMask + 1;
    }

    /** Overflow counter of a block (for tests). */
    int overflowCount(int block) {
        return counter(table[blockBase(block)]);
    }

    /** Home block of {@code key} (for tests). */
    int homeBlock(long key) {
        return (int) (hash(key, seed) >>> blockShift);
    }
}
