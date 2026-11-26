// Generated from template btree/KTypeVTypeTreeMap.java. Edit the template, not this file.
package net.generated.example;

import java.util.Arrays;
import java.util.Objects;

import net.generated.example.function.IntBooleanConsumer;
import net.generated.example.function.IntBooleanPredicate;

/**
 * Sorted map from {@code int} keys to {@code boolean} values, implemented as a B+ tree.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Layout:</b> the entries live in leaves of up to 64 entries: sorted keys in one
 *       {@code int[]} and their values in a parallel {@code boolean[]}, shared by all leaves
 *       with a fixed stride per leaf. Leaves are linked in key order in both directions. Inner
 *       nodes of up to 64 children hold separator keys and child ids in two more shared arrays. There are no node
 *       objects.</li>
 *   <li><b>Index handles:</b> a handle is the array index of an entry ({@code leaf * LEAF +
 *       position}), so {@link #indexKey}, {@link #indexGet} and stepping to the next entry
 *       within a leaf are single array accesses.</li>
 *   <li><b>Appending:</b> a key greater than every key goes directly into the last leaf,
 *       without a search. When that leaf is full, it is kept as it is and a new leaf starts with
 *       the new key, so ascending inserts fill leaves completely. Other full leaves are split in
 *       halves.</li>
 *   <li><b>Removal:</b> a node with less than a quarter of its capacity is merged with a
 *       neighbor if both fit into three quarters of a node, otherwise entries move over from
 *       the neighbor.</li>
 *   <li><b>Memory:</b> with random inserts, leaves are about 70% full; this is about
 *       {@code (sizeof(int) + sizeof(boolean)) / 0.7} bytes per entry, plus a small share for
 *       the inner nodes. Ascending inserts fill the leaves completely.</li>
 * </ul>
 * See {@link IntBooleanSortedMap} for the contract. This class is not thread-safe.
 */
public final class IntBooleanTreeMap implements IntBooleanSortedMap {

    // Node capacities: measured against leaves of 32 and 128 entries and inner nodes of 32 and
    // 128 children; 64/64 is the best compromise between lookups, inserts and range scans.
    private static final int LEAF_SHIFT = 6;
    /** Maximum number of entries in a leaf. */
    static final int LEAF = 1 << LEAF_SHIFT;
    /** Maximum number of children of an inner node. */
    static final int INNER = 64;

    private static final int LEAF_MASK = LEAF - 1;
    private static final int MIN_LEAF = LEAF / 4;
    private static final int MERGE_LEAF = LEAF * 3 / 4;
    private static final int MIN_INNER = INNER / 4;
    private static final int MERGE_INNER = INNER * 3 / 4;
    /** Largest number of leaves whose handles are non-negative {@code int}s and whose arrays fit. */
    private static final int MAX_LEAVES = (Integer.MAX_VALUE - 8) / LEAF;
    /** Expected fill of leaves and inner nodes after random inserts, in percent. */
    private static final int EXPECTED_FILL = 70;

    // Leaves: entry i of leaf l is at index l * LEAF + i.
    private int[] leafKeys;
    private boolean[] leafValues;
    private int[] leafSize;
    /** Next leaf in key order, {@code -1} for the last; also links the free leaves. */
    private int[] leafNext;
    /** Previous leaf in key order, {@code -1} for the first. */
    private int[] leafPrev;
    /** Number of leaf ids in use or on the free list. */
    private int leafCount;
    private int freeLeaf = -1;

    // Inner nodes: node n has innerSize[n] children at n * INNER and innerSize[n] - 1
    // separators at n * INNER. Child c holds the keys k with sep[c - 1] <= k < sep[c].
    private int[] innerKeys;
    /** Child ids; slot {@code n * INNER} also links the free inner nodes. */
    private int[] innerChildren;
    private int[] innerSize;
    private int innerCount;
    private int freeInner = -1;

    /** Root node: a leaf if {@link #height} is 0, otherwise an inner node. */
    private int root;
    /** Number of inner levels. */
    private int height;
    private int firstLeaf;
    private int lastLeaf;
    private int size;

    // Path of the last recording descent: at level h (1 = parent of the leaves), the inner node
    // and the child slot taken. Valid for pathLeaf until the next structural modification.
    private int[] pathNode = new int[8];
    private int[] pathSlot = new int[8];
    private int pathLeaf = -1;

    // Scratch space for splitting and redistributing inner nodes, allocated on first use.
    private int[] scratchKeys;
    private int[] scratchChildren;

    /**
     * An empty map. It allocates a single leaf and grows its node pools as entries are added.
     */
    public IntBooleanTreeMap() {
        this(0);
    }

    /**
     * A map with room for about {@code expectedSize} mappings inserted in random order.
     * Recommended for large maps: the node pools are a few large arrays that are copied on every
     * growth step, and with the G1 collector, every array above half a heap region is a
     * humongous allocation.
     *
     * @param expectedSize the number of mappings to allocate node storage for
     * @throws IllegalArgumentException if {@code expectedSize} is negative
     */
    public IntBooleanTreeMap(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        leafKeys = new int[0];
        leafValues = new boolean[0];
        leafSize = new int[0];
        leafNext = new int[0];
        leafPrev = new int[0];
        innerKeys = new int[0];
        innerChildren = new int[0];
        innerSize = new int[0];
        ensureCapacity(expectedSize);
        initEmpty();
    }

    /**
     * A map with the mappings of {@code other} (any implementation).
     *
     * @param other the map to copy (any implementation)
     */
    public IntBooleanTreeMap(IntBooleanMap other) {
        this(0);
        if (other instanceof IntBooleanSortedMap sorted) {
            growLeaves(sorted.size() / LEAF + 1);
            for (int i = sorted.firstIndex(); i >= 0; i = sorted.nextIndex(i)) {
                append(sorted.indexKey(i), sorted.indexGet(i));
            }
            buildInnerLevels();
        } else {
            ensureCapacity(other.size());
            putAll(other);
        }
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
    public boolean containsKey(int key) {
        return leafSearch(findLeaf(key), key) >= 0;
    }

    @Override
    public boolean get(int key) {
        return getOrDefault(key, false);
    }

    @Override
    public boolean getOrDefault(int key, boolean defaultValue) {
        int i = leafSearch(findLeaf(key), key);
        return i >= 0 ? leafValues[i] : defaultValue;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public boolean put(int key, boolean value) {
        if (tryAppend(key, value)) {
            return false;
        }
        int leaf = descend(key);
        int i = leafSearch(leaf, key);
        if (i >= 0) {
            boolean old = leafValues[i];
            leafValues[i] = value;
            return old;
        }
        insertAt(leaf, ~i - (leaf << LEAF_SHIFT), key, value);
        return false;
    }

    @Override
    public boolean putIfAbsent(int key, boolean value) {
        if (tryAppend(key, value)) {
            return true;
        }
        int leaf = descend(key);
        int i = leafSearch(leaf, key);
        if (i >= 0) {
            return false;
        }
        insertAt(leaf, ~i - (leaf << LEAF_SHIFT), key, value);
        return true;
    }

    @Override
    public boolean remove(int key) {
        int leaf = descend(key);
        int i = leafSearch(leaf, key);
        if (i < 0) {
            return false;
        }
        return removeAt(leaf, i - (leaf << LEAF_SHIFT));
    }

    /** Removes all mappings. The allocated node storage is kept. */
    @Override
    public void clear() {
        initEmpty();
    }

    /**
     * Allocates node storage for about {@code expectedSize} mappings inserted in random order.
     * The tree grows on demand in any case.
     * @throws IllegalArgumentException if {@code expectedSize} is negative
     */
    @Override
    public void ensureCapacity(int expectedSize) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        long leaves = (long) expectedSize * 100 / ((long) LEAF * EXPECTED_FILL) + 1;
        growLeaves((int) Math.min(leaves, MAX_LEAVES));
        long inner = leaves * 100 / ((long) INNER * EXPECTED_FILL) + (leaves > 1 ? 2 : 0);
        growInner((int) Math.min(inner, Integer.MAX_VALUE / INNER));
    }

    // ------------------------------------------------------------------
    // Index handles
    // ------------------------------------------------------------------

    /**
     * Returns the handle of {@code key}'s entry, or a negative value if {@code key} is absent.
     * A negative handle can be passed to {@link #indexInsert} for the same key.
     */
    @Override
    public int indexOf(int key) {
        int leaf = descend(key);
        int i = leafSearch(leaf, key);
        if (i >= 0) {
            return i;
        }
        // The insertion position can be LEAF in a full leaf; keep the handle inside the leaf.
        return ~Math.min(~i, (leaf << LEAF_SHIFT) + LEAF_MASK);
    }

    @Override
    public boolean indexExists(int index) {
        return index >= 0;
    }

    @Override
    public boolean indexGet(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return leafValues[index];
    }

    @Override
    public boolean indexReplace(int index, boolean value) {
        assert index >= 0 : "index does not exist: " + index;
        boolean old = leafValues[index];
        leafValues[index] = value;
        return old;
    }

    @Override
    public void indexInsert(int index, int key, boolean value) {
        assert index < 0 : "index exists: " + index;
        int leaf = ~index >>> LEAF_SHIFT;
        if (leaf != pathLeaf) {
            leaf = descend(key);
        }
        assert leaf == ~index >>> LEAF_SHIFT : "index does not belong to key";
        int i = leafSearch(leaf, key);
        assert i < 0 : "key exists";
        insertAt(leaf, ~i - (leaf << LEAF_SHIFT), key, value);
    }

    @Override
    public boolean indexRemove(int index) {
        assert index >= 0 : "index does not exist: " + index;
        int leaf = index >>> LEAF_SHIFT;
        if (leaf != pathLeaf) {
            descend(leafKeys[index]);
        }
        return removeAt(leaf, index & LEAF_MASK);
    }

    @Override
    public int indexKey(int index) {
        assert index >= 0 : "index does not exist: " + index;
        return leafKeys[index];
    }

    // ------------------------------------------------------------------
    // Navigation and iteration
    // ------------------------------------------------------------------

    @Override
    public int firstIndex() {
        return size == 0 ? -1 : firstLeaf << LEAF_SHIFT;
    }

    @Override
    public int lastIndex() {
        return size == 0 ? -1 : (lastLeaf << LEAF_SHIFT) + leafSize[lastLeaf] - 1;
    }

    @Override
    public int nextIndex(int index) {
        assert index >= 0 : "index does not exist: " + index;
        int leaf = index >>> LEAF_SHIFT;
        if ((index & LEAF_MASK) + 1 < leafSize[leaf]) {
            return index + 1;
        }
        int next = leafNext[leaf];
        return next < 0 ? -1 : next << LEAF_SHIFT;
    }

    @Override
    public int prevIndex(int index) {
        assert index >= 0 : "index does not exist: " + index;
        if ((index & LEAF_MASK) != 0) {
            return index - 1;
        }
        int prev = leafPrev[index >>> LEAF_SHIFT];
        return prev < 0 ? -1 : (prev << LEAF_SHIFT) + leafSize[prev] - 1;
    }

    @Override
    public int floorIndex(int key) {
        int leaf = findLeaf(key);
        int i = leafSearch(leaf, key);
        return i >= 0 ? i : before(leaf, ~i);
    }

    @Override
    public int ceilingIndex(int key) {
        int leaf = findLeaf(key);
        int i = leafSearch(leaf, key);
        return at(leaf, i >= 0 ? i : ~i);
    }

    @Override
    public int lowerIndex(int key) {
        int leaf = findLeaf(key);
        int i = leafSearch(leaf, key);
        return before(leaf, i >= 0 ? i : ~i);
    }

    @Override
    public int higherIndex(int key) {
        int leaf = findLeaf(key);
        int i = leafSearch(leaf, key);
        return at(leaf, i >= 0 ? i + 1 : ~i);
    }

    /** Handle of the entry at array index {@code i} of {@code leaf}, or of the next one if {@code i} is past its end. */
    private int at(int leaf, int i) {
        if (i - (leaf << LEAF_SHIFT) < leafSize[leaf]) {
            return i;
        }
        int next = leafNext[leaf];
        return next < 0 ? -1 : next << LEAF_SHIFT;
    }

    /** Handle of the entry before array index {@code i} of {@code leaf}. */
    private int before(int leaf, int i) {
        if (i > leaf << LEAF_SHIFT) {
            return i - 1;
        }
        int prev = leafPrev[leaf];
        return prev < 0 ? -1 : (prev << LEAF_SHIFT) + leafSize[prev] - 1;
    }

    @Override
    public void forEach(IntBooleanConsumer action) {
        final int[] ks = leafKeys;
        for (int leaf = size == 0 ? -1 : firstLeaf; leaf >= 0; leaf = leafNext[leaf]) {
            int base = leaf << LEAF_SHIFT;
            for (int i = base, end = base + leafSize[leaf]; i < end; i++) {
                action.accept(ks[i], leafValues[i]);
            }
        }
    }

    // ------------------------------------------------------------------
    // Range operations
    // ------------------------------------------------------------------

    @Override
    public void forEachInRange(int from, boolean fromInclusive, int to, boolean toInclusive,
            IntBooleanConsumer action) {
        Objects.requireNonNull(action, "action");
        checkRange(from, to);
        if (isEmptyRange(from, fromInclusive, to, toInclusive)) {
            return;
        }
        final int lo = fromInclusive ? from : from + 1;
        final int hi = toInclusive ? to : to - 1;
        final int[] ks = leafKeys;
        int i = ceilingIndex(lo);
        if (i < 0) {
            return;
        }
        for (int leaf = i >>> LEAF_SHIFT; ; ) {
            for (int end = (leaf << LEAF_SHIFT) + leafSize[leaf]; i < end; i++) {
                int k = ks[i];
                if (k > hi) {
                    return;
                }
                action.accept(k, leafValues[i]);
            }
            leaf = leafNext[leaf];
            if (leaf < 0) {
                return;
            }
            i = leaf << LEAF_SHIFT;
        }
    }

    @Override
    public int countRange(int from, boolean fromInclusive, int to, boolean toInclusive) {
        checkRange(from, to);
        if (isEmptyRange(from, fromInclusive, to, toInclusive)) {
            return 0;
        }
        int a = ceilingIndex(fromInclusive ? from : from + 1);
        int b = floorIndex(toInclusive ? to : to - 1);
        if (a < 0 || b < 0 || leafKeys[a] > leafKeys[b]) {
            return 0;
        }
        int leafA = a >>> LEAF_SHIFT;
        int leafB = b >>> LEAF_SHIFT;
        if (leafA == leafB) {
            return b - a + 1;
        }
        int n = leafSize[leafA] - (a & LEAF_MASK) + (b & LEAF_MASK) + 1;
        for (int leaf = leafNext[leafA]; leaf != leafB; leaf = leafNext[leaf]) {
            n += leafSize[leaf];
        }
        return n;
    }

    /**
     * Removes the entries leaf by leaf: leaves inside the range are unlinked as a whole, the
     * leaves at the two ends lose part of their entries.
     * @throws IllegalArgumentException if {@code from > to}
     */
    @Override
    public int removeRange(int from, boolean fromInclusive, int to, boolean toInclusive) {
        checkRange(from, to);
        if (isEmptyRange(from, fromInclusive, to, toInclusive)) {
            return 0;
        }
        final int lo = fromInclusive ? from : from + 1;
        final int hi = toInclusive ? to : to - 1;
        int removed = 0;
        while (true) {
            int leaf = descend(lo);
            int a = leafSearch(leaf, lo);
            if (a < 0) {
                a = ~a;
            }
            int base = leaf << LEAF_SHIFT;
            int n = leafSize[leaf];
            if (a == base + n) {
                // All keys of this leaf are below the range: continue at the next leaf.
                int next = leafNext[leaf];
                if (next < 0) {
                    return removed;
                }
                leaf = descend(leafKeys[next << LEAF_SHIFT]);
                base = leaf << LEAF_SHIFT;
                n = leafSize[leaf];
                a = base;
            }
            if (leafKeys[a] > hi) {
                return removed;
            }
            int b = leafSearch(leaf, hi);
            b = b >= 0 ? b + 1 : ~b; // first index after the range
            int count = b - a;
            if (count == n && height > 0) {
                removeLeaf(leaf);
            } else {
                removeEntries(leaf, a - base, count);
            }
            removed += count;
        }
    }

    @Override
    public IntBooleanTreeMap subMapCopy(int from, int to) {
        return subMapCopy(from, true, to, false);
    }

    @Override
    public IntBooleanTreeMap subMapCopy(int from, boolean fromInclusive, int to, boolean toInclusive) {
        checkRange(from, to);
        IntBooleanTreeMap copy = new IntBooleanTreeMap();
        if (!isEmptyRange(from, fromInclusive, to, toInclusive)) {
            copy.growLeaves(countRange(from, fromInclusive, to, toInclusive) / LEAF + 1);
            forEachInRange(from, fromInclusive, to, toInclusive, copy::append);
            copy.buildInnerLevels();
        }
        return copy;
    }

    private static void checkRange(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("from > to: " + from + " > " + to);
        }
    }

    /** Whether a range with {@code from <= to} contains no key at all. */
    private static boolean isEmptyRange(int from, boolean fromInclusive, int to, boolean toInclusive) {
        return from == to ? !(fromInclusive && toInclusive) : (!fromInclusive && !toInclusive && from + 1 == to);
    }

    // ------------------------------------------------------------------
    // Bulk removal and Object methods
    // ------------------------------------------------------------------

    /**
     * Removes all entries for which {@code predicate} returns {@code true}. The surviving
     * entries are packed into full leaves, and the inner levels are rebuilt.
     */
    @Override
    public boolean removeIf(IntBooleanPredicate predicate) {
        Objects.requireNonNull(predicate, "predicate");
        if (size == 0) {
            return false;
        }
        final int[] ks = leafKeys;
        final boolean[] vs = leafValues;
        RuntimeException failure = null;
        int kept = 0;
        int dest = firstLeaf; // the leaf being written; never ahead of the leaf being read
        int destPos = 0;
        boolean removed = false;
        for (int leaf = firstLeaf; leaf >= 0; leaf = leafNext[leaf]) {
            int base = leaf << LEAF_SHIFT;
            for (int i = base, end = base + leafSize[leaf]; i < end; i++) {
                boolean remove = false;
                if (failure == null) {
                    try {
                        remove = predicate.test(ks[i], leafValues[i]);
                    } catch (RuntimeException e) {
                        failure = e;
                    }
                }
                if (remove) {
                    removed = true;
                    continue;
                }
                kept++;
                if (!removed) { // nothing moves before the first removal
                    dest = leaf;
                    destPos = i - base + 1;
                    continue;
                }
                if (destPos == LEAF) {
                    leafSize[dest] = LEAF;
                    dest = leafNext[dest];
                    destPos = 0;
                }
                int d = (dest << LEAF_SHIFT) + destPos++;
                ks[d] = ks[i];
                vs[d] = vs[i];
            }
        }
        if (removed) {
            leafSize[dest] = destPos;
            for (int leaf = leafNext[dest]; leaf >= 0; ) {
                int next = leafNext[leaf];
                pushFreeLeaf(leaf);
                leaf = next;
            }
            leafNext[dest] = -1;
            lastLeaf = dest;
            size = kept;
            buildInnerLevels();
        }
        if (failure != null) {
            throw failure;
        }
        return removed;
    }

    /** Equal to any {@link IntBooleanMap} with the same mappings. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntBooleanMap other) || other.size() != size) {
            return false;
        }
        for (int i = firstIndex(); i >= 0; i = nextIndex(i)) {
            int j = other.indexOf(leafKeys[i]);
            if (!other.indexExists(j) || other.indexGet(j) != leafValues[i]) {
                return false;
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int h = 0;
        for (int i = firstIndex(); i >= 0; i = nextIndex(i)) {
            h += Integer.hashCode(leafKeys[i]) ^ Boolean.hashCode(leafValues[i]);
        }
        return h;
    }

    /** The mappings in ascending key order. */
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
    // Search
    // ------------------------------------------------------------------

    /** The leaf whose key range contains {@code key}. */
    private int findLeaf(int key) {
        int node = root;
        for (int level = height; level > 0; level--) {
            node = innerChildren[node * INNER + childSlot(node, key)];
        }
        return node;
    }

    /** Like {@link #findLeaf}, and records the path for a following structural modification. */
    private int descend(int key) {
        int node = root;
        for (int level = height; level > 0; level--) {
            int slot = childSlot(node, key);
            pathNode[level] = node;
            pathSlot[level] = slot;
            node = innerChildren[node * INNER + slot];
        }
        pathLeaf = node;
        return node;
    }

    // Search within a node: a blocked search, written for nodes of 64 keys. The keys at positions
    // 8, 16, ..., 56 are compared first; these are independent loads, so a node that is not in
    // the cache requests its cache lines at once instead of one after the other. Their count
    // selects a block of eight keys, which is searched without branches (conditional moves).
    // Measured against a plain binary search (about 2x slower for nodes in the cache, which
    // mispredicts half of its branches) and a branchless binary search over the whole node
    // (whose dependent loads made lookups in maps beyond the caches up to a third slower).

    /** Slot of the child of inner node {@code node} whose key range contains {@code key}. */
    private int childSlot(int node, int key) {
        final int[] ks = innerKeys;
        int base = node * INNER;
        int seps = innerSize[node] - 1; // at least one
        int c = ((8 < seps) & (ks[base + 8] <= key) ? 1 : 0)
                + ((16 < seps) & (ks[base + 16] <= key) ? 1 : 0)
                + ((24 < seps) & (ks[base + 24] <= key) ? 1 : 0)
                + ((32 < seps) & (ks[base + 32] <= key) ? 1 : 0)
                + ((40 < seps) & (ks[base + 40] <= key) ? 1 : 0)
                + ((48 < seps) & (ks[base + 48] <= key) ? 1 : 0)
                + ((56 < seps) & (ks[base + 56] <= key) ? 1 : 0);
        int i = base + (c << 3);
        for (int n = Math.min(8, seps - (c << 3)); n > 1; ) {
            int half = n >>> 1;
            i = ks[i + half] <= key ? i + half : i;
            n -= half;
        }
        return i - base + (ks[i] <= key ? 1 : 0);
    }

    /**
     * Array index of {@code key} in {@code leaf}, or {@code ~insertionIndex} if it is absent.
     * The insertion index can be the end of the leaf.
     */
    private int leafSearch(int leaf, int key) {
        final int[] ks = leafKeys;
        int lo = leaf << LEAF_SHIFT;
        int n = leafSize[leaf];
        if (n == 0) {
            return ~lo;
        }
        int c = ((8 < n) & (ks[lo + 8] < key) ? 1 : 0)
                + ((16 < n) & (ks[lo + 16] < key) ? 1 : 0)
                + ((24 < n) & (ks[lo + 24] < key) ? 1 : 0)
                + ((32 < n) & (ks[lo + 32] < key) ? 1 : 0)
                + ((40 < n) & (ks[lo + 40] < key) ? 1 : 0)
                + ((48 < n) & (ks[lo + 48] < key) ? 1 : 0)
                + ((56 < n) & (ks[lo + 56] < key) ? 1 : 0);
        int i = lo + (c << 3);
        for (int m = Math.min(8, n - (c << 3)); m > 1; ) {
            int half = m >>> 1;
            i = ks[i + half] < key ? i + half : i;
            m -= half;
        }
        i += ks[i] < key ? 1 : 0;
        return i < lo + n && ks[i] == key ? i : ~i;
    }

    // ------------------------------------------------------------------
    // Insertion
    // ------------------------------------------------------------------

    /**
     * Appends the entry to the last leaf without a search if {@code key} is greater than every
     * key in the map and the last leaf has room (ascending inserts, sliding windows). Returns
     * {@code false} otherwise. Structure and recorded path stay valid: no node is split.
     * Measured: ascending builds 3-4x faster, sliding windows 15-20% faster; random inserts
     * pay one comparison with a cached key.
     */
    private boolean tryAppend(int key, boolean value) {
        int leaf = lastLeaf;
        int n = leafSize[leaf];
        int i = (leaf << LEAF_SHIFT) + n;
        if (n == 0 || n == LEAF || leafKeys[i - 1] >= key) {
            return false;
        }
        leafKeys[i] = key;
        leafValues[i] = value;
        leafSize[leaf] = n + 1;
        size++;
        return true;
    }

    /** Inserts at position {@code pos} of {@code leaf}; the path must lead to {@code leaf}. */
    private void insertAt(int leaf, int pos, int key, boolean value) {
        assert pathLeaf == leaf;
        int n = leafSize[leaf];
        if (n < LEAF) {
            int i = (leaf << LEAF_SHIFT) + pos;
            System.arraycopy(leafKeys, i, leafKeys, i + 1, n - pos);
            System.arraycopy(leafValues, i, leafValues, i + 1, n - pos);
            leafKeys[i] = key;
            leafValues[i] = value;
            leafSize[leaf] = n + 1;
        } else {
            splitLeaf(leaf, pos, key, value);
            pathLeaf = -1;
        }
        size++;
    }

    /** Splits the full {@code leaf} and inserts the entry at position {@code pos}. */
    private void splitLeaf(int leaf, int pos, int key, boolean value) {
        boolean append = pos == LEAF && leafNext[leaf] < 0;
        int right = allocLeaf();
        linkAfter(leaf, right);
        final int[] ks = leafKeys;
        final boolean[] vs = leafValues;
        int lb = leaf << LEAF_SHIFT;
        int rb = right << LEAF_SHIFT;
        if (append) {
            ks[rb] = key;
            vs[rb] = value;
            leafSize[right] = 1;
        } else {
            int mid = LEAF >>> 1;
            System.arraycopy(ks, lb + mid, ks, rb, LEAF - mid);
            System.arraycopy(vs, lb + mid, vs, rb, LEAF - mid);
            leafSize[leaf] = mid;
            leafSize[right] = LEAF - mid;
            int target = pos <= mid ? leaf : right;
            int p = pos <= mid ? pos : pos - mid;
            int n = leafSize[target];
            int i = (target << LEAF_SHIFT) + p;
            System.arraycopy(ks, i, ks, i + 1, n - p);
            System.arraycopy(vs, i, vs, i + 1, n - p);
            ks[i] = key;
            vs[i] = value;
            leafSize[target] = n + 1;
        }
        insertIntoParent(1, leaf, ks[rb], right, append);
    }

    /**
     * Inserts separator {@code sep} and the new node {@code right} behind {@code left} into the
     * parent at {@code level}, splitting it if it is full.
     */
    private void insertIntoParent(int level, int left, int sep, int right, boolean append) {
        if (level > height) {
            int r = allocInner();
            int base = r * INNER;
            innerChildren[base] = left;
            innerChildren[base + 1] = right;
            innerKeys[base] = sep;
            innerSize[r] = 2;
            root = r;
            height = level;
            if (height + 1 >= pathNode.length) {
                pathNode = Arrays.copyOf(pathNode, pathNode.length * 2);
                pathSlot = Arrays.copyOf(pathSlot, pathSlot.length * 2);
            }
            return;
        }
        int node = pathNode[level];
        int slot = pathSlot[level];
        int n = innerSize[node];
        if (n < INNER) {
            int base = node * INNER;
            System.arraycopy(innerChildren, base + slot + 1, innerChildren, base + slot + 2, n - slot - 1);
            System.arraycopy(innerKeys, base + slot, innerKeys, base + slot + 1, n - 1 - slot);
            innerChildren[base + slot + 1] = right;
            innerKeys[base + slot] = sep;
            innerSize[node] = n + 1;
            return;
        }
        // Full: combine the INNER + 1 children and INNER separators in scratch space, then split.
        ensureScratch();
        final int[] sk = scratchKeys;
        final int[] sc = scratchChildren;
        int base = node * INNER;
        System.arraycopy(innerChildren, base, sc, 0, slot + 1);
        sc[slot + 1] = right;
        System.arraycopy(innerChildren, base + slot + 1, sc, slot + 2, n - slot - 1);
        System.arraycopy(innerKeys, base, sk, 0, slot);
        sk[slot] = sep;
        System.arraycopy(innerKeys, base + slot, sk, slot + 1, n - 1 - slot);
        int total = n + 1;
        // Appending at the right edge keeps the left node nearly full; the new node gets two
        // children, so that no inner node below the root has a single child.
        int leftCount = append ? total - 2 : total / 2;
        int q = allocInner();
        int qb = q * INNER;
        System.arraycopy(sc, 0, innerChildren, base, leftCount);
        System.arraycopy(sk, 0, innerKeys, base, leftCount - 1);
        System.arraycopy(sc, leftCount, innerChildren, qb, total - leftCount);
        System.arraycopy(sk, leftCount, innerKeys, qb, total - leftCount - 1);
        innerSize[node] = leftCount;
        innerSize[q] = total - leftCount;
        insertIntoParent(level + 1, node, sk[leftCount - 1], q, append);
    }

    // ------------------------------------------------------------------
    // Removal and rebalancing
    // ------------------------------------------------------------------

    /** Removes position {@code pos} of {@code leaf}; the path must lead to {@code leaf}. */
    private boolean removeAt(int leaf, int pos) {
        boolean old = leafValues[(leaf << LEAF_SHIFT) + pos];
        removeEntries(leaf, pos, 1);
        return old;
    }

    /** Removes {@code count} entries from position {@code pos} of {@code leaf} and rebalances. */
    private void removeEntries(int leaf, int pos, int count) {
        assert pathLeaf == leaf;
        int n = leafSize[leaf];
        int base = leaf << LEAF_SHIFT;
        int i = base + pos;
        System.arraycopy(leafKeys, i + count, leafKeys, i, n - pos - count);
        System.arraycopy(leafValues, i + count, leafValues, i, n - pos - count);
        leafSize[leaf] = n - count;
        size -= count;
        if (n - count < MIN_LEAF && height > 0) {
            rebalance(1);
        }
        pathLeaf = -1;
    }

    /** Removes the whole {@code leaf} (not the root); the path must lead to it. */
    private void removeLeaf(int leaf) {
        assert pathLeaf == leaf && height > 0;
        size -= leafSize[leaf];
        removeChild(pathNode[1], pathSlot[1]);
        freeLeaf(leaf);
        if (innerSize[pathNode[1]] < MIN_INNER) {
            rebalance(2);
        }
        collapseRoot();
        pathLeaf = -1;
    }

    /**
     * Fixes an underfull child along the recorded path, starting with the child of the node at
     * {@code level}, and continues upwards while nodes become underfull.
     */
    private void rebalance(int level) {
        for (; level <= height; level++) {
            int node = pathNode[level];
            fixChild(node, level, pathSlot[level]);
            if (level == height || innerSize[node] >= MIN_INNER) {
                break;
            }
        }
        collapseRoot();
    }

    /** Removes inner levels above a root with a single child. */
    private void collapseRoot() {
        while (height > 0 && innerSize[root] == 1) {
            int r = root;
            root = innerChildren[r * INNER];
            freeInner(r);
            height--;
        }
    }

    /**
     * Merges or refills child {@code slot} of {@code node} (at {@code level}, so the children
     * are at {@code level - 1}) while it is underfull and has a neighbor.
     */
    private void fixChild(int node, int level, int slot) {
        boolean leaves = level == 1;
        int min = leaves ? MIN_LEAF : MIN_INNER;
        int mergeMax = leaves ? MERGE_LEAF : MERGE_INNER;
        while (innerSize[node] > 1) {
            int base = node * INNER;
            int child = innerChildren[base + slot];
            if ((leaves ? leafSize[child] : innerSize[child]) >= min) {
                return;
            }
            int l = slot > 0 ? slot - 1 : slot; // left of the pair to merge or balance
            int left = innerChildren[base + l];
            int right = innerChildren[base + l + 1];
            int total = leaves ? leafSize[left] + leafSize[right] : innerSize[left] + innerSize[right];
            if (total <= mergeMax) {
                if (leaves) {
                    mergeLeaves(left, right);
                } else {
                    mergeInner(left, innerKeys[base + l], right);
                }
                removeChild(node, l + 1);
                slot = l;
            } else {
                if (leaves) {
                    balanceLeaves(left, right);
                    innerKeys[base + l] = leafKeys[right << LEAF_SHIFT];
                } else {
                    innerKeys[base + l] = balanceInner(left, innerKeys[base + l], right);
                }
                return;
            }
        }
    }

    /** Removes child {@code slot} and the separator in front of it (behind it for slot 0). */
    private void removeChild(int node, int slot) {
        int base = node * INNER;
        int n = innerSize[node];
        System.arraycopy(innerChildren, base + slot + 1, innerChildren, base + slot, n - slot - 1);
        int sep = slot > 0 ? slot - 1 : 0;
        System.arraycopy(innerKeys, base + sep + 1, innerKeys, base + sep, n - 2 - sep);
        innerSize[node] = n - 1;
    }

    /** Moves all entries of {@code right} to the end of {@code left} and frees {@code right}. */
    private void mergeLeaves(int left, int right) {
        int nl = leafSize[left];
        int nr = leafSize[right];
        int lb = left << LEAF_SHIFT;
        int rb = right << LEAF_SHIFT;
        System.arraycopy(leafKeys, rb, leafKeys, lb + nl, nr);
        System.arraycopy(leafValues, rb, leafValues, lb + nl, nr);
        leafSize[left] = nl + nr;
        freeLeaf(right);
    }

    /** Moves entries between two neighboring leaves so that both hold about the same number. */
    private void balanceLeaves(int left, int right) {
        final int[] ks = leafKeys;
        final boolean[] vs = leafValues;
        int nl = leafSize[left];
        int nr = leafSize[right];
        int lb = left << LEAF_SHIFT;
        int rb = right << LEAF_SHIFT;
        int newLeft = (nl + nr) / 2;
        if (nl < newLeft) {
            int m = newLeft - nl; // move the first m entries of right to the end of left
            System.arraycopy(ks, rb, ks, lb + nl, m);
            System.arraycopy(vs, rb, vs, lb + nl, m);
            System.arraycopy(ks, rb + m, ks, rb, nr - m);
            System.arraycopy(vs, rb + m, vs, rb, nr - m);
        } else {
            int m = nl - newLeft; // move the last m entries of left to the front of right
            System.arraycopy(ks, rb, ks, rb + m, nr);
            System.arraycopy(vs, rb, vs, rb + m, nr);
            System.arraycopy(ks, lb + newLeft, ks, rb, m);
            System.arraycopy(vs, lb + newLeft, vs, rb, m);
        }
        leafSize[left] = newLeft;
        leafSize[right] = nl + nr - newLeft;
    }

    /** Appends {@code sep} and the children of {@code right} to {@code left} and frees {@code right}. */
    private void mergeInner(int left, int sep, int right) {
        int nl = innerSize[left];
        int nr = innerSize[right];
        int lb = left * INNER;
        int rb = right * INNER;
        System.arraycopy(innerChildren, rb, innerChildren, lb + nl, nr);
        innerKeys[lb + nl - 1] = sep;
        System.arraycopy(innerKeys, rb, innerKeys, lb + nl, nr - 1);
        innerSize[left] = nl + nr;
        freeInner(right);
    }

    /**
     * Moves children between two neighboring inner nodes, separated by {@code sep} in their
     * parent, so that both have about the same number. Returns the new separator.
     */
    private int balanceInner(int left, int sep, int right) {
        ensureScratch();
        final int[] sk = scratchKeys;
        final int[] sc = scratchChildren;
        int nl = innerSize[left];
        int nr = innerSize[right];
        int lb = left * INNER;
        int rb = right * INNER;
        System.arraycopy(innerChildren, lb, sc, 0, nl);
        System.arraycopy(innerChildren, rb, sc, nl, nr);
        System.arraycopy(innerKeys, lb, sk, 0, nl - 1);
        sk[nl - 1] = sep;
        System.arraycopy(innerKeys, rb, sk, nl, nr - 1);
        int total = nl + nr;
        int newLeft = total / 2;
        System.arraycopy(sc, 0, innerChildren, lb, newLeft);
        System.arraycopy(sk, 0, innerKeys, lb, newLeft - 1);
        System.arraycopy(sc, newLeft, innerChildren, rb, total - newLeft);
        System.arraycopy(sk, newLeft, innerKeys, rb, total - newLeft - 1);
        innerSize[left] = newLeft;
        innerSize[right] = total - newLeft;
        return sk[newLeft - 1];
    }

    private void ensureScratch() {
        if (scratchChildren == null) {
            scratchKeys = new int[2 * INNER];
            scratchChildren = new int[2 * INNER];
        }
    }

    // ------------------------------------------------------------------
    // Bulk building
    // ------------------------------------------------------------------

    /**
     * Appends an entry behind all others, filling leaves completely. Only for building a new
     * tree, which {@link #buildInnerLevels} completes.
     */
    private void append(int key, boolean value) {
        int leaf = lastLeaf;
        int n = leafSize[leaf];
        if (n == LEAF) {
            int next = allocLeaf();
            linkAfter(leaf, next);
            leaf = next;
            n = 0;
        }
        int i = (leaf << LEAF_SHIFT) + n;
        leafKeys[i] = key;
        leafValues[i] = value;
        leafSize[leaf] = n + 1;
        size++;
    }

    /** Discards all inner nodes and builds the inner levels over the linked leaves. */
    private void buildInnerLevels() {
        innerCount = 0;
        freeInner = -1;
        pathLeaf = -1;
        int count = 0;
        for (int leaf = firstLeaf; leaf >= 0; leaf = leafNext[leaf]) {
            count++;
        }
        int[] nodes = new int[count];
        int[] mins = new int[count];
        count = 0;
        for (int leaf = firstLeaf; leaf >= 0; leaf = leafNext[leaf]) {
            nodes[count] = leaf;
            mins[count++] = leafKeys[leaf << LEAF_SHIFT];
        }
        int levels = 0;
        while (count > 1) {
            // Spread the children evenly, so that no node is much smaller than the others.
            int groups = (count + INNER - 1) / INNER;
            int k = 0;
            for (int g = 0; g < groups; g++) {
                int take = count / groups + (g < count % groups ? 1 : 0);
                int node = allocInner();
                int base = node * INNER;
                for (int t = 0; t < take; t++) {
                    innerChildren[base + t] = nodes[k + t];
                    if (t > 0) {
                        innerKeys[base + t - 1] = mins[k + t];
                    }
                }
                innerSize[node] = take;
                nodes[g] = node;
                mins[g] = mins[k];
                k += take;
            }
            count = groups;
            levels++;
        }
        root = nodes[0];
        height = levels;
        if (height + 1 >= pathNode.length) {
            pathNode = new int[height + 8];
            pathSlot = new int[height + 8];
        }
    }

    // ------------------------------------------------------------------
    // Node pools
    // ------------------------------------------------------------------

    /** Resets the tree to a single empty root leaf. */
    private void initEmpty() {
        leafCount = 0;
        freeLeaf = -1;
        innerCount = 0;
        freeInner = -1;
        int leaf = allocLeaf();
        leafNext[leaf] = -1;
        leafPrev[leaf] = -1;
        root = leaf;
        firstLeaf = leaf;
        lastLeaf = leaf;
        height = 0;
        size = 0;
        pathLeaf = -1;
    }

    /** Links the new {@code leaf} behind {@code prev}. */
    private void linkAfter(int prev, int leaf) {
        int next = leafNext[prev];
        leafNext[leaf] = next;
        leafPrev[leaf] = prev;
        leafNext[prev] = leaf;
        if (next >= 0) {
            leafPrev[next] = leaf;
        } else {
            lastLeaf = leaf;
        }
    }

    private int allocLeaf() {
        int leaf = freeLeaf;
        if (leaf >= 0) {
            freeLeaf = leafNext[leaf];
        } else {
            if (leafCount == leafSize.length) {
                if (leafCount == MAX_LEAVES) {
                    throw new IllegalStateException("map is full");
                }
                growLeaves((int) Math.min(MAX_LEAVES, Math.max(4L, leafCount * 3L / 2)));
            }
            leaf = leafCount++;
        }
        leafSize[leaf] = 0;
        return leaf;
    }

    /** Unlinks {@code leaf} and puts it on the free list. */
    private void freeLeaf(int leaf) {
        int prev = leafPrev[leaf];
        int next = leafNext[leaf];
        if (prev >= 0) {
            leafNext[prev] = next;
        } else {
            firstLeaf = next;
        }
        if (next >= 0) {
            leafPrev[next] = prev;
        } else {
            lastLeaf = prev;
        }
        pushFreeLeaf(leaf);
    }

    private void pushFreeLeaf(int leaf) {
        leafSize[leaf] = 0;
        leafPrev[leaf] = -1;
        leafNext[leaf] = freeLeaf;
        freeLeaf = leaf;
    }

    private void growLeaves(int capacity) {
        if (capacity > leafSize.length) {
            leafKeys = Arrays.copyOf(leafKeys, capacity << LEAF_SHIFT);
            leafValues = Arrays.copyOf(leafValues, capacity << LEAF_SHIFT);
            leafSize = Arrays.copyOf(leafSize, capacity);
            leafNext = Arrays.copyOf(leafNext, capacity);
            leafPrev = Arrays.copyOf(leafPrev, capacity);
        }
    }

    private int allocInner() {
        int node = freeInner;
        if (node >= 0) {
            freeInner = innerChildren[node * INNER];
        } else {
            if (innerCount == innerSize.length) {
                growInner((int) Math.min(Integer.MAX_VALUE / INNER, Math.max(4L, innerCount * 3L / 2)));
            }
            node = innerCount++;
        }
        innerSize[node] = 0;
        return node;
    }

    private void freeInner(int node) {
        innerSize[node] = 0;
        innerChildren[node * INNER] = freeInner;
        freeInner = node;
    }

    private void growInner(int capacity) {
        if (capacity > innerSize.length) {
            innerKeys = Arrays.copyOf(innerKeys, capacity * INNER);
            innerChildren = Arrays.copyOf(innerChildren, capacity * INNER);
            innerSize = Arrays.copyOf(innerSize, capacity);
        }
    }
}
