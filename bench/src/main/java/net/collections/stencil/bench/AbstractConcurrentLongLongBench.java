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

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks of thread-safe maps from {@code long} keys to {@code long} values (counters), run by
 * one or more threads at the same time (JMH {@code -t}; the interleaved runner sets it from
 * {@code --threads}).
 * <p>
 * Key data and the build key sets are as in {@link AbstractLongObjectBench}. The value stored
 * for the key with index {@code i} is {@code i + 1}, never {@code 0}, so that every adapter can
 * report an absent key as {@code 0}. Each implementation is a subclass that implements the map
 * operations; the benchmark methods are inherited, and JMH runs every implementation in its own
 * fork, so every call site still sees one map class.
 * <p>
 * Benchmarks (ns per operation and thread; the total throughput is {@code threads / score}):
 * <ul>
 *   <li>{@code getHit}, {@code getMiss}: every thread looks up {@value #OPS} present or absent
 *       keys, each thread from a different offset in the same random sequence.</li>
 *   <li>{@code mixed}: of 16 operations, 14 lookups of present keys, one insertion of a new key
 *       and one removal of the oldest key that this thread inserted, so the size of the map
 *       stays constant.</li>
 *   <li>{@code writeHeavy}: like {@code mixed}, with 2 lookups, one insertion and one removal of
 *       4 operations.</li>
 *   <li>{@code count}: every operation adds one to the value of a present key with Zipf-like
 *       rank (density proportional to {@code 1/rank}), so all threads keep counting the same
 *       few hot keys: the counter use case under contention.</li>
 *   <li>{@code countSpread}: like {@code count}, but on uniformly sampled present keys, so the
 *       threads rarely count the same key at the same time.</li>
 *   <li>{@code build}: {@link #workers} threads fill a new map from the default capacity with
 *       {@code size} distinct keys, each a contiguous part of the build key set. Runs with one
 *       JMH thread; JMH reports ns per map, the report divides it by {@code size}.</li>
 * </ul>
 *
 * @param <M> the map class
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractConcurrentLongLongBench<M> {

    /** Number of operations per invocation and thread. */
    public static final int OPS = 1 << 16;
    private static final int MASK = OPS - 1;

    /** Keys a thread inserts before it starts to remove its oldest one in {@code mixed}. */
    static final int THREAD_WINDOW = 1024;
    /** Distance between the key indices of two threads in {@code mixed} and {@code writeHeavy}. */
    private static final int THREAD_KEY_SPACE = 1 << 24;

    @Param({"1400", "180000", "1400000", "11000000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /** Number of threads that fill the map in {@code build}. */
    @Param({"1"})
    public int workers;

    /** The map of all benchmarks except {@code build}. */
    protected M map;

    /** Population in insertion order. */
    protected long[] keys;
    /** Key sets of {@code build}, used in rotation (see {@link AbstractLongObjectBench#buildKeySets}). */
    protected long[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} present keys, uniformly sampled, in random order. */
    protected long[] hits;
    /** {@link #OPS} absent keys, in random order. */
    protected long[] misses;
    /** {@link #OPS} present keys with Zipf-like ranks, in random order. */
    protected long[] zipf;

    private final AtomicInteger threadIds = new AtomicInteger();
    private ExecutorService pool;

    // ------------------------------------------------------------------
    // Operations of the implementation under test
    // ------------------------------------------------------------------

    /** A new, empty map with the default capacity. */
    protected abstract M create();

    /** The value of {@code key}, or {@code 0} if the key is absent. */
    protected abstract long get(M m, long key);

    protected abstract void put(M m, long key, long value);

    protected abstract void remove(M m, long key);

    /** Atomically adds one to the value of the present {@code key}; returns a value to consume. */
    protected abstract long addOne(M m, long key);

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    /** The value stored for the key with index {@code i}; never {@code 0}. */
    protected static long value(int i) {
        return i + 1L;
    }

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (AbstractLongObjectBench.BUILD_POOL_KEYS + size - 1) / size);
        buildKeySets = new long[sets][size];
        for (int j = 0; j < sets; j++) {
            for (int i = 0; i < size; i++) {
                buildKeySets[j][i] = dist.longKey(j * size + i);
            }
        }
        keys = buildKeySets[0];
        buildCursor = 0;
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        hits = new long[OPS];
        misses = new long[OPS];
        zipf = new long[OPS];
        double logRange = Math.log(size + 1.0);
        for (int j = 0; j < OPS; j++) {
            hits[j] = keys[rnd.nextInt(size)];
            misses[j] = dist.longKey(size + j);
            // log-uniform rank in [0, size): density proportional to 1 / (rank + 1)
            zipf[j] = keys[Math.min(size - 1, (int) Math.exp(rnd.nextDouble() * logRange) - 1)];
        }
        shuffle(misses, rnd);
        map = create();
        fill(map, keys, 0, size);
        if (workers > 1) {
            pool = Executors.newFixedThreadPool(workers - 1, r -> {
                Thread t = new Thread(r, "build-worker");
                t.setDaemon(true);
                return t;
            });
        }
    }

    @TearDown(Level.Trial)
    public final void tearDown() {
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    private static void shuffle(long[] a, SplittableRandom rnd) {
        for (int j = a.length - 1; j > 0; j--) {
            int k = rnd.nextInt(j + 1);
            long t = a[j];
            a[j] = a[k];
            a[k] = t;
        }
    }

    private void fill(M m, long[] ks, int from, int to) {
        for (int i = from; i < to; i++) {
            put(m, ks[i], value(i));
        }
    }

    /** Per-thread state: an offset into the key sequences and this thread's own keys. */
    @State(Scope.Thread)
    public static class ThreadState {
        int id = -1;
        int offset;
        /** Index of this thread's next new key in {@code mixed} and {@code writeHeavy}. */
        int next;
        /** Index of this thread's oldest key in the map. */
        int oldest;

        /** Assigns the thread an id on its first call. */
        void init(AbstractConcurrentLongLongBench<?> bench) {
            if (id < 0) {
                id = bench.threadIds.getAndIncrement();
                offset = (int) (id * 0x9E37_79B9L) & MASK;
                oldest = bench.size + OPS + id * THREAD_KEY_SPACE;
                next = oldest;
            }
        }
    }

    // ------------------------------------------------------------------
    // Benchmarks
    // ------------------------------------------------------------------

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit(ThreadState t) {
        t.init(this);
        M m = map;
        long[] ks = hits;
        int off = t.offset;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            if (get(m, ks[(off + j) & MASK]) != 0) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss(ThreadState t) {
        t.init(this);
        M m = map;
        long[] ks = misses;
        int off = t.offset;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            if (get(m, ks[(off + j) & MASK]) != 0) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int mixed(ThreadState t) {
        return updateMix(t, 15);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int writeHeavy(ThreadState t) {
        return updateMix(t, 3);
    }

    /** Of every {@code period + 1} operations: one insertion, one removal, the rest lookups. */
    private int updateMix(ThreadState t, int period) {
        t.init(this);
        M m = map;
        if (t.next == t.oldest) { // first call: insert this thread's first keys
            for (int j = 0; j < THREAD_WINDOW; j++, t.next++) {
                put(m, dist.longKey(t.next), value(t.next));
            }
        }
        long[] ks = hits;
        KeyDist d = dist;
        int off = t.offset;
        int next = t.next;
        int oldest = t.oldest;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            int phase = j & period;
            if (phase == 0) {
                put(m, d.longKey(next), value(next));
                next++;
            } else if (phase == 1) {
                remove(m, d.longKey(oldest));
                oldest++;
            } else if (get(m, ks[(off + j) & MASK]) != 0) {
                n++;
            }
        }
        t.next = next;
        t.oldest = oldest;
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count(ThreadState t) {
        t.init(this);
        M m = map;
        long[] ks = zipf;
        int off = t.offset;
        long n = 0;
        for (int j = 0; j < OPS; j++) {
            n += addOne(m, ks[(off + j) & MASK]);
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long countSpread(ThreadState t) {
        t.init(this);
        M m = map;
        long[] ks = hits;
        int off = t.offset;
        long n = 0;
        for (int j = 0; j < OPS; j++) {
            n += addOne(m, ks[(off + j) & MASK]);
        }
        return n;
    }

    @Benchmark
    public Object build(GcBeforeLargeBuild gc) throws Exception {
        M m = create();
        long[] ks = nextBuildKeys();
        int w = workers;
        if (w == 1) {
            fill(m, ks, 0, ks.length);
            return m;
        }
        int chunk = (ks.length + w - 1) / w;
        List<Future<?>> parts = new ArrayList<>(w - 1);
        for (int p = 1; p < w; p++) {
            int from = Math.min(ks.length, p * chunk);
            int to = Math.min(ks.length, from + chunk);
            parts.add(pool.submit(() -> fill(m, ks, from, to)));
        }
        fill(m, ks, 0, Math.min(ks.length, chunk));
        for (Future<?> f : parts) {
            f.get();
        }
        return m;
    }

    /** The next key set for {@code build} (rotation over {@link #buildKeySets}). */
    protected final long[] nextBuildKeys() {
        int c = buildCursor;
        buildCursor = c + 1 == buildKeySets.length ? 0 : c + 1;
        return buildKeySets[c];
    }

    /** The map of the lookup benchmarks, for footprint measurements and tests. */
    public final M map() {
        return map;
    }
}
