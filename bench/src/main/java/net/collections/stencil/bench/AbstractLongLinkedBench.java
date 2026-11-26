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
 * Shared parameters and data for the benchmarks of insertion-ordered maps from long keys to
 * object values.
 * <p>
 * Keys: {@code RANDOM} keys are spread over the whole {@code long} range, {@code SEQUENTIAL}
 * keys are {@code 1, 2, 3, ...}. Values come from a small pool of shared objects, as in
 * {@link AbstractLongObjectBench}.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code getHit}, {@code getMiss}: {@value #OPS} lookups of present or absent keys in
 *       random order (ns per lookup)</li>
 *   <li>{@code build}: builds a map of {@code size} entries from the default capacity, from a
 *       different key set in each invocation. The report divides the time by {@code size}.</li>
 *   <li>{@code iterate}: visits all entries in insertion order. The report divides the time by
 *       {@code size}.</li>
 *   <li>{@code churn}: {@value #OPS} steps of "remove a random present key, insert a new one"
 *       (ns per step). Removals hit entries of any age.</li>
 *   <li>{@code fifo}: {@value #OPS} steps of "remove the oldest entry, insert a new key" (ns per
 *       step): a bounded cache with first-in, first-out eviction.</li>
 *   <li>{@code touch}: {@value #OPS} steps of "store a value for a random present key and make
 *       its entry the newest" (ns per step): the write path of a least-recently-used cache.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractLongLinkedBench {

    /** Pool of shared value objects; its length is a power of two. */
    private static final Object[] VALUES = new Object[1024];

    static {
        for (int i = 0; i < VALUES.length; i++) {
            VALUES[i] = Integer.valueOf(i);
        }
    }

    /** The value stored for the key with index or key bits {@code i}. */
    protected static Object value(long i) {
        return VALUES[(int) i & (VALUES.length - 1)];
    }

    /** Number of operations per invocation for all benchmarks except {@code build} and {@code iterate}. */
    public static final int OPS = 1 << 16;

    @Param({"1400", "180000", "1400000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /** Minimum total number of keys over all build key sets (see {@link AbstractLongObjectBench}). */
    static final int BUILD_POOL_KEYS = 1 << 20;

    /** Population in insertion order. */
    protected long[] keys;
    /** Key sets for {@code build}, used in rotation; set 0 is {@link #keys}. */
    protected long[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} present keys, uniformly sampled, in random order. */
    protected long[] hits;
    /** {@link #OPS} absent keys, in random order. */
    protected long[] misses;
    /** The keys that {@code churn} has in the map; it replaces random elements. */
    protected long[] present;
    /** {@link #OPS} random indices into {@link #present}. */
    protected int[] victims;
    /** Index of the next new key for {@code churn} and {@code fifo}; see {@link #key}. */
    protected int nextNew;
    /** Sink for the consumers of the iteration benchmark. */
    protected long sink;

    /** The {@code i}-th key of the population; {@code i >= size} yields keys that are absent at first. */
    protected final long key(int i) {
        return dist == KeyDist.RANDOM ? KeyDist.fmix64(i + 1) : i + 1L;
    }

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (BUILD_POOL_KEYS + size - 1) / size);
        buildKeySets = new long[sets][size];
        for (int j = 0; j < sets; j++) {
            for (int i = 0; i < size; i++) {
                buildKeySets[j][i] = key(j * size + i);
            }
        }
        keys = buildKeySets[0];
        present = keys.clone();
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        hits = new long[OPS];
        misses = new long[OPS];
        victims = new int[OPS];
        for (int j = 0; j < OPS; j++) {
            hits[j] = keys[rnd.nextInt(size)];
            misses[j] = -key(size + j); // never a key of any build key set, nor a churn key
            victims[j] = rnd.nextInt(size);
        }
        nextNew = sets * size;
        buildCursor = 0;
        populate();
        buildCursor = 0;
    }

    /** The next key set for a build benchmark (rotation over {@link #buildKeySets}). */
    protected final long[] nextBuildKeys() {
        int c = buildCursor;
        buildCursor = c + 1 == buildKeySets.length ? 0 : c + 1;
        return buildKeySets[c];
    }

    /** Builds the map under test from {@link #keys}. */
    protected abstract void populate();
}
