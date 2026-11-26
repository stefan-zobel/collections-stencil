// Generated from template linked-set/KTypeLinkedHashSet.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Hash set of {@code int} elements that iterates in insertion order.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Elements:</b> a {@code int[]} in insertion order. A new element is appended, so
 *       iteration is a sequential scan.</li>
 *   <li><b>Index table:</b> an {@code int[]} with linear probing. A slot holds the position of
 *       an element plus one in its low bits and hash bits (a tag) in the remaining high bits;
 *       {@code 0} is an empty slot. A lookup compares the tag before it reads an element, so a
 *       lookup of an absent element usually reads only the index table.</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed} with a per-instance seed. The
 *       highest bits of the product select the home slot, the bits below them form the tag.</li>
 *   <li><b>Removal</b> leaves a hole in the element array and a tombstone in the index table;
 *       elements do not move. Holes inside the element range are marked in a bitmap that is
 *       allocated with the first such hole; a hole at either end just shrinks the range. When
 *       the element array is full, it is compacted in place if at least a quarter of it is
 *       unused, and grows otherwise. Both rebuild the index table, which removes the
 *       tombstones.</li>
 *   <li><b>Order:</b> {@link #add} of a present element keeps its position. {@link #addLast}
 *       and {@link #moveToLast} make an element the last one; {@link #pollFirst} and
 *       {@link #pollLast} remove at the ends.</li>
 *   <li><b>Every element</b> is stored in the element array; {@code 0} needs no special
 *       case.</li>
 *   <li><b>Load factor:</b> the maximum ratio of element positions to index slots. The default
 *       {@value #DEFAULT_LOAD_FACTOR} keeps the index table small.</li>
 * </ul>
 * See {@link IntSet} for the contract. Two sets are equal if they contain the same elements,
 * whatever their order. This class is not thread-safe.
 */
public final class IntLinkedHashSet implements IntSet {

    /** Default maximum ratio of element positions (holes included) to index slots. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of elements the set can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_SLOTS = 8;
    private static final int MAX_SLOTS = 1 << 30;
    /** Index slot of a removed element: position part {@code 0}, so it never denotes an element. */
    private static final int TOMBSTONE = Integer.MIN_VALUE;

    private final float loadFactor;
    private final long seed;

    /** The elements in insertion order; positions {@code [head, end)} are in use. */
    private int[] keys;
    /** Index table; its length is a power of two. */
    private int[] index;
    /** {@code log2(index.length)}: the number of low bits of a slot that hold the position plus one. */
    private int bits;
    /** {@code 64 - bits}. */
    private int shift;
    /** Position of the first element (if the set is not empty). */
    private int head;
    /** Position behind the last element. */
    private int end;
    /** Number of elements. */
    private int size;
    /** Number of index slots that are not empty: one per element, plus the tombstones. */
    private int occupied;
    /** Bit {@code e} is set if position {@code e} in {@code (head, end)} is a hole; {@code null} without holes. */
    private long[] holes;

    /** An empty set with room for {@link #DEFAULT_EXPECTED_SIZE} elements. */
    public IntLinkedHashSet() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty set with room for {@code expectedSize} elements.
     *
     * @param expectedSize the number of elements the set can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public IntLinkedHashSet(int expectedSize) {
        this(expectedSize, DEFAULT_LOAD_FACTOR);
    }

    /**
     * An empty set with room for {@code expectedSize} elements at the given load factor.
     *
     * @param expectedSize the number of elements the set can hold without resizing
     * @param loadFactor the maximum fill of the index table, in {@code (0, 1)}
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large, or
     *         {@code loadFactor} is not in {@code (0, 1)}
     */
    public IntLinkedHashSet(int expectedSize, float loadFactor) {
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
     * A set with the elements of {@code other} in its iteration order.
     *
     * @param other the set to copy (any implementation)
     */
    public IntLinkedHashSet(IntSet other) {
        this(other.size());
        addAll(other);
    }

    /**
     * A set with the given elements, in the order of their first occurrence; an element given more than once is added once. Unlike
     * {@link java.util.Set#of}, the set is modifiable and accepts duplicates.
     *
     * @param elements the elements
     * @return a new set
     */
    public static IntLinkedHashSet of(int... elements) {
        IntLinkedHashSet set = new IntLinkedHashSet(elements.length);
        for (int e : elements) {
            set.add(e);
        }
        return set;
    }

    private static long hash(long key, long seed) {
        return (key ^ seed) * PHI;
    }

    /** The tag of a hash in the high bits of a slot (the bits below those that select the home slot). */
    private static int tag(long hash, int bits) {
        return (int) (hash >>> 32) << bits;
    }

    /** The element position in a slot, {@code -1} for a tombstone. */
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
    public boolean contains(int key) {
        return find(key) >= 0;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public boolean add(int key) {
        int s = probe(key);
        if (s >= 0) {
            return false;
        }
        insertNew(~s, key);
        return true;
    }

    @Override
    public boolean remove(int key) {
        int s = probe(key);
        if (s < 0) {
            return false;
        }
        delete(s, position(index[s]));
        return true;
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
     * Adds {@code key} if it is absent and makes it the last element in iteration order,
     * whether or not it was present.
     *
     * @param key the element
     * @return {@code true} if the element was added, {@code false} if it was present
     */
    public boolean addLast(int key) {
        int s = probe(key);
        if (s < 0) {
            insertNew(~s, key);
            return true;
        }
        moveToLast(s, position(index[s]), key);
        return false;
    }

    /**
     * Makes {@code key} the last element in iteration order.
     *
     * @param key the element
     * @return {@code false} if the element is absent
     */
    public boolean moveToLast(int key) {
        int s = probe(key);
        if (s < 0) {
            return false;
        }
        moveToLast(s, position(index[s]), key);
        return true;
    }

    private void moveToLast(int s, int e, int key) {
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
        drop(e);
        int at = end++;
        keys[at] = key;
        index[s] = (index[s] & -index.length) | (at + 1);
    }

    /**
     * The first element in iteration order.
     *
     * @return the first element
     * @throws NoSuchElementException if the set is empty
     */
    public int first() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return keys[head];
    }

    /**
     * The last element in iteration order.
     *
     * @return the last element
     * @throws NoSuchElementException if the set is empty
     */
    public int last() {
        if (size == 0) {
            throw new NoSuchElementException();
        }
        return keys[end - 1];
    }

    /**
     * Removes and returns the first element in iteration order.
     *
     * @return the removed element
     * @throws NoSuchElementException if the set is empty
     */
    public int pollFirst() {
        int key = first();
        delete(probe(key), head);
        return key;
    }

    /**
     * Removes and returns the last element in iteration order.
     *
     * @return the removed element
     * @throws NoSuchElementException if the set is empty
     */
    public int pollLast() {
        int key = last();
        delete(probe(key), end - 1);
        return key;
    }

    /**
     * Handle of the last element in iteration order, or {@code -1} if the set is empty.
     *
     * @return the handle of the last element, or {@code -1}
     */
    public int lastIndex() {
        return size == 0 ? -1 : end - 1;
    }

    /**
     * Handle of the element before {@code index} in iteration order, or {@code -1} if there is none.
     *
     * @param index an existing handle
     * @return the handle of the previous element, or {@code -1}
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
    public int indexKey(int index) {
        assert index >= head && index < end && !isHole(index) : "index does not exist: " + index;
        return keys[index];
    }

    @Override
    public boolean removeIf(IntPredicate predicate) {
        boolean removed = false;
        // Removals do not move elements; they may advance head and lower end.
        for (int e = head; e < end; e++) {
            if (!isHole(e) && predicate.test(keys[e])) {
                delete(probe(keys[e]), e);
                removed = true;
            }
        }
        return removed;
    }

    @Override
    public void forEach(IntConsumer action) {
        final int[] ks = keys;
        final long[] h = holes;
        final int to = end;
        if (h == null) {
            for (int e = head; e < to; e++) {
                action.accept(ks[e]);
            }
        } else {
            for (int e = head; e < to; e++) {
                if ((h[e >>> 6] & (1L << e)) == 0) {
                    action.accept(ks[e]);
                }
            }
        }
    }

    @Override
    public int[] toArray() {
        if (holes == null) {
            return Arrays.copyOfRange(keys, head, end);
        }
        int[] result = new int[size];
        int n = 0;
        for (int e = head; e < end; e++) {
            if (!isHole(e)) {
                result[n++] = keys[e];
            }
        }
        return result;
    }

    /**
     * A new {@link LinkedHashSet} with the elements of this set, boxed, in iteration order.
     *
     * @return a new {@link LinkedHashSet}
     */
    @Override
    public Set<Integer> toSet() {
        Set<Integer> set = LinkedHashSet.newLinkedHashSet(size);
        forEach(set::add);
        return set;
    }

    /** Equal to any {@link IntSet} with the same elements, whatever the order. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntSet other) || other.size() != size) {
            return false;
        }
        for (int e = head; e < end; e++) {
            if (!isHole(e) && !other.contains(keys[e])) {
                return false;
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Set#hashCode()} on boxed elements. */
    @Override
    public int hashCode() {
        int h = 0;
        for (int e = head; e < end; e++) {
            if (!isHole(e)) {
                h += Integer.hashCode(keys[e]);
            }
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size, 1 << 16) * 8 + 2).append('[');
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

    /** Position of {@code key}, or {@code -1} if absent. */
    private int find(int key) {
        final int[] ix = index;
        final int[] ks = keys;
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
    private int probe(int key) {
        final int[] ix = index;
        final int[] ks = keys;
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

    /** Appends an absent element; {@code s} is its insertion slot from {@link #probe}. */
    private void insertNew(int s, int key) {
        // A position that a removal at the end gave back has left its tombstone behind, so the
        // index table can fill up before the element array does.
        if (end == keys.length || (index[s] == 0 && occupied == keys.length)) {
            makeRoom();
            s = ~probe(key);
        }
        if (index[s] == 0) {
            occupied++;
        }
        int at = end++;
        keys[at] = key;
        index[s] = tag(hash(key, seed), bits) | (at + 1);
        size++;
    }

    /** Removes the element at position {@code e}, whose index slot is {@code s}. */
    private void delete(int s, int e) {
        index[s] = TOMBSTONE;
        size--;
        drop(e);
    }

    /** Takes position {@code e} out of the element range: shrinks the range at its ends, marks a hole inside. */
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

    /** The element array is full: compacts it if a quarter is unused, grows otherwise. */
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

    /** Moves the elements to the front of an element array for an index table of {@code slots} slots, and rebuilds the table. */
    private void rebuild(int slots) {
        final int[] oldKeys = keys;
        final int from = head;
        final int to = end;
        final boolean inPlace = slots == index.length;
        if (inPlace) {
            Arrays.fill(index, 0);
        } else {
            allocate(slots);
        }
        final int[] ks = keys;
        int n = 0;
        for (int e = from; e < to; e++) {
            if (!isHole(e)) {
                ks[n] = oldKeys[e];
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
        keys = new int[capacity];
        index = new int[slots];
        bits = Integer.numberOfTrailingZeros(slots);
        shift = 64 - bits;
    }

    /** Number of element positions for an index table of {@code slots} slots (at least one slot stays empty). */
    private static int capacity(int slots, float loadFactor) {
        return Math.min(slots - 1, Math.max(1, (int) (slots * (double) loadFactor)));
    }

    /** Index slots (a power of two) for {@code expectedSize} elements. */
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

    /** Number of element positions (for tests). */
    int capacity() {
        return keys.length;
    }

    /** Number of positions in the element range that are holes (for tests). */
    int holeCount() {
        return end - head - size;
    }
}
