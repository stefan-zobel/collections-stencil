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
 * Benchmarks of thread-safe maps from {@code int} keys to object values, run by one or more
 * threads at the same time (JMH {@code -t}; the interleaved runner sets it from
 * {@code --threads}).
 * <p>
 * Key data, values, and the build key sets are as in {@link AbstractIntObjectBench}. Each
 * implementation is a subclass that implements the map operations; the benchmark methods are
 * inherited. JMH runs every implementation in its own fork, so every call site still sees one
 * map class.
 * <p>
 * Benchmarks (ns per operation and thread; the total throughput is
 * {@code threads / score}):
 * <ul>
 *   <li>{@code getHit}, {@code getMiss}: every thread looks up {@value #OPS} present or
 *       absent keys, each thread from a different offset in the same random sequence.</li>
 *   <li>{@code mixed}: of 16 operations, 14 lookups of present keys, one insertion of a new
 *       key and one removal of the oldest key that this thread inserted. Every thread inserts
 *       and removes its own keys, so the size of the map stays constant.</li>
 *   <li>{@code writeHeavy}: like {@code mixed}, with 2 lookups, one insertion and one removal
 *       of 4 operations.</li>
 *   <li>{@code computeIfAbsent}: a cache. Every operation calls {@code computeIfAbsent} for a
 *       key with Zipf-like rank (density proportional to {@code 1/rank}), and every 16th
 *       operation also removes a key with the same rank distribution, so popular keys are
 *       present most of the time and the size of the map stays stable.</li>
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
public abstract class AbstractConcurrentIntObjectBench<M> {

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
    protected int[] keys;
    /** Key sets of {@code build}, used in rotation (see {@link AbstractIntObjectBench#buildKeySets}). */
    protected int[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} present keys, uniformly sampled, in random order. */
    protected int[] hits;
    /** {@link #OPS} absent keys, in random order. */
    protected int[] misses;
    /** {@link #OPS} present keys with Zipf-like ranks, in random order. */
    protected int[] zipf;
    /** Like {@link #zipf}, independently shuffled: the keys that {@code computeIfAbsent} removes. */
    protected int[] zipfEvict;

    private final AtomicInteger threadIds = new AtomicInteger();
    private ExecutorService pool;

    // ------------------------------------------------------------------
    // Operations of the implementation under test
    // ------------------------------------------------------------------

    /** A new, empty map with the default capacity. */
    protected abstract M create();

    /** The value of {@code key}, or {@code null}. */
    protected abstract Object get(M m, int key);

    protected abstract void put(M m, int key, Object value);

    protected abstract void remove(M m, int key);

    /** {@code computeIfAbsent} with {@link #value} as the mapping function. */
    protected abstract Object computeIfAbsent(M m, int key);

    // ------------------------------------------------------------------
    // Setup
    // ------------------------------------------------------------------

    /**
     * Pool of shared value objects; its length is a power of two. {@link SharedValue} rather than
     * {@link Integer}, so that the footprint can count boxed {@code Integer} keys.
     */
    private static final Object[] VALUES = new Object[1024];

    static {
        for (int i = 0; i < VALUES.length; i++) {
            VALUES[i] = new SharedValue(i);
        }
    }

    /** The value stored for the key with index or key bits {@code i}. */
    protected static Object value(long i) {
        return VALUES[(int) i & (VALUES.length - 1)];
    }

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (AbstractIntObjectBench.BUILD_POOL_KEYS + size - 1) / size);
        buildKeySets = new int[sets][size];
        for (int j = 0; j < sets; j++) {
            for (int i = 0; i < size; i++) {
                buildKeySets[j][i] = dist.key(j * size + i);
            }
        }
        keys = buildKeySets[0];
        buildCursor = 0;
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        hits = new int[OPS];
        misses = new int[OPS];
        zipf = new int[OPS];
        zipfEvict = new int[OPS];
        double logRange = Math.log(size + 1.0);
        for (int j = 0; j < OPS; j++) {
            hits[j] = keys[rnd.nextInt(size)];
            misses[j] = dist.key(size + j);
            // log-uniform rank in [0, size): density proportional to 1 / (rank + 1)
            zipf[j] = keys[Math.min(size - 1, (int) Math.exp(rnd.nextDouble() * logRange) - 1)];
        }
        shuffle(misses, rnd);
        System.arraycopy(zipf, 0, zipfEvict, 0, OPS);
        shuffle(zipfEvict, rnd);
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

    private static void shuffle(int[] a, SplittableRandom rnd) {
        for (int j = a.length - 1; j > 0; j--) {
            int k = rnd.nextInt(j + 1);
            int t = a[j];
            a[j] = a[k];
            a[k] = t;
        }
    }

    private void fill(M m, int[] ks, int from, int to) {
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
        void init(AbstractConcurrentIntObjectBench<?> bench) {
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
        int[] ks = hits;
        int off = t.offset;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            if (get(m, ks[(off + j) & MASK]) != null) {
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
        int[] ks = misses;
        int off = t.offset;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            if (get(m, ks[(off + j) & MASK]) != null) {
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
                put(m, dist.key(t.next), value(t.next));
            }
        }
        int[] ks = hits;
        KeyDist d = dist;
        int off = t.offset;
        int next = t.next;
        int oldest = t.oldest;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            int phase = j & period;
            if (phase == 0) {
                put(m, d.key(next), value(next));
                next++;
            } else if (phase == 1) {
                remove(m, d.key(oldest));
                oldest++;
            } else if (get(m, ks[(off + j) & MASK]) != null) {
                n++;
            }
        }
        t.next = next;
        t.oldest = oldest;
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int computeIfAbsent(ThreadState t) {
        t.init(this);
        M m = map;
        int[] ks = zipf;
        int[] evict = zipfEvict;
        int off = t.offset;
        int n = 0;
        for (int j = 0; j < OPS; j++) {
            int i = (off + j) & MASK;
            if (computeIfAbsent(m, ks[i]) != null) {
                n++;
            }
            if ((j & 15) == 0) {
                remove(m, evict[i]);
            }
        }
        return n;
    }

    @Benchmark
    public Object build(GcBeforeLargeBuild gc) throws Exception {
        M m = create();
        int[] ks = nextBuildKeys();
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
    protected final int[] nextBuildKeys() {
        int c = buildCursor;
        buildCursor = c + 1 == buildKeySets.length ? 0 : c + 1;
        return buildKeySets[c];
    }

    /** The map of the lookup benchmarks, for footprint measurements and tests. */
    public final M map() {
        return map;
    }
}
