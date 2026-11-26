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
 * Shared parameters and data for the benchmarks of insertion-ordered maps from {@link String}
 * keys to {@code int} values: counters that report in the order in which keys first appeared,
 * and caches with first-in, first-out or least-recently-used eviction.
 * <p>
 * The keys are those of {@link AbstractObjectIntBench}: strings from {@link KeyDist#stringKey}
 * ({@code RANDOM}: hexadecimal strings of random numbers, {@code SEQUENTIAL}: decimal strings
 * {@code "0", "1", ...}), all created during setup with their hash codes computed. The keys used
 * for lookups, counting, and {@code touch} are equal, but not identical copies of the map's keys,
 * so that every successful lookup compares the characters. No string is created during a
 * measurement.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code getHit}, {@code getMiss}: {@value #OPS} lookups of present or absent keys in
 *       random order (ns per lookup)</li>
 *   <li>{@code build}: builds a map of {@code size} entries from the default capacity, from a
 *       different key set in each invocation. The report divides the time by {@code size}.</li>
 *   <li>{@code iterate}: visits all entries in insertion order and adds up the values. The report
 *       divides the time by {@code size}.</li>
 *   <li>{@code churn}: {@value #OPS} steps of "remove a random present key, insert a key that is
 *       absent" (ns per step). Removals hit entries of any age. The removed keys return to a ring
 *       of absent keys, from which the insertions take theirs.</li>
 *   <li>{@code fifo}: {@value #OPS} steps of "remove the oldest entry, insert a new key" (ns per
 *       step): a bounded cache with first-in, first-out eviction. The keys slide cyclically
 *       through {@link #fifoPool}.</li>
 *   <li>{@code touch}: {@value #OPS} steps of "store a value for a random present key and make
 *       its entry the newest" (ns per step): the write path of a least-recently-used cache.</li>
 *   <li>{@code count}: {@value #OPS} increments by one of present keys whose ranks follow a
 *       Zipf-like distribution, as in {@link AbstractObjectIntBench} (ns per increment).</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractObjectIntLinkedBench {

    /** Number of operations per invocation for all benchmarks except {@code build} and {@code iterate}. */
    public static final int OPS = 1 << 16;

    @Param({"1400", "180000", "1400000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /** Minimum total number of keys over all build key sets (see {@link AbstractLongObjectBench}). */
    static final int BUILD_POOL_KEYS = 1 << 20;

    /** Population in insertion order; the value of {@code keys[i]} is {@code i}. */
    protected String[] keys;
    /** Key sets for {@code build}, used in rotation; set 0 is {@link #keys}. */
    protected String[][] buildKeySets;
    private int buildCursor;
    /** {@link #OPS} copies of present keys, uniformly sampled, in random order. */
    protected String[] hits;
    /** {@link #OPS} absent keys, in random order; never inserted by any benchmark. */
    protected String[] misses;
    /** {@link #OPS} copies of present keys with Zipf-like ranks, in random order. */
    protected String[] counted;
    /** The keys that {@code churn} has in the map; it replaces random elements. */
    protected String[] present;
    /** {@link #OPS} random indices into {@link #present}. */
    protected int[] victims;
    /** Ring of {@link #OPS} keys that are not in the map of {@code churn}. */
    protected String[] spare;
    /** Index in {@link #spare} of the next key to insert. */
    protected int spareCursor;
    /**
     * Keys of the {@code fifo} benchmark: the population followed by {@link #OPS} further keys.
     * The map holds the {@code size} keys before {@link #fifoNext}, cyclically.
     */
    protected String[] fifoPool;
    /** Index in {@link #fifoPool} of the next key to insert. */
    protected int fifoNext;
    /** Sink for the consumers of the iteration benchmark. */
    protected long sink;

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
        present = keys.clone();
        // Key numbers from sets * size on belong to no build key set.
        int fresh = sets * size;
        spare = new String[OPS];
        for (int j = 0; j < OPS; j++) {
            spare[j] = hashed(dist.stringKey(fresh + j));
        }
        spareCursor = 0;
        fifoPool = new String[size + OPS];
        System.arraycopy(keys, 0, fifoPool, 0, size);
        System.arraycopy(spare, 0, fifoPool, size, OPS);
        fifoNext = size;
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        hits = new String[OPS];
        misses = new String[OPS];
        counted = new String[OPS];
        victims = new int[OPS];
        double logRange = Math.log(size + 1.0);
        for (int j = 0; j < OPS; j++) {
            hits[j] = copy(keys[rnd.nextInt(size)]);
            misses[j] = hashed(dist.stringKey(fresh + OPS + j));
            // log-uniform rank in [0, size): density proportional to 1 / (rank + 1)
            int rank = Math.min(size - 1, (int) Math.exp(rnd.nextDouble() * logRange) - 1);
            counted[j] = copy(keys[rank]);
            victims[j] = rnd.nextInt(size);
        }
        shuffle(misses, rnd);
        shuffle(counted, rnd);
        buildCursor = 0;
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
