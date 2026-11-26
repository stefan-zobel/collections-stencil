// Generated from template indexed-heap-long/LongVTypeIndexedHeap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;

import net.generated.example.function.LongLongConsumer;

/**
 * Indexed priority queue with arbitrary {@code long} keys: a heap of keys with {@code long}
 * priorities that can change while the keys are in the heap ("decrease key"). It returns the key
 * with the smallest priority first, or the largest with {@code maxFirst}. Every key is in the
 * heap at most once. Typical uses are timeouts that are rescheduled or canceled (key: a
 * connection or session id, priority: the deadline) and shortest paths on graphs with sparse
 * 64-bit node ids.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Heap:</b> an implicit 4-ary heap of entries in two parallel arrays, as in
 *       {@link IntLongIndexedHeap}: encoded priorities in a {@code long[]}, and for every
 *       entry the slot of its key in the hash table in an {@code int[]}.</li>
 *   <li><b>Hash table:</b> open addressing with linear probing over one {@code long[]}; slot
 *       {@code s} holds the key at index {@code 2s} and its heap position + 1 at
 *       {@code 2s + 1}, so a lookup reads one cache line. A slot is empty when its position word
 *       is {@code 0}, so every {@code long}, including {@code 0}, can be a key. Fibonacci
 *       hashing of {@code key ^ seed} with a random {@code seed} per instance; removal uses
 *       backward-shift deletion (no tombstones); the table grows at a load of
 *       {@code 0.75}.</li>
 *   <li><b>The table slot is the id:</b> a sift step that moves an entry writes its new
 *       position into the table slot of its key, an array access without hashing. Only the
 *       lookup of the key at the start of an operation hashes, once.</li>
 *   <li><b>Order:</b> as in {@code LongHeap}, the arrays hold a min-heap of encoded
 *       priorities ({@code ~p} for a max-first heap).</li>
 *   <li><b>Cost:</b> {@link #put}, {@link #putIfBetter}, {@link #poll}, and {@link #remove} take
 *       {@code O(log n)} time, the queries expected constant time. Memory is
 *       {@code 4 + Long.BYTES} bytes per entry of heap capacity, plus 16 bytes per table slot
 *       (about 21 bytes per key at a load of {@code 0.75}). For dense ids {@code 0, 1, 2, ...},
 *       {@link IntLongIndexedHeap} needs less memory and no hashing.</li>
 * </ul>
 * Entries of equal priority come out in an unspecified order. Like
 * {@link java.util.PriorityQueue}, a heap is equal only to itself. This class is not
 * thread-safe.
 */
public final class LongLongIndexedHeap {

    /** Default number of keys the heap can hold without growing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final double LOAD_FACTOR = 0.75;
    /** Minimum number of table slots. */
    private static final int MIN_TABLE_CAPACITY = 4;
    /** Maximum number of table slots; the table has twice as many words. */
    private static final int MAX_TABLE_CAPACITY = 1 << 29;
    /** Maximum number of keys: the fill limit of the largest table. */
    private static final int MAX_SIZE = maxFill(MAX_TABLE_CAPACITY);

    private final boolean maxFirst;
    /** {@code 0} for a min-first heap, {@code -1} for a max-first heap: the stored priority of {@code p} is {@code p ^ flip}. */
    private final long flip;
    private final long seed;

    /** Keys and heap positions + 1, interleaved; length is twice the table capacity, a power of two. */
    private long[] table;
    /** {@code 64 - log2(table.length)}. */
    private int shift;
    /** Maximum number of keys before the table grows. */
    private int maxFill;

    /** Table index ({@code 2s}) of the key of every heap entry, in heap order. */
    private int[] slots;
    /** Encoded priorities in heap order; {@code prios[0 .. size)} is a min-heap. */
    private long[] prios;
    private int size;

    /** An empty min-first heap with room for {@link #DEFAULT_EXPECTED_SIZE} keys. */
    public LongLongIndexedHeap() {
        this(DEFAULT_EXPECTED_SIZE, false);
    }

    /**
     * An empty min-first heap with room for {@code expectedSize} keys.
     *
     * @param expectedSize the number of keys the heap can hold without growing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the
     *         maximum capacity
     */
    public LongLongIndexedHeap(int expectedSize) {
        this(expectedSize, false);
    }

    /**
     * An empty heap with room for {@code expectedSize} keys.
     *
     * @param expectedSize the number of keys the heap can hold without growing
     * @param maxFirst {@code true} to return the key with the largest priority first, {@code false} for the smallest
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the
     *         maximum capacity
     */
    public LongLongIndexedHeap(int expectedSize, boolean maxFirst) {
        if (expectedSize < 0 || expectedSize > MAX_SIZE) {
            throw new IllegalArgumentException("expectedSize must be in [0, " + MAX_SIZE + "]: " + expectedSize);
        }
        this.maxFirst = maxFirst;
        this.flip = maxFirst ? -1 : 0;
        this.seed = ThreadLocalRandom.current().nextLong();
        allocate(capacityFor(expectedSize));
        this.slots = new int[expectedSize];
        this.prios = new long[expectedSize];
    }

    /** A copy of {@code other} with heap arrays of the size of its content. */
    private LongLongIndexedHeap(LongLongIndexedHeap other) {
        this.maxFirst = other.maxFirst;
        this.flip = other.flip;
        this.seed = other.seed;
        this.table = other.table.clone();
        this.shift = other.shift;
        this.maxFill = other.maxFill;
        this.slots = Arrays.copyOf(other.slots, other.size);
        this.prios = Arrays.copyOf(other.prios, other.size);
        this.size = other.size;
    }

    /** The stored priority of {@code p}. */
    private long encode(long p) {
        return p ^ flip;
    }

    /** The priority of a stored priority. */
    private long decode(long v) {
        return v ^ flip;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * The number of keys in the heap.
     *
     * @return the number of keys
     */
    public int size() {
        return size;
    }

    /**
     * Whether the heap holds no keys.
     *
     * @return {@code true} if the heap is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Whether the heap returns the key with the largest priority first.
     *
     * @return {@code true} for a max-first heap, {@code false} for a min-first heap
     */
    public boolean isMaxFirst() {
        return maxFirst;
    }

    /**
     * Whether {@code key} is in the heap.
     *
     * @param key the key
     * @return {@code true} if the heap holds {@code key}
     */
    public boolean contains(long key) {
        return probe(key) >= 0;
    }

    /**
     * The priority of {@code key}.
     *
     * @param key the key
     * @return the priority of {@code key}
     * @throws NoSuchElementException if {@code key} is not in the heap
     */
    public long priority(long key) {
        int t = probe(key);
        if (t < 0) {
            throw new NoSuchElementException("key not in the heap: " + key);
        }
        return decode(prios[(int) table[t + 1] - 1]);
    }

    /**
     * The priority of {@code key}, or {@code defaultValue} if it is not in the heap.
     *
     * @param key the key
     * @param defaultValue the result for a key that is not in the heap
     * @return the priority of {@code key}, or {@code defaultValue}
     */
    public long priorityOrDefault(long key, long defaultValue) {
        int t = probe(key);
        return t >= 0 ? decode(prios[(int) table[t + 1] - 1]) : defaultValue;
    }

    /**
     * The key that {@link #poll} would return, without removing it.
     *
     * @return the key with the first priority
     * @throws NoSuchElementException if the heap is empty
     */
    public long peekKey() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return table[slots[0]];
    }

    /**
     * The priority of the key that {@link #poll} would return.
     *
     * @return the first priority: the smallest, or the largest for a max-first heap
     * @throws NoSuchElementException if the heap is empty
     */
    public long peekPriority() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return decode(prios[0]);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /**
     * Adds {@code key} with {@code priority}, or changes its priority to {@code priority} if it is in
     * the heap already.
     *
     * @param key the key
     * @param priority the priority
     * @throws IllegalStateException if the heap would exceed its maximum size
     */
    public void put(long key, long priority) {
        long v = encode(priority);
        int t = probe(key);
        if (t < 0) {
            insert(~t, key, v);
            return;
        }
        int s = (int) table[t + 1] - 1;
        if (v < prios[s]) {
            siftUp(s, t, v);
        } else {
            siftDown(s, t, v, size);
        }
    }

    /**
     * Adds {@code key} with {@code priority} if it is not in the heap; otherwise changes its
     * priority to {@code priority} only if that comes before the current priority (smaller, or
     * larger for a max-first heap). This is the "relax" step of Dijkstra's algorithm.
     *
     * @param key the key
     * @param priority the priority
     * @return {@code true} if the heap changed
     * @throws IllegalStateException if the heap would exceed its maximum size
     */
    public boolean putIfBetter(long key, long priority) {
        long v = encode(priority);
        int t = probe(key);
        if (t < 0) {
            insert(~t, key, v);
            return true;
        }
        int s = (int) table[t + 1] - 1;
        if (v < prios[s]) {
            siftUp(s, t, v);
            return true;
        }
        return false;
    }

    /**
     * Removes and returns the key with the first priority. Call {@link #peekPriority} before to
     * get its priority.
     *
     * @return the key with the first priority
     * @throws NoSuchElementException if the heap is empty
     */
    public long poll() {
        int n = size;
        if (n == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        int t = slots[0];
        long top = table[t];
        // Deleting the table slot may move other keys in the table and update their entries in
        // slots[], so the last entry is read afterwards.
        deleteAt(t);
        size = --n;
        if (n > 0) {
            siftDown(0, slots[n], prios[n], n);
        }
        return top;
    }

    /**
     * Removes {@code key} from the heap.
     *
     * @param key the key
     * @return {@code true} if {@code key} was in the heap
     */
    public boolean remove(long key) {
        int t = probe(key);
        if (t < 0) {
            return false;
        }
        int s = (int) table[t + 1] - 1;
        deleteAt(t);
        int n = --size;
        if (s < n) {
            // The last entry fills the gap and moves up or down from there.
            long removed = prios[s];
            int lt = slots[n];
            long lv = prios[n];
            if (lv < removed) {
                siftUp(s, lt, lv);
            } else {
                siftDown(s, lt, lv, n);
            }
        }
        return true;
    }

    /** Removes all keys; the capacity stays. Takes time proportional to the size, not to the capacity. */
    public void clear() {
        final long[] tab = table;
        final int[] sl = slots;
        for (int i = 0, n = size; i < n; i++) {
            int t = sl[i];
            tab[t] = 0L;
            tab[t + 1] = 0L;
        }
        size = 0;
    }

    /**
     * Grows the hash table and the heap, if needed, to hold {@code expectedSize} keys without
     * growing again.
     *
     * @param expectedSize the number of keys
     * @throws IllegalArgumentException if {@code expectedSize} is negative or larger than the
     *         maximum size
     */
    public void ensureCapacity(int expectedSize) {
        if (expectedSize < 0 || expectedSize > MAX_SIZE) {
            throw new IllegalArgumentException("expectedSize must be in [0, " + MAX_SIZE + "]: " + expectedSize);
        }
        int capacity = capacityFor(expectedSize);
        if (capacity > table.length >>> 1) {
            rehash(capacity);
        }
        if (expectedSize > slots.length) {
            slots = Arrays.copyOf(slots, expectedSize);
            prios = Arrays.copyOf(prios, expectedSize);
        }
    }

    /**
     * Shrinks the hash table to the smallest capacity for the current size, and the heap to
     * {@link #size} entries. Takes time proportional to the size.
     */
    public void trimToSize() {
        final int n = size;
        int capacity = capacityFor(n);
        if (capacity < table.length >>> 1) {
            rehash(capacity);
        }
        if (slots.length > n) {
            slots = Arrays.copyOf(slots, n);
            prios = Arrays.copyOf(prios, n);
        }
    }

    // ------------------------------------------------------------------
    // Iteration
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for every key and its priority, in an unspecified order.
     *
     * @param action the action
     */
    public void forEach(LongLongConsumer action) {
        final long[] tab = table;
        final int[] sl = slots;
        final long[] ps = prios;
        for (int i = 0, n = size; i < n; i++) {
            action.accept(tab[sl[i]], decode(ps[i]));
        }
    }

    /** The entries in the order in which {@link #poll} would return them, as {@code {key=priority, ...}}. */
    @Override
    public String toString() {
        LongLongIndexedHeap copy = new LongLongIndexedHeap(this);
        StringBuilder sb = new StringBuilder(Math.min(size, 1 << 16) * 16 + 2).append('{');
        while (!copy.isEmpty()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            long p = copy.peekPriority();
            sb.append(copy.poll()).append('=').append(p);
        }
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Hash table
    // ------------------------------------------------------------------

    /**
     * Fibonacci hashing: high bits of {@code (key ^ seed) * PHI}, cleared lowest bit. Result
     * is the even table index of the key's home slot, in {@code [0, 2^(64 - shift))}.
     */
    private static int mix(long key, long seed, int shift) {
        return (int) (((key ^ seed) * PHI) >>> shift) & ~1;
    }

    /** Table index of {@code key} if present, otherwise {@code ~indexOfFirstEmptySlot}. */
    private int probe(long key) {
        final long[] tab = table;
        final int mask = tab.length - 2;
        int i = mix(key, seed, shift);
        for (;;) {
            if (tab[i + 1] == 0L) {
                return ~i;
            }
            if (tab[i] == key) {
                return i;
            }
            i = (i + 2) & mask;
        }
    }

    /**
     * Backward-shift deletion of the key at table index {@code gap}: moves later keys of the
     * probe chain into the gap and updates their entries in {@link #slots}.
     */
    private void deleteAt(int gap) {
        final long[] tab = table;
        final int[] sl = slots;
        final int mask = tab.length - 2;
        final long s = seed;
        final int sh = shift;
        int i = gap;
        for (;;) {
            i = (i + 2) & mask;
            long hp = tab[i + 1];
            if (hp == 0L) {
                break;
            }
            long k = tab[i];
            int ideal = mix(k, s, sh);
            // The key can move into gap iff gap lies on its probe path ideal -> i (cyclically).
            if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                tab[gap] = k;
                tab[gap + 1] = hp;
                sl[(int) hp - 1] = gap;
                gap = i;
            }
        }
        tab[gap] = 0L;
        tab[gap + 1] = 0L;
    }

    /** Doubles the table. */
    private void rehash() {
        int capacity = table.length >>> 1;
        if (capacity >= MAX_TABLE_CAPACITY) {
            throw new IllegalStateException("Maximum size exceeded: " + MAX_SIZE);
        }
        rehash(capacity << 1);
    }

    /**
     * Rebuilds the table with {@code capacity} slots, at least {@link #capacityFor} the size:
     * reinserts the keys of all heap entries and updates their slots.
     */
    private void rehash(int capacity) {
        final long[] old = table;
        allocate(capacity);
        final long[] tab = table;
        final int[] sl = slots;
        final int mask = tab.length - 2;
        final long s = seed;
        final int sh = shift;
        for (int h = 0, n = size; h < n; h++) {
            long k = old[sl[h]];
            int i = mix(k, s, sh);
            while (tab[i + 1] != 0L) {
                i = (i + 2) & mask;
            }
            tab[i] = k;
            tab[i + 1] = h + 1;
            sl[h] = i;
        }
    }

    /** Allocates an empty table of {@code capacity} slots. */
    private void allocate(int capacity) {
        table = new long[capacity << 1];
        shift = 64 - Integer.numberOfTrailingZeros(capacity << 1);
        maxFill = maxFill(capacity);
    }

    private static int maxFill(int capacity) {
        // Always keep at least one empty slot so that probing terminates.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * LOAD_FACTOR)));
    }

    /** Table capacity in slots (a power of two) for {@code expectedSize} keys. */
    private static int capacityFor(int expectedSize) {
        int cap = MIN_TABLE_CAPACITY;
        while (maxFill(cap) < expectedSize && cap < MAX_TABLE_CAPACITY) {
            cap <<= 1;
        }
        return cap;
    }

    // ------------------------------------------------------------------
    // Heap
    // ------------------------------------------------------------------

    /** Adds the absent {@code key} with the stored priority {@code v}; {@code t} is the empty table index that {@link #probe} found. */
    private void insert(int t, long key, long v) {
        int n = size;
        if (n >= maxFill) {
            rehash();
            t = ~probe(key);
        }
        if (n == slots.length) {
            int cap = (int) Math.min(MAX_SIZE, n + (n >> 1) + 1L);
            slots = Arrays.copyOf(slots, cap);
            prios = Arrays.copyOf(prios, cap);
        }
        // The slot stays empty (position word 0) until the sift stores the position.
        table[t] = key;
        size = n + 1;
        siftUp(n, t, v);
    }

    /** Moves the hole at position {@code i} up until {@code v} fits, and stores the entry of table index {@code t} there. */
    private void siftUp(int i, int t, long v) {
        final long[] tab = table;
        final int[] sl = slots;
        final long[] ps = prios;
        while (i > 0) {
            int p = (i - 1) >>> 2;
            long pv = ps[p];
            if (!(v < pv)) {
                break;
            }
            int pt = sl[p];
            ps[i] = pv;
            sl[i] = pt;
            tab[pt + 1] = i + 1;
            i = p;
        }
        ps[i] = v;
        sl[i] = t;
        tab[t + 1] = i + 1;
    }

    /**
     * Moves the hole at position {@code i} of a heap of {@code n} entries down until {@code v}
     * fits, and stores the entry of table index {@code t} there.
     */
    private void siftDown(int i, int t, long v, int n) {
        final long[] tab = table;
        final int[] sl = slots;
        final long[] ps = prios;
        // Positions up to lastParent have at least one child; 4 * lastParent + 4 cannot overflow.
        final int lastParent = (n - 2) >> 2;
        while (i <= lastParent) {
            int c = (i << 2) + 1;
            int end = Math.min(c + 4, n);
            int m = c;
            long mv = ps[c];
            // A plain loop over at most four children, as in IntLongIndexedHeap: its speed does
            // not depend on the data the JIT saw when it compiled the method.
            for (int j = c + 1; j < end; j++) {
                long jv = ps[j];
                if (jv < mv) {
                    m = j;
                    mv = jv;
                }
            }
            if (!(mv < v)) {
                break;
            }
            int mt = sl[m];
            ps[i] = mv;
            sl[i] = mt;
            tab[mt + 1] = i + 1;
            i = m;
        }
        ps[i] = v;
        sl[i] = t;
        tab[t + 1] = i + 1;
    }

    // ------------------------------------------------------------------
    // Consistency check for tests
    // ------------------------------------------------------------------

    /**
     * Checks the heap order, the links between heap entries and table slots, and that every key
     * is reachable from its home slot.
     *
     * @throws IllegalStateException if a check fails
     */
    void checkInvariants() {
        final long[] tab = table;
        final int mask = tab.length - 2;
        int occupied = 0;
        for (int j = 0; j < tab.length; j += 2) {
            if (tab[j + 1] == 0L) {
                continue;
            }
            occupied++;
            long hp = tab[j + 1];
            if (hp < 1 || hp > size || slots[(int) hp - 1] != j) {
                throw new IllegalStateException("slot " + j + " has a wrong heap position " + hp);
            }
            for (int i = mix(tab[j], seed, shift); i != j; i = (i + 2) & mask) {
                if (tab[i + 1] == 0L) {
                    throw new IllegalStateException("key at " + j + " is not reachable from its home slot");
                }
            }
        }
        if (occupied != size) {
            throw new IllegalStateException("table has " + occupied + " keys, heap " + size);
        }
        for (int i = 0; i < size; i++) {
            if (tab[slots[i] + 1] != i + 1) {
                throw new IllegalStateException("heap entry " + i + " is not linked to its slot");
            }
            if (i > 0 && prios[i] < prios[(i - 1) >>> 2]) {
                throw new IllegalStateException("heap order violated at " + i);
            }
        }
        if (size > maxFill) {
            throw new IllegalStateException("table over its fill limit");
        }
    }
}
