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
 * Shared parameters and data for the benchmarks of indexed priority queues with sparse
 * {@code long} keys and {@code long} priorities.
 * <p>
 * Keys are spread over the whole {@code long} range: the key of the number {@code x} is
 * {@code x * ODD} (modulo 2^64), and {@code key * ODD_INV} gives the number back for free. So the
 * benchmarks need no map from keys to their own data; the queues have to find the keys by
 * hashing (or, for the dense indexed heap, map them to dense ids first).
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code dijkstra}: shortest paths from one source to all nodes of a random graph with
 *       {@code size} nodes and {@value #DEGREE} outgoing edges each (weights uniform in
 *       {@code [1, 1000]}), the source rotating over {@value #SOURCES} nodes; node {@code u} has
 *       the key {@code u * ODD}. Settled nodes are marked in an array by node number, the same
 *       for all implementations. The report divides the time by {@code size}.</li>
 *   <li>{@code timers}: {@value #OPS} steps on {@code size} timers (ns per step). Three of four
 *       steps reschedule a random timer to a later deadline ("activity on a connection"); the
 *       fourth lets the earliest timer expire and starts a timer with a new key in its place
 *       ("a new connection"). The state carries over from one invocation to the next.</li>
 * </ul>
 * A timer priority is {@code (deadline << 24) | (sequence & 0xFFFFFF)}: deadlines repeat, but
 * the priorities of the live timers are distinct, so every implementation expires the timers in
 * the same order and the results can be compared.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractLongLongIndexedHeapBench {

    /** Number of steps per invocation of {@code timers}. */
    public static final int OPS = 1 << 16;
    /** Outgoing edges per node. */
    static final int DEGREE = 8;
    /** Number of sources of {@code dijkstra}, used in rotation. */
    static final int SOURCES = 16;
    /** Largest delay of a timer deadline. */
    static final int MAX_DELAY = 1024;
    /** Odd multiplier from numbers to keys. */
    static final long ODD = 0x9E37_79B9_7F4A_7C15L;
    /** Inverse of {@link #ODD} modulo 2^64. */
    static final long ODD_INV = inverse(ODD);

    @Param({"1400", "180000", "1400000"})
    public int size;

    /** Only {@code RANDOM}: the runner and the footprint measurement set this parameter. */
    @Param({"RANDOM"})
    public KeyDist dist;

    /** The edges of node {@code u} are {@code start[u] .. start[u + 1] - 1}. */
    protected int[] start;
    /** Edge targets. */
    protected int[] to;
    /** Edge weights. */
    protected int[] weight;
    private int sourceCursor;

    /** {@code timers}: per step, the index into {@link #live} of the timer to reschedule, or {@code -1} to expire one. */
    protected int[] picks;
    /** {@code timers}: per step, the delay of the new deadline, in {@code [1, MAX_DELAY]}. */
    protected int[] delays;
    /**
     * {@code timers}: the keys of the live timers. The number behind the key of the timer at
     * index {@code i} is {@code (generation << 32) | i}, so an expired key tells its index.
     */
    protected long[] live;
    /** {@code timers}: initial priorities of the timers in {@link #live}. */
    protected long[] initialPriorities;
    /** {@code timers}: the current time (the deadline of the last expired timer). */
    protected long now;
    /** {@code timers}: the sequence number of the next priority. */
    protected long sequence;
    /** {@code timers}: the last generation of keys. */
    protected long generation;

    @Setup(Level.Trial)
    public final void setUpData() {
        SplittableRandom rnd = new SplittableRandom(0x5EED_C0FFEEL);
        int n = size;
        start = new int[n + 1];
        to = new int[n * DEGREE];
        weight = new int[n * DEGREE];
        for (int u = 0; u < n; u++) {
            start[u + 1] = (u + 1) * DEGREE;
            for (int e = u * DEGREE; e < (u + 1) * DEGREE; e++) {
                to[e] = rnd.nextInt(n);
                weight[e] = 1 + rnd.nextInt(1000);
            }
        }
        sourceCursor = 0;
        picks = new int[OPS];
        delays = new int[OPS];
        for (int j = 0; j < OPS; j++) {
            picks[j] = rnd.nextInt(4) == 0 ? -1 : rnd.nextInt(n);
            delays[j] = 1 + rnd.nextInt(MAX_DELAY);
        }
        live = new long[n];
        initialPriorities = new long[n];
        now = 0;
        sequence = 0;
        generation = 0;
        for (int i = 0; i < n; i++) {
            live[i] = key(i);
            initialPriorities[i] = priority(1 + rnd.nextInt(MAX_DELAY), sequence++);
        }
        populate();
    }

    private static long inverse(long a) {
        long x = a; // Newton's iteration doubles the correct low bits: 3, 6, 12, 24, 48, 96
        for (int i = 0; i < 5; i++) {
            x *= 2 - a * x;
        }
        return x;
    }

    /** The key of the number {@code x}. */
    static long key(long x) {
        return x * ODD;
    }

    /** The number behind {@code key}. */
    static long number(long key) {
        return key * ODD_INV;
    }

    /** The timer priority of {@code deadline} with the sequence number {@code seq}. */
    static long priority(long deadline, long seq) {
        return (deadline << 24) | (seq & 0xFF_FFFF);
    }

    /** The deadline of a timer priority. */
    static long deadline(long priority) {
        return priority >>> 24;
    }

    /** The next source of {@code dijkstra} (rotation over {@value #SOURCES} nodes). */
    protected final int nextSource() {
        int c = sourceCursor;
        sourceCursor = c + 1 == SOURCES ? 0 : c + 1;
        return (int) ((long) c * size / SOURCES);
    }

    /**
     * Creates the reusable state of the implementation: the empty queue of {@code dijkstra}, and
     * the timers of {@code timers} with the keys {@link #live} and the {@link #initialPriorities}
     * in the field {@code map}, which the footprint measurement reads.
     */
    protected abstract void populate();
}
