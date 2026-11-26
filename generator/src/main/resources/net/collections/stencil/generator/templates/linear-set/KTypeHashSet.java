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
//#-- Linear probing over a plain key array: slot s is table[s]. Same probing and deletion as
//#-- the linear map templates, without values.
//#types K=int|long
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.KTypeConsumer;
import java.util.function.KTypePredicate;

/**
 * Open-addressing hash set of {@code KType} elements.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> one {@code KType[]} with one element per slot.</li>
 *   <li><b>Empty marker:</b> a slot holding {@code 0} is empty. The element {@code 0} itself
 *       is recorded outside the table ({@link #hasZeroKey}).</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed}. The slot is taken from the
 *       <em>high</em> bits of the 64-bit product, which depend on all bits of the key, and
 *       {@code seed} is chosen at random per instance.</li>
 *   <li><b>Collision resolution:</b> linear probing. Removal uses backward-shift
 *       deletion, so there are no tombstones.</li>
 *   <li><b>Load factor:</b> the default {@value #DEFAULT_LOAD_FACTOR} keeps the table small.
 *       For maximum speed, pass {@code 0.5f}: probe chains get shorter, especially for lookups
 *       of absent elements, at up to twice the memory.</li>
 * </ul>
 * See {@link KTypeSet} for the contract. This class is not thread-safe. Iteration order is
 * unspecified and differs between instances.
 */
public final class KTypeHashSet implements KTypeSet {

    /** Default maximum ratio of occupied slots to capacity. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of elements the set can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_CAPACITY = 4;
    private static final int MAX_CAPACITY = 1 << 30;

    private final float loadFactor;
    private final long seed;

    /** Elements; length is the capacity, a power of two. */
    private KType[] table;
    /** {@code 64 - log2(table.length)}. */
    private int shift;
    /** Number of occupied slots in {@link #table} (the zero element is not counted). */
    private int assigned;
    /** Maximum value of {@link #assigned} before the table grows. */
    private int maxFill;

    private boolean hasZeroKey;

    /**
     * An empty set with room for {@link #DEFAULT_EXPECTED_SIZE} elements and the default load
     * factor {@link #DEFAULT_LOAD_FACTOR}.
     */
    public KTypeHashSet() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty set with room for {@code expectedSize} elements and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of elements the set can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public KTypeHashSet(int expectedSize) {
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
    public KTypeHashSet(int expectedSize, float loadFactor) {
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
     * A set with the elements of {@code other} (any implementation) and the default load factor.
     *
     * @param other the set to copy (any implementation)
     */
    public KTypeHashSet(KTypeSet other) {
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
    public static KTypeHashSet of(KType... elements) {
        KTypeHashSet set = new KTypeHashSet(elements.length);
        for (KType e : elements) {
            set.add(e);
        }
        return set;
    }

    /**
     * Fibonacci hashing: high bits of {@code (key ^ seed) * PHI}. Result is the key's home
     * slot, in {@code [0, 2^(64 - shift))}.
     */
    private static int mix(long key, long seed, int shift) {
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
    public boolean contains(KType key) {
        if (key == 0) {
            return hasZeroKey;
        }
        final KType[] tab = table;
        final int mask = tab.length - 1;
        int i = mix(key, seed, shift);
        KType k;
        while ((k = tab[i]) != key) {
            if (k == 0) {
                return false;
            }
            i = (i + 1) & mask;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public boolean add(KType key) {
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
    public boolean remove(KType key) {
        if (key == 0) {
            boolean had = hasZeroKey;
            hasZeroKey = false;
            return had;
        }
        int i = probe(key);
        if (i < 0) {
            return false;
        }
        deleteAt(i);
        return true;
    }

    @Override
    public void clear() {
        Arrays.fill(table, KZero);
        assigned = 0;
        hasZeroKey = false;
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int cap = capacityFor(expectedSize, loadFactor);
        if (cap > table.length) {
            rehash(cap);
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
    public KType indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? KZero : table[index];
    }

    /** First occupied slot at or after {@code from}, then the zero element's virtual slot, then {@code -1}. */
    private int occupiedFrom(int from) {
        final KType[] tab = table;
        for (int i = from; i < tab.length; i++) {
            if (tab[i] != 0) {
                return i;
            }
        }
        return hasZeroKey ? tab.length : -1;
    }

    @Override
    public boolean removeIf(KTypePredicate predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(KZero)) {
            hasZeroKey = false;
            removed = true;
        }
        final KType[] tab = table;
        final int mask = tab.length - 1;
        // Start behind an empty slot (there always is one). Backward shifts never move an element
        // across an empty slot, so they only move elements that come later in this pass, and
        // every element is tested exactly once.
        int start = 0;
        while (tab[start] != 0) {
            start++;
        }
        for (int i = (start + 1) & mask; i != start; ) {
            KType k = tab[i];
            if (k != 0 && predicate.test(k)) {
                deleteAt(i); // a later element may move into slot i: test it next
                removed = true;
            } else {
                i = (i + 1) & mask;
            }
        }
        return removed;
    }

    @Override
    public void forEach(KTypeConsumer action) {
        if (hasZeroKey) {
            action.accept(KZero);
        }
        for (KType k : table) {
            if (k != 0) {
                action.accept(k);
            }
        }
    }

    @Override
    public KType[] toArray() {
        KType[] result = new KType[size()];
        int n = hasZeroKey ? 1 : 0; // the zero element is result[0]
        for (KType k : table) {
            if (k != 0) {
                result[n++] = k;
            }
        }
        return result;
    }

    /** Equal to any {@link KTypeSet} with the same elements. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KTypeSet other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !other.contains(KZero)) {
            return false;
        }
        for (KType k : table) {
            if (k != 0 && !other.contains(k)) {
                return false;
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Set#hashCode()} on boxed elements. */
    @Override
    public int hashCode() {
        int h = 0; // KBoxed.hashCode(0) == 0
        for (KType k : table) {
            h += KBoxed.hashCode(k);
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

    /** For {@code key != 0}: slot of {@code key} if present, otherwise {@code ~firstEmptySlot}. */
    private int probe(KType key) {
        final KType[] tab = table;
        final int mask = tab.length - 1;
        int i = mix(key, seed, shift);
        for (;;) {
            KType k = tab[i];
            if (k == key) {
                return i;
            }
            if (k == 0) {
                return ~i;
            }
            i = (i + 1) & mask;
        }
    }

    /** Puts a new element (known to be absent) into the empty slot {@code i}, growing first if needed. */
    private void insertNew(int i, KType key) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            i = ~probe(key);
        }
        table[i] = key;
        assigned++;
    }

    /** Backward-shift deletion: moves later elements of the probe chain into the gap. */
    private void deleteAt(int gap) {
        final KType[] tab = table;
        final int mask = tab.length - 1;
        final long s = seed;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 1) & mask;
            KType k = tab[i];
            if (k == 0) {
                break;
            }
            int ideal = mix(k, s, sh);
            // The element can move into gap iff gap lies on its probe path ideal -> i (cyclically).
            if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                tab[gap] = k;
                gap = i;
            }
        }
        tab[gap] = KZero;
        assigned--;
    }

    private int nextCapacity() {
        if (table.length >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return table.length << 1;
    }

    private void rehash(int newCapacity) {
        final KType[] old = table;
        allocate(newCapacity);
        final KType[] tab = table;
        final int mask = tab.length - 1;
        final long s = seed;
        final int sh = shift;
        for (KType k : old) {
            if (k != 0) {
                int i = mix(k, s, sh);
                while (tab[i] != 0) {
                    i = (i + 1) & mask;
                }
                tab[i] = k;
            }
        }
    }

    private void allocate(int capacity) {
        table = new KType[capacity];
        shift = 64 - Integer.numberOfTrailingZeros(capacity);
        maxFill = maxFill(capacity, loadFactor);
    }

    private static int maxFill(int capacity, float loadFactor) {
        // Always keep at least one empty slot so that probing terminates.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * (double) loadFactor)));
    }

    /** Capacity in slots (a power of two) for {@code expectedSize} elements. */
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
