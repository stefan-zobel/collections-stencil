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

import java.util.Arrays;
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
 * Shared parameters and data for the benchmarks of sorted maps from long keys to object values.
 * <p>
 * Keys: {@code RANDOM} keys are spread over the whole {@code long} range in random order;
 * {@code SEQUENTIAL} keys are the even numbers {@code 0, 2, 4, ...} in ascending order, so that
 * the odd numbers between them are absent keys inside the key range (a tree finds absent keys
 * beyond the last key on its rightmost path, which stays in the cache). Values come from a
 * small pool of shared objects, as in {@link AbstractLongObjectBench}. The benchmarks of sorted
 * maps from long keys to long values ({@code longlongsorted}) share this class: their lookups
 * return the sum of the values found, and their scans add up keys and values. So do the
 * benchmarks of sorted sets of long elements ({@code longsetsorted}): their {@code getHit} and
 * {@code getMiss} call {@code contains} and return the number of elements found, and their scans
 * add up the elements.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code getHit}, {@code getMiss}: {@value #OPS} lookups of present or absent keys in
 *       random order (ns per lookup)</li>
 *   <li>{@code floor}: {@value #OPS} lookups of the greatest key {@code <=} an absent key
 *       (ns per lookup)</li>
 *   <li>{@code build}: builds a map of {@code size} entries, in random key order or (for
 *       {@code SEQUENTIAL}) in ascending order, from a different key set in each invocation. The
 *       report divides the time by {@code size}.</li>
 *   <li>{@code churn}: {@value #OPS} steps of "remove the oldest key, insert a new one" (ns per
 *       step). With {@code SEQUENTIAL} keys this is a sliding window: removal at the low end,
 *       insertion at the high end.</li>
 *   <li>{@code iterate}: visits all entries in key order. The report divides the time by
 *       {@code size}.</li>
 *   <li>{@code rangeScan}: {@value #RANGE_SCANS} scans of {@value #RANGE_LEN} consecutive
 *       entries from random start keys (ns per visited entry, the search for the start
 *       included).</li>
 *   <li>{@code removeRange}: {@value #REMOVE_ROUNDS} rounds of removing {@value #RANGE_LEN}
 *       consecutive entries as one range and inserting them again (ns per entry).</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractLongSortedBench {

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

    /**
     * The value stored for the key with index {@code i} in the suite with {@code long} values
     * ({@code longlongsorted}): never {@code 0}, and outside the range of the {@link Long} cache,
     * so that a boxed map holds one value object per entry.
     */
    protected static long longValue(long i) {
        return i + 1024;
    }

    /** Number of operations per invocation for the lookup and churn benchmarks. */
    public static final int OPS = 1 << 16;
    /** Number of range scans per invocation. */
    public static final int RANGE_SCANS = 1 << 10;
    /** Number of entries per range scan and per removed range. */
    public static final int RANGE_LEN = 64;
    /** Number of range removals per invocation. */
    public static final int REMOVE_ROUNDS = 16;

    @Param({"1400", "180000", "1400000", "11000000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /** Minimum total number of keys over all build key sets (see {@link AbstractLongObjectBench}). */
    static final int BUILD_POOL_KEYS = 1 << 20;

    /** Population in insertion order. */
    protected long[] keys;
    /** The population in ascending order. */
    protected long[] sorted;
    /** Key sets for {@code build}, used in rotation; set 0 is {@link #keys}. */
    protected long[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} present keys, uniformly sampled, in random order. */
    protected long[] hits;
    /** {@link #OPS} absent keys between and around the present ones, in random order. */
    protected long[] misses;
    /** Indices into {@link #sorted} where range scans and removals start; each leaves room for {@link #RANGE_LEN} entries. */
    protected int[] rangeStarts;
    private int rangeCursor;
    /** Churn cursor: the map holds the keys with indices {@code [churnNext - size, churnNext)}. */
    protected int churnNext;
    /** Sink for the consumers of the iteration benchmarks. */
    protected long sink;

    /** The {@code i}-th key of the population; {@code i >= size} yields keys that are absent. */
    protected final long key(int i) {
        return dist == KeyDist.RANDOM ? KeyDist.fmix64(i + 1) : 2L * i;
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
        sorted = keys.clone();
        Arrays.sort(sorted);
        buildCursor = 0;
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        hits = new long[OPS];
        misses = new long[OPS];
        for (int j = 0; j < OPS; j++) {
            hits[j] = keys[rnd.nextInt(size)];
            misses[j] = dist == KeyDist.RANDOM ? key(size + j) : 2L * rnd.nextInt(size) + 1;
        }
        rangeStarts = new int[RANGE_SCANS];
        for (int j = 0; j < RANGE_SCANS; j++) {
            rangeStarts[j] = rnd.nextInt(size - RANGE_LEN);
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

    /** The next start index for a range removal (rotation over {@link #rangeStarts}). */
    protected final int nextRangeStart() {
        int c = rangeCursor;
        rangeCursor = (c + 1) & (RANGE_SCANS - 1);
        return rangeStarts[c];
    }

    /** Builds the map under test from {@link #keys}. */
    protected abstract void populate();
}
