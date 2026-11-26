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
//#types K=int V=boolean|byte|short|char|int|long|float|double
//#types K=long V=boolean|byte|short|char|int|long|float|double
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KTypeVTypeCompactHashMapTest {

    /**
     * Maps an {@code int} to a key: a bijection with {@code k(0) == 0} that keeps the sign.
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

    /** Maps an {@code int} to a value; {@code v(0)} is the default value of the map. */
    private static VType v(int i) {
//#if V == boolean
        return (i & 1) != 0;
//#elif V == int|long|float|double
        return i;
//#else
        return (VType) i;
//#end
    }
//#if V.numeric

    /** The sum as {@link KTypeVTypeCompactHashMap#addTo} computes it. */
    private static VType sum(VBoxed a, VBoxed b) {
//#if V == byte|short|char
        return (VType) (a + b);
//#else
        return a + b;
//#end
    }
//#end

    @Test
    void basicOperations() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        assertTrue(m.isEmpty());
        assertEquals(v(0), m.put(k(1), v(10)));
        assertEquals(v(10), m.put(k(1), v(11)));
        assertEquals(v(11), m.get(k(1)));
        assertEquals(v(0), m.get(k(2)));
        assertEquals(v(-7), m.getOrDefault(k(2), v(-7)));
        assertTrue(m.containsKey(k(1)));
        assertFalse(m.containsKey(k(2)));
        assertEquals(1, m.size());
        assertEquals(v(11), m.remove(k(1)));
        assertEquals(v(0), m.remove(k(1)));
        assertTrue(m.isEmpty());
    }

    @Test
    void zeroKeyIsHandledOutOfBand() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        assertFalse(m.containsKey(k(0)));
        assertEquals(v(0), m.put(k(0), v(5)));
        assertTrue(m.containsKey(k(0)));
        assertEquals(v(5), m.get(k(0)));
        assertEquals(1, m.size());
//#if V.numeric
        assertEquals(v(8), m.addTo(k(0), v(3)));
//#else
        m.put(k(0), v(8));
//#end
        assertFalse(m.putIfAbsent(k(0), v(1)));
        assertEquals(v(8), m.remove(k(0)));
        assertFalse(m.containsKey(k(0)));
        assertEquals(0, m.size());
    }

    @Test
    void defaultValuesAreStored() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        m.put(k(7), v(0));
        assertTrue(m.containsKey(k(7)));
        assertEquals(1, m.size());
        Map<KBoxed, VBoxed> seen = new HashMap<>();
        m.forEach(seen::put);
        assertEquals(Map.of(k(7), v(0)), seen);
    }
//#if V.numeric

    @Test
    void addToReturnsNewValue() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        assertEquals(v(3), m.addTo(k(42), v(3)));
        assertEquals(v(1), m.addTo(k(42), v(-2)));
        assertEquals(v(-1), m.addTo(k(-5), v(-1)));
        assertEquals(v(1), m.get(k(42)));
        assertEquals(v(-1), m.get(k(-5))); // key bits untouched by negative values
    }
//#end
//#if V.integral

    @Test
    void addToWrapsAround() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        m.put(k(-1), VBoxed.MAX_VALUE);
        assertEquals(VBoxed.MIN_VALUE, m.addTo(k(-1), v(1)));
        assertEquals(VBoxed.MIN_VALUE, m.get(k(-1)));
        assertEquals(VBoxed.MAX_VALUE, m.addTo(k(-1), v(-1)));
    }
//#end
//#if V.floating

    @Test
    void floatingPointValuesKeepTheirBits() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        m.put(k(1), VBoxed.NaN);
        m.put(k(2), -VZero);
        m.put(k(3), VBoxed.NEGATIVE_INFINITY);
        assertTrue(VBoxed.isNaN(m.get(k(1))));
        assertEquals(0, VBoxed.compare(-VZero, m.get(k(2))), "sign of zero");
        assertEquals(VBoxed.NEGATIVE_INFINITY, m.get(k(3)));
        KTypeVTypeCompactHashMap other = new KTypeVTypeCompactHashMap();
        other.put(k(1), VBoxed.NaN);
        other.put(k(2), -VZero);
        other.put(k(3), VBoxed.NEGATIVE_INFINITY);
        assertEquals(m, other);
        other.put(k(2), VZero);
        assertNotEquals(m, other);
    }
//#end

    @Test
    void negativeKeysAndValues() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        int[] keys = {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, -123456, 1};
        for (int k : keys) {
            m.put(k(k), v(~k));
        }
        for (int k : keys) {
            assertEquals(v(~k), m.get(k(k)));
        }
        assertEquals(keys.length, m.size());
    }

    @Test
    void indexHandles() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        for (int i : new int[] {0, 17, -3}) {
            KType key = k(i);
            int idx = m.indexOf(key);
            assertFalse(m.indexExists(idx));
            m.indexInsert(idx, key, v(100));
            idx = m.indexOf(key);
            assertTrue(m.indexExists(idx));
            assertEquals(v(100), m.indexGet(idx));
            assertEquals(v(100), m.indexReplace(idx, v(201)));
            assertEquals(v(201), m.get(key));
        }
        assertEquals(3, m.size());
        assertEquals(v(201), m.indexRemove(m.indexOf(k(17))));
        assertEquals(v(201), m.indexRemove(m.indexOf(k(0))));
        assertEquals(1, m.size());
        assertEquals(v(201), m.get(k(-3)));
    }

    @Test
    void indexInsertTriggersGrowth() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap(0);
        for (int k = 1; k <= 10_000; k++) {
            int idx = m.indexOf(k(k));
            assertFalse(m.indexExists(idx));
            m.indexInsert(idx, k(k), v(k * 3));
        }
        for (int k = 1; k <= 10_000; k++) {
            assertEquals(v(k * 3), m.get(k(k)));
        }
    }

    @Test
    void forEachVisitsAllEntries() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        for (int k = -50; k <= 50; k++) {
            m.put(k(k), v(k * k));
            ref.put(k(k), v(k * k));
        }
        Map<KBoxed, VBoxed> seen = new HashMap<>();
        m.forEach((k, v) -> assertEquals(null, seen.put(k, v)));
        assertEquals(ref, seen);
        assertEquals(ref.hashCode(), m.hashCode());
        assertEquals(ref.toString().length(), m.toString().length());
    }

    @Test
    void equalsIsIndependentOfSeedAndCapacity() {
        KTypeVTypeCompactHashMap a = new KTypeVTypeCompactHashMap();
        KTypeVTypeCompactHashMap b = new KTypeVTypeCompactHashMap(10_000, 0.5f);
        for (int k = 0; k < 1000; k++) {
            a.put(k(k * 31), v(k));
            b.put(k(k * 31), v(k));
        }
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.put(k(0), v(-1));
        assertNotEquals(a, b);
    }

    @Test
    void clearAndReuse() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap();
        for (int k = 0; k < 1000; k++) {
            m.put(k(k), v(k));
        }
        m.clear();
        assertEquals(0, m.size());
        assertFalse(m.containsKey(k(0)));
        assertFalse(m.containsKey(k(500)));
        m.put(k(500), v(1));
        assertEquals(v(1), m.get(k(500)));
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeCompactHashMap(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeCompactHashMap(10, 0f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeCompactHashMap(10, 1f));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeCompactHashMap(10, Float.NaN));
    }

    @Test
    void ensureCapacityKeepsContent() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap(0);
        for (int k = 1; k <= 100; k++) {
            m.put(k(k), v(-k));
        }
        m.ensureCapacity(100_000);
        assertTrue(m.blockCount() * 7 > 100_000);
        for (int k = 1; k <= 100; k++) {
            assertEquals(v(-k), m.get(k(k)));
        }
    }

    @Test
    void capacityHoldsExpectedSize() {
        for (float lf : new float[] {0.25f, 0.5f, 0.75f, 0.85f, 0.9f, 0.99f}) {
            for (int n : new int[] {0, 1, 2, 3, 5, 12, 13, 14, 100, 1000, 12345}) {
                int blocks = KTypeVTypeCompactHashMap.blocksFor(n, lf);
                assertEquals(0, blocks & (blocks - 1), "power of two");
                assertTrue(blocks >= 2, "at least two blocks");
                assertTrue(blocks * 7 > n, "at least one free slot");
            }
        }
    }

    /**
     * Many keys with the same home block: the home block overflows, its counter saturates,
     * and lookups, updates and removals must still be correct.
     */
    @Test
    void collidingKeysSaturateOverflowCounter() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap(3000);
        int blocks = m.blockCount();
        int target = m.homeBlock(k(1));
        List<Integer> ids = new ArrayList<>(); // keys k(i) with the same home block
        for (int i = 1; ids.size() < 600; i++) {
            if (m.homeBlock(k(i)) == target) {
                ids.add(i);
            }
        }
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        for (int i : ids) {
            m.put(k(i), v(-i));
            ref.put(k(i), v(-i));
        }
        assertEquals(blocks, m.blockCount(), "no growth expected");
        assertEquals(255, m.overflowCount(target), "counter saturates");
        assertContentEquals(ref, m);
        for (int j = 0; j < ids.size(); j += 2) {
            KType key = k(ids.get(j));
            assertEquals((VType) ref.remove(key), m.remove(key));
        }
        assertContentEquals(ref, m);
        for (int i : ids) {
            assertEquals(ref.containsKey(k(i)), m.containsKey(k(i)));
        }
        for (KBoxed key : ref.keySet().toArray(new KBoxed[0])) {
            assertEquals((VType) ref.remove(key), m.remove(key));
        }
        assertTrue(m.isEmpty());
        for (int i : ids) {
            assertFalse(m.containsKey(k(i)));
        }
    }

    /** Counters return to zero once all overflowed keys are gone (no saturation involved). */
    @Test
    void overflowCountersAreExact() {
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap(3000);
        int target = m.homeBlock(k(7));
        List<Integer> ids = new ArrayList<>(); // keys k(i) with the same home block
        for (int i = 7; ids.size() < 40; i++) {
            if (m.homeBlock(k(i)) == target) {
                ids.add(i);
            }
        }
        for (int i : ids) {
            m.put(k(i), v(i));
        }
        // 7 keys fit into the home block; each of the other 33 passes it at least once (a later
        // overflow step may come back to it, depending on the seed)
        assertTrue(m.overflowCount(target) >= 33, "counter " + m.overflowCount(target));
        for (int i : ids) {
            assertEquals(v(i), m.remove(k(i)));
        }
        for (int b = 0; b < m.blockCount(); b++) {
            assertEquals(0, m.overflowCount(b), "block " + b);
        }
    }

    /**
     * Differential test against {@link HashMap}. Small key ranges and high load factors
     * force full blocks, overflow chains, frequent removals, and repeated growth.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 16, 0.85, 1, 200000",
            "2, 300, 0.85, 1, 200000",
            "3, 5000, 0.5, 1, 200000",
            "4, 5000, 0.97, 1, 200000",
            "5, 100000, 0.85, 1, 400000",
            "6, 5000, 0.97, 65536, 200000",
            "7, 20000, 0.9, 4096, 200000",
    })
    void randomOperationsMatchHashMap(long rngSeed, int keyRange, float loadFactor, int stride, int ops) {
        SplittableRandom rnd = new SplittableRandom(rngSeed);
        KTypeVTypeCompactHashMap m = new KTypeVTypeCompactHashMap(0, loadFactor);
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        for (int n = 0; n < ops; n++) {
            KType key = k((rnd.nextInt(keyRange) - keyRange / 2) * stride); // includes 0 and negatives
            VType value = v(rnd.nextInt());
            switch (rnd.nextInt(8)) {
                case 0, 1 -> assertEquals(orZero(ref.put(key, value)), m.put(key, value));
                case 2 -> assertEquals(orZero(ref.remove(key)), m.remove(key));
//#if V.numeric
                case 3 -> assertEquals((VType) ref.merge(key, value, KTypeVTypeCompactHashMapTest::sum), m.addTo(key, value));
//#else
                case 3 -> assertEquals(orZero(ref.put(key, value)), m.put(key, value));
//#end
                case 4 -> assertEquals(ref.putIfAbsent(key, value) == null, m.putIfAbsent(key, value));
                case 5 -> {
                    int idx = m.indexOf(key);
                    assertEquals(ref.containsKey(key), m.indexExists(idx));
                    if (m.indexExists(idx)) {
                        assertEquals((VType) ref.get(key), m.indexGet(idx));
                        if (rnd.nextBoolean()) {
                            assertEquals((VType) ref.put(key, value), m.indexReplace(idx, value));
                        } else {
                            assertEquals((VType) ref.remove(key), m.indexRemove(idx));
                        }
                    } else {
                        ref.put(key, value);
                        m.indexInsert(idx, key, value);
                    }
                }
                case 6 -> assertEquals(ref.containsKey(key), m.containsKey(key));
                default -> assertEquals((VType) ref.getOrDefault(key, v(-1)), m.getOrDefault(key, v(-1)));
            }
            assertEquals(ref.size(), m.size());
            if ((n & 0xFFFF) == 0) {
                assertContentEquals(ref, m);
            }
        }
        assertContentEquals(ref, m);
        // drain completely through removals
        for (KBoxed k : ref.keySet().toArray(new KBoxed[0])) {
            assertEquals((VType) ref.remove(k), m.remove(k));
        }
        assertTrue(m.isEmpty());
        for (int b = 0; b < m.blockCount(); b++) {
            int c = m.overflowCount(b);
            assertTrue(c == 0 || c == 255, "counter must be zero (or saturated) when empty: " + c);
        }
    }

    private static VType orZero(VBoxed v) {
        return v == null ? VZero : v;
    }

    private static void assertContentEquals(Map<KBoxed, VBoxed> ref, KTypeVTypeCompactHashMap m) {
        Map<KBoxed, VBoxed> copy = new HashMap<>();
        m.forEach(copy::put);
        assertEquals(ref, copy);
        for (Map.Entry<KBoxed, VBoxed> e : ref.entrySet()) {
            assertEquals((VType) e.getValue(), m.get(e.getKey()));
        }
    }
}
