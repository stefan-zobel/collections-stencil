// Generated from template linear-set/KTypeHashSet.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Open-addressing hash set of {@code int} elements.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> one {@code int[]} with one element per slot.</li>
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
 * See {@link IntSet} for the contract. This class is not thread-safe. Iteration order is
 * unspecified and differs between instances.
 */
public final class IntHashSet implements IntSet {

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
    private int[] table;
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
    public IntHashSet() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty set with room for {@code expectedSize} elements and the default
     * load factor {@link #DEFAULT_LOAD_FACTOR}.
     *
     * @param expectedSize the number of elements the set can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public IntHashSet(int expectedSize) {
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
    public IntHashSet(int expectedSize, float loadFactor) {
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
    public IntHashSet(IntSet other) {
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
    public static IntHashSet of(int... elements) {
        IntHashSet set = new IntHashSet(elements.length);
        for (int e : elements) {
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
    public boolean contains(int key) {
        if (key == 0) {
            return hasZeroKey;
        }
        final int[] tab = table;
        final int mask = tab.length - 1;
        int i = mix(key, seed, shift);
        int k;
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
    public boolean add(int key) {
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
    public boolean remove(int key) {
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
        Arrays.fill(table, 0);
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
    public int indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return index == table.length ? 0 : table[index];
    }

    /** First occupied slot at or after {@code from}, then the zero element's virtual slot, then {@code -1}. */
    private int occupiedFrom(int from) {
        final int[] tab = table;
        for (int i = from; i < tab.length; i++) {
            if (tab[i] != 0) {
                return i;
            }
        }
        return hasZeroKey ? tab.length : -1;
    }

    @Override
    public boolean removeIf(IntPredicate predicate) {
        boolean removed = false;
        if (hasZeroKey && predicate.test(0)) {
            hasZeroKey = false;
            removed = true;
        }
        final int[] tab = table;
        final int mask = tab.length - 1;
        // Start behind an empty slot (there always is one). Backward shifts never move an element
        // across an empty slot, so they only move elements that come later in this pass, and
        // every element is tested exactly once.
        int start = 0;
        while (tab[start] != 0) {
            start++;
        }
        for (int i = (start + 1) & mask; i != start; ) {
            int k = tab[i];
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
    public void forEach(IntConsumer action) {
        if (hasZeroKey) {
            action.accept(0);
        }
        for (int k : table) {
            if (k != 0) {
                action.accept(k);
            }
        }
    }

    @Override
    public int[] toArray() {
        int[] result = new int[size()];
        int n = hasZeroKey ? 1 : 0; // the zero element is result[0]
        for (int k : table) {
            if (k != 0) {
                result[n++] = k;
            }
        }
        return result;
    }

    /** Equal to any {@link IntSet} with the same elements. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntSet other) || other.size() != size()) {
            return false;
        }
        if (hasZeroKey && !other.contains(0)) {
            return false;
        }
        for (int k : table) {
            if (k != 0 && !other.contains(k)) {
                return false;
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Set#hashCode()} on boxed elements. */
    @Override
    public int hashCode() {
        int h = 0; // Integer.hashCode(0) == 0
        for (int k : table) {
            h += Integer.hashCode(k);
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
    private int probe(int key) {
        final int[] tab = table;
        final int mask = tab.length - 1;
        int i = mix(key, seed, shift);
        for (;;) {
            int k = tab[i];
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
    private void insertNew(int i, int key) {
        if (assigned >= maxFill) {
            rehash(nextCapacity());
            i = ~probe(key);
        }
        table[i] = key;
        assigned++;
    }

    /** Backward-shift deletion: moves later elements of the probe chain into the gap. */
    private void deleteAt(int gap) {
        final int[] tab = table;
        final int mask = tab.length - 1;
        final long s = seed;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 1) & mask;
            int k = tab[i];
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
        tab[gap] = 0;
        assigned--;
    }

    private int nextCapacity() {
        if (table.length >= MAX_CAPACITY) {
            throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
        }
        return table.length << 1;
    }

    private void rehash(int newCapacity) {
        final int[] old = table;
        allocate(newCapacity);
        final int[] tab = table;
        final int mask = tab.length - 1;
        final long s = seed;
        final int sh = shift;
        for (int k : old) {
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
        table = new int[capacity];
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
