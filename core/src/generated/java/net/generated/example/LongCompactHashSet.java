// Generated from template compact-set/KTypeCompactHashSet.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongConsumer;
import java.util.function.LongPredicate;

/**
 * Open-addressing hash set of {@code long} elements for high load factors: blocks with
 * one-byte tags, in the style of F14 and SwissTable.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Blocks:</b> the table is a {@code long[]} of blocks with eight words (64 bytes,
 *       one cache line). Word 0 is the block's <em>control word</em>, words 1-7 are the
 *       elements of its seven slots.</li>
 *   <li><b>Control word:</b> bytes 0-6 hold one tag per slot ({@code 0} = empty, otherwise
 *       {@code 0x80} plus seven hash bits). Byte 7 is the block's overflow counter.</li>
 *   <li><b>Lookup:</b> one SWAR comparison of the control word with the element's tag
 *       yields the candidate slots. Hits and misses usually read a single cache line.</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed} with a per-instance seed. The
 *       highest bits of the product select the home block, the next bits form the tag.</li>
 *   <li><b>Overflow:</b> if the home block is full, the element goes to the first block with
 *       a free slot along a per-element sequence of blocks: first the neighbor block, then
 *       double hashing. Every block passed on the way increments its saturating 8-bit counter.
 *       A lookup stops at the first block whose counter is zero. Removal walks the same
 *       sequence and decrements the counters, so there are no tombstones.</li>
 *   <li><b>Element 0</b> is recorded outside the table; an empty slot holds {@code 0}.</li>
 * </ul>
 * Memory: 64 bytes per seven slots; at the default load factor about the same as
 * {@link LongHashSet} at its default load factor.
 * <p>
 * See {@link LongSet} for the contract. This class is not thread-safe. Iteration order is
 * unspecified and differs between instances.
 */
public final class LongCompactHashSet implements LongSet {

    /** Default maximum ratio of elements to slots. */
    public static final float DEFAULT_LOAD_FACTOR = 0.85f;

    /** Default number of elements the set can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    private static final int BLOCK_SHIFT = 3; // eight words per block
    /**
     * Block {@code b} starts at array index {@code 8 * b + PAD}. With the usual 16-byte array
     * header, index {@code 6} is 64-byte aligned whenever the array object is, which holds
     * for large arrays (G1 allocates them at region boundaries). Then every block occupies
     * exactly one cache line. The alignment affects only speed.
     */
    private static final int PAD = 6;
    private static final int MIN_BLOCKS = 2;
    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    /** Multiplier for the overflow step (odd). */
    private static final long STEP_MULTIPLIER = 0xC2B2_AE3D_27D4_EB4FL;
    private static final int SLOTS_PER_BLOCK = 7;
    private static final int MAX_BLOCKS = 1 << 27;
    /** {@code log2} of the tag width in bits (8). */
    private static final int TAG_WIDTH_SHIFT = 3;
    private static final long TAG_MASK = 0xFFL;
    /** {@code 1} in each of the seven tag bytes. */
    private static final long LANES_ONE = 0x0001_0101_0101_0101L;
    /** High bit of each of the seven tag bytes. */
    private static final long TAG_HIGH_BITS = 0x0080_8080_8080_8080L;
    private static final int TAG_FLAG = 0x80;
    private static final int COUNTER_SHIFT = 56;
    private static final long COUNTER_ONE = 1L << COUNTER_SHIFT;
    /** A saturated counter is never decremented again (until the next rehash). */
    private static final int COUNTER_SATURATED = 0xFF;

    private final float loadFactor;
    private final long seed;

    /** Blocks; length is {@code 8 * blocks + 8}, see {@link #PAD}. */
    private long[] table;
    private int blockMask;
    /** {@code 64 - log2(blocks)}: the product's high bits select the block. */
    private int blockShift;
    /** Number of elements in {@link #table} (the zero element is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;

    /**
     * An empty set with room for {@link #DEFAULT_EXPECTED_SIZE} elements and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public LongCompactHashSet() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty set with room for {@code expectedSize} elements and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of elements the set can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public LongCompactHashSet(int expectedSize) {
        this(expectedSize, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty set with room for {@code expectedSize} elements at the given load factor.
     *
     * @param expectedSize the number of elements the set can hold without resizing
     * @param loadFactor the maximum fill of the table, in {@code (0, 1)}
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large, or
     *         {@code loadFactor} is not in {@code (0, 1)}
     */
    public LongCompactHashSet(int expectedSize, float loadFactor) {
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
     * A set with the elements of {@code other} (any implementation) and the default load factor.
     *
     * @param other the set to copy (any implementation)
     */
    public LongCompactHashSet(LongSet other) {
        this(other.size());
        addAll(other);
    }

    /**
     * A set with the given elements; an element given more than once is added once. Unlike
     * {@link java.util.Set#of}, the set is modifiable and accepts duplicates.
     *
     * @param elements the elements
     * @return a new set
     */
    public static LongCompactHashSet of(long... elements) {
        LongCompactHashSet set = new LongCompactHashSet(elements.length);
        for (long e : elements) {
            set.add(e);
        }
        return set;
    }

    // ------------------------------------------------------------------
    // Slots, tags and hashing
    // ------------------------------------------------------------------
    //
    // A slot is identified by the array index of its element.

    /** Array index of the slot in lane {@code lane} of the block at {@code base}. */
    private static int slot(int base, int lane) {
        return base + 1 + lane;
    }

    /** Lane of slot {@code i} within its block at {@code base}. */
    private static int lane(int base, int i) {
        return i - base - 1;
    }

    /** Block of slot {@code i}. */
    private static int blockOf(int i) {
        return (i - PAD) >>> BLOCK_SHIFT;
    }

    private static long hash(long key, long seed) {
        return (key ^ seed) * PHI;
    }

    /** The tag in every lane. */
    private static long tagPattern(long hash, int blockShift) {
        return tag(hash, blockShift) * LANES_ONE;
    }

    /** Nonzero tag from the hash bits below the block bits. */
    private static int tag(long hash, int blockShift) {
        return ((int) (hash >>> (blockShift - 7)) & 0x7F) | TAG_FLAG;
    }

    /**
     * Odd block step of the overflow sequence; callers mask it with {@link #blockMask}.
     * The sequence of an element is {@code home, home + 1, home + 1 + step, ...}: the first
     * overflow goes to the neighbor block, later ones are spread by double hashing so that
     * chains stay short. With an odd step, the sequence reaches every block within
     * {@code blocks} steps after the home block.
     */
    private static int step(long key, long seed) {
        return (int) (((key ^ seed) * STEP_MULTIPLIER) >>> 33) | 1;
    }

    /**
     * High bit set in each tag lane of {@code control} that equals the corresponding lane of
     * {@code pattern}. Exact for the lowest match; a higher lane may be reported falsely after
     * a match (borrow), which the key comparison filters out.
     */
    private static long matches(long control, long pattern) {
        long x = control ^ pattern;
        return (x - LANES_ONE) & ~x & TAG_HIGH_BITS;
    }

    /** Array index of the slot of the lowest lane flagged in {@code bits}. */
    private static int slotIndex(int base, long bits) {
        return slot(base, Long.numberOfTrailingZeros(bits) >>> TAG_WIDTH_SHIFT);
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
    public boolean contains(long key) {
        if (key == 0) {
            return hasZeroKey;
        }
        return find(key) >= 0;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public boolean add(long key) {
        if (key == 0) {
            if (hasZeroKey) {
                return false;
            }
            hasZeroKey = true;
            return true;
        }
        int i = probe(key);
        if (i >= 0) {
            return false;
        }
        insertNew(~i, key);
        return true;
    }

    @Override
    public boolean remove(long key) {
        if (key == 0) {
            boolean had = hasZeroKey;
            hasZeroKey = false;
            return had;
        }
        int i = find(key);
        if (i < 0) {
            return false;
        }
        removeAt(i, key);
        return true;
    }

    @Override
    public void clear() {
        Arrays.fill(table, 0L);
        assigned = 0;
        hasZeroKey = false;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int blocks = blocksFor(expectedSize, loadFactor);
        if (blocks > blockMask + 1) {
            rehash(blocks);
        }
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
     * First occupied slot at array index {@code from} or later (control words and padding are
     * skipped), then the zero element's virtual slot, then {@code -1}.
     */
    private int occupiedFrom(int from) {
        final long[] tab = table;
        for (int i = from; i < tab.length; i++) {
            if (((i - PAD) & ((1 << BLOCK_SHIFT) - 1)) != 0 && tab[i] != 0L) {
                return i;
            }
        }
        return hasZeroKey ? tab.length : -1;
    }

    @Override
    public boolean removeIf(LongPredicate predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(0L)) {
            hasZeroKey = false;
            removed = true;
        }
        final long[] tab = table;
        for (int b = 0; b <= blockMask; b++) {
            int base = blockBase(b);
            for (int i = slot(base, 0); i < slot(base, SLOTS_PER_BLOCK); i++) {
                long k = tab[i];
                if (k != 0 && predicate.test(k)) {
                    removeAt(i, k); // elements never move: no slot is skipped
                    removed = true;
                }
            }
        }
        return removed;
    }

    @Override
    public void forEach(LongConsumer action) {
        if (hasZeroKey) {
            action.accept(0L);
        }
        final long[] tab = table;
        for (int b = 0; b <= blockMask; b++) {
            int base = blockBase(b);
            for (int i = slot(base, 0); i < slot(base, SLOTS_PER_BLOCK); i++) {
                long k = tab[i];
                if (k != 0) {
                    action.accept(k);
                }
            }
        }
    }

    @Override
    public long[] toArray() {
        long[] result = new long[size()];
        int n = hasZeroKey ? 1 : 0; // the zero element is result[0]
        final long[] tab = table;
        for (int b = 0; b <= blockMask; b++) {
            int base = blockBase(b);
            for (int i = slot(base, 0); i < slot(base, SLOTS_PER_BLOCK); i++) {
                long k = tab[i];
                if (k != 0) {
                    result[n++] = k;
                }
            }
        }
        return result;
    }

    /** Equal to any {@link LongSet} with the same elements. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LongSet other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !other.contains(0L)) {
            return false;
        }
        final long[] tab = table;
        for (int b = 0; b <= blockMask; b++) {
            int base = blockBase(b);
            for (int i = slot(base, 0); i < slot(base, SLOTS_PER_BLOCK); i++) {
                long k = tab[i];
                if (k != 0 && !other.contains(k)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Set#hashCode()} on boxed elements. */
    @Override
    public int hashCode() {
        int h = 0; // Long.hashCode(0) == 0
        final long[] tab = table;
        for (int b = 0; b <= blockMask; b++) {
            int base = blockBase(b);
            for (int i = slot(base, 0); i < slot(base, SLOTS_PER_BLOCK); i++) {
                h += Long.hashCode(tab[i]);
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size(), 1 << 16) * 8 + 2).append('[');
        forEach(k -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(k);
        });
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /**
     * For {@code key != 0}: slot of {@code key}, or {@code -1} if absent. The overflow walk
     * stays inline: a call on this path makes the compiler spill the caller's loop state.
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
     * For {@code key != 0}: slot of {@code key} if present, otherwise {@code ~slot} of the
     * first free slot along the element's block sequence.
     * <p>
     * Only the home block is handled here. The method must stay small enough for the
     * compiler to inline it into {@code add}; the rare rest is in {@link #probeOverflow}.
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

    /** First free slot along the element's block sequence (there is always one). */
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
     * Inserts an absent element into the free {@code slot} found by {@link #probe}: writes
     * the tag and the element, and increments the counters of all blocks between the home
     * block and the slot's block (rare).
     */
    private void insertNew(int slot, long key) {
        if (assigned >= maxFill) {
            rehash(nextBlocks());
            place(key);
        } else {
            final long[] tab = table;
            final int sh = blockShift;
            long h = hash(key, seed);
            int target = blockOf(slot);
            int base = blockBase(target);
            tab[base] |= (long) tag(h, sh) << (lane(base, slot) << TAG_WIDTH_SHIFT);
            tab[slot] = key;
            int home = (int) (h >>> sh);
            if (home != target) {
                adjustCounters(key, home, target, COUNTER_ONE);
            }
        }
        assigned++;
    }

    /**
     * Stores an absent element in the first free slot along its sequence and increments the
     * counters of the full blocks it passes. Used for growth and rehashing.
     */
    private void place(long key) {
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
        tab[base] |= (long) tag(h, sh) << (lane(base, slot) << TAG_WIDTH_SHIFT);
        tab[slot] = key;
    }

    /**
     * Clears slot {@code i}, which holds {@code key}. If the element overflowed, decrements
     * the counters of all blocks between the home block and the slot's block.
     */
    private void removeAt(int i, long key) {
        final long[] tab = table;
        int target = blockOf(i);
        int base = blockBase(target);
        tab[base] &= ~(TAG_MASK << (lane(base, i) << TAG_WIDTH_SHIFT));
        tab[i] = 0L;
        assigned--;
        int home = (int) (hash(key, seed) >>> blockShift);
        if (home != target) {
            adjustCounters(key, home, target, -COUNTER_ONE);
        }
    }

    /**
     * Adds {@code delta} (plus or minus one counter unit) to the counters of the blocks
     * from {@code home} (inclusive) to {@code target} (exclusive) along the element's
     * sequence. Saturated counters stay unchanged.
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
        final int oldBlocks = blockMask + 1;
        allocate(newBlocks);
        for (int b = 0; b < oldBlocks; b++) {
            int base = blockBase(b);
            for (int i = slot(base, 0); i < slot(base, SLOTS_PER_BLOCK); i++) {
                long k = old[i];
                if (k != 0) {
                    place(k);
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
