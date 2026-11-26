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
 * Shared parameters and data for the benchmarks of priority queues of {@code long} values (min
 * first), modeled on the event queue of a discrete-event simulation: the values are event times.
 * <p>
 * Value sets: {@code RANDOM} holds times drawn uniformly from the horizon
 * {@code [0, size * MEAN_STEP)}; {@code SEQUENTIAL} holds ascending times, one per
 * {@link #MEAN_STEP} with random jitter (events that arrive in time order).
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code build}: pushes {@code size} values into a queue with the default capacity, from a
 *       different value set in each invocation. The report divides the time by {@code size}.</li>
 *   <li>{@code sort}: pushes {@code size} values, then polls them all (heapsort through the
 *       queue's API). The report divides the time by {@code size}.</li>
 *   <li>{@code hold}: the classic "hold" model: {@value #OPS} steps of "poll the earliest event at
 *       time {@code t}, schedule a new one at {@code t + increment}" on a queue of {@code size}
 *       events (ns per step). The increments are uniform in {@code [1, 2 * size * MEAN_STEP)},
 *       so a new event lands at a random place in the queue. Each implementation uses its
 *       fastest way: one {@code replaceTop} where it has one, otherwise a poll and a push.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractLongHeapBench {

    /** Number of steps per invocation of {@code hold}. */
    public static final int OPS = 1 << 16;

    /** Mean distance of consecutive event times. */
    static final long MEAN_STEP = 1 << 16;

    @Param({"1400", "180000", "1400000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /** Minimum total number of values over all build value sets (see {@link AbstractLongObjectBench}). */
    static final int BUILD_POOL_VALUES = 1 << 20;

    /** Value sets for {@code build} and {@code sort}, used in rotation; set 0 fills the queue of {@code hold}. */
    protected long[][] valueSets;
    private int valueCursor;
    /** {@link #OPS} increments of {@code hold}. */
    protected long[] increments;

    @Setup(Level.Trial)
    public final void setUpData() {
        int sets = Math.max(1, (BUILD_POOL_VALUES + size - 1) / size);
        valueSets = new long[sets][size];
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        long horizon = size * MEAN_STEP;
        for (long[] set : valueSets) {
            for (int i = 0; i < size; i++) {
                set[i] = switch (dist) {
                    case RANDOM -> rnd.nextLong(horizon);
                    case SEQUENTIAL -> i * MEAN_STEP + rnd.nextLong(MEAN_STEP);
                };
            }
        }
        increments = new long[OPS];
        for (int j = 0; j < OPS; j++) {
            increments[j] = 1 + rnd.nextLong(2 * horizon - 1);
        }
        valueCursor = 0;
        populate();
    }

    /** The next value set for {@code build} and {@code sort} (rotation over {@link #valueSets}). */
    protected final long[] nextValues() {
        int c = valueCursor;
        valueCursor = c + 1 == valueSets.length ? 0 : c + 1;
        return valueSets[c];
    }

    /** Fills the queue of {@code hold} (field {@code map}, for the footprint) with value set 0. */
    protected abstract void populate();
}
