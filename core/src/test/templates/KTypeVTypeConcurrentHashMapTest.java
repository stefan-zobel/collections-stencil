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
//#types K=int|long V=int|long|double
package net.generated.example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KTypeVTypeConcurrentHashMapTest {

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

    /** A value as a {@code long}. */
    private static long asLong(VType value) {
//#if V == long
        return value;
//#else
        return (long) value;
//#end
    }

    /** Maps an {@code int} to a value. */
    private static VType v(int i) {
//#if V == int
        return i;
//#else
        return (VType) i;
//#end
    }

    // ------------------------------------------------------------------
    // Single-threaded contract
    // ------------------------------------------------------------------

    @Test
    void basicOperations() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap();
        assertTrue(m.isEmpty());
        assertEquals(VZero, m.put(k(1), v(10)));
        assertEquals(v(10), m.put(k(1), v(11)));
        assertEquals(VZero, m.put(k(-2), v(12)));
        assertEquals(2, m.size());
        assertEquals(v(11), m.get(k(1)));
        assertEquals(VZero, m.get(k(2)));
        assertEquals(v(99), m.getOrDefault(k(2), v(99)));
        assertFalse(m.putIfAbsent(k(1), v(5)));
        assertTrue(m.putIfAbsent(k(3), v(13)));
        assertFalse(m.replace(k(4), v(1)));
        assertFalse(m.containsKey(k(4)));
        assertTrue(m.replace(k(3), v(14)));
        assertFalse(m.replace(k(3), v(13), v(15)));
        assertTrue(m.replace(k(3), v(14), v(15)));
        assertEquals(v(15), m.get(k(3)));
        assertFalse(m.remove(k(3), v(14)));
        assertTrue(m.remove(k(3), v(15)));
        assertEquals(v(11), m.remove(k(1)));
        assertEquals(VZero, m.remove(k(1)));
        assertFalse(m.containsKey(k(1)));
        assertEquals(1, m.size());
        m.clear();
        assertTrue(m.isEmpty());
        assertEquals(VZero, m.get(k(-2)));
    }

    @Test
    void zeroKeyAndZeroValue() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 1);
        assertFalse(m.containsKey(KZero));
        assertEquals(VZero, m.put(KZero, VZero));
        assertTrue(m.containsKey(KZero));
        assertEquals(v(7), m.getOrDefault(k(5), v(7)));
        assertEquals(VZero, m.getOrDefault(KZero, v(7)));
        assertEquals(1, m.size());
        assertEquals(v(3), m.addTo(KZero, v(3)));
        assertEquals(Map.of(KZero, v(3)), m.toMap());
        assertEquals(Map.of(KZero, v(3)).hashCode(), m.hashCode());
        assertArrayEquals(new KType[] {KZero}, m.keysToArray());
        assertEquals("{0=" + v(3) + "}", m.toString());
        assertEquals(v(3), m.remove(KZero));
        assertTrue(m.isEmpty());
        m.put(k(9), VZero);
        assertTrue(m.containsKey(k(9)));
    }

    @Test
    void atomicOperations() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap();
        assertEquals(v(5), m.addTo(k(1), v(5)));
        assertEquals(v(7), m.addTo(k(1), v(2)));
        assertEquals(v(4), m.merge(k(2), v(4), (a, b) -> a * b));
        assertEquals(v(12), m.merge(k(2), v(3), (a, b) -> a * b));
        int[] calls = {0};
        assertEquals(v(20), m.computeIfAbsent(k(3), key -> {
            calls[0]++;
            return v(20);
        }));
        assertEquals(v(20), m.computeIfAbsent(k(3), key -> {
            calls[0]++;
            return v(21);
        }));
        assertEquals(1, calls[0]);
        assertEquals(Map.of(k(1), v(7), k(2), v(12), k(3), v(20)), m.toMap());
    }
//#if V.floating

    @Test
    void floatingValuesCompareLikeEquals() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap();
        m.put(k(1), VBoxed.NaN);
        assertTrue(m.replace(k(1), VBoxed.NaN, (VType) 1));
        m.put(k(2), -0.0);
        assertFalse(m.remove(k(2), 0.0));
        assertTrue(m.remove(k(2), -0.0));
        assertEquals(2.5, m.addTo(k(3), 2.5));
    }
//#end

    @Test
    void recursiveUpdatesThrow() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(16, 0.75f, 1);
        m.put(k(1), v(1));
        assertThrows(IllegalStateException.class, () -> m.merge(k(1), v(1), (a, b) -> m.addTo(k(2), b)));
        assertThrows(IllegalStateException.class, () -> m.computeIfAbsent(k(3), key -> m.remove(k(1))));
        // reads inside a function are fine
        assertEquals(v(2), m.merge(k(1), v(1), (a, b) -> a + m.get(k(1))));
        assertEquals(Map.of(k(1), v(2)), m.toMap());
        // the lock was released after each failure
        assertEquals(VZero, m.put(k(2), v(2)));
    }

    @Test
    void weakHashCodes() {
        for (int level : new int[] {1, 4, 64}) {
            KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, level);
            for (int i = 0; i < 10_000; i++) {
                m.put(k(i), v(i));
            }
            for (int i = 1; i < 2048; i++) {
                m.put(k(i << 20), v(-i));
            }
            assertEquals(10_000 + 2047, m.size());
            for (int i = 0; i < 10_000; i++) {
                assertEquals(v(i), m.get(k(i)));
            }
            for (int i = 1; i < 2048; i++) {
                assertEquals(v(-i), m.get(k(i << 20)));
            }
        }
    }

    @Test
    void extremeKeys() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap();
        KType[] keys = {KBoxed.MIN_VALUE, KBoxed.MAX_VALUE, -1, 1, KZero};
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], v(i + 1));
        }
        for (int i = 0; i < keys.length; i++) {
            assertEquals(v(i + 1), m.get(keys[i]));
        }
        assertEquals(keys.length, m.size());
    }

    @Test
    void equalsHashCodeAndConversions() {
        KTypeVTypeConcurrentHashMap a = new KTypeVTypeConcurrentHashMap(0, 0.75f, 2);
        KTypeVTypeConcurrentHashMap b = new KTypeVTypeConcurrentHashMap(1000, 0.5f, 16);
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        for (int i = -50; i < 50; i++) {
            a.put(k(i), v(i * 3));
            b.put(k(-i - 1), v((-i - 1) * 3));
            ref.put(k(i), v(i * 3));
        }
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(ref.hashCode(), a.hashCode());
        assertEquals(ref, a.toMap());
        b.put(k(0), v(1));
        assertNotEquals(a, b);
        assertNotEquals(a, ref);

        KType[] keys = a.keysToArray();
        VType[] values = a.valuesToArray();
        assertEquals(100, keys.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals((VType) ref.get(keys[i]), values[i]);
        }
        KTypeVTypeHashMap plain = new KTypeVTypeHashMap();
        ref.forEach(plain::put);
        assertEquals(a, new KTypeVTypeConcurrentHashMap(plain));
        assertEquals(a, new KTypeVTypeConcurrentHashMap(a));
        KTypeVTypeConcurrentHashMap c = new KTypeVTypeConcurrentHashMap();
        c.putAll(ref);
        assertEquals(a, c);
        String s = a.toString();
        assertTrue(s.startsWith("{") && s.endsWith("}") && s.contains(k(-50) + "=" + v(-150)), s);
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeConcurrentHashMap(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeConcurrentHashMap(16, 0f, 4));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeConcurrentHashMap(16, 1f, 4));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeConcurrentHashMap(16, 0.75f, 0));
        assertThrows(IllegalArgumentException.class, () -> new KTypeVTypeConcurrentHashMap(16, 0.75f,
                KTypeVTypeConcurrentHashMap.MAX_CONCURRENCY_LEVEL + 1));
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap();
        assertThrows(NullPointerException.class, () -> m.merge(k(1), v(1), null));
        assertThrows(NullPointerException.class, () -> m.computeIfAbsent(k(1), null));
    }

    @Test
    void ensureCapacityAndClearKeepContentRight() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 8);
        for (int i = 0; i < 1000; i++) {
            m.put(k(i), v(i));
        }
        m.ensureCapacity(100_000);
        for (int i = 0; i < 1000; i++) {
            assertEquals(v(i), m.get(k(i)));
        }
        m.clear();
        assertTrue(m.isEmpty());
        for (int i = 0; i < 1000; i++) {
            assertFalse(m.containsKey(k(i)));
        }
        m.put(k(5), v(5));
        assertEquals(Map.of(k(5), v(5)), m.toMap());
    }

    @Test
    void removeIfRemovesMatchesOnly() {
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 4);
        Map<KBoxed, VBoxed> ref = new HashMap<>();
        for (int i = -500; i < 500; i++) {
            m.put(k(i), v(i));
            ref.put(k(i), v(i));
        }
        assertTrue(m.removeIf((key, val) -> val % 3 == 0));
        ref.entrySet().removeIf(e -> e.getValue() % 3 == 0);
        assertEquals(ref, m.toMap());
        assertFalse(m.removeIf((key, val) -> val % 3 == 0));
        // the predicate may update the map; a changed value is not removed
        assertTrue(m.removeIf((key, val) -> {
            if (key == k(1)) {
                m.addTo(key, v(1000));
            }
            return key == k(1) || key == k(2);
        }));
        assertEquals(v(1001), m.get(k(1)));
        assertFalse(m.containsKey(k(2)));
    }

    @ParameterizedTest
    @CsvSource({
            // seed, key range, concurrency level, ops
            "1, 50, 1, 20000",
            "2, 500, 2, 40000",
            "3, 5000, 64, 60000",
            "4, 200, 16, 40000",
    })
    void randomOperationsMatchConcurrentHashMap(long seed, int keyRange, int level, int ops) {
        SplittableRandom rnd = new SplittableRandom(seed);
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, level);
        ConcurrentHashMap<KBoxed, VBoxed> ref = new ConcurrentHashMap<>();
        for (int n = 0; n < ops; n++) {
            KType key = k(rnd.nextInt(keyRange) - keyRange / 4);
            VType value = v(rnd.nextInt(8) - 2);
            switch (rnd.nextInt(12)) {
                case 0, 1 -> {
                    VBoxed old = ref.put(key, value);
                    assertEquals(old == null ? VZero : old, m.put(key, value));
                }
                case 2 -> assertEquals(ref.putIfAbsent(key, value) == null, m.putIfAbsent(key, value));
                case 3 -> assertEquals(ref.replace(key, value) != null, m.replace(key, value));
                case 4 -> assertEquals(ref.replace(key, v(1), value), m.replace(key, v(1), value));
                case 5 -> {
                    VBoxed old = ref.remove(key);
                    assertEquals(old == null ? VZero : old, m.remove(key));
                }
                case 6 -> assertEquals(ref.remove(key, value), m.remove(key, value));
                case 7 -> assertEquals((VType) ref.merge(key, value, VBoxed::sum), m.addTo(key, value));
                case 8 -> assertEquals((VType) ref.merge(key, value, (a, b) -> a * 2 - b),
                        m.merge(key, value, (a, b) -> a * 2 - b));
                case 9 -> assertEquals((VType) ref.computeIfAbsent(key, x -> value), m.computeIfAbsent(key, x -> value));
                case 10 -> assertEquals((VType) ref.getOrDefault(key, v(-77)), m.getOrDefault(key, v(-77)));
                default -> {
                    assertEquals((VType) ref.getOrDefault(key, VZero), m.get(key));
                    assertEquals(ref.containsKey(key), m.containsKey(key));
                }
            }
            if (n % 997 == 0) {
                assertEquals(ref.size(), m.size());
                assertEquals(ref, m.toMap());
            }
            if (n == ops / 2) {
                m.removeIf((x, val) -> val > 3);
                ref.values().removeIf(val -> val > 3);
            }
        }
        assertEquals(ref, m.toMap());
    }

    // ------------------------------------------------------------------
    // Multi-threaded
    // ------------------------------------------------------------------

    /** Runs the tasks in parallel threads that start together, and rethrows the first failure. */
    private static void runConcurrently(List<Runnable> tasks) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>();
        for (Runnable task : tasks) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    task.run();
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
            t.start();
            threads.add(t);
        }
        start.countDown();
        for (Thread t : threads) {
            t.join(60_000);
            assertFalse(t.isAlive(), "thread did not finish");
        }
        if (failure.get() != null) {
            throw new AssertionError("a thread failed", failure.get());
        }
    }

    @Test
    void concurrentCountersAreExact() throws InterruptedException {
        final int threads = 8;
        final int increments = 20_000;
        final int hotKeys = 16;
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 4);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            tasks.add(() -> {
                for (int i = 0; i < increments; i++) {
                    KType key = k((i + id) % hotKeys);
                    if ((i & 1) == 0) {
                        m.addTo(key, v(1));
                    } else {
                        m.merge(key, v(1), (a, b) -> a + b);
                    }
                }
            });
        }
        runConcurrently(tasks);
        long total = 0;
        for (int i = 0; i < hotKeys; i++) {
            total += asLong(m.get(k(i)));
        }
        assertEquals((long) threads * increments, total);
    }

    /** A key of the churn range, far from the counted keys; its value is {@code v(i + 1)}. */
    private static KType churnKey(int i) {
        return k(1_000_000 + i * 7919);
    }

    @ParameterizedTest
    @CsvSource({"1", "4"})
    void countsWithoutLockAreExactWhileTheTableChanges(int level) throws InterruptedException {
        final int counters = 8;
        final int increments = 200_000;
        final int hotKeys = 32;
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, level);
        AtomicBoolean done = new AtomicBoolean();
        AtomicIntegerArray left = new AtomicIntegerArray(1);
        left.set(0, counters);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < counters; t++) {
            final int id = t;
            tasks.add(() -> {
                for (int i = 0; i < increments; i++) {
                    m.addTo(k((i + id) % hotKeys), v(1)); // key 0 included (always under the lock)
                }
                if (left.decrementAndGet(0) == 0) {
                    done.set(true);
                }
            });
        }
        for (int c = 0; c < 2; c++) {
            final int base = c * 50_000;
            tasks.add(() -> { // backward shifts and rehashes in the segments of the counted keys
                while (!done.get()) {
                    for (int i = base; i < base + 3_000; i++) {
                        m.put(churnKey(i), v(i + 1));
                    }
                    for (int i = base; i < base + 3_000; i++) {
                        m.remove(churnKey(i));
                    }
                }
            });
        }
        tasks.add(() -> { // counts never go down, other keys keep their values
            SplittableRandom rnd = new SplittableRandom(1);
            double[] last = new double[hotKeys];
            while (!done.get()) {
                int i = rnd.nextInt(hotKeys);
                double value = m.get(k(i));
                if (value < last[i]) {
                    throw new AssertionError("count of " + i + " went down from " + last[i] + " to " + value);
                }
                last[i] = value;
                int c = rnd.nextInt(3_000);
                VType cv = m.get(churnKey(c));
                if (cv != VZero && cv != v(c + 1)) {
                    throw new AssertionError("key " + churnKey(c) + " returned " + cv);
                }
            }
        });
        runConcurrently(tasks);
        for (int i = 0; i < hotKeys; i++) {
            assertEquals(v(counters * increments / hotKeys), m.get(k(i)), "count of key " + i);
        }
    }

    @Test
    void addToWithoutLockAndLockedUpdatesTogetherLoseNothing() throws InterruptedException {
        final int threads = 8;
        final int increments = 40_000;
        final int keys = 16;
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 2);
        for (int i = 1; i <= keys; i++) {
            m.put(k(i), VZero);
        }
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            tasks.add(() -> {
                for (int i = 0; i < increments; i++) {
                    KType key = k(1 + (i + id) % keys);
                    switch (i % 4) {
                        case 0 -> m.merge(key, v(1), (a, b) -> a + b);
                        case 1 -> {
                            VType seen = m.get(key);
                            m.replace(key, seen, seen); // locked compare-and-set, changes nothing
                        }
                        default -> m.addTo(key, v(1));
                    }
                }
            });
        }
        runConcurrently(tasks);
        long total = 0;
        for (int i = 1; i <= keys; i++) {
            total += asLong(m.get(k(i)));
        }
        assertEquals((long) threads * increments * 3 / 4, total);
    }

    @ParameterizedTest
    @CsvSource({"1", "4"})
    void readersNeverSeeWrongValues(int level) throws InterruptedException {
        final int writers = 4;
        final int readers = 4;
        final int keys = 20_000;
        final int rounds = 6;
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, level);
        AtomicBoolean done = new AtomicBoolean();
        AtomicIntegerArray left = new AtomicIntegerArray(1);
        left.set(0, writers);
        List<Runnable> tasks = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            final int owner = w;
            tasks.add(() -> {
                for (int round = 0; round < rounds; round++) {
                    for (int i = owner; i < keys; i += writers) {
                        m.put(k(i), v(i * 8 + round + 1));
                    }
                    for (int i = owner; i < keys; i += writers) {
                        if ((i / writers) % 3 != round % 3) {
                            assertTrue(m.containsKey(k(i)));
                            m.remove(k(i));
                        }
                    }
                }
                for (int i = owner; i < keys; i += writers) {
                    m.put(k(i), v(i * 8 + rounds + 1));
                }
                if (left.decrementAndGet(0) == 0) {
                    done.set(true);
                }
            });
        }
        for (int r = 0; r < readers; r++) {
            final long seed = r;
            tasks.add(() -> {
                SplittableRandom rnd = new SplittableRandom(seed);
                while (!done.get()) {
                    int i = rnd.nextInt(keys);
                    long value = asLong(m.get(k(i)));
                    if (value != 0 && (value - 1) / 8 != i) {
                        throw new AssertionError("key " + i + " returned the value " + value);
                    }
                }
            });
        }
        runConcurrently(tasks);
        assertEquals(keys, m.size());
        for (int i = 0; i < keys; i++) {
            assertEquals(v(i * 8 + rounds + 1), m.get(k(i)));
        }
    }

    @Test
    void computeIfAbsentCallsTheFunctionOncePerKey() throws InterruptedException {
        final int threads = 8;
        final int keys = 20_000;
        AtomicIntegerArray calls = new AtomicIntegerArray(keys);
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 8);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final long seed = t;
            tasks.add(() -> {
                int[] order = new SplittableRandom(seed).ints(0, keys).limit(3L * keys).toArray();
                for (int i : order) {
                    VType value = m.computeIfAbsent(k(i), x -> {
                        calls.incrementAndGet(i);
                        return v(i + 1);
                    });
                    assertEquals(v(i + 1), value);
                }
            });
        }
        runConcurrently(tasks);
        for (int i = 0; i < keys; i++) {
            assertTrue(calls.get(i) <= 1, "function called " + calls.get(i) + " times for " + i);
            assertEquals(calls.get(i) == 1, m.containsKey(k(i)));
        }
    }

    @ParameterizedTest
    @CsvSource({"1", "4", "64"})
    void concurrentGrowthKeepsEverything(int level) throws InterruptedException {
        final int threads = 8;
        final int perThread = 40_000;
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, level);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            tasks.add(() -> {
                for (int i = base; i < base + perThread; i++) {
                    assertTrue(m.putIfAbsent(k(i), v(i)));
                }
            });
        }
        runConcurrently(tasks);
        assertEquals(threads * perThread, m.size());
        for (int i = 0; i < threads * perThread; i++) {
            assertEquals(v(i), m.get(k(i)));
        }
    }

    @Test
    void forEachSeesStableEntriesExactlyOnce() throws InterruptedException {
        final int stable = 2_000;
        KTypeVTypeConcurrentHashMap m = new KTypeVTypeConcurrentHashMap(0, 0.75f, 8);
        for (int i = 0; i < stable; i++) {
            m.put(k(i), v(i));
        }
        AtomicBoolean done = new AtomicBoolean();
        List<Runnable> tasks = new ArrayList<>();
        for (int w = 0; w < 3; w++) {
            final int base = stable + w * 100_000;
            tasks.add(() -> {
                for (int round = 0; round < 20; round++) {
                    for (int i = base; i < base + 5_000; i++) {
                        m.put(k(i), v(i));
                    }
                    for (int i = base; i < base + 5_000; i++) {
                        m.remove(k(i));
                    }
                }
                done.set(true);
            });
        }
        tasks.add(() -> {
            int passes = 0;
            while (!done.get() || passes == 0) {
                int[] seen = new int[stable];
                m.forEach((key, val) -> {
                    int i = (int) asLong(val);
                    assertEquals(key, k(i));
                    if (i < stable) {
                        seen[i]++;
                    }
                });
                for (int i = 0; i < stable; i++) {
                    if (seen[i] != 1) {
                        throw new AssertionError("stable key " + i + " seen " + seen[i] + " times");
                    }
                }
                passes++;
            }
        });
        runConcurrently(tasks);
        assertEquals(stable, m.size());
    }
}
