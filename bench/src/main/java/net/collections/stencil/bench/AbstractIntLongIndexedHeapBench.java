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
 * Shared parameters and data for the benchmarks of indexed priority queues: {@code int} ids
 * (graph nodes) with {@code long} priorities that decrease while the ids are in the queue.
 * <p>
 * The graph has {@code size} nodes with {@value #DEGREE} outgoing edges each, in compressed rows
 * ({@link #start}, {@link #to}, {@link #weight}), with weights uniform in {@code [1, 1000]}.
 * {@code RANDOM}: the edges lead to random nodes (a random graph, no locality).
 * {@code SEQUENTIAL}: the edges of node {@code u} lead to nodes within the next
 * {@value #WINDOW} ids (cyclically), like the local structure of a road network. A few nodes
 * may be unreachable from a source; the distance sums that the benchmarks return skip them.
 * <p>
 * Benchmarks:
 * <ul>
 *   <li>{@code dijkstra}: shortest paths from one source to all nodes, the source rotating over
 *       {@value #SOURCES} nodes. The report divides the time by {@code size}. Each implementation
 *       uses its natural form of the algorithm: "decrease key" where the queue offers it,
 *       otherwise lazy deletion (push a node again with a smaller distance, skip stale
 *       entries).</li>
 *   <li>{@code sort}: inserts all ids with random priorities ({@code RANDOM}) or ascending ones
 *       ({@code SEQUENTIAL}), then polls them all. The report divides the time by
 *       {@code size}.</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 4, time = 1)
@Fork(value = 2, jvmArgsAppend = {"-Xms4g", "-Xmx4g", "--sun-misc-unsafe-memory-access=allow"})
public abstract class AbstractIntLongIndexedHeapBench {

    /** Outgoing edges per node. */
    static final int DEGREE = 8;
    /** Range of the edge targets of {@code SEQUENTIAL} graphs. */
    static final int WINDOW = 64;
    /** Number of sources of {@code dijkstra}, used in rotation. */
    static final int SOURCES = 16;

    @Param({"1400", "180000", "1400000"})
    public int size;

    @Param({"RANDOM", "SEQUENTIAL"})
    public KeyDist dist;

    /** The edges of node {@code u} are {@code start[u] .. start[u + 1] - 1}. */
    protected int[] start;
    /** Edge targets. */
    protected int[] to;
    /** Edge weights. */
    protected int[] weight;
    /** Priorities of {@code sort}, by id. */
    protected long[] priorities;
    private int sourceCursor;

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
                to[e] = switch (dist) {
                    case RANDOM -> rnd.nextInt(n);
                    case SEQUENTIAL -> (int) ((u + 1L + rnd.nextInt(WINDOW)) % n);
                };
                weight[e] = 1 + rnd.nextInt(1000);
            }
        }
        priorities = new long[n];
        for (int i = 0; i < n; i++) {
            priorities[i] = switch (dist) {
                case RANDOM -> rnd.nextLong(Long.MAX_VALUE);
                case SEQUENTIAL -> i * 1000L + rnd.nextInt(1000);
            };
        }
        sourceCursor = 0;
        populate();
    }

    /** The next source of {@code dijkstra} (rotation over {@value #SOURCES} nodes). */
    protected final int nextSource() {
        int c = sourceCursor;
        sourceCursor = c + 1 == SOURCES ? 0 : c + 1;
        return (int) ((long) c * size / SOURCES);
    }

    /**
     * Creates the reusable state of the implementation, and a queue with all ids and their
     * {@link #priorities} in the field {@code map}, for the footprint measurement.
     */
    protected abstract void populate();
}
