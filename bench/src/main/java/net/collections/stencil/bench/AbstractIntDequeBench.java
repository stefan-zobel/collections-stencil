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
 * Shared parameters and data for the benchmarks of double-ended queues of {@code int} values.
 * The values are random; the operations do not depend on them, so {@code RANDOM} is the only
 * distribution.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code fifo}: {@value #OPS} steps of "add a value at the end, remove the first" on a
 *       queue of {@code size} values (ns per step): a work queue in a steady state.</li>
 *   <li>{@code stack}: adds {@code size} values at the end of a deque with the default
 *       capacity, then removes them all from the end, from a different value set in each
 *       invocation. The report divides the time by {@code size}.</li>
 *   <li>{@code window}: a sliding window: {@value #OPS} values are added at the end of a deque
 *       of {@code size} values; after every {@value #WINDOW_BLOCK} values, the first
 *       {@value #WINDOW_BLOCK} are removed (ns per added value). Each implementation removes
 *       them in its fastest way: in one call where it has one, otherwise one by one.</li>
 * </ul>
 * A comparison of iteration or access by index is not possible: the {@code int} queue of
 * fastutil offers neither, the JDK deque no access by index.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractIntDequeBench {

    /** Number of steps per invocation of {@code fifo} and {@code window}. */
    public static final int OPS = 1 << 16;

    /** Number of values that {@code window} adds before it removes as many from the front. */
    public static final int WINDOW_BLOCK = 64;

    @Param({"1400", "180000", "1400000"})
    public int size;

    /** Only {@code RANDOM}: the runner and the footprint measurement set this parameter. */
    @Param({"RANDOM"})
    public KeyDist dist;

    /** Minimum total number of values over all build value sets (see {@link AbstractLongObjectBench}). */
    static final int BUILD_POOL_VALUES = 1 << 20;

    /** Value sets for the builds, used in rotation; set 0 fills the collection of the other benchmarks. */
    protected int[][] valueSets;
    private int valueCursor;

    /** The next value set (rotation over {@link #valueSets}). */
    protected final int[] nextValues() {
        int c = valueCursor;
        valueCursor = c + 1 == valueSets.length ? 0 : c + 1;
        return valueSets[c];
    }

    /** Fills the collection of the other benchmarks (field {@code map}, for the footprint) with value set 0. */
    protected abstract void populate();

    /** {@link #OPS} values added by {@code fifo} and {@code window}. */
    protected int[] opsValues;

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (BUILD_POOL_VALUES + size - 1) / size);
        valueSets = new int[sets][size];
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        for (int[] set : valueSets) {
            for (int i = 0; i < size; i++) {
                set[i] = rnd.nextInt();
            }
        }
        opsValues = new int[OPS];
        for (int j = 0; j < OPS; j++) {
            opsValues[j] = rnd.nextInt();
        }
        valueCursor = 0;
        populate();
    }
}
