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
import org.junit.jupiter.params.provider.ValueSource;

class IntIntBlockHashMapTest {

    private static IntIntBlockHashMap newMap(boolean identity) {
        return new IntIntBlockHashMap(IntIntBlockHashMap.DEFAULT_EXPECTED_SIZE,
                IntIntBlockHashMap.DEFAULT_LOAD_FACTOR, identity);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void basicOperations(boolean identity) {
        IntIntBlockHashMap m = newMap(identity);
        assertTrue(m.isEmpty());
        assertEquals(0, m.put(1, 10));
        assertEquals(10, m.put(1, 11));
        assertEquals(11, m.get(1));
        assertEquals(0, m.get(2));
        assertEquals(-7, m.getOrDefault(2, -7));
        assertTrue(m.containsKey(1));
        assertFalse(m.containsKey(2));
        assertEquals(1, m.size());
        assertEquals(11, m.remove(1));
        assertEquals(0, m.remove(1));
        assertTrue(m.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void zeroKeyIsHandledOutOfBand(boolean identity) {
        IntIntBlockHashMap m = newMap(identity);
        assertFalse(m.containsKey(0));
        assertEquals(0, m.put(0, 5));
        assertTrue(m.containsKey(0));
        assertEquals(5, m.get(0));
        assertEquals(1, m.size());
        assertEquals(8, m.addTo(0, 3));
        assertFalse(m.putIfAbsent(0, 1));
        assertEquals(8, m.remove(0));
        assertFalse(m.containsKey(0));
        assertEquals(0, m.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addToReturnsNewValueAndWrapsAround(boolean identity) {
        IntIntBlockHashMap m = newMap(identity);
        assertEquals(3, m.addTo(42, 3));
        assertEquals(1, m.addTo(42, -2));
        m.put(-1, Integer.MAX_VALUE);
        assertEquals(Integer.MIN_VALUE, m.addTo(-1, 1));
        assertEquals(-1, m.addTo(-5, -1));
        assertEquals(Integer.MIN_VALUE, m.get(-1));
        assertEquals(-1, m.get(-5));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void negativeAndExtremeKeys(boolean identity) {
        IntIntBlockHashMap m = newMap(identity);
        int[] keys = {-1, Integer.MIN_VALUE, Integer.MAX_VALUE, -123456, 1};
        for (int k : keys) {
            m.put(k, ~k);
        }
        for (int k : keys) {
            assertEquals(~k, m.get(k));
        }
        assertEquals(keys.length, m.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void forEachVisitsAllEntries(boolean identity) {
        IntIntBlockHashMap m = newMap(identity);
        Map<Integer, Integer> ref = new HashMap<>();
        for (int k = -50; k <= 50; k++) {
            m.put(k, k * k);
            ref.put(k, k * k);
        }
        Map<Integer, Integer> seen = new HashMap<>();
        m.forEach((k, v) -> assertEquals(null, seen.put(k, v)));
        assertEquals(ref, seen);
        assertEquals(ref.hashCode(), m.hashCode());
    }

    @Test
    void equalsIsIndependentOfHomeAndCapacity() {
        IntIntBlockHashMap a = newMap(true);
        IntIntBlockHashMap b = new IntIntBlockHashMap(10_000, 0.5f, false);
        for (int k = 0; k < 1000; k++) {
            a.put(k * 31, k);
            b.put(k * 31, k);
        }
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        b.put(0, -1);
        assertNotEquals(a, b);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void clearAndReuse(boolean identity) {
        IntIntBlockHashMap m = newMap(identity);
        for (int k = 0; k < 1000; k++) {
            m.put(k, k);
        }
        m.clear();
        assertEquals(0, m.size());
        assertFalse(m.containsKey(0));
        assertFalse(m.containsKey(500));
        m.put(500, 1);
        assertEquals(1, m.get(500));
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new IntIntBlockHashMap(-1));
        assertThrows(IllegalArgumentException.class, () -> new IntIntBlockHashMap(10, 0f));
        assertThrows(IllegalArgumentException.class, () -> new IntIntBlockHashMap(10, 1f));
        assertThrows(IllegalArgumentException.class, () -> new IntIntBlockHashMap(10, Float.NaN));
    }

    @Test
    void capacityHoldsExpectedSize() {
        for (float lf : new float[] {0.25f, 0.5f, 0.75f, 0.9f, 0.99f}) {
            for (int n : new int[] {0, 1, 2, 3, 5, 12, 13, 100, 1000, 12345}) {
                int cap = IntIntBlockHashMap.capacityFor(n, lf);
                assertEquals(0, cap & (cap - 1), "power of two");
                assertTrue(cap >= 8, "at least one block");
                assertTrue(cap > n, "at least one free slot");
            }
        }
    }

    @Test
    void sequentialKeysUseTheirIdentitySlots() {
        IntIntBlockHashMap m = new IntIntBlockHashMap(1000, 0.75f, true);
        for (int k = 1; k <= 1000; k++) {
            m.put(k, k);
        }
        for (int b = 0; b < m.capacity() / 8; b++) {
            assertEquals(0, m.overflowCount(b), "no overflow for distinct keys below the capacity");
        }
    }

    /**
     * Many keys with the same home block: the home block overflows, its counter saturates,
     * and lookups, updates and removals must still be correct.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void collidingKeysSaturateOverflowCounter(boolean identity) {
        IntIntBlockHashMap m = new IntIntBlockHashMap(3000, 0.75f, identity);
        int cap = m.capacity();
        int target = m.homeBlock(1);
        List<Integer> keys = new ArrayList<>();
        for (int k = 1; keys.size() < 600; k++) {
            if (m.homeBlock(k) == target) {
                keys.add(k);
            }
        }
        Map<Integer, Integer> ref = new HashMap<>();
        for (int k : keys) {
            m.put(k, -k);
            ref.put(k, -k);
        }
        assertEquals(cap, m.capacity(), "no growth expected");
        assertEquals(255, m.overflowCount(target), "counter saturates");
        assertContentEquals(ref, m);
        for (int j = 0; j < keys.size(); j += 2) {
            int k = keys.get(j);
            assertEquals(ref.remove(k).intValue(), m.remove(k));
        }
        assertContentEquals(ref, m);
        for (int k : keys) {
            assertEquals(ref.containsKey(k), m.containsKey(k));
        }
        for (Integer k : ref.keySet().toArray(new Integer[0])) {
            assertEquals(ref.remove(k).intValue(), m.remove(k));
        }
        assertTrue(m.isEmpty());
        for (int k : keys) {
            assertFalse(m.containsKey(k));
        }
    }

    /** Counters return to zero once all overflowed keys are gone (no saturation involved). */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void overflowCountersAreExact(boolean identity) {
        IntIntBlockHashMap m = new IntIntBlockHashMap(3000, 0.75f, identity);
        int target = m.homeBlock(7);
        List<Integer> keys = new ArrayList<>();
        for (int k = 7; keys.size() < 40; k++) {
            if (m.homeBlock(k) == target) {
                keys.add(k);
            }
        }
        for (int k : keys) {
            m.put(k, k);
        }
        assertEquals(32, m.overflowCount(target), "8 keys fit into the home block");
        for (int k : keys) {
            assertEquals(k, m.remove(k));
        }
        for (int b = 0; b < m.capacity() / 8; b++) {
            assertEquals(0, m.overflowCount(b), "block " + b);
        }
    }

    /**
     * Differential test against {@link HashMap}. Small key ranges and high load factors
     * force full blocks, long overflow sequences, frequent removals, and repeated growth.
     * {@code stride} spreads the keys so that identity home slots collide.
     */
    @ParameterizedTest
    @CsvSource({
            "1, 16, 0.75, 1, true, 200000",
            "2, 300, 0.75, 1, true, 200000",
            "3, 5000, 0.5, 1, true, 200000",
            "4, 5000, 0.95, 1, true, 200000",
            "5, 100000, 0.75, 1, true, 400000",
            "6, 5000, 0.95, 65536, true, 200000",
            "7, 20000, 0.9, 4096, true, 200000",
            "8, 16, 0.75, 1, false, 200000",
            "9, 300, 0.75, 1, false, 200000",
            "10, 5000, 0.95, 1, false, 200000",
            "11, 100000, 0.75, 1, false, 400000",
            "12, 5000, 0.95, 65536, false, 200000",
    })
    void randomOperationsMatchHashMap(long rngSeed, int keyRange, float loadFactor, int stride,
                                      boolean identity, int ops) {
        SplittableRandom rnd = new SplittableRandom(rngSeed);
        IntIntBlockHashMap m = new IntIntBlockHashMap(0, loadFactor, identity);
        Map<Integer, Integer> ref = new HashMap<>();
        for (int n = 0; n < ops; n++) {
            int key = (rnd.nextInt(keyRange) - keyRange / 2) * stride; // includes 0 and negatives
            int value = rnd.nextInt();
            switch (rnd.nextInt(7)) {
                case 0, 1 -> assertEquals(orZero(ref.put(key, value)), m.put(key, value));
                case 2 -> assertEquals(orZero(ref.remove(key)), m.remove(key));
                case 3 -> assertEquals(ref.merge(key, value, Integer::sum).intValue(), m.addTo(key, value));
                case 4 -> assertEquals(ref.putIfAbsent(key, value) == null, m.putIfAbsent(key, value));
                case 5 -> assertEquals(ref.containsKey(key), m.containsKey(key));
                default -> assertEquals(ref.getOrDefault(key, -1).intValue(), m.getOrDefault(key, -1));
            }
            assertEquals(ref.size(), m.size());
            if ((n & 0xFFFF) == 0) {
                assertContentEquals(ref, m);
            }
        }
        assertContentEquals(ref, m);
        for (Integer k : ref.keySet().toArray(new Integer[0])) {
            assertEquals(ref.remove(k).intValue(), m.remove(k));
        }
        assertTrue(m.isEmpty());
        for (int b = 0; b < m.capacity() / 8; b++) {
            int c = m.overflowCount(b);
            assertTrue(c == 0 || c == 255, "counter must be zero (or saturated) when empty: " + c);
        }
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static void assertContentEquals(Map<Integer, Integer> ref, IntIntBlockHashMap m) {
        Map<Integer, Integer> copy = new HashMap<>();
        m.forEach(copy::put);
        assertEquals(ref, copy);
        for (Map.Entry<Integer, Integer> e : ref.entrySet()) {
            assertEquals(e.getValue().intValue(), m.get(e.getKey()));
        }
    }
}
