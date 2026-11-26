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

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Shared parameters and data for the long-to-long map benchmarks (same design as
 * {@link AbstractIntIntBench}, with 64-bit keys and values).
 * <p>
 * Every implementation has its own concrete subclass with its own {@code @Benchmark}
 * methods, and JMH runs every parameter combination in a separate fork. Each call
 * site therefore sees exactly one map class, so the results contain no adapter
 * interface and no megamorphic dispatch.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code getHit}: {@value #OPS} lookups of present keys in random order (ns per lookup)</li>
 *   <li>{@code getMiss}: {@value #OPS} lookups of absent keys in random order (ns per lookup)</li>
 *   <li>{@code addTo}: {@value #OPS} increments of present keys (ns per update)</li>
 *   <li>{@code build}: builds a map of {@code size} entries from the default capacity,
 *       growth included, from a different key set in each invocation (see
 *       {@link #buildKeySets}). JMH reports ns per map. The report divides this by {@code size}.</li>
 *   <li>{@code buildPresized}: like {@code build}, but the map is created for {@code size}
 *       entries, so no growth occurs (not in the default set of the interleaved runner).</li>
 *   <li>{@code churn}: {@value #OPS} steps of "remove the oldest key, insert a new key", so the
 *       map keeps {@code size} entries while its key set slides through the key sequence
 *       (ns per step). The state carries over between invocations, so the benchmark measures
 *       the steady state, including any cleanup cost of deleted slots (tombstones).</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractLongLongBench {

    /** Number of operations per invocation for the lookup/update benchmarks. */
    public static final int OPS = 1 << 16;

    /**
     * Map sizes. Each is about 2/3 of a power of two, so maps with load factor 0.75 run
     * near their maximum fill, while maps with load factor 0.5 already use the next
     * larger table (twice the memory). Round sizes such as 10^k mostly land at a fill
     * of 0.4-0.5, where the load factor makes no difference at all.
     */
    @Param({"1400", "180000", "1400000", "11000000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /**
     * Minimum total number of keys over all build key sets. Far more branch outcomes than a
     * branch predictor can memorize.
     */
    static final int BUILD_POOL_KEYS = 1 << 20;

    /** Population in insertion order. */
    protected long[] keys;
    /**
     * Key sets for {@code build} and {@code buildPresized}, used in rotation. Set 0 is
     * {@link #keys}; set {@code j} holds the keys with indices {@code [j * size, (j + 1) * size)}.
     * <p>
     * Building the <em>same</em> map from the <em>same</em> keys in every invocation would let the
     * branch predictor learn the complete sequence of probe outcomes for small maps. Maps
     * with a deterministic hash function profit from that; maps with a per-instance hash
     * seed cannot, because every instance probes differently. Measured on a Ryzen 5 5600H
     * with 1400 random keys: about 3 ns per insert with a fixed seed versus about 8 ns with
     * a random seed, for otherwise identical code. Rotating through enough distinct key sets
     * removes this artifact.
     */
    protected long[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} present keys, uniformly sampled, in random order. */
    protected long[] hits;
    /** {@link #OPS} absent keys, in random order. */
    protected long[] misses;
    /**
     * Churn cursor: the map holds the keys with indices {@code [churnNext - size, churnNext)}.
     * {@code int} arithmetic is sufficient: a fork performs far fewer than 2^31 steps.
     */
    protected int churnNext;

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (BUILD_POOL_KEYS + size - 1) / size);
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
        for (int j = 0; j < OPS; j++) {
            hits[j] = keys[rnd.nextInt(size)];
            misses[j] = dist.longKey(size + j);
        }
        // Shuffle the misses: in ascending order, maps that keep sequential keys close
        // together would turn the lookups into a sequential scan that the hardware
        // prefetcher hides completely.
        for (int j = OPS - 1; j > 0; j--) {
            int k = rnd.nextInt(j + 1);
            long t = misses[j];
            misses[j] = misses[k];
            misses[k] = t;
        }
        churnNext = size;
        populate();
        buildCursor = 0;
    }

    /** The next key set for a build benchmark (rotation over {@link #buildKeySets}). */
    protected final long[] nextBuildKeys() {
        int c = buildCursor;
        buildCursor = c + 1 == buildKeySets.length ? 0 : c + 1;
        return buildKeySets[c];
    }

    /** Builds the map under test from {@link #keys} (value = key). */
    protected abstract void populate();
}
