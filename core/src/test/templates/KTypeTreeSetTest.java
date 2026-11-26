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
//#types K=int|long
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Differential tests against {@link TreeSet} and checks of the structural invariants. */
class KTypeTreeSetTest {

    private static final int LEAF = KTypeTreeSet.LEAF;
    private static final int INNER = KTypeTreeSet.INNER;

    /**
     * Maps an {@code int} to an element: an order-preserving bijection with {@code k(0) == 0}.
//#if K == long
     * The upper 32 bits are used, so that comparisons must take them into account.
//#end
     */
    private static KType k(int i) {
//#if K == long
        return ((long) i << 32) | (i & 0xFFFF_FFFFL);
//#else
        return i;
//#end
    }

    private static final KType MIN = KBoxed.MIN_VALUE;
    private static final KType MAX = KBoxed.MAX_VALUE;

    @Test
    void basicOperations() {
        KTypeTreeSet s = new KTypeTreeSet();
        assertTrue(s.isEmpty());
        assertTrue(s.add(k(1)));
        assertFalse(s.add(k(1)));
        assertTrue(s.add(k(-2)));
        assertTrue(s.add(k(3)));
        assertEquals(3, s.size());
        assertTrue(s.contains(k(1)));
        assertFalse(s.contains(k(2)));
        assertEquals("[" + k(-2) + ", " + k(1) + ", " + k(3) + "]", s.toString());
        assertArrayEquals(new KType[] {k(-2), k(1), k(3)}, s.toArray());
        assertTrue(s.remove(k(1)));
        assertFalse(s.remove(k(1)));
        assertEquals(2, s.size());
        s.clear();
        assertTrue(s.isEmpty());
        assertFalse(s.contains(k(-2)));
        assertEquals("[]", s.toString());
        assertEquals(0, s.toArray().length);
        checkInvariants(s);
    }

    @Test
    void extremeElements() {
        KTypeTreeSet s = new KTypeTreeSet();
        s.add(MAX);
        s.add(KZero);
        s.add(MIN);
        assertEquals(MIN, s.first());
        assertEquals(MAX, s.last());
        assertTrue(s.contains(KZero));
        assertEquals(MAX, s.floor(MAX, k(9)));
        assertEquals(MIN, s.ceiling(MIN, k(9)));
        assertEquals(-1, s.lowerIndex(MIN));
        assertEquals(-1, s.higherIndex(MAX));
        assertEquals(k(9), s.lower(MIN, k(9)));
        assertEquals(k(9), s.higher(MAX, k(9)));
        assertEquals(3, s.countRange(MIN, true, MAX, true));
        assertEquals(2, s.countRange(MIN, MAX));
        assertEquals(1, s.countRange(MIN, false, MAX, false));
        Set<KBoxed> ref = new HashSet<>(List.of(MAX, KZero, MIN));
        assertEquals(ref.hashCode(), s.hashCode());
        assertEquals(new TreeSet<>(ref), s.toNavigableSet());
        assertTrue(s.remove(KZero));
        assertEquals(2, s.size());
    }

    @Test
    void emptySet() {
        KTypeTreeSet s = new KTypeTreeSet();
        assertEquals(-1, s.firstIndex());
        assertEquals(-1, s.lastIndex());
        assertEquals(-1, s.floorIndex(KZero));
        assertEquals(-1, s.ceilingIndex(KZero));
        assertEquals(-1, s.lowerIndex(KZero));
        assertEquals(-1, s.higherIndex(KZero));
        assertThrows(NoSuchElementException.class, s::first);
        assertThrows(NoSuchElementException.class, s::last);
        assertThrows(NoSuchElementException.class, s::pollFirst);
        assertThrows(NoSuchElementException.class, s::pollLast);
        assertEquals(k(7), s.floor(KZero, k(7)));
        assertEquals(k(7), s.ceiling(KZero, k(7)));
        assertEquals(k(7), s.lower(KZero, k(7)));
        assertEquals(k(7), s.higher(KZero, k(7)));
        assertEquals(0, s.countRange(MIN, true, MAX, true));
        assertEquals(0, s.removeRange(MIN, true, MAX, true));
        assertTrue(s.subSetCopy(MIN, MAX).isEmpty());
        assertTrue(s.toNavigableSet().isEmpty());
        assertFalse(s.removeIf(key -> true));
        s.forEach(key -> {
            throw new AssertionError();
        });
        checkInvariants(s);
        // Polling the last element leaves a usable empty set.
        s.add(k(4));
        assertEquals(k(4), s.pollFirst());
        assertThrows(NoSuchElementException.class, s::pollLast);
        assertTrue(s.add(k(5)));
        checkInvariants(s);
    }

    @Test
    void invalidArguments() {
        KTypeTreeSet s = new KTypeTreeSet();
        assertThrows(IllegalArgumentException.class, () -> new KTypeTreeSet(-1));
        assertThrows(IllegalArgumentException.class, () -> s.ensureCapacity(-1));
        assertThrows(IllegalArgumentException.class, () -> s.countRange(k(2), k(1)));
        assertThrows(IllegalArgumentException.class, () -> s.removeRange(k(2), k(1)));
        assertThrows(IllegalArgumentException.class, () -> s.subSetCopy(k(2), k(1)));
        assertThrows(IllegalArgumentException.class, () -> s.forEachInRange(k(2), k(1), key -> { }));
        assertThrows(NullPointerException.class, () -> s.forEachInRange(k(1), k(2), null));
        assertThrows(NullPointerException.class, () -> s.removeIf(null));
        // Empty ranges with from == to are valid.
        assertEquals(0, s.countRange(k(1), k(1)));
    }

    @Test
    void ascendingInsertsFillLeaves() throws ReflectiveOperationException {
        int n = 100 * LEAF * INNER / 8 + 17;
        KTypeTreeSet s = new KTypeTreeSet();
        for (int i = 0; i < n; i++) {
            assertTrue(s.add(k(i)));
        }
        checkInvariants(s);
        assertEquals((n + LEAF - 1) / LEAF, new Tree(s).reachableLeaves, "ascending inserts fill leaves completely");
        KTypeTreeSet d = new KTypeTreeSet();
        for (int i = n - 1; i >= 0; i--) {
            d.add(k(i));
        }
        checkInvariants(d);
        assertEquals(s, d);
        int i = 0;
        for (int h = d.firstIndex(); h >= 0; h = d.nextIndex(h), i++) {
            assertEquals(k(i), d.indexKey(h));
        }
        assertEquals(n, i);
        for (int h = d.lastIndex(); h >= 0; h = d.prevIndex(h)) {
            assertEquals(k(--i), d.indexKey(h));
        }
        assertEquals(0, i);
    }

    @Test
    void pollRemovesInOrder() {
        KTypeTreeSet s = new KTypeTreeSet();
        TreeSet<KBoxed> ref = new TreeSet<>();
        SplittableRandom rnd = new SplittableRandom(3);
        while (ref.size() < 5000) {
            int i = rnd.nextInt(1_000_000) - 500_000;
            s.add(k(i));
            ref.add(k(i));
        }
        while (!ref.isEmpty()) {
            if (ref.size() % 2 == 0) {
                assertEquals((KType) ref.pollFirst(), s.pollFirst());
            } else {
                assertEquals((KType) ref.pollLast(), s.pollLast());
            }
            assertEquals(ref.size(), s.size());
            if (ref.size() % 500 == 0) {
                checkInvariants(s);
            }
        }
        checkInvariants(s);
    }

    // ------------------------------------------------------------------
    // Differential test against java.util.TreeSet
    // ------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({
        "1, 1000, 200000",
        "2, 30000, 300000",
        "3, 2000000, 400000",
        "4, 300, 100000",
    })
    void randomOperationsMatchTreeSet(long seed, int keyRange, int ops) {
        SplittableRandom rnd = new SplittableRandom(seed);
        KTypeTreeSet s = new KTypeTreeSet();
        TreeSet<KBoxed> ref = new TreeSet<>();
        for (int op = 0; op < ops; op++) {
            int i = rnd.nextInt(keyRange) - keyRange / 2;
            KType key = k(i);
            switch (rnd.nextInt(16)) {
                case 0, 1, 2, 3, 4, 5 -> assertEquals(ref.add(key), s.add(key));
                case 6, 7, 8 -> assertEquals(ref.remove(key), s.remove(key));
                case 9, 10 -> checkNavigation(s, ref, key);
                case 11 -> {
                    if (rnd.nextInt(8) == 0 && !ref.isEmpty()) {
                        if (rnd.nextBoolean()) {
                            assertEquals((KType) ref.pollFirst(), s.pollFirst());
                        } else {
                            assertEquals((KType) ref.pollLast(), s.pollLast());
                        }
                    }
                }
                case 12, 13 -> checkRange(s, ref, rnd, keyRange, false);
                case 14 -> {
                    if (rnd.nextInt(16) == 0) {
                        checkRange(s, ref, rnd, keyRange, true);
                    }
                }
                default -> assertEquals(ref.contains(key), s.contains(key));
            }
            assertEquals(ref.size(), s.size());
            if ((op & 0x3FFF) == 0) {
                checkInvariants(s);
                assertContentEquals(ref, s);
            }
        }
        checkInvariants(s);
        assertContentEquals(ref, s);
        for (KBoxed key : ref.toArray(new KBoxed[0])) {
            assertTrue(s.remove(key));
        }
        assertTrue(s.isEmpty());
        checkInvariants(s);
    }

    @Test
    void largeTreeWithRangeRemovals() throws ReflectiveOperationException {
        SplittableRandom rnd = new SplittableRandom(11);
        KTypeTreeSet s = new KTypeTreeSet();
        TreeSet<KBoxed> ref = new TreeSet<>();
        int range = 50_000_000;
        // Enough elements for three inner levels with randomly filled nodes.
        int n = LEAF * INNER * INNER;
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            s.add(k(i));
            ref.add(k(i));
        }
        checkInvariants(s);
        assertTrue(new Tree(s).height >= 3, "height");
        for (int r = 0; r < 120; r++) {
            checkRange(s, ref, rnd, range, true);
            for (int j = 0; j < 2000; j++) {
                int i = rnd.nextInt(range) - range / 2;
                s.add(k(i));
                ref.add(k(i));
            }
            if (r % 20 == 0) {
                checkInvariants(s);
            }
        }
        checkInvariants(s);
        assertContentEquals(ref, s);
        // Remove everything in a few large ranges, then refill.
        assertEquals(ref.headSet(k(0)).size(), s.removeRange(MIN, k(0)));
        ref.headSet(k(0)).clear();
        checkInvariants(s);
        assertEquals(ref.size(), s.removeRange(MIN, true, MAX, true));
        ref.clear();
        assertTrue(s.isEmpty());
        checkInvariants(s);
        for (int i = 0; i < 3 * LEAF * INNER; i++) {
            int j = rnd.nextInt(range);
            s.add(k(j));
            ref.add(k(j));
        }
        checkInvariants(s);
        assertContentEquals(ref, s);
    }

    private static void checkNavigation(KTypeTreeSet s, TreeSet<KBoxed> ref, KType key) {
        checkHandle(s, ref.floor(key), s.floorIndex(key));
        checkHandle(s, ref.ceiling(key), s.ceilingIndex(key));
        checkHandle(s, ref.lower(key), s.lowerIndex(key));
        checkHandle(s, ref.higher(key), s.higherIndex(key));
        KBoxed f = ref.floor(key);
        assertEquals(f == null ? k(123) : (KType) f, s.floor(key, k(123)));
        KBoxed c = ref.ceiling(key);
        assertEquals(c == null ? k(123) : (KType) c, s.ceiling(key, k(123)));
        KBoxed lo = ref.lower(key);
        assertEquals(lo == null ? k(123) : (KType) lo, s.lower(key, k(123)));
        KBoxed hi = ref.higher(key);
        assertEquals(hi == null ? k(123) : (KType) hi, s.higher(key, k(123)));
        int h = s.floorIndex(key);
        if (h >= 0) {
            checkHandle(s, ref.higher(s.indexKey(h)), s.nextIndex(h));
            checkHandle(s, ref.lower(s.indexKey(h)), s.prevIndex(h));
        }
        if (!ref.isEmpty()) {
            assertEquals((KType) ref.first(), s.first());
            assertEquals((KType) ref.last(), s.last());
            checkHandle(s, ref.first(), s.firstIndex());
            checkHandle(s, ref.last(), s.lastIndex());
        }
    }

    private static void checkHandle(KTypeTreeSet s, KBoxed expected, int h) {
        if (expected == null) {
            assertEquals(-1, h);
        } else {
            assertTrue(h >= 0, "handle expected for " + expected);
            assertEquals((KType) expected, s.indexKey(h));
        }
    }

    /** Range operations on a random range; with {@code remove}, also {@code removeRange} and {@code subSetCopy}. */
    private static void checkRange(KTypeTreeSet s, TreeSet<KBoxed> ref, SplittableRandom rnd, int keyRange,
                                   boolean remove) {
        int a = rnd.nextInt(keyRange) - keyRange / 2;
        int width = rnd.nextInt(8) == 0 ? rnd.nextInt(keyRange / 2 + 1) : rnd.nextInt(keyRange / 50 + 2);
        int b = (int) Math.min((long) a + width, Integer.MAX_VALUE);
        KType from = k(a);
        KType to = k(b);
        boolean fi = rnd.nextBoolean();
        boolean ti = rnd.nextBoolean();
        NavigableSet<KBoxed> sub = ref.subSet(from, fi, to, ti);
        assertEquals(sub.size(), s.countRange(from, fi, to, ti));
        if (fi && !ti) {
            assertEquals(sub.size(), s.countRange(from, to));
        }
        List<KBoxed> seen = new ArrayList<>();
        s.forEachInRange(from, fi, to, ti, seen::add);
        assertEquals(new ArrayList<>(sub), seen);
        if (remove) {
            KTypeSortedSet copy = s.subSetCopy(from, fi, to, ti);
            checkInvariants((KTypeTreeSet) copy);
            assertEquals(sub, copy.toNavigableSet());
            int n = sub.size();
            assertEquals(n, s.removeRange(from, fi, to, ti));
            sub.clear();
            assertEquals(ref.size(), s.size());
        }
    }

    // ------------------------------------------------------------------
    // removeIf, copies, conversions
    // ------------------------------------------------------------------

    private static KTypeTreeSet fill(int n, int range, TreeSet<KBoxed> ref, long seed) {
        KTypeTreeSet s = new KTypeTreeSet();
        SplittableRandom rnd = new SplittableRandom(seed);
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            s.add(k(i));
            ref.add(k(i));
        }
        return s;
    }

    @ParameterizedTest
    @CsvSource({"12", "100", "5000", "60000"})
    void removeIfMatchesTreeSet(int n) {
        for (long seed = 1; seed <= 6; seed++) {
            TreeSet<KBoxed> ref = new TreeSet<>();
            KTypeTreeSet s = fill(n, 4 * n + 10, ref, seed);
            int modulus = 1 + (int) (seed % 4); // 1 removes everything
            List<KBoxed> order = new ArrayList<>();
            boolean removed = s.removeIf(key -> {
                order.add(key);
                return Math.floorMod(KBoxed.hashCode(key), modulus) == 0;
            });
            assertEquals(new ArrayList<>(ref), order, "every element is tested once, in ascending order");
            assertEquals(ref.removeIf(key -> Math.floorMod(KBoxed.hashCode(key), modulus) == 0), removed);
            checkInvariants(s);
            assertContentEquals(ref, s);
            // The tree stays usable.
            for (int i = 0; i < 1000; i++) {
                s.add(k(i * 7));
                ref.add(k(i * 7));
            }
            checkInvariants(s);
            assertContentEquals(ref, s);
        }
    }

    @Test
    void removeIfKeepsAConsistentSetWhenThePredicateThrows() {
        TreeSet<KBoxed> ref = new TreeSet<>();
        KTypeTreeSet s = fill(5000, 100_000, ref, 9);
        int[] calls = {0};
        assertThrows(IllegalStateException.class, () -> s.removeIf(key -> {
            if (++calls[0] == 3000) {
                throw new IllegalStateException();
            }
            return calls[0] % 3 == 0;
        }));
        int[] n = {0};
        ref.removeIf(key -> ++n[0] < 3000 && n[0] % 3 == 0);
        checkInvariants(s);
        assertContentEquals(ref, s);
    }

    @Test
    void copiesAndConversions() throws ReflectiveOperationException {
        TreeSet<KBoxed> ref = new TreeSet<>();
        KTypeTreeSet source = fill(20_000, 1_000_000, ref, 5);
        KTypeTreeSet copy = new KTypeTreeSet(source);
        checkInvariants(copy);
        assertEquals(source, copy);
        assertEquals(source.hashCode(), copy.hashCode());
        assertEquals((20_000 + LEAF - 1) / LEAF, new Tree(copy).reachableLeaves, "a copy has full leaves");
        copy.add(k(9_999_999));
        assertFalse(source.contains(k(9_999_999)), "copies are independent");
        assertNotEquals(source, copy);

        KTypeHashSet hash = new KTypeHashSet(source);
        assertEquals(hash, source);
        assertEquals(source, hash);
        KTypeTreeSet fromHash = new KTypeTreeSet(hash);
        checkInvariants(fromHash);
        assertEquals(source, fromHash);
        assertEquals(source.toString(), fromHash.toString());
        assertEquals((20_000 + LEAF - 1) / LEAF, new Tree(fromHash).reachableLeaves, "a copy has full leaves");
        KTypeTreeSet fromEmpty = new KTypeTreeSet(new KTypeHashSet());
        assertTrue(fromEmpty.isEmpty());
        checkInvariants(fromEmpty);

        assertEquals(ref, source.toNavigableSet());
        assertEquals(new HashSet<>(ref), source.toSet());
        assertEquals(new ArrayList<>(source.toNavigableSet()), new ArrayList<>(source.toSet()), "toSet keeps the order");
        assertEquals(ref.toString(), source.toString());
        KType[] elements = source.toArray();
        int i = 0;
        for (KBoxed e : ref) {
            assertEquals((KType) e, elements[i++]);
        }

        KTypeTreeSet all = new KTypeTreeSet();
        assertTrue(all.addAll(source));
        assertFalse(all.addAll(ref));
        assertEquals(source, all);
        checkInvariants(all);

        KTypeTreeSet sub = source.subSetCopy(k(-100_000), k(100_000));
        checkInvariants(sub);
        assertEquals(ref.subSet(k(-100_000), k(100_000)), sub.toNavigableSet());
        KTypeTreeSet whole = source.subSetCopy(MIN, true, MAX, true);
        assertEquals(source, whole);
        assertSame(KTypeTreeSet.class, whole.getClass());
    }

    @Test
    void clearAndEnsureCapacityKeepContentRules() {
        TreeSet<KBoxed> ref = new TreeSet<>();
        KTypeTreeSet s = fill(10_000, 100_000, ref, 2);
        s.ensureCapacity(1_000_000);
        checkInvariants(s);
        assertContentEquals(ref, s);
        s.clear();
        assertTrue(s.isEmpty());
        checkInvariants(s);
        ref.clear();
        for (int i = 0; i < 5000; i++) {
            s.add(k(i * 3));
            ref.add(k(i * 3));
        }
        checkInvariants(s);
        assertContentEquals(ref, s);
        KTypeTreeSet presized = new KTypeTreeSet(100_000);
        presized.addAll(s);
        assertEquals(s, presized);
        checkInvariants(presized);
    }

    private static void assertContentEquals(TreeSet<KBoxed> ref, KTypeTreeSet s) {
        List<KBoxed> seen = new ArrayList<>();
        s.forEach(seen::add);
        assertEquals(new ArrayList<>(ref), seen);
        assertEquals(ref.hashCode(), s.hashCode());
        assertEquals(ref.size(), s.size());
    }

    // ------------------------------------------------------------------
    // Structural invariants (read through reflection)
    // ------------------------------------------------------------------

    /** The fields of a tree and the result of walking it from the root. */
    private static final class Tree {
        final KType[] leafKeys;
        final int[] leafSize;
        final int[] leafNext;
        final int[] leafPrev;
        final int leafCount;
        final int freeLeaf;
        final KType[] innerKeys;
        final int[] innerChildren;
        final int[] innerSize;
        final int innerCount;
        final int freeInner;
        final int root;
        final int height;
        final int firstLeaf;
        final int lastLeaf;
        final int size;
        final List<Integer> leaves = new ArrayList<>();
        final Set<Integer> leafSet = new HashSet<>();
        final Set<Integer> inner = new HashSet<>();
        int reachableLeaves;

        Tree(KTypeTreeSet s) throws ReflectiveOperationException {
            leafKeys = (KType[]) get(s, "leafKeys");
            leafSize = (int[]) get(s, "leafSize");
            leafNext = (int[]) get(s, "leafNext");
            leafPrev = (int[]) get(s, "leafPrev");
            leafCount = (int) get(s, "leafCount");
            freeLeaf = (int) get(s, "freeLeaf");
            innerKeys = (KType[]) get(s, "innerKeys");
            innerChildren = (int[]) get(s, "innerChildren");
            innerSize = (int[]) get(s, "innerSize");
            innerCount = (int) get(s, "innerCount");
            freeInner = (int) get(s, "freeInner");
            root = (int) get(s, "root");
            height = (int) get(s, "height");
            firstLeaf = (int) get(s, "firstLeaf");
            lastLeaf = (int) get(s, "lastLeaf");
            size = (int) get(s, "size");
            walk(root, height, false, KZero, false, KZero, true, true);
            reachableLeaves = leaves.size();
        }

        private static Object get(Object o, String name) throws ReflectiveOperationException {
            Field f = KTypeTreeSet.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        }

        private void walk(int node, int level, boolean hasLo, KType lo, boolean hasHi, KType hi,
                          boolean rightmost, boolean isRoot) {
            if (level == 0) {
                int n = leafSize[node];
                assertTrue(n <= LEAF, "leaf size");
                if (!isRoot) {
                    assertTrue(n >= 1, "non-root leaf is empty");
                    if (!rightmost) {
                        assertTrue(n >= LEAF / 4, "leaf underfull: " + n);
                    }
                }
                int base = node * LEAF;
                for (int j = 0; j < n; j++) {
                    KType key = leafKeys[base + j];
                    if (j > 0) {
                        assertTrue(leafKeys[base + j - 1] < key, "leaf elements sorted");
                    }
                    assertTrue(!hasLo || key >= lo, "element below the separator");
                    assertTrue(!hasHi || key < hi, "element above the separator");
                }
                assertTrue(leafSet.add(node), "leaf reachable twice");
                leaves.add(node);
                return;
            }
            int n = innerSize[node];
            assertTrue(n >= 2 && n <= INNER, "inner node size: " + n);
            if (!isRoot && !rightmost) {
                assertTrue(n >= INNER / 4, "inner node underfull: " + n);
            }
            assertTrue(inner.add(node), "inner node reachable twice");
            int base = node * INNER;
            for (int c = 1; c < n - 1; c++) {
                assertTrue(innerKeys[base + c - 1] < innerKeys[base + c], "separators sorted");
            }
            for (int c = 0; c < n; c++) {
                boolean cHasLo = c > 0 || hasLo;
                KType cLo = c > 0 ? innerKeys[base + c - 1] : lo;
                boolean cHasHi = c < n - 1 || hasHi;
                KType cHi = c < n - 1 ? innerKeys[base + c] : hi;
                walk(innerChildren[base + c], level - 1, cHasLo, cLo, cHasHi, cHi, rightmost && c == n - 1, false);
            }
        }
    }

    private static void checkInvariants(KTypeTreeSet s) {
        Tree t;
        try {
            t = new Tree(s);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        // Leaf chain in both directions equals the leaves in tree order.
        List<Integer> chain = new ArrayList<>();
        int prev = -1;
        for (int leaf = t.firstLeaf; leaf >= 0; leaf = t.leafNext[leaf]) {
            assertEquals(prev, t.leafPrev[leaf], "prev link");
            chain.add(leaf);
            prev = leaf;
            assertTrue(chain.size() <= t.leafCount, "cycle in the leaf chain");
        }
        assertEquals(t.leaves, chain, "leaf chain");
        assertEquals(prev, t.lastLeaf, "last leaf");
        long total = 0;
        for (int leaf : t.leaves) {
            total += t.leafSize[leaf];
        }
        assertEquals(t.size, total, "size");
        assertEquals(s.size(), t.size);
        // Free lists are disjoint from the tree and cover the rest of the pools.
        Set<Integer> free = new HashSet<>();
        for (int leaf = t.freeLeaf; leaf >= 0; leaf = t.leafNext[leaf]) {
            assertFalse(t.leafSet.contains(leaf), "free leaf in the tree");
            assertTrue(free.add(leaf), "free leaf twice");
        }
        assertEquals(t.leafCount, t.leaves.size() + free.size(), "leaf pool");
        free.clear();
        for (int node = t.freeInner; node >= 0; node = t.innerChildren[node * INNER]) {
            assertFalse(t.inner.contains(node), "free inner node in the tree");
            assertTrue(free.add(node), "free inner node twice");
        }
        assertEquals(t.innerCount, t.inner.size() + free.size(), "inner pool");
    }
}
