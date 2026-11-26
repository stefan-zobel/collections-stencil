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
 * Shared parameters and data for the benchmarks of lists of {@code int} values. The values are
 * random; list operations do not depend on them, so {@code RANDOM} is the only distribution.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code build}: appends {@code size} values to a list with the default capacity, from a
 *       different value set in each invocation. The report divides the time by {@code size}.</li>
 *   <li>{@code get}: {@value #OPS} reads at random indexes of a list of {@code size} values
 *       (ns per read).</li>
 *   <li>{@code iterate}: sums the values of the list with the internal iteration of each library
 *       ({@code forEach} or {@code each} with a lambda). The report divides the time by
 *       {@code size}.</li>
 *   <li>{@code insertRemove}: {@value #SHIFT_OPS} steps of "insert a value at a random index,
 *       then remove the value at another random index" on a list of {@code size} values (ns per
 *       step). Each step moves half the list on average, twice.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractIntListBench {

    /** Number of reads per invocation of {@code get}. */
    public static final int OPS = 1 << 16;

    /** Number of steps per invocation of {@code insertRemove}. */
    public static final int SHIFT_OPS = 256;

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

    /** {@link #OPS} random indexes in {@code [0, size)}. */
    protected int[] indexes;
    /** Per step of {@code insertRemove}: the index of the insertion, in {@code [0, size]}. */
    protected int[] insertAt;
    /** Per step of {@code insertRemove}: the index of the removal, in {@code [0, size]}. */
    protected int[] removeAt;
    /** Per step of {@code insertRemove}: the inserted value. */
    protected int[] shiftValues;

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
        indexes = new int[OPS];
        for (int j = 0; j < OPS; j++) {
            indexes[j] = rnd.nextInt(size);
        }
        insertAt = new int[SHIFT_OPS];
        removeAt = new int[SHIFT_OPS];
        shiftValues = new int[SHIFT_OPS];
        for (int j = 0; j < SHIFT_OPS; j++) {
            insertAt[j] = rnd.nextInt(size + 1);
            removeAt[j] = rnd.nextInt(size + 1);
            shiftValues[j] = rnd.nextInt();
        }
        valueCursor = 0;
        populate();
    }
}
