// Generated from template indexed-heap/IntVTypeIndexedHeap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.NoSuchElementException;

import net.generated.example.function.IntIntConsumer;

/**
 * Indexed priority queue: a heap of ids {@code 0, 1, 2, ...} with {@code int} priorities that
 * can change while the ids are in the heap ("decrease key"), as in Dijkstra's shortest paths
 * or A* search. It returns the id with the smallest priority first, or the largest with
 * {@code maxFirst}. Every id is in the heap at most once.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> an implicit 4-ary heap (as in {@code IntHeap}) of entries in two
 *       parallel arrays, {@code int[]} ids and {@code int[]} priorities, so that a sift
 *       compares priorities without following the ids. A third {@code int[]}, indexed by id,
 *       holds the heap position of each id, so that {@link #contains}, {@link #priority},
 *       {@link #put}, and {@link #remove} find an id in constant time.</li>
 *   <li><b>Dense ids:</b> the position array covers the ids from {@code 0} to the largest id
 *       ever added; it grows by half its length or more. Ids are meant to be small, such as
 *       node numbers of a graph.</li>
 *   <li><b>Order:</b> as in {@code IntHeap}, the arrays hold a min-heap of encoded
 *       priorities ({@code ~p} for a max-first heap).</li>
 *   <li><b>Cost:</b> {@link #put}, {@link #putIfBetter}, {@link #poll}, and {@link #remove} take
 *       {@code O(log n)} time, the queries constant time. Memory is {@code 4 + Integer.BYTES}
 *       bytes per entry of capacity, plus 4 bytes per id up to the largest id.</li>
 * </ul>
 * Entries of equal priority come out in an unspecified order. Like
 * {@link java.util.PriorityQueue}, a heap is equal only to itself. This class is not
 * thread-safe.
 */
public final class IntIntIndexedHeap {

    /** Default number of ids the heap can hold without growing. */
    public static final int DEFAULT_EXPECTED_IDS = 16;

    private static final int MAX_CAPACITY = Integer.MAX_VALUE - 8;

    private final boolean maxFirst;
    /** {@code 0} for a min-first heap, {@code -1} for a max-first heap: the stored priority of {@code p} is {@code p ^ flip}. */
    private final int flip;

    /** Ids in heap order. */
    private int[] ids;
    /** Encoded priorities in heap order; {@code prios[0 .. size)} is a min-heap. */
    private int[] prios;
    /** Heap position + 1 of every id, {@code 0} for an id that is not in the heap. */
    private int[] pos;
    private int size;

    /** An empty min-first heap with room for {@link #DEFAULT_EXPECTED_IDS} ids. */
    public IntIntIndexedHeap() {
        this(DEFAULT_EXPECTED_IDS, false);
    }

    /**
     * An empty min-first heap with room for the ids {@code 0 .. expectedIds - 1}.
     *
     * @param expectedIds the number of ids the heap can hold without growing
     * @throws IllegalArgumentException if {@code expectedIds} is negative or larger than the maximum
     *         capacity
     */
    public IntIntIndexedHeap(int expectedIds) {
        this(expectedIds, false);
    }

    /**
     * An empty heap with room for the ids {@code 0 .. expectedIds - 1}.
     *
     * @param expectedIds the number of ids the heap can hold without growing
     * @param maxFirst {@code true} to return the id with the largest priority first, {@code false} for the smallest
     * @throws IllegalArgumentException if {@code expectedIds} is negative or larger than the maximum
     *         capacity
     */
    public IntIntIndexedHeap(int expectedIds, boolean maxFirst) {
        if (expectedIds < 0 || expectedIds > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedIds must be in [0, " + MAX_CAPACITY + "]: " + expectedIds);
        }
        this.maxFirst = maxFirst;
        this.flip = maxFirst ? -1 : 0;
        this.ids = new int[expectedIds];
        this.prios = new int[expectedIds];
        this.pos = new int[expectedIds];
    }

    /** A copy of {@code other} with arrays of the size of its content. */
    private IntIntIndexedHeap(IntIntIndexedHeap other) {
        this.maxFirst = other.maxFirst;
        this.flip = other.flip;
        this.ids = Arrays.copyOf(other.ids, other.size);
        this.prios = Arrays.copyOf(other.prios, other.size);
        this.pos = other.pos.clone();
        this.size = other.size;
    }

    /** The stored priority of {@code p}. */
    private int encode(int p) {
        return p ^ flip;
    }

    /** The priority of a stored priority. */
    private int decode(int v) {
        return v ^ flip;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /**
     * The number of ids in the heap.
     *
     * @return the number of ids
     */
    public int size() {
        return size;
    }

    /**
     * Whether the heap holds no ids.
     *
     * @return {@code true} if the heap is empty
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Whether the heap returns the id with the largest priority first.
     *
     * @return {@code true} for a max-first heap, {@code false} for a min-first heap
     */
    public boolean isMaxFirst() {
        return maxFirst;
    }

    /**
     * Whether {@code id} is in the heap.
     *
     * @param id the id; negative ids are never in the heap
     * @return {@code true} if the heap holds {@code id}
     */
    public boolean contains(int id) {
        return slot(id) >= 0;
    }

    /**
     * The priority of {@code id}.
     *
     * @param id the id
     * @return the priority of {@code id}
     * @throws NoSuchElementException if {@code id} is not in the heap
     */
    public int priority(int id) {
        int s = slot(id);
        if (s < 0) {
            throw new NoSuchElementException("id not in the heap: " + id);
        }
        return decode(prios[s]);
    }

    /**
     * The priority of {@code id}, or {@code defaultValue} if it is not in the heap.
     *
     * @param id the id
     * @param defaultValue the result for an id that is not in the heap
     * @return the priority of {@code id}, or {@code defaultValue}
     */
    public int priorityOrDefault(int id, int defaultValue) {
        int s = slot(id);
        return s >= 0 ? decode(prios[s]) : defaultValue;
    }

    /**
     * The id that {@link #poll} would return, without removing it.
     *
     * @return the id with the first priority
     * @throws NoSuchElementException if the heap is empty
     */
    public int peekId() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return ids[0];
    }

    /**
     * The priority of the id that {@link #poll} would return.
     *
     * @return the first priority: the smallest, or the largest for a max-first heap
     * @throws NoSuchElementException if the heap is empty
     */
    public int peekPriority() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        return decode(prios[0]);
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    /**
     * Adds {@code id} with {@code priority}, or changes its priority to {@code priority} if it
     * is in the heap already.
     *
     * @param id the id, {@code >= 0}
     * @param priority the priority
     * @throws IllegalArgumentException if {@code id} is negative
     */
    public void put(int id, int priority) {
        int v = encode(priority);
        int s = slotForUpdate(id);
        if (s < 0) {
            insert(id, v);
        } else if (v < prios[s]) {
            siftUp(s, id, v);
        } else {
            siftDown(s, id, v, size);
        }
    }

    /**
     * Adds {@code id} with {@code priority} if it is not in the heap; otherwise changes its
     * priority to {@code priority} only if that comes before the current priority (smaller, or
     * larger for a max-first heap). This is the "relax" step of Dijkstra's algorithm.
     *
     * @param id the id, {@code >= 0}
     * @param priority the priority
     * @return {@code true} if the heap changed
     * @throws IllegalArgumentException if {@code id} is negative
     */
    public boolean putIfBetter(int id, int priority) {
        int v = encode(priority);
        int s = slotForUpdate(id);
        if (s < 0) {
            insert(id, v);
            return true;
        }
        if (v < prios[s]) {
            siftUp(s, id, v);
            return true;
        }
        return false;
    }

    /**
     * Removes and returns the id with the first priority. Call {@link #peekPriority} before to
     * get its priority.
     *
     * @return the id with the first priority
     * @throws NoSuchElementException if the heap is empty
     */
    public int poll() {
        int n = size;
        if (n == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        int top = ids[0];
        pos[top] = 0;
        size = --n;
        if (n > 0) {
            siftDown(0, ids[n], prios[n], n);
        }
        return top;
    }

    /**
     * Removes {@code id} from the heap.
     *
     * @param id the id
     * @return {@code true} if {@code id} was in the heap
     */
    public boolean remove(int id) {
        int s = slot(id);
        if (s < 0) {
            return false;
        }
        pos[id] = 0;
        int n = --size;
        if (s < n) {
            // The last entry fills the gap and moves up or down from there.
            int removed = prios[s];
            int lid = ids[n];
            int lv = prios[n];
            if (lv < removed) {
                siftUp(s, lid, lv);
            } else {
                siftDown(s, lid, lv, n);
            }
        }
        return true;
    }

    /** Removes all ids; the capacity stays. Takes time proportional to the size, not to the largest id. */
    public void clear() {
        final int[] is = ids;
        final int[] ps = pos;
        for (int i = 0, n = size; i < n; i++) {
            ps[is[i]] = 0;
        }
        size = 0;
    }

    /**
     * Grows the arrays, if needed, so that the ids {@code 0 .. expectedIds - 1} fit without
     * growing again.
     *
     * @param expectedIds the number of ids
     * @throws IllegalArgumentException if {@code expectedIds} is negative or larger than the maximum
     *         capacity
     */
    public void ensureCapacity(int expectedIds) {
        if (expectedIds < 0 || expectedIds > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedIds must be in [0, " + MAX_CAPACITY + "]: " + expectedIds);
        }
        if (expectedIds > pos.length) {
            pos = Arrays.copyOf(pos, expectedIds);
        }
        if (expectedIds > ids.length) {
            ids = Arrays.copyOf(ids, expectedIds);
            prios = Arrays.copyOf(prios, expectedIds);
        }
    }

    /**
     * Shrinks the arrays to the ids in the heap: the heap to {@link #size} entries, the position
     * array to the largest id in the heap plus one. Takes time proportional to the size.
     */
    public void trimToSize() {
        final int n = size;
        final int[] is = ids;
        int maxId = -1;
        for (int i = 0; i < n; i++) {
            maxId = Math.max(maxId, is[i]);
        }
        if (pos.length > maxId + 1) {
            pos = Arrays.copyOf(pos, maxId + 1);
        }
        if (ids.length > n) {
            ids = Arrays.copyOf(ids, n);
            prios = Arrays.copyOf(prios, n);
        }
    }

    // ------------------------------------------------------------------
    // Iteration
    // ------------------------------------------------------------------

    /**
     * Calls {@code action} for every id and its priority, in an unspecified order.
     *
     * @param action the action
     */
    public void forEach(IntIntConsumer action) {
        final int[] is = ids;
        final int[] ps = prios;
        for (int i = 0, n = size; i < n; i++) {
            action.accept(is[i], decode(ps[i]));
        }
    }

    /** The entries in the order in which {@link #poll} would return them, as {@code {id=priority, ...}}. */
    @Override
    public String toString() {
        IntIntIndexedHeap copy = new IntIntIndexedHeap(this);
        StringBuilder sb = new StringBuilder(Math.min(size, 1 << 16) * 12 + 2).append('{');
        while (!copy.isEmpty()) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(copy.ids[0]).append('=').append(copy.peekPriority());
            copy.poll();
        }
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Heap position of {@code id}, or {@code -1} if it is not in the heap. */
    private int slot(int id) {
        final int[] ps = pos;
        return id >= 0 && id < ps.length ? ps[id] - 1 : -1;
    }

    /** Heap position of {@code id}, or {@code -1}; makes room in the position array for {@code id >= 0}. */
    private int slotForUpdate(int id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0: " + id);
        }
        int[] ps = pos;
        if (id >= ps.length) {
            if (id >= MAX_CAPACITY) {
                throw new IllegalArgumentException("id too large: " + id);
            }
            int len = ps.length;
            pos = Arrays.copyOf(ps, (int) Math.min(MAX_CAPACITY, Math.max(id + 1L, len + (len >> 1) + 1L)));
            return -1;
        }
        return ps[id] - 1;
    }

    /** Adds the absent {@code id} with the stored priority {@code v}. */
    private void insert(int id, int v) {
        int n = size;
        if (n == ids.length) {
            if (n >= MAX_CAPACITY) {
                throw new IllegalStateException("Maximum capacity exceeded: " + MAX_CAPACITY);
            }
            int cap = (int) Math.min(MAX_CAPACITY, n + (n >> 1) + 1L);
            ids = Arrays.copyOf(ids, cap);
            prios = Arrays.copyOf(prios, cap);
        }
        size = n + 1;
        siftUp(n, id, v);
    }

    /** Moves the hole at position {@code i} up until {@code v} fits, and stores the entry there. */
    private void siftUp(int i, int id, int v) {
        final int[] is = ids;
        final int[] ps = prios;
        final int[] at = pos;
        while (i > 0) {
            int p = (i - 1) >>> 2;
            int pv = ps[p];
            if (!(v < pv)) {
                break;
            }
            int pid = is[p];
            ps[i] = pv;
            is[i] = pid;
            at[pid] = i + 1;
            i = p;
        }
        ps[i] = v;
        is[i] = id;
        at[id] = i + 1;
    }

    /**
     * Moves the hole at position {@code i} of a heap of {@code n} entries down until {@code v}
     * fits, and stores the entry there.
     */
    private void siftDown(int i, int id, int v, int n) {
        final int[] is = ids;
        final int[] ps = prios;
        final int[] at = pos;
        // Positions up to lastParent have at least one child; 4 * lastParent + 4 cannot overflow.
        final int lastParent = (n - 2) >> 2;
        while (i <= lastParent) {
            int c = (i << 2) + 1;
            int end = Math.min(c + 4, n);
            int m = c;
            int mv = ps[c];
            // A plain loop over at most four children: its speed does not depend on the data the
            // JIT saw when it compiled the method. With eight children, a queue that the JIT first
            // saw in ascending order (the first child always the smallest) stayed twice as slow for
            // any data; a tournament of pairwise comparisons was slow for random values.
            for (int j = c + 1; j < end; j++) {
                int jv = ps[j];
                if (jv < mv) {
                    m = j;
                    mv = jv;
                }
            }
            if (!(mv < v)) {
                break;
            }
            int mid = is[m];
            ps[i] = mv;
            is[i] = mid;
            at[mid] = i + 1;
            i = m;
        }
        ps[i] = v;
        is[i] = id;
        at[id] = i + 1;
    }
}
