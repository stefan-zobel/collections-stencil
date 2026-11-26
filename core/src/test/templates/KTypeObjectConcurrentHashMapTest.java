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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KTypeObjectConcurrentHashMapTest {

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

    /** A value that names its key, so that a lookup can tell whether it got the right entry. */
    private record Tagged(long key, int generation) {
    }

    // ------------------------------------------------------------------
    // Single-threaded contract
    // ------------------------------------------------------------------

    @Test
    void basicOperations() {
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>();
        assertTrue(m.isEmpty());
        assertNull(m.put(k(1), "a"));
        assertEquals("a", m.put(k(1), "b"));
        assertNull(m.put(k(-2), "c"));
        assertEquals(2, m.size());
        assertEquals("b", m.get(k(1)));
        assertEquals("c", m.get(k(-2)));
        assertNull(m.get(k(2)));
        assertEquals("x", m.getOrDefault(k(2), "x"));
        assertEquals("b", m.putIfAbsent(k(1), "z"));
        assertNull(m.putIfAbsent(k(3), "d"));
        assertEquals("d", m.get(k(3)));
        assertNull(m.replace(k(4), "e"));
        assertFalse(m.containsKey(k(4)));
        assertEquals("d", m.replace(k(3), "e"));
        assertFalse(m.replace(k(3), "d", "f"));
        assertTrue(m.replace(k(3), "e", "f"));
        assertEquals("f", m.get(k(3)));
        assertFalse(m.remove(k(3), "e"));
        assertFalse(m.remove(k(3), null));
        assertTrue(m.remove(k(3), "f"));
        assertEquals("b", m.remove(k(1)));
        assertNull(m.remove(k(1)));
        assertFalse(m.containsKey(k(1)));
        assertEquals(1, m.size());
        m.clear();
        assertTrue(m.isEmpty());
        assertNull(m.get(k(-2)));
    }

    @Test
    void zeroKey() {
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 1);
        assertFalse(m.containsKey(KZero));
        assertNull(m.put(KZero, "zero"));
        assertTrue(m.containsKey(KZero));
        assertEquals("zero", m.get(KZero));
        assertEquals(1, m.size());
        assertEquals("{0=zero}", m.toString());
        assertEquals(Map.of(KZero, "zero").hashCode(), m.hashCode());
        assertArrayEquals(new KType[] {KZero}, m.keysToArray());
        assertEquals("zero!", m.merge(KZero, "!", String::concat));
        assertEquals("zero!", m.computeIfAbsent(KZero, key -> "other"));
        m.clear();
        assertTrue(m.isEmpty());
        assertNull(m.compute(KZero, (key, v) -> null));
        assertEquals("z", m.compute(KZero, (key, v) -> "z"));
        assertEquals("z", m.remove(KZero));
        assertTrue(m.isEmpty());
    }

    @Test
    void nullValuesAreRejected() {
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>();
        m.put(k(1), "a");
        assertThrows(NullPointerException.class, () -> m.put(k(2), null));
        assertThrows(NullPointerException.class, () -> m.putIfAbsent(k(2), null));
        assertThrows(NullPointerException.class, () -> m.replace(k(1), null));
        assertThrows(NullPointerException.class, () -> m.replace(k(1), "a", null));
        assertThrows(NullPointerException.class, () -> m.replace(k(1), null, "b"));
        assertThrows(NullPointerException.class, () -> m.merge(k(1), null, String::concat));
        assertThrows(NullPointerException.class, () -> m.computeIfAbsent(k(2), null));
        Map<KBoxed, String> withNull = new HashMap<>();
        withNull.put(k(3), null);
        assertThrows(NullPointerException.class, () -> m.putAll(withNull));
        assertEquals("a", m.get(k(1)));
        // null results of functions mean "no mapping"
        assertNull(m.computeIfAbsent(k(5), key -> null));
        assertFalse(m.containsKey(k(5)));
        assertNull(m.computeIfPresent(k(1), (key, v) -> null));
        assertFalse(m.containsKey(k(1)));
        m.put(k(1), "a");
        assertNull(m.merge(k(1), "b", (a, b) -> null));
        assertFalse(m.containsKey(k(1)));
    }

    @Test
    void atomicOperationsFollowConcurrentMapSemantics() {
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>();
        AtomicInteger calls = new AtomicInteger();
        assertEquals("v7", m.computeIfAbsent(k(7), key -> "v" + calls.incrementAndGet() * 7));
        assertEquals("v7", m.computeIfAbsent(k(7), key -> "v" + calls.incrementAndGet()));
        assertEquals(1, calls.get());
        assertNull(m.computeIfPresent(k(8), (key, v) -> "never"));
        assertEquals("v7+", m.computeIfPresent(k(7), (key, v) -> v + "+"));
        assertEquals("new", m.compute(k(9), (key, v) -> v == null ? "new" : v + "?"));
        assertEquals("new?", m.compute(k(9), (key, v) -> v == null ? "new" : v + "?"));
        assertEquals("a", m.merge(k(10), "a", String::concat));
        assertEquals("ab", m.merge(k(10), "b", String::concat));
        assertEquals(Map.of(k(7), "v7+", k(9), "new?", k(10), "ab"), m.toMap());
    }

    @Test
    void recursiveUpdatesThrow() {
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>(16, 0.75f, 1);
        m.put(k(1), "a");
        assertThrows(IllegalStateException.class, () -> m.computeIfAbsent(k(2), key -> m.put(k(3), "x")));
        assertThrows(IllegalStateException.class, () -> m.compute(k(1), (key, v) -> m.remove(k(1))));
        assertThrows(IllegalStateException.class, () -> m.merge(k(1), "b", (a, b) -> {
            m.clear();
            return a;
        }));
        // reads inside a function are fine
        assertEquals("aa", m.compute(k(1), (key, v) -> v + m.get(k(1))));
        assertEquals(Map.of(k(1), "aa"), m.toMap());
        // the lock was released after each failure
        assertNull(m.put(k(2), "b"));
    }

    @Test
    void weakHashCodes() {
        for (int level : new int[] {1, 4, 64}) {
            KTypeObjectConcurrentHashMap<Integer> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, level);
            for (int i = 0; i < 10_000; i++) {
                m.put(k(i), i);
            }
            for (int i = 1; i < 2048; i++) {
                m.put(k(i << 20), -i);
            }
            assertEquals(10_000 + 2047, m.size());
            for (int i = 0; i < 10_000; i++) {
                assertEquals(i, m.get(k(i)));
            }
            for (int i = 1; i < 2048; i++) {
                assertEquals(-i, m.get(k(i << 20)));
            }
        }
    }

    @Test
    void extremeKeys() {
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>();
        KType[] keys = {KBoxed.MIN_VALUE, KBoxed.MAX_VALUE, -1, 1, KZero};
        for (KType key : keys) {
            m.put(key, "v" + key);
        }
        for (KType key : keys) {
            assertEquals("v" + key, m.get(key));
        }
        assertEquals(keys.length, m.size());
    }

    @Test
    void equalsHashCodeAndConversions() {
        KTypeObjectConcurrentHashMap<String> a = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 2);
        KTypeObjectConcurrentHashMap<String> b = new KTypeObjectConcurrentHashMap<>(1000, 0.5f, 16);
        Map<KBoxed, String> ref = new HashMap<>();
        for (int i = -50; i < 50; i++) {
            a.put(k(i), "v" + i);
            b.put(k(-i - 1 + 0), "v" + (-i - 1));
            ref.put(k(i), "v" + i);
        }
        assertEquals(a, b);
        assertEquals(b, a);
        assertEquals(ref.hashCode(), a.hashCode());
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(ref, a.toMap());
        b.put(k(0), "other");
        assertNotEquals(a, b);
        assertNotEquals(a, ref);

        KType[] keys = a.keysToArray();
        String[] values = a.valuesToArray(String[]::new);
        assertEquals(100, keys.length);
        for (int i = 0; i < keys.length; i++) {
            assertEquals(ref.get(keys[i]), values[i]);
        }

        KTypeObjectHashMap<String> plain = new KTypeObjectHashMap<>();
        ref.forEach(plain::put);
        assertEquals(a, new KTypeObjectConcurrentHashMap<>(plain));
        assertEquals(a, new KTypeObjectConcurrentHashMap<>(a));
        KTypeObjectConcurrentHashMap<String> c = new KTypeObjectConcurrentHashMap<>();
        c.putAll(ref);
        assertEquals(a, c);
        String s = a.toString();
        assertTrue(s.startsWith("{") && s.endsWith("}") && s.contains(k(-50) + "=v-50"), s);
    }

    @Test
    void invalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectConcurrentHashMap<>(-1));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectConcurrentHashMap<>(16, 0f, 4));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectConcurrentHashMap<>(16, 1f, 4));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectConcurrentHashMap<>(16, Float.NaN, 4));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectConcurrentHashMap<>(16, 0.75f, 0));
        assertThrows(IllegalArgumentException.class, () -> new KTypeObjectConcurrentHashMap<>(16, 0.75f,
                KTypeObjectConcurrentHashMap.MAX_CONCURRENCY_LEVEL + 1));
        new KTypeObjectConcurrentHashMap<>(0, 0.75f, KTypeObjectConcurrentHashMap.MAX_CONCURRENCY_LEVEL).put(k(1), 1);
    }

    @Test
    void ensureCapacityAndClearKeepContentRight() {
        KTypeObjectConcurrentHashMap<Integer> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 8);
        for (int i = 0; i < 1000; i++) {
            m.put(k(i), i);
        }
        m.ensureCapacity(100_000);
        for (int i = 0; i < 1000; i++) {
            assertEquals(i, m.get(k(i)));
        }
        m.clear();
        assertTrue(m.isEmpty());
        assertEquals(0, m.size());
        for (int i = 0; i < 1000; i++) {
            assertNull(m.get(k(i)));
        }
        m.put(k(5), 5);
        assertEquals(Map.of(k(5), 5), m.toMap());
    }

    @Test
    void removeIfRemovesMatchesOnly() {
        KTypeObjectConcurrentHashMap<Integer> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 4);
        Map<KBoxed, Integer> ref = new HashMap<>();
        for (int i = -500; i < 500; i++) {
            m.put(k(i), i);
            ref.put(k(i), i);
        }
        assertTrue(m.removeIf((key, v) -> v % 3 == 0));
        ref.entrySet().removeIf(e -> e.getValue() % 3 == 0);
        assertEquals(ref, m.toMap());
        assertFalse(m.removeIf((key, v) -> v % 3 == 0));
        // the predicate may update the map; a replaced value is not removed
        assertTrue(m.removeIf((key, v) -> {
            if (v == 1) {
                m.put(key, 1_000);
            }
            return v == 1 || v == 2;
        }));
        assertEquals(1_000, m.get(k(1)));
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
        KTypeObjectConcurrentHashMap<String> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, level);
        ConcurrentHashMap<KBoxed, String> ref = new ConcurrentHashMap<>();
        for (int n = 0; n < ops; n++) {
            KType key = k(rnd.nextInt(keyRange) - keyRange / 4);
            String value = "v" + rnd.nextInt(8);
            switch (rnd.nextInt(12)) {
                case 0, 1 -> assertEquals(ref.put(key, value), m.put(key, value));
                case 2 -> assertEquals(ref.putIfAbsent(key, value), m.putIfAbsent(key, value));
                case 3 -> assertEquals(ref.replace(key, value), m.replace(key, value));
                case 4 -> assertEquals(ref.replace(key, "v1", value), m.replace(key, "v1", value));
                case 5 -> assertEquals(ref.remove(key), m.remove(key));
                case 6 -> assertEquals(ref.remove(key, value), m.remove(key, value));
                case 7 -> assertEquals(ref.computeIfAbsent(key, x -> value), m.computeIfAbsent(key, x -> value));
                case 8 -> assertEquals(ref.computeIfPresent(key, (x, v) -> v.length() > 3 ? null : v + "+"),
                        m.computeIfPresent(key, (x, v) -> v.length() > 3 ? null : v + "+"));
                case 9 -> assertEquals(ref.compute(key, (x, v) -> v == null ? value : v.length() > 3 ? null : v + "*"),
                        m.compute(key, (x, v) -> v == null ? value : v.length() > 3 ? null : v + "*"));
                case 10 -> assertEquals(ref.merge(key, value, (a, b) -> a.length() > 3 ? null : a + b),
                        m.merge(key, value, (a, b) -> a.length() > 3 ? null : a + b));
                default -> {
                    assertEquals(ref.get(key), m.get(key));
                    assertEquals(ref.containsKey(key), m.containsKey(key));
                }
            }
            if (n % 997 == 0) {
                assertEquals(ref.size(), m.size());
                assertEquals(ref, m.toMap());
            }
            if (n == ops / 2) {
                m.removeIf((x, v) -> v.length() > 2);
                ref.values().removeIf(v -> v.length() > 2);
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

    @ParameterizedTest
    @CsvSource({"1", "4"})
    void readersNeverSeeWrongEntries(int level) throws InterruptedException {
        final int writers = 4;
        final int readers = 4;
        final int keys = 20_000;
        final int rounds = 6;
        KTypeObjectConcurrentHashMap<Tagged> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, level);
        AtomicBoolean done = new AtomicBoolean();
        AtomicInteger writersLeft = new AtomicInteger(writers);
        List<Runnable> tasks = new ArrayList<>();
        for (int w = 0; w < writers; w++) {
            final int owner = w;
            tasks.add(() -> {
                for (int round = 0; round < rounds; round++) {
                    for (int i = owner; i < keys; i += writers) {
                        m.put(k(i), new Tagged(k(i), round));
                    }
                    for (int i = owner; i < keys; i += writers) {
                        if ((i / writers) % 3 != round % 3) {
                            assertEquals(k(i), m.remove(k(i)).key());
                        }
                    }
                }
                for (int i = owner; i < keys; i += writers) {
                    m.put(k(i), new Tagged(k(i), rounds));
                }
                if (writersLeft.decrementAndGet() == 0) {
                    done.set(true);
                }
            });
        }
        for (int r = 0; r < readers; r++) {
            final long seed = r;
            tasks.add(() -> {
                SplittableRandom rnd = new SplittableRandom(seed);
                while (!done.get()) {
                    KType key = k(rnd.nextInt(keys));
                    Tagged t = m.get(key);
                    if (t != null && t.key() != key) {
                        throw new AssertionError("key " + key + " returned the value of " + t.key());
                    }
                }
            });
        }
        runConcurrently(tasks);
        assertEquals(keys, m.size());
        for (int i = 0; i < keys; i++) {
            assertEquals(new Tagged(k(i), rounds), m.get(k(i)));
        }
    }

    @Test
    void atomicCountersAreExact() throws InterruptedException {
        final int threads = 8;
        final int increments = 20_000;
        final int hotKeys = 16;
        KTypeObjectConcurrentHashMap<Long> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 4);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            tasks.add(() -> {
                for (int i = 0; i < increments; i++) {
                    KType key = k((i + id) % hotKeys);
                    if ((i & 1) == 0) {
                        m.merge(key, 1L, Long::sum);
                    } else {
                        m.compute(key, (x, v) -> v == null ? 1L : v + 1);
                    }
                }
            });
        }
        runConcurrently(tasks);
        long total = 0;
        for (int i = 0; i < hotKeys; i++) {
            total += m.get(k(i));
        }
        assertEquals((long) threads * increments, total);
    }

    @Test
    void computeIfAbsentCallsTheFunctionOncePerKey() throws InterruptedException {
        final int threads = 8;
        final int keys = 20_000;
        AtomicIntegerArray calls = new AtomicIntegerArray(keys);
        KTypeObjectConcurrentHashMap<Integer> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 8);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final long seed = t;
            tasks.add(() -> {
                int[] order = new SplittableRandom(seed).ints(0, keys).limit(3L * keys).toArray();
                for (int i : order) {
                    Integer v = m.computeIfAbsent(k(i), x -> {
                        calls.incrementAndGet(i);
                        return i;
                    });
                    assertEquals(i, v);
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
        KTypeObjectConcurrentHashMap<Integer> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, level);
        List<Runnable> tasks = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            tasks.add(() -> {
                for (int i = base; i < base + perThread; i++) {
                    assertNull(m.putIfAbsent(k(i), i));
                }
            });
        }
        runConcurrently(tasks);
        assertEquals(threads * perThread, m.size());
        for (int i = 0; i < threads * perThread; i++) {
            assertEquals(i, m.get(k(i)));
        }
    }

    @Test
    void forEachSeesStableEntriesExactlyOnce() throws InterruptedException {
        final int stable = 2_000;
        KTypeObjectConcurrentHashMap<Integer> m = new KTypeObjectConcurrentHashMap<>(0, 0.75f, 8);
        for (int i = 0; i < stable; i++) {
            m.put(k(i), i);
        }
        AtomicBoolean done = new AtomicBoolean();
        List<Runnable> tasks = new ArrayList<>();
        for (int w = 0; w < 3; w++) {
            final int base = stable + w * 100_000;
            tasks.add(() -> {
                for (int round = 0; round < 20; round++) {
                    for (int i = base; i < base + 5_000; i++) {
                        m.put(k(i), i);
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
                m.forEach((key, v) -> {
                    assertEquals(key, k(v));
                    if (v < stable) {
                        seen[v]++;
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
        assertSame(Integer.valueOf(7), m.get(k(7)));
    }
}
