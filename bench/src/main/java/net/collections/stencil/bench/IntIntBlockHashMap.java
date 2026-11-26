/*
 * Copyright 2026 The collections-stencil authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.collections.stencil.bench;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.IntIntHashMap;
import net.generated.example.function.IntIntConsumer;

/**
 * Open-addressing hash map from {@code int} keys to {@code int} values, organized in
 * blocks of eight slots (experimental layout class).
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Packed slots:</b> as in {@link IntIntHashMap}, each slot is one {@code long} with the
 *       key in the low and the value in the high 32 bits. Key bits {@code 0} mark an empty
 *       slot; key {@code 0} itself is stored outside the table.</li>
 *   <li><b>Blocks:</b> eight consecutive slots (64 bytes, one cache line) form a block. A key
 *       has a <em>home slot</em>; it is stored there if possible, otherwise anywhere in the
 *       home slot's block. A lookup compares all eight keys of the block without
 *       data-dependent branches, so the probe length within the block costs nothing.</li>
 *   <li><b>Overflow:</b> if the home block is full, the key goes to the next block with a free
 *       slot along a per-key double-hashing sequence of blocks. Every block has a saturating
 *       8-bit counter of the keys that passed it this way. A lookup stops at the first block
 *       whose counter is zero. Removal walks the same sequence again and decrements the
 *       counters, so there are no tombstones and no backward shifting.</li>
 *   <li><b>Home slot:</b> a variation point.
 *     <ul>
 *       <li><em>identity</em> (default): the key itself, with the bits above the table size
 *           folded in ({@code key ^ (key >>> log2(capacity))}). Dense or sequential keys
 *           occupy consecutive slots, which halves the working set and turns sequential
 *           access into streaming access. Distinct keys below the capacity never collide.
 *           Unfavorable key patterns only cause overflow, which uses a seeded hash.</li>
 *       <li><em>hashed</em>: Fibonacci hashing of {@code key ^ seed} with a per-instance seed.</li>
 *     </ul></li>
 * </ul>
 * Memory: {@code 8} bytes per slot plus one counter byte per block (about 1.6 %).
 * <p>
 * This class is not thread-safe. Iteration order is unspecified.
 */
public final class IntIntBlockHashMap {

    /** Default maximum ratio of occupied slots to capacity. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    private static final int BLOCK_SHIFT = 3;
    private static final int BLOCK_SIZE = 1 << BLOCK_SHIFT;
    /**
     * Logical slot {@code s} is stored at array index {@code s + PAD}. With the usual 16-byte
     * array header, index {@code 6} is 64-byte aligned whenever the array object itself is,
     * which holds for large arrays (G1 allocates them at region boundaries). Then every
     * block occupies exactly one cache line. The alignment affects only speed.
     */
    private static final int PAD = 6;
    private static final int MIN_CAPACITY = BLOCK_SIZE;
    private static final int MAX_CAPACITY = 1 << 30;
    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    /** Multiplier for the overflow step (odd). */
    private static final long STEP_MULTIPLIER = 0xC2B2_AE3D_27D4_EB4FL;
    private static final long KEY_BITS = 0xFFFF_FFFFL;
    /** A saturated overflow counter is never decremented again (until the next rehash). */
    private static final byte SATURATED = (byte) 0xFF;
    /** Result of {@link #probe(int)}: absent, and the home block has no free slot. */
    private static final int NO_SLOT = Integer.MIN_VALUE;

    private final float loadFactor;
    private final boolean identityHome;
    private final int seed;

    /** Packed slots; length is {@code capacity + BLOCK_SIZE}, see {@link #PAD}. */
    private long[] table;
    /** Saturating overflow counter per block. */
    private byte[] overflow;
    private int log2Capacity;
    private int slotMask;
    private int blockMask;
    /** Number of occupied slots in {@link #table} (the zero key is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;
    private int zeroValue;

    public IntIntBlockHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR, true);
    }

    public IntIntBlockHashMap(int expectedSize) {
        this(expectedSize, DEFAULT_LOAD_FACTOR, true);
    }

    public IntIntBlockHashMap(int expectedSize, float loadFactor) {
        this(expectedSize, loadFactor, true);
    }

    /**
     * @param identityHome {@code true} for identity home slots, {@code false} for hashed
     *                     home slots (see the class description)
     */
    public IntIntBlockHashMap(int expectedSize, float loadFactor, boolean identityHome) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (!(loadFactor > 0f && loadFactor < 1f)) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + loadFactor);
        }
        this.loadFactor = loadFactor;
        this.identityHome = identityHome;
        this.seed = ThreadLocalRandom.current().nextInt();
        allocate(capacityFor(expectedSize, loadFactor));
    }

    // ------------------------------------------------------------------
    // Packing / hashing
    // ------------------------------------------------------------------

    private static long pack(int key, int value) {
        return ((long) value << 32) | (key & KEY_BITS);
    }

    private static int keyOf(long entry) {
        return (int) entry;
    }

    private static int valueOf(long entry) {
        return (int) (entry >>> 32);
    }

    private static long withValue(long entry, int value) {
        return (entry & KEY_BITS) | ((long) value << 32);
    }

    /** Home slot of {@code key} in {@code [0, capacity)}. */
    private int homeSlot(int key) {
        if (identityHome) {
            return (key ^ (key >>> log2Capacity)) & slotMask;
        }
        return (int) (((key ^ seed) * PHI) >>> (64 - log2Capacity));
    }

    /** Odd block step of the overflow sequence; callers mask it with {@link #blockMask}. */
    private int step(int key) {
        return (int) (((key ^ seed) * STEP_MULTIPLIER) >>> 33) | 1;
    }

    /** 1 if the key bits of {@code entry} equal {@code key}, otherwise 0; no branches. */
    private static int eq(long entry, int key) {
        int x = (int) entry ^ key;
        return ((x | -x) >>> 31) ^ 1;
    }

    /** Bit {@code j} is set iff the key of slot {@code base + j} equals {@code key}. */
    private static int match(long[] tab, int base, int key) {
        long e7 = tab[base + 7]; // highest index first: one range check covers the block
        return eq(tab[base], key)
                | eq(tab[base + 1], key) << 1
                | eq(tab[base + 2], key) << 2
                | eq(tab[base + 3], key) << 3
                | eq(tab[base + 4], key) << 4
                | eq(tab[base + 5], key) << 5
                | eq(tab[base + 6], key) << 6
                | eq(e7, key) << 7;
    }

    private static int blockBase(int block) {
        return (block << BLOCK_SHIFT) + PAD;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public int size() {
        return hasZeroKey ? assigned + 1 : assigned;
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public boolean containsKey(int key) {
        if (key == 0) {
            return hasZeroKey;
        }
        return find(key) >= 0;
    }

    /** Returns the value mapped to {@code key}, or {@code 0} if there is none. */
    public int get(int key) {
        return getOrDefault(key, 0);
    }

    public int getOrDefault(int key, int defaultValue) {
        if (key == 0) {
            return hasZeroKey ? zeroValue : defaultValue;
        }
        int i = find(key);
        return i >= 0 ? valueOf(table[i]) : defaultValue;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /** Associates {@code value} with {@code key}. Returns the previous value, or {@code 0}. */
    public int put(int key, int value) {
        if (key == 0) {
            int old = hasZeroKey ? zeroValue : 0;
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
        insertNew(i, key, pack(key, value));
        return 0;
    }

    /** Inserts only if {@code key} is absent. Returns {@code true} if inserted. */
    public boolean putIfAbsent(int key, int value) {
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
        insertNew(i, key, pack(key, value));
        return true;
    }

    /**
     * Adds {@code increment} to the value of {@code key}. An absent key counts as
     * {@code 0}. Returns the <em>new</em> value.
     */
    public int addTo(int key, int increment) {
        if (key == 0) {
            int v = hasZeroKey ? zeroValue + increment : increment;
            hasZeroKey = true;
            zeroValue = v;
            return v;
        }
        int i = probe(key);
        if (i >= 0) {
            // Adding a multiple of 2^32 changes only the value bits (mod 2^32).
            long e = table[i] + ((long) increment << 32);
            table[i] = e;
            return valueOf(e);
        }
        insertNew(i, key, pack(key, increment));
        return increment;
    }

    /** Removes {@code key}. Returns the previous value, or {@code 0}. */
    public int remove(int key) {
        if (key == 0) {
            if (!hasZeroKey) {
                return 0;
            }
            int old = zeroValue;
            hasZeroKey = false;
            zeroValue = 0;
            return old;
        }
        final long[] tab = table;
        int s = homeSlot(key);
        int i = s + PAD;
        if (keyOf(tab[i]) != key) {
            int home = s >>> BLOCK_SHIFT;
            int base = blockBase(home);
            int m = match(tab, base, key);
            if (m != 0) {
                i = base + Integer.numberOfTrailingZeros(m);
            } else {
                i = removeOverflowed(key, home);
                if (i < 0) {
                    return 0;
                }
            }
        }
        int old = valueOf(tab[i]);
        tab[i] = 0L;
        assigned--;
        return old;
    }

    public void clear() {
        Arrays.fill(table, 0L);
        Arrays.fill(overflow, (byte) 0);
        assigned = 0;
        hasZeroKey = false;
        zeroValue = 0;
    }

    /** Makes room for {@code expectedSize} entries without further resizing. */
    public void ensureCapacity(int expectedSize) {
        int cap = capacityFor(expectedSize, loadFactor);
        if (cap > slotMask + 1) {
            rehash(cap);
        }
    }

    // ------------------------------------------------------------------
    // Bulk / Object methods
    // ------------------------------------------------------------------

    /** Calls {@code action} for each entry. The map must not be modified during the call. */
    public void forEach(IntIntConsumer action) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntIntBlockHashMap other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && (!other.hasZeroKey || other.zeroValue != zeroValue)) {
            return false;
        }
        for (long e : table) {
            int k = keyOf(e);
            if (k != 0) {
                int j = other.find(k);
                if (j < 0 || valueOf(other.table[j]) != valueOf(e)) {
                    return false;
                }
            }
        }
        return true;
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

    /** For {@code key != 0}: array index of {@code key}, or {@code -1} if absent. */
    private int find(int key) {
        final long[] tab = table;
        int s = homeSlot(key);
        int i = s + PAD;
        if (keyOf(tab[i]) == key) {
            return i;
        }
        int home = s >>> BLOCK_SHIFT;
        int base = blockBase(home);
        int m = match(tab, base, key);
        if (m != 0) {
            return base + Integer.numberOfTrailingZeros(m);
        }
        return overflow[home] == 0 ? -1 : findOverflowed(key, home);
    }

    /**
     * For {@code key != 0}: array index of {@code key} if present. Otherwise {@code ~index} of
     * a free slot in the home block, or {@link #NO_SLOT} if the home block is full.
     */
    private int probe(int key) {
        final long[] tab = table;
        int s = homeSlot(key);
        int i = s + PAD;
        int k = keyOf(tab[i]);
        if (k == key) {
            return i;
        }
        int home = s >>> BLOCK_SHIFT;
        int base = blockBase(home);
        int m = match(tab, base, key);
        if (m != 0) {
            return base + Integer.numberOfTrailingZeros(m);
        }
        if (overflow[home] != 0) {
            int j = findOverflowed(key, home);
            if (j >= 0) {
                return j;
            }
        }
        if (k == 0) {
            return ~i;
        }
        int free = match(tab, base, 0);
        return free != 0 ? ~(base + Integer.numberOfTrailingZeros(free)) : NO_SLOT;
    }

    /** Searches the overflow sequence of {@code key}, starting after its home block. */
    private int findOverflowed(int key, int home) {
        final long[] tab = table;
        final byte[] ov = overflow;
        final int mask = blockMask;
        final int st = step(key);
        int b = home;
        for (int n = mask; n > 0; n--) { // at most all other blocks
            b = (b + st) & mask;
            int base = blockBase(b);
            int m = match(tab, base, key);
            if (m != 0) {
                return base + Integer.numberOfTrailingZeros(m);
            }
            if (ov[b] == 0) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Like {@link #findOverflowed}, but also decrements the counters of all blocks that
     * {@code key} passed when it was inserted. Returns the array index, or {@code -1}.
     */
    private int removeOverflowed(int key, int home) {
        final long[] tab = table;
        final byte[] ov = overflow;
        if (ov[home] == 0) {
            return -1;
        }
        final int mask = blockMask;
        final int st = step(key);
        int b = home;
        for (int n = mask; n > 0; n--) {
            b = (b + st) & mask;
            int base = blockBase(b);
            int m = match(tab, base, key);
            if (m != 0) {
                for (int c = home; c != b; c = (c + st) & mask) {
                    if (ov[c] != SATURATED) {
                        ov[c]--;
                    }
                }
                return base + Integer.numberOfTrailingZeros(m);
            }
            if (ov[b] == 0) {
                return -1;
            }
        }
        return -1;
    }

    /** Inserts an absent key; {@code probeResult} is the negative result of {@link #probe}. */
    private void insertNew(int probeResult, int key, long entry) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            place(key, entry);
        } else if (probeResult != NO_SLOT) {
            table[~probeResult] = entry;
        } else {
            place(key, entry);
        }
        assigned++;
    }

    /** Stores an absent key in the first free slot along its sequence; updates the counters. */
    private void place(int key, long entry) {
        final long[] tab = table;
        int s = homeSlot(key);
        int i = s + PAD;
        if (keyOf(tab[i]) == 0) {
            tab[i] = entry;
            return;
        }
        int b = s >>> BLOCK_SHIFT;
        int base = blockBase(b);
        int free = match(tab, base, 0);
        if (free == 0) {
            final byte[] ov = overflow;
            final int mask = blockMask;
            final int st = step(key);
            do { // terminates: there is a free slot, and the odd step visits every block
                if (ov[b] != SATURATED) {
                    ov[b]++;
                }
                b = (b + st) & mask;
                base = blockBase(b);
                free = match(tab, base, 0);
            } while (free == 0);
        }
        tab[base + Integer.numberOfTrailingZeros(free)] = entry;
    }

    private int nextCapacity() {
        if (slotMask + 1 >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return (slotMask + 1) << 1;
    }

    private void rehash(int newCapacity) {
        final long[] old = table;
        allocate(newCapacity);
        for (long e : old) {
            int k = keyOf(e);
            if (k != 0) {
                place(k, e);
            }
        }
    }

    private void allocate(int capacity) {
        table = new long[capacity + BLOCK_SIZE];
        overflow = new byte[capacity >>> BLOCK_SHIFT];
        log2Capacity = Integer.numberOfTrailingZeros(capacity);
        slotMask = capacity - 1;
        blockMask = (capacity >>> BLOCK_SHIFT) - 1;
        maxFill = maxFill(capacity, loadFactor);
    }

    private static int maxFill(int capacity, float loadFactor) {
        // Always keep at least one free slot so that insertion terminates.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * (double) loadFactor)));
    }

    static int capacityFor(int expectedSize, float loadFactor) {
        long needed = (long) Math.ceil(expectedSize / (double) loadFactor) + 1;
        if (needed > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize too large: " + expectedSize);
        }
        int cap = needed <= MIN_CAPACITY ? MIN_CAPACITY : Integer.highestOneBit((int) needed - 1) << 1;
        while (maxFill(cap, loadFactor) < expectedSize && cap < MAX_CAPACITY) {
            cap <<= 1;
        }
        return cap;
    }

    /** Number of slots (for tests). */
    int capacity() {
        return slotMask + 1;
    }

    /** Overflow counter of a block as an unsigned value (for tests). */
    int overflowCount(int block) {
        return overflow[block] & 0xFF;
    }

    /** Home block of {@code key} (for tests). */
    int homeBlock(int key) {
        return homeSlot(key) >>> BLOCK_SHIFT;
    }
}
