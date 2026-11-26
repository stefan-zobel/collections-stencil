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
 * Shared parameters and data for the benchmarks of maps from {@link String} keys to
 * {@code int} values, the typical "counter per key" map.
 * <p>
 * The keys come from {@link KeyDist#stringKey}. All strings are created during setup, and their
 * hash codes are computed there (a string caches its hash code), as for long-lived keys in
 * practice. The keys used for lookups and counting are <em>copies</em> of the map's keys: equal,
 * but not identical, like keys parsed from input. Every successful lookup therefore compares
 * the characters, not only the references.
 * <p>
 * Every implementation has its own concrete subclass with its own {@code @Benchmark} methods,
 * and JMH runs every parameter combination in a separate fork. Each call site therefore sees
 * exactly one map class.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code getHit}: {@value #OPS} lookups of present keys in random order (ns per lookup)</li>
 *   <li>{@code getMiss}: {@value #OPS} lookups of absent keys in random order (ns per lookup)</li>
 *   <li>{@code build}: builds a map of {@code size} entries from the default capacity, growth
 *       included, from a different key set in each invocation. JMH reports ns per map. The
 *       report divides this by {@code size}.</li>
 *   <li>{@code churn}: {@value #OPS} steps of "remove the oldest key, insert a new one", so the
 *       map keeps {@code size} entries while its key set slides cyclically through
 *       {@link #churnPool} (ns per step). The state carries over between invocations.</li>
 *   <li>{@code count}: {@value #OPS} increments by one of present keys whose ranks follow a
 *       Zipf-like distribution (density proportional to {@code 1/rank}): a few hot keys and a
 *       long tail, as when counting words or events per category (ns per increment). The JDK
 *       map uses {@code merge(key, 1, Integer::sum)}.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractObjectIntBench {

    /** Number of operations per invocation for the lookup/update benchmarks. */
    public static final int OPS = 1 << 16;

    /**
     * Sizes. Each is about 2/3 of a power of two, so tables with load factor 0.75 run near their
     * maximum fill (see {@link AbstractLongObjectBench#size}).
     */
    @Param({"1400", "180000", "1400000", "11000000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /**
     * Minimum total number of keys over all build key sets, so that the branch predictor cannot
     * learn the probe outcomes of small maps (see {@link AbstractLongObjectBench#buildKeySets}).
     */
    static final int BUILD_POOL_KEYS = 1 << 20;

    /** Population in insertion order; the value of {@code keys[i]} is {@code i}. */
    protected String[] keys;
    /** Key sets for {@code build}, used in rotation. Set 0 is {@link #keys}. */
    protected String[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} copies of present keys, uniformly sampled, in random order. */
    protected String[] hits;
    /** {@link #OPS} absent keys, in random order. */
    protected String[] misses;
    /** {@link #OPS} copies of present keys with Zipf-like ranks, in random order. */
    protected String[] counted;
    /**
     * Keys of the churn benchmark: the population followed by {@link #OPS} further keys. The map
     * holds the {@code size} keys before {@link #churnNext}, cyclically.
     */
    protected String[] churnPool;
    /** Index in {@link #churnPool} of the next key to insert. */
    protected int churnNext;
    /** Index in {@link #churnPool} of the next key to remove. */
    protected int churnOldest;

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (BUILD_POOL_KEYS + size - 1) / size);
        buildKeySets = new String[sets][size];
        for (int j = 0; j < sets; j++) {
            for (int i = 0; i < size; i++) {
                buildKeySets[j][i] = hashed(dist.stringKey(j * size + i));
            }
        }
        keys = buildKeySets[0];
        buildCursor = 0;
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        hits = new String[OPS];
        misses = new String[OPS];
        counted = new String[OPS];
        double logRange = Math.log(size + 1.0);
        for (int j = 0; j < OPS; j++) {
            hits[j] = copy(keys[rnd.nextInt(size)]);
            misses[j] = hashed(dist.stringKey(size + j));
            // log-uniform rank in [0, size): density proportional to 1 / (rank + 1)
            int rank = Math.min(size - 1, (int) Math.exp(rnd.nextDouble() * logRange) - 1);
            counted[j] = copy(keys[rank]);
        }
        // Shuffle the misses (see AbstractLongObjectBench) and the counted keys.
        shuffle(misses, rnd);
        shuffle(counted, rnd);
        churnPool = new String[size + OPS];
        System.arraycopy(keys, 0, churnPool, 0, size);
        System.arraycopy(misses, 0, churnPool, size, OPS);
        churnNext = size;
        churnOldest = 0;
        populate();
        buildCursor = 0;
    }

    /** {@code s} with its hash code computed (and cached in the string). */
    private static String hashed(String s) {
        s.hashCode();
        return s;
    }

    /** An equal, but not identical copy of {@code s}, with its hash code computed. */
    private static String copy(String s) {
        return hashed(new String(s));
    }

    private static void shuffle(String[] a, SplittableRandom rnd) {
        for (int j = a.length - 1; j > 0; j--) {
            int k = rnd.nextInt(j + 1);
            String t = a[j];
            a[j] = a[k];
            a[k] = t;
        }
    }

    /** The next key set for a build benchmark (rotation over {@link #buildKeySets}). */
    protected final String[] nextBuildKeys() {
        int c = buildCursor;
        buildCursor = c + 1 == buildKeySets.length ? 0 : c + 1;
        return buildKeySets[c];
    }

    /** Builds the map under test from {@link #keys}, mapping {@code keys[i]} to {@code i}. */
    protected abstract void populate();
}
