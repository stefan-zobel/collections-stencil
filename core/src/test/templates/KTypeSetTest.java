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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

//#if K == long
import java.util.ArrayList;
//#end
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests all set implementations; the compact-specific tests are at the end. */
class KTypeSetTest {

    enum Impl {
        LINEAR,
//#if K == long
        COMPACT,
//#end
        LINKED,
        TREE;

        /** For {@code TREE}, which has no load factor, {@code loadFactor} is ignored. */
        KTypeSet create(int expectedSize, float loadFactor) {
            if (this == TREE) {
                return new KTypeTreeSet(expectedSize);
            }
            if (this == LINKED) {
                return new KTypeLinkedHashSet(expectedSize, loadFactor);
            }
//#if K == long
            if (this == COMPACT) {
                return new KTypeCompactHashSet(expectedSize, loadFactor);
            }
//#end
            return new KTypeHashSet(expectedSize, loadFactor);
        }

        KTypeSet create(int expectedSize) {
            if (this == TREE) {
                return new KTypeTreeSet(expectedSize);
            }
            if (this == LINKED) {
                return new KTypeLinkedHashSet(expectedSize);
            }
//#if K == long
            if (this == COMPACT) {
                return new KTypeCompactHashSet(expectedSize);
            }
//#end
            return new KTypeHashSet(expectedSize);
        }

        KTypeSet create() {
            if (this == TREE) {
                return new KTypeTreeSet();
            }
            if (this == LINKED) {
                return new KTypeLinkedHashSet();
            }
//#if K == long
            if (this == COMPACT) {
                return new KTypeCompactHashSet();
            }
//#end
            return new KTypeHashSet();
        }

        KTypeSet copyOf(KTypeSet other) {
            if (this == TREE) {
                return new KTypeTreeSet(other);
            }
            if (this == LINKED) {
                return new KTypeLinkedHashSet(other);
            }
//#if K == long
            if (this == COMPACT) {
                return new KTypeCompactHashSet(other);
            }
//#end
            return new KTypeHashSet(other);
        }

        KTypeSet of(KType... elements) {
            if (this == TREE) {
                return KTypeTreeSet.of(elements);
            }
            if (this == LINKED) {
                return KTypeLinkedHashSet.of(elements);
            }
//#if K == long
            if (this == COMPACT) {
                return KTypeCompactHashSet.of(elements);
            }
//#end
            return KTypeHashSet.of(elements);
        }
    }

    /**
     * Maps an {@code int} to an element: a bijection with {@code k(0) == 0} that keeps the sign.
//#if K == long
     * The upper 32 bits are used, so that hashing must take them into account.
//#end
     */
    private static KType k(int i) {
//#if K == long
        return ((long) i << 32) | (i & 0xFFFF_FFFFL);
//#else
        return i;
//#end
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void basicOperations(Impl impl) {
        KTypeSet s = impl.create();
        assertTrue(s.isEmpty());
        assertTrue(s.add(k(1)));
        assertFalse(s.add(k(1)));
        assertTrue(s.add(k(-2)));
        assertEquals(2, s.size());
        assertTrue(s.contains(k(1)));
        assertTrue(s.contains(k(-2)));
        assertFalse(s.contains(k(2)));
        assertTrue(s.remove(k(1)));
        assertFalse(s.remove(k(1)));
        assertFalse(s.contains(k(1)));
        assertEquals(1, s.size());
        s.clear();
        assertTrue(s.isEmpty());
        assertFalse(s.contains(k(-2)));
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void zeroElement(Impl impl) {
        KTypeSet s = impl.create();
        assertFalse(s.contains(KZero));
        assertFalse(s.remove(KZero));
        assertTrue(s.add(KZero));
        assertFalse(s.add(KZero));
        assertTrue(s.contains(KZero));
        assertEquals(1, s.size());
        assertArrayEquals(new KType[] {KZero}, s.toArray());
        assertEquals("[0]", s.toString());
        assertTrue(s.remove(KZero));
        assertTrue(s.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void extremeValues(Impl impl) {
        KTypeSet s = impl.create();
        KType[] values = {KBoxed.MIN_VALUE, KBoxed.MAX_VALUE, -1, 1, k(-123456), k(1 << 30)};
        for (KType v : values) {
            assertTrue(s.add(v));
        }
        for (KType v : values) {
            assertTrue(s.contains(v));
            assertFalse(s.contains(v ^ 2));
        }
        assertEquals(values.length, s.size());
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void growsFromSmallCapacity(Impl impl) {
        KTypeSet s = impl.create(0);
        for (int i = -50_000; i < 50_000; i++) {
            assertTrue(s.add(k(i)));
        }
        assertEquals(100_000, s.size());
        for (int i = -50_000; i < 50_000; i++) {
            assertTrue(s.contains(k(i)));
        }
        assertFalse(s.contains(k(50_000)));
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void ensureCapacityKeepsContent(Impl impl) {
        KTypeSet s = impl.create(0);
        for (int i = 1; i <= 100; i++) {
            s.add(k(i));
        }
        s.ensureCapacity(100_000);
        s.ensureCapacity(10); // no shrinking
        assertEquals(100, s.size());
        for (int i = 1; i <= 100; i++) {
            assertTrue(s.contains(k(i)));
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void bulkMethodsMatchHashSet(Impl impl) {
        KTypeSet s = impl.create();
        Set<KBoxed> ref = new HashSet<>();
        SplittableRandom rnd = new SplittableRandom(11);
        for (int n = 0; n < 5000; n++) {
            KType key = k(rnd.nextInt(20_000) - 10_000);
            assertEquals(ref.add(key), s.add(key));
        }
        assertContentEquals(ref, s);
        assertEquals(ref.hashCode(), s.hashCode());
        String str = s.toString();
        assertTrue(str.startsWith("[") && str.endsWith("]"));
        assertEquals(ref.size(), str.split(", ").length);
    }

    @Test
    void equalsAcrossImplementations() {
        KTypeSet a = new KTypeHashSet();
//#if K == long
        KTypeSet b = new KTypeCompactHashSet();
//#else
        KTypeSet b = new KTypeHashSet(0, 0.5f); // a differently configured instance
//#end
        assertEquals(a, b);
        for (int i = -1000; i <= 1000; i++) {
            a.add(k(i));
            b.add(k(-i));
        }
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(a.hashCode(), b.hashCode());
        b.remove(KZero);
        assertNotEquals(a, b);
        assertNotEquals(b, a);
        b.add(k(5000));
        assertNotEquals(a, b);
        assertNotEquals(b, a);
        assertNotEquals(a, new HashSet<>(List.of(1, 2)));
        KTypeSet tree = new KTypeTreeSet(a);
        assertEquals(a, tree);
        assertEquals(tree, a);
        assertEquals(a.hashCode(), tree.hashCode());
        tree.remove(KZero);
        assertNotEquals(a, tree);
        assertNotEquals(tree, a);
    }

    /** Load factors and the maximum capacity apply to the hash sets; see KTypeTreeSetTest for the tree. */
    @ParameterizedTest
    @EnumSource(value = Impl.class, mode = EnumSource.Mode.EXCLUDE, names = "TREE")
    void invalidArguments(Impl impl) {
        assertThrows(IllegalArgumentException.class, () -> impl.create(-1));
        assertThrows(IllegalArgumentException.class, () -> impl.create(10, 0f));
        assertThrows(IllegalArgumentException.class, () -> impl.create(10, 1f));
        assertThrows(IllegalArgumentException.class, () -> impl.create(10, Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> impl.create(Integer.MAX_VALUE));
    }
//#if K == long

    /** Elements that differ only in their upper 32 bits must not collide in the hash function's output. */
    @ParameterizedTest
    @EnumSource(Impl.class)
    void keysDifferingInUpperBits(Impl impl) {
        KTypeSet s = impl.create(0);
        for (int j = 1; j <= 5000; j++) {
            s.add((long) j << 32);
            s.add(-((long) j << 32));
        }
        assertEquals(10_000, s.size());
        for (int j = 1; j <= 5000; j++) {
            assertTrue(s.contains((long) j << 32));
            assertTrue(s.contains(-((long) j << 32)));
            assertFalse(s.contains(j));
        }
    }
//#end

    /**
     * Differential test against {@link HashSet}. Small key ranges and high load factors
     * force full blocks or long probe chains, frequent removals, and repeated growth.
     */
    @ParameterizedTest
    @CsvSource({
            "LINEAR, 1, 16, 0.75, 1, 200000",
            "LINEAR, 2, 5000, 0.97, 1, 200000",
            "LINEAR, 3, 20000, 0.5, 4096, 200000",
            "LINEAR, 4, 100000, 0.75, 65536, 400000",
            "TREE, 1, 16, 0.75, 1, 200000",
            "TREE, 2, 5000, 0.75, 1, 200000",
            "TREE, 4, 100000, 0.75, 65536, 400000",
//#if K == long
            "COMPACT, 1, 16, 0.85, 1, 200000",
            "COMPACT, 2, 300, 0.85, 1, 200000",
            "COMPACT, 3, 5000, 0.5, 1, 200000",
            "COMPACT, 4, 5000, 0.97, 1, 200000",
            "COMPACT, 5, 100000, 0.85, 1, 400000",
            "COMPACT, 6, 5000, 0.97, 65536, 200000",
            "COMPACT, 7, 20000, 0.9, 4096, 200000",
//#end
    })
    void randomOperationsMatchHashSet(Impl impl, long rngSeed, int keyRange, float loadFactor, int stride, int ops) {
        SplittableRandom rnd = new SplittableRandom(rngSeed);
        KTypeSet s = impl.create(0, loadFactor);
        Set<KBoxed> ref = new HashSet<>();
        for (int n = 0; n < ops; n++) {
            KType key = k((rnd.nextInt(keyRange) - keyRange / 2) * stride); // includes 0 and negatives
            switch (rnd.nextInt(4)) {
                case 0, 1 -> assertEquals(ref.add(key), s.add(key));
                case 2 -> assertEquals(ref.remove(key), s.remove(key));
                default -> assertEquals(ref.contains(key), s.contains(key));
            }
            assertEquals(ref.size(), s.size());
            if ((n & 0xFFFF) == 0) {
                assertContentEquals(ref, s);
            }
        }
        assertContentEquals(ref, s);
        // drain completely through removals
        for (KBoxed key : ref.toArray(new KBoxed[0])) {
            assertTrue(s.remove(key));
        }
        assertTrue(s.isEmpty());
//#if K == long
        if (s instanceof KTypeCompactHashSet c) {
            for (int b = 0; b < c.blockCount(); b++) {
                int count = c.overflowCount(b);
                assertTrue(count == 0 || count == 255, "counter must be zero (or saturated) when empty: " + count);
            }
        }
//#end
    }

//#if K == long
    // ------------------------------------------------------------------
    // Compact layout
    // ------------------------------------------------------------------

    private static final int SLOTS_PER_BLOCK = 7;

    @Test
    void compactCapacityHoldsExpectedSize() {
        for (float lf : new float[] {0.25f, 0.5f, 0.75f, 0.85f, 0.9f, 0.99f}) {
            for (int n : new int[] {0, 1, 2, 3, 5, 12, 13, 14, 27, 28, 100, 1000, 12345}) {
                int blocks = KTypeCompactHashSet.blocksFor(n, lf);
                assertEquals(0, blocks & (blocks - 1), "power of two");
                assertTrue(blocks >= 2, "at least two blocks");
                assertTrue(blocks * SLOTS_PER_BLOCK > n, "at least one free slot");
            }
        }
        KTypeCompactHashSet s = new KTypeCompactHashSet(0);
        s.ensureCapacity(100_000);
        assertTrue(s.blockCount() * SLOTS_PER_BLOCK > 100_000);
    }

    /** Elements of one home block, as ids {@code i} of elements {@code k(i)}. */
    private static List<Integer> sameHomeBlock(KTypeCompactHashSet s, int first, int count) {
        int target = s.homeBlock(k(first));
        List<Integer> ids = new ArrayList<>();
        for (int i = first; ids.size() < count; i++) {
            if (s.homeBlock(k(i)) == target) {
                ids.add(i);
            }
        }
        return ids;
    }

    /**
     * Many elements with the same home block: the home block overflows, its counter
     * saturates, and lookups and removals must still be correct.
     */
    @Test
    void collidingElementsSaturateOverflowCounter() {
        KTypeCompactHashSet s = new KTypeCompactHashSet(3000);
        int blocks = s.blockCount();
        List<Integer> ids = sameHomeBlock(s, 1, 600);
        int target = s.homeBlock(k(1));
        Set<KBoxed> ref = new HashSet<>();
        for (int i : ids) {
            assertTrue(s.add(k(i)));
            ref.add(k(i));
        }
        assertEquals(blocks, s.blockCount(), "no growth expected");
        assertEquals(255, s.overflowCount(target), "counter saturates");
        assertContentEquals(ref, s);
        for (int j = 0; j < ids.size(); j += 2) {
            KType key = k(ids.get(j));
            assertEquals(ref.remove(key), s.remove(key));
        }
        assertContentEquals(ref, s);
        for (int i : ids) {
            assertEquals(ref.contains(k(i)), s.contains(k(i)));
        }
        for (KBoxed key : ref.toArray(new KBoxed[0])) {
            assertTrue(s.remove(key));
        }
        assertTrue(s.isEmpty());
        for (int i : ids) {
            assertFalse(s.contains(k(i)));
        }
    }

    /** Counters return to zero once all overflowed elements are gone (no saturation involved). */
    @Test
    void overflowCountersAreExact() {
        KTypeCompactHashSet s = new KTypeCompactHashSet(3000);
        int overflowed = 33;
        List<Integer> ids = sameHomeBlock(s, 7, SLOTS_PER_BLOCK + overflowed);
        int target = s.homeBlock(k(7));
        for (int i : ids) {
            s.add(k(i));
        }
        // SLOTS_PER_BLOCK elements fit into the home block; each of the others passes it at
        // least once (a later overflow step may come back to it, depending on the seed)
        assertTrue(s.overflowCount(target) >= overflowed, "counter " + s.overflowCount(target));
        for (int i : ids) {
            assertTrue(s.remove(k(i)));
        }
        for (int b = 0; b < s.blockCount(); b++) {
            assertEquals(0, s.overflowCount(b), "block " + b);
        }
    }
//#end

    // ------------------------------------------------------------------
    // Iteration, removeIf, bulk operations
    // ------------------------------------------------------------------

    /** A set and its reference with random content, including 0 if {@code withZero}. */
    private static KTypeSet fill(Impl impl, float loadFactor, int n, int range, boolean withZero,
                                 Set<KBoxed> ref, long seed) {
        KTypeSet s = impl.create(0, loadFactor);
        SplittableRandom rnd = new SplittableRandom(seed);
        while (ref.size() < n) {
            int i = rnd.nextInt(range) - range / 2;
            if (i != 0 || withZero) {
                s.add(k(i));
                ref.add(k(i));
            }
        }
        return s;
    }

    private static Set<KBoxed> iterate(KTypeSet s) {
        Set<KBoxed> seen = new HashSet<>();
        for (int i = s.firstIndex(); i >= 0; i = s.nextIndex(i)) {
            KType key = s.indexKey(i);
            assertTrue(seen.add(key), "visited twice: " + key);
        }
        return seen;
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void iterationVisitsEveryElementOnce(Impl impl) {
        for (boolean withZero : new boolean[] {false, true}) {
            Set<KBoxed> ref = new HashSet<>();
            KTypeSet s = fill(impl, 0.85f, 3000, 20_000, withZero, ref, 1);
            assertEquals(ref, iterate(s));
        }
        KTypeSet s = impl.create();
        assertEquals(-1, s.firstIndex());
        s.add(KZero);
        assertEquals(KZero, s.indexKey(s.firstIndex()));
        assertEquals(-1, s.nextIndex(s.firstIndex()));
    }

    /** removeIf against {@link Set#removeIf}; wrapping probe chains; every element tested once. */
    @ParameterizedTest
    @CsvSource({
            "LINEAR, 0.5, 100", "LINEAR, 0.75, 3000", "LINEAR, 0.97, 3000", "LINEAR, 0.97, 12",
            "TREE, 0.75, 100", "TREE, 0.75, 3000", "TREE, 0.75, 12",
//#if K == long
            "COMPACT, 0.5, 100", "COMPACT, 0.85, 3000", "COMPACT, 0.97, 3000", "COMPACT, 0.97, 12",
//#end
    })
    void removeIfMatchesHashSet(Impl impl, float loadFactor, int n) {
        for (long seed = 1; seed <= 20; seed++) {
            Set<KBoxed> ref = new HashSet<>();
            KTypeSet s = fill(impl, loadFactor, n, 4 * n + 10, seed % 2 == 0, ref, seed);
            int modulus = 2 + (int) (seed % 3);
            Map<KBoxed, Integer> calls = new HashMap<>();
            boolean removed = s.removeIf(key -> {
                calls.merge(key, 1, Integer::sum);
                return Math.floorMod(KBoxed.hashCode(key), modulus) == 0;
            });
            assertEquals(ref, calls.keySet(), "every element is tested");
            for (int c : calls.values()) {
                assertEquals(1, c, "every element is tested once");
            }
            assertEquals(ref.removeIf(key -> Math.floorMod(KBoxed.hashCode(key), modulus) == 0), removed);
            assertEquals(ref, iterate(s));
            assertContentEquals(ref, s);
        }
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void bulkOperationsAndCopies(Impl impl) {
        Set<KBoxed> ref = new HashSet<>();
        KTypeSet source = fill(Impl.LINEAR, 0.75f, 2000, 50_000, true, ref, 5);
        KTypeSet copy = impl.copyOf(source);
        assertEquals(source, copy);
        assertEquals(ref, copy.toSet());
        KTypeSet target = impl.create();
        assertTrue(target.addAll(copy));
        assertFalse(target.addAll(copy));
        assertEquals(source, target);
        KTypeSet fromCollection = impl.create();
        assertTrue(fromCollection.addAll(ref));
        assertFalse(fromCollection.addAll(ref));
        assertEquals(source, fromCollection);
        copy.add(k(99_999));
        assertFalse(source.contains(k(99_999)), "copies are independent");
        assertTrue(copy.removeIf(key -> true));
        assertTrue(copy.isEmpty());
        assertThrows(NullPointerException.class, () -> impl.create().addAll(java.util.Arrays.asList(k(1), null)));
    }

    @ParameterizedTest
    @EnumSource(Impl.class)
    void ofAndStream(Impl impl) {
        assertTrue(impl.of().isEmpty());
        assertEquals(0, impl.of().stream().count());
        KType[] given = {k(5), KBoxed.MIN_VALUE, k(-3), k(5), KBoxed.MAX_VALUE, 0, k(-3), 0};
        KTypeSet s = impl.of(given);
        Set<KBoxed> ref = new HashSet<>();
        for (KType e : given) {
            ref.add(e);
        }
        assertContentEquals(ref, s);
        KType[] distinct = {k(5), KBoxed.MIN_VALUE, k(-3), KBoxed.MAX_VALUE, 0};
        if (impl == Impl.LINKED) {
            assertArrayEquals(distinct, s.toArray(), "the order of first occurrence");
        } else if (impl == Impl.TREE) {
            KType[] sorted = distinct.clone();
            Arrays.sort(sorted);
            assertArrayEquals(sorted, s.toArray(), "ascending");
        }
        KType[] before = s.toArray();
        assertArrayEquals(before, s.stream().toArray(), "the stream follows the iteration order");
        var stream = s.stream();
        assertTrue(s.add(k(77)), "the set stays modifiable");
        assertTrue(s.remove(k(5)));
        assertArrayEquals(before, stream.toArray(), "the stream reads a copy");
        assertEquals(s.size(), s.stream().count());
    }

    private static void assertContentEquals(Set<KBoxed> ref, KTypeSet s) {
        Set<KBoxed> copy = new HashSet<>();
        s.forEach(k -> assertTrue(copy.add(k), "duplicate " + k));
        assertEquals(ref, copy);
        KType[] array = s.toArray();
        assertEquals(ref.size(), array.length);
        KType[] expected = new KType[ref.size()];
        int n = 0;
        for (KBoxed k : ref) {
            expected[n++] = k;
        }
        Arrays.sort(array);
        Arrays.sort(expected);
        assertArrayEquals(expected, array);
        for (KBoxed k : ref) {
            assertTrue(s.contains(k));
        }
    }
}
