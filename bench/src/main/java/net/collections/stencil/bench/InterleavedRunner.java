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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.openjdk.jmh.results.BenchmarkResult;
import org.openjdk.jmh.results.Result;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;
import org.openjdk.jmh.runner.options.VerboseMode;

/**
 * Runs the benchmarks <b>interleaved</b> instead of one after another, so that thermal
 * throttling and other slow drift affect all implementations equally.
 * <p>
 * Design: randomized complete blocks. A <em>cell</em> is one (op, dist, size, threads)
 * combination. Each round visits all cells in random order. Within a cell, every
 * implementation runs exactly one fork, also in random order. The forks of one block
 * run back to back, typically within a minute. Comparisons are therefore paired
 * per block, and repeating the rounds gives the spread.
 *
 * <pre>
 * java -cp benchmarks.jar net.collections.stencil.bench.InterleavedRunner --out=results/x --label=windows
 *      [--suite=int|long|intset|longset|intobj|longobj|longsorted|longlongsorted|longsetsorted|longlinked|objectint|objectintlinked|longheap|intlongindexedheap|longlongindexedheap|intlist|intdeque|intconc|longconc|intintconc|longlongconc] [--rounds=5] [--impls=stencil,stencil-lf50,stencil-block,stencil-block-hash,stencil-compact,fastutil,hppc,eclipse,jdk]
 *      [--ops=getHit,getMiss,addTo,build,churn]   (sets: containsHit,containsMiss,build,churn;
 *                                                  object values: getHit,getMiss,build,churn,computeIfAbsent;
 *                                                  sorted: getHit,getMiss,floor,build,churn,iterate,rangeScan,removeRange;
 *                                                  insertion-ordered: getHit,getMiss,build,iterate,churn,fifo,touch;
 *                                                  object keys: getHit,getMiss,build,churn,count;
 *                                                  insertion-ordered, object keys: getHit,getMiss,build,iterate,churn,fifo,touch,count;
 *                                                  priority queues: build,sort,hold;
 *                                                  indexed priority queues: dijkstra,sort;
 *                                                  indexed priority queues, long keys: dijkstra,timers;
 *                                                  lists: build,get,iterate,insertRemove;
 *                                                  deques: fifo,stack,window;
 *                                                  concurrent: getHit,getMiss,mixed,writeHeavy,computeIfAbsent,build;
 *                                                  concurrent counters: getHit,getMiss,mixed,writeHeavy,count,countSpread,build)
 *      [--sizes=1400,180000,1400000,11000000] [--dists=RANDOM,SEQUENTIAL] [--threads=1]
 *      [--wi=2] [--w=1000] [--i=2] [--r=1000] [--seed=42] [--prof=gc,stack]
 *      [--jvmArgs=-XX:+AlwaysPreTouch,...]
 * </pre>
 * {@code --suite} selects the key/value types: {@code int} (default, all {@code int -> int}
 * implementations) or {@code long} ({@code long -> long}: stencil, stencil-lf50, stencil-compact, fastutil, hppc,
 * eclipse, jdk). {@code intset} and {@code longset} compare sets of {@code int} or {@code long}
 * elements (stencil, stencil-lf50, stencil-compact (longset only), fastutil, hppc, eclipse, jdk), with the
 * operations {@code containsHit,containsMiss,build,churn} by default. {@code intobj} and
 * {@code longobj} compare maps from {@code int} or {@code long} keys to object values (stencil,
 * stencil-lf50, fastutil, hppc, eclipse, jdk) with {@code getHit,getMiss,build,churn,computeIfAbsent}.
 * {@code longsorted} compares sorted maps from {@code long} keys to object values (stencil,
 * fastutil-rb, fastutil-avl, jdk) with {@code getHit,getMiss,floor,build,churn,iterate,rangeScan,removeRange}
 * (see {@link AbstractLongSortedBench}). {@code longlongsorted} compares sorted maps from
 * {@code long} keys to {@code long} values (stencil, fastutil-rb, fastutil-avl, speiger, jdk) with
 * the same operations. {@code longsetsorted} compares sorted sets of {@code long} elements
 * (stencil, fastutil-rb, fastutil-avl, speiger, jdk) with the same operations; there,
 * {@code getHit} and {@code getMiss} are {@code contains}. {@code longlinked} compares insertion-ordered maps from
 * {@code long} keys to object values (stencil, fastutil, speiger, jdk) with
 * {@code getHit,getMiss,build,iterate,churn,fifo,touch}, by default with the sizes
 * {@code 1400,180000,1400000} and RANDOM keys only (see {@link AbstractLongLinkedBench}).
 * {@code objectint} compares maps from {@code String} keys to
 * {@code int} values (stencil, fastutil, hppc, eclipse, jdk) with {@code getHit,getMiss,build,churn,count}
 * (see {@link AbstractObjectIntBench}). {@code objectintlinked} compares insertion-ordered maps from
 * {@code String} keys to {@code int} values (stencil, fastutil, speiger, jdk) with
 * {@code getHit,getMiss,build,iterate,churn,fifo,touch,count}, by default with the sizes
 * {@code 1400,180000,1400000} and both key distributions (see {@link AbstractObjectIntLinkedBench}).
 * {@code longheap} compares priority queues of {@code long} values (stencil, fastutil, speiger,
 * jdk) with {@code build,sort,hold} (see {@link AbstractLongHeapBench}); the variants of the arity
 * experiment (arity2, arity4, arity4-bottomup, arity8-bottomup, arity4-bottomup-loop, arity8,
 * arity16-bottomup, arity4-loop) run only when named in
 * {@code --impls}. {@code intlongindexedheap} compares indexed priority queues with {@code int}
 * ids and {@code long} priorities (stencil, fastutil, jdk) with {@code dijkstra,sort} (see
 * {@link AbstractIntLongIndexedHeapBench}). Both use the sizes {@code 1400,180000,1400000} and
 * both distributions by default. {@code longlongindexedheap} compares indexed priority queues
 * with sparse {@code long} keys and {@code long} priorities (stencil, stencil-dense: the dense
 * heap with a map from keys to ids, fastutil, jdk) with {@code dijkstra,timers}, the same sizes,
 * and RANDOM only (see {@link AbstractLongLongIndexedHeapBench}).
 * {@code intlist} compares lists of {@code int} values (stencil, fastutil, hppc, eclipse, speiger,
 * jdk) with {@code build,get,iterate,insertRemove} (see {@link AbstractIntListBench});
 * {@code intdeque} compares double-ended queues of {@code int} values (stencil, fastutil, hppc,
 * speiger, jdk) with {@code fifo,stack,window} (see {@link AbstractIntDequeBench}). Both use the
 * sizes {@code 1400,180000,1400000} and RANDOM values only by default.
 * {@code intconc} and {@code longconc} compare thread-safe maps
 * from {@code int} or {@code long} keys to object values (stencil, jdk, jctools, speiger) with
 * {@code getHit,getMiss,mixed,writeHeavy,computeIfAbsent,build}, by default with the sizes
 * {@code 1400,180000,1400000}, RANDOM keys only, and {@code --threads=1,6,12} (see
 * {@link AbstractConcurrentLongObjectBench}): every thread count is a dimension of the cells, set as
 * the JMH thread count, for {@code build} as the number of worker threads. {@code intintconc} and
 * {@code longlongconc} compare thread-safe maps with primitive values (stencil, jdk, jdk-adder,
 * jctools, speiger, trivago) with {@code getHit,getMiss,mixed,writeHeavy,count,countSpread,build} and the same
 * defaults (see {@link AbstractConcurrentLongLongBench}). The implementation names are the same in
 * all suites.
 * Writes {@code <out>/<label>.csv} (one line per fork), {@code <label>.log} (progress with ETA),
 * and {@code <label>-footprint.csv} (table bytes per map, for {@code longsorted}, {@code longlongsorted}, {@code longsetsorted}, {@code longlinked}, {@code objectint},
 * {@code objectintlinked}, {@code longheap}, {@code intlongindexedheap}, {@code longlongindexedheap}, {@code intlist}, {@code intdeque}, {@code intconc}, {@code longconc}, {@code intintconc} and {@code longlongconc} the deep size; see
 * {@link Footprint}).
 * With {@code --prof}, the named JMH profilers run in every fork, and their secondary
 * results go to {@code <label>-prof.csv}, textual output (for example from the {@code stack}
 * profiler) to {@code <label>-prof.txt}. A profiler takes options after a colon, as on the JMH
 * command line ({@code --prof=perfasm:events=cpu-clock}). Profilers add overhead, so use such runs for
 * diagnosis only, not for the timing comparison. {@code --jvmArgs} (comma-separated) adds JVM
 * options to every fork, in front of the options from the {@code @Fork} annotation.
 * Without {@code --overwrite}, it refuses to replace an existing CSV.
 */
public final class InterleavedRunner {

    /** Benchmark classes of the {@code int -> int} suite, by implementation name. */
    static final Map<String, String> INT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code long -> long} suite, by implementation name. */
    static final Map<String, String> LONG_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code int} set suite, by implementation name. */
    static final Map<String, String> INT_SET_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code long} set suite, by implementation name. */
    static final Map<String, String> LONG_SET_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code int -> Object} suite, by implementation name. */
    static final Map<String, String> INT_OBJECT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code long -> Object} suite, by implementation name. */
    static final Map<String, String> LONG_OBJECT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the sorted {@code long -> Object} suite, by implementation name. */
    static final Map<String, String> LONG_SORTED_CLASSES = new LinkedHashMap<>();
    /** Implementations of the sorted suite that run by default (others would be experiment variants). */
    static final List<String> LONG_SORTED_DEFAULT = List.of("stencil", "fastutil-rb", "fastutil-avl", "jdk");
    /** Benchmark classes of the sorted {@code long -> long} suite, by implementation name. */
    static final Map<String, String> LONG_LONG_SORTED_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the sorted {@code long} set suite, by implementation name. */
    static final Map<String, String> LONG_SET_SORTED_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the insertion-ordered {@code long -> Object} suite, by implementation name. */
    static final Map<String, String> LONG_LINKED_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code String -> int} suite, by implementation name. */
    static final Map<String, String> OBJECT_INT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the insertion-ordered {@code String -> int} suite, by implementation name. */
    static final Map<String, String> OBJECT_INT_LINKED_CLASSES = new LinkedHashMap<>();
    /**
     * Implementations of the insertion-ordered {@code String -> int} suite that run by default
     * (the others are diagnostic variants with {@code getHit} and {@code count} only).
     */
    static final List<String> OBJECT_INT_LINKED_DEFAULT = List.of("stencil", "fastutil", "speiger", "jdk");
    /** Benchmark classes of the {@code long} priority queue suite, by implementation name. */
    static final Map<String, String> LONG_HEAP_CLASSES = new LinkedHashMap<>();
    /** Implementations of the {@code long} priority queue suite that run by default (the others are the arity experiment). */
    static final List<String> LONG_HEAP_DEFAULT = List.of("stencil", "fastutil", "speiger", "jdk");
    /** Benchmark classes of the indexed priority queue suite, by implementation name. */
    static final Map<String, String> INT_LONG_INDEXED_HEAP_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the indexed priority queue suite with {@code long} keys, by implementation name. */
    static final Map<String, String> LONG_LONG_INDEXED_HEAP_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code int} list suite, by implementation name. */
    static final Map<String, String> INT_LIST_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the {@code int} deque suite, by implementation name. */
    static final Map<String, String> INT_DEQUE_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the thread-safe {@code int -> Object} suite, by implementation name. */
    static final Map<String, String> INT_CONCURRENT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the thread-safe {@code long -> Object} suite, by implementation name. */
    static final Map<String, String> LONG_CONCURRENT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the thread-safe {@code int -> int} suite, by implementation name. */
    static final Map<String, String> INT_INT_CONCURRENT_CLASSES = new LinkedHashMap<>();
    /** Benchmark classes of the thread-safe {@code long -> long} suite, by implementation name. */
    static final Map<String, String> LONG_LONG_CONCURRENT_CLASSES = new LinkedHashMap<>();

    static {
        INT_CLASSES.put("stencil", StencilBench.class.getName());
        INT_CLASSES.put("stencil-lf50", StencilLf50Bench.class.getName());
        INT_CLASSES.put("stencil-block", StencilBlockBench.class.getName());
        INT_CLASSES.put("stencil-block-hash", StencilBlockHashBench.class.getName());
        INT_CLASSES.put("stencil-compact", StencilCompactBench.class.getName());
        INT_CLASSES.put("fastutil", FastutilBench.class.getName());
        INT_CLASSES.put("hppc", HppcBench.class.getName());
        INT_CLASSES.put("eclipse", EclipseBench.class.getName());
        INT_CLASSES.put("jdk", JdkBench.class.getName());

        LONG_CLASSES.put("stencil", StencilLongBench.class.getName());
        LONG_CLASSES.put("stencil-lf50", StencilLongLf50Bench.class.getName());
        LONG_CLASSES.put("stencil-compact", StencilLongCompactBench.class.getName());
        LONG_CLASSES.put("fastutil", FastutilLongBench.class.getName());
        LONG_CLASSES.put("hppc", HppcLongBench.class.getName());
        LONG_CLASSES.put("eclipse", EclipseLongBench.class.getName());
        LONG_CLASSES.put("jdk", JdkLongBench.class.getName());

        INT_SET_CLASSES.put("stencil", StencilIntSetBench.class.getName());
        INT_SET_CLASSES.put("stencil-lf50", StencilIntSetLf50Bench.class.getName());
        INT_SET_CLASSES.put("fastutil", FastutilIntSetBench.class.getName());
        INT_SET_CLASSES.put("hppc", HppcIntSetBench.class.getName());
        INT_SET_CLASSES.put("eclipse", EclipseIntSetBench.class.getName());
        INT_SET_CLASSES.put("jdk", JdkIntSetBench.class.getName());

        LONG_SET_CLASSES.put("stencil", StencilLongSetBench.class.getName());
        LONG_SET_CLASSES.put("stencil-lf50", StencilLongSetLf50Bench.class.getName());
        LONG_SET_CLASSES.put("stencil-compact", StencilLongSetCompactBench.class.getName());
        LONG_SET_CLASSES.put("fastutil", FastutilLongSetBench.class.getName());
        LONG_SET_CLASSES.put("hppc", HppcLongSetBench.class.getName());
        LONG_SET_CLASSES.put("eclipse", EclipseLongSetBench.class.getName());
        LONG_SET_CLASSES.put("jdk", JdkLongSetBench.class.getName());

        INT_OBJECT_CLASSES.put("stencil", StencilIntObjectBench.class.getName());
        INT_OBJECT_CLASSES.put("stencil-lf50", StencilIntObjectLf50Bench.class.getName());
        INT_OBJECT_CLASSES.put("fastutil", FastutilIntObjectBench.class.getName());
        INT_OBJECT_CLASSES.put("hppc", HppcIntObjectBench.class.getName());
        INT_OBJECT_CLASSES.put("eclipse", EclipseIntObjectBench.class.getName());
        INT_OBJECT_CLASSES.put("jdk", JdkIntObjectBench.class.getName());

        LONG_OBJECT_CLASSES.put("stencil", StencilLongObjectBench.class.getName());
        LONG_OBJECT_CLASSES.put("stencil-lf50", StencilLongObjectLf50Bench.class.getName());
        LONG_OBJECT_CLASSES.put("fastutil", FastutilLongObjectBench.class.getName());
        LONG_OBJECT_CLASSES.put("hppc", HppcLongObjectBench.class.getName());
        LONG_OBJECT_CLASSES.put("eclipse", EclipseLongObjectBench.class.getName());
        LONG_OBJECT_CLASSES.put("jdk", JdkLongObjectBench.class.getName());

        LONG_SORTED_CLASSES.put("stencil", StencilLongTreeBench.class.getName());
        LONG_SORTED_CLASSES.put("fastutil-rb", FastutilLongRBTreeBench.class.getName());
        LONG_SORTED_CLASSES.put("fastutil-avl", FastutilLongAVLTreeBench.class.getName());
        LONG_SORTED_CLASSES.put("jdk", JdkLongTreeBench.class.getName());

        LONG_LONG_SORTED_CLASSES.put("stencil", StencilLongLongTreeBench.class.getName());
        LONG_LONG_SORTED_CLASSES.put("fastutil-rb", FastutilLongLongRBTreeBench.class.getName());
        LONG_LONG_SORTED_CLASSES.put("fastutil-avl", FastutilLongLongAVLTreeBench.class.getName());
        LONG_LONG_SORTED_CLASSES.put("speiger", SpeigerLongLongRBTreeBench.class.getName());
        LONG_LONG_SORTED_CLASSES.put("jdk", JdkLongLongTreeBench.class.getName());

        LONG_SET_SORTED_CLASSES.put("stencil", StencilLongTreeSetBench.class.getName());
        LONG_SET_SORTED_CLASSES.put("fastutil-rb", FastutilLongRBTreeSetBench.class.getName());
        LONG_SET_SORTED_CLASSES.put("fastutil-avl", FastutilLongAVLTreeSetBench.class.getName());
        LONG_SET_SORTED_CLASSES.put("speiger", SpeigerLongRBTreeSetBench.class.getName());
        LONG_SET_SORTED_CLASSES.put("jdk", JdkLongTreeSetBench.class.getName());

        LONG_LINKED_CLASSES.put("stencil", StencilLongLinkedBench.class.getName());
        LONG_LINKED_CLASSES.put("fastutil", FastutilLongLinkedBench.class.getName());
        LONG_LINKED_CLASSES.put("speiger", SpeigerLongLinkedBench.class.getName());
        LONG_LINKED_CLASSES.put("jdk", JdkLongLinkedBench.class.getName());

        OBJECT_INT_CLASSES.put("stencil", StencilObjectIntBench.class.getName());
        OBJECT_INT_CLASSES.put("fastutil", FastutilObjectIntBench.class.getName());
        OBJECT_INT_CLASSES.put("hppc", HppcObjectIntBench.class.getName());
        OBJECT_INT_CLASSES.put("eclipse", EclipseObjectIntBench.class.getName());
        OBJECT_INT_CLASSES.put("jdk", JdkObjectIntBench.class.getName());

        OBJECT_INT_LINKED_CLASSES.put("stencil", StencilObjectIntLinkedBench.class.getName());
        OBJECT_INT_LINKED_CLASSES.put("fastutil", FastutilObjectIntLinkedBench.class.getName());
        OBJECT_INT_LINKED_CLASSES.put("speiger", SpeigerObjectIntLinkedBench.class.getName());
        OBJECT_INT_LINKED_CLASSES.put("jdk", JdkObjectIntLinkedBench.class.getName());
        OBJECT_INT_LINKED_CLASSES.put("stencil-hash", StencilHashObjectIntLinkedBench.class.getName());
        OBJECT_INT_LINKED_CLASSES.put("fastutil-hash", FastutilHashObjectIntLinkedBench.class.getName());
        OBJECT_INT_LINKED_CLASSES.put("stencil-shuffled", StencilShuffledObjectIntLinkedBench.class.getName());

        LONG_HEAP_CLASSES.put("stencil", StencilLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("fastutil", FastutilLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("speiger", SpeigerLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("jdk", JdkLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity2", Arity2LongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity4", Arity4LongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity4-bottomup", Arity4BottomUpLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity8-bottomup", Arity8BottomUpLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity4-bottomup-loop", Arity4BottomUpLoopLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity8", Arity8LongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity16-bottomup", Arity16BottomUpLongHeapBench.class.getName());
        LONG_HEAP_CLASSES.put("arity4-loop", Arity4LoopLongHeapBench.class.getName());

        INT_LONG_INDEXED_HEAP_CLASSES.put("stencil", StencilIntLongIndexedHeapBench.class.getName());
        INT_LONG_INDEXED_HEAP_CLASSES.put("fastutil", FastutilIntLongIndexedHeapBench.class.getName());
        INT_LONG_INDEXED_HEAP_CLASSES.put("jdk", JdkIntLongIndexedHeapBench.class.getName());

        LONG_LONG_INDEXED_HEAP_CLASSES.put("stencil", StencilLongLongIndexedHeapBench.class.getName());
        LONG_LONG_INDEXED_HEAP_CLASSES.put("stencil-dense", StencilDenseLongLongIndexedHeapBench.class.getName());
        LONG_LONG_INDEXED_HEAP_CLASSES.put("fastutil", FastutilLongLongIndexedHeapBench.class.getName());
        LONG_LONG_INDEXED_HEAP_CLASSES.put("jdk", JdkLongLongIndexedHeapBench.class.getName());

        INT_LIST_CLASSES.put("stencil", StencilIntListBench.class.getName());
        INT_LIST_CLASSES.put("fastutil", FastutilIntListBench.class.getName());
        INT_LIST_CLASSES.put("hppc", HppcIntListBench.class.getName());
        INT_LIST_CLASSES.put("eclipse", EclipseIntListBench.class.getName());
        INT_LIST_CLASSES.put("speiger", SpeigerIntListBench.class.getName());
        INT_LIST_CLASSES.put("jdk", JdkIntListBench.class.getName());

        INT_DEQUE_CLASSES.put("stencil", StencilIntDequeBench.class.getName());
        INT_DEQUE_CLASSES.put("fastutil", FastutilIntDequeBench.class.getName());
        INT_DEQUE_CLASSES.put("hppc", HppcIntDequeBench.class.getName());
        INT_DEQUE_CLASSES.put("speiger", SpeigerIntDequeBench.class.getName());
        INT_DEQUE_CLASSES.put("jdk", JdkIntDequeBench.class.getName());

        INT_CONCURRENT_CLASSES.put("stencil", StencilConcurrentIntObjectBench.class.getName());
        INT_CONCURRENT_CLASSES.put("jdk", JdkConcurrentIntObjectBench.class.getName());
        INT_CONCURRENT_CLASSES.put("jctools", JctoolsConcurrentIntObjectBench.class.getName());
        INT_CONCURRENT_CLASSES.put("speiger", SpeigerConcurrentIntObjectBench.class.getName());

        LONG_CONCURRENT_CLASSES.put("stencil", StencilConcurrentLongObjectBench.class.getName());
        LONG_CONCURRENT_CLASSES.put("jdk", JdkConcurrentLongObjectBench.class.getName());
        LONG_CONCURRENT_CLASSES.put("jctools", JctoolsConcurrentLongObjectBench.class.getName());
        LONG_CONCURRENT_CLASSES.put("speiger", SpeigerConcurrentLongObjectBench.class.getName());

        INT_INT_CONCURRENT_CLASSES.put("stencil", StencilConcurrentIntIntBench.class.getName());
        INT_INT_CONCURRENT_CLASSES.put("jdk", JdkConcurrentIntIntBench.class.getName());
        INT_INT_CONCURRENT_CLASSES.put("jdk-adder", JdkAdderConcurrentIntIntBench.class.getName());
        INT_INT_CONCURRENT_CLASSES.put("jctools", JctoolsConcurrentIntIntBench.class.getName());
        INT_INT_CONCURRENT_CLASSES.put("speiger", SpeigerConcurrentIntIntBench.class.getName());
        INT_INT_CONCURRENT_CLASSES.put("trivago", TrivagoConcurrentIntIntBench.class.getName());

        LONG_LONG_CONCURRENT_CLASSES.put("stencil", StencilConcurrentLongLongBench.class.getName());
        LONG_LONG_CONCURRENT_CLASSES.put("jdk", JdkConcurrentLongLongBench.class.getName());
        LONG_LONG_CONCURRENT_CLASSES.put("jdk-adder", JdkAdderConcurrentLongLongBench.class.getName());
        LONG_LONG_CONCURRENT_CLASSES.put("jctools", JctoolsConcurrentLongLongBench.class.getName());
        LONG_LONG_CONCURRENT_CLASSES.put("speiger", SpeigerConcurrentLongLongBench.class.getName());
        LONG_LONG_CONCURRENT_CLASSES.put("trivago", TrivagoConcurrentLongLongBench.class.getName());
    }

    /** The implementation classes of the suite selected with {@code --suite}. */
    static Map<String, String> implClasses = INT_CLASSES;

    record Cell(String op, String dist, int size, int threads) {}

    record Fork(int round, int block, int pos, Cell cell, String impl) {}

    public static void main(String[] args) throws IOException, RunnerException {
        Map<String, String> a = parseArgs(args);
        String suite = a.getOrDefault("suite", "int");
        implClasses = switch (suite) {
            case "int" -> INT_CLASSES;
            case "long" -> LONG_CLASSES;
            case "intset" -> INT_SET_CLASSES;
            case "longset" -> LONG_SET_CLASSES;
            case "intobj" -> INT_OBJECT_CLASSES;
            case "longobj" -> LONG_OBJECT_CLASSES;
            case "longsorted" -> LONG_SORTED_CLASSES;
            case "longlongsorted" -> LONG_LONG_SORTED_CLASSES;
            case "longsetsorted" -> LONG_SET_SORTED_CLASSES;
            case "longlinked" -> LONG_LINKED_CLASSES;
            case "objectint" -> OBJECT_INT_CLASSES;
            case "objectintlinked" -> OBJECT_INT_LINKED_CLASSES;
            case "longheap" -> LONG_HEAP_CLASSES;
            case "intlongindexedheap" -> INT_LONG_INDEXED_HEAP_CLASSES;
            case "longlongindexedheap" -> LONG_LONG_INDEXED_HEAP_CLASSES;
            case "intlist" -> INT_LIST_CLASSES;
            case "intdeque" -> INT_DEQUE_CLASSES;
            case "intconc" -> INT_CONCURRENT_CLASSES;
            case "longconc" -> LONG_CONCURRENT_CLASSES;
            case "intintconc" -> INT_INT_CONCURRENT_CLASSES;
            case "longlongconc" -> LONG_LONG_CONCURRENT_CLASSES;
            default -> throw new IllegalArgumentException("unknown suite: " + suite
                    + " (int, long, intset, longset, intobj, longobj, longsorted, longlongsorted, longsetsorted,"
                    + " longlinked, objectint, objectintlinked, longheap, intlongindexedheap, longlongindexedheap,"
                    + " intlist, intdeque, intconc, longconc, intintconc or longlongconc)");
        };
        boolean sortedSuite = suite.endsWith("sorted");
        boolean linkedSuite = suite.endsWith("linked");
        boolean concurrentSuite = suite.endsWith("conc");
        boolean objectKeySuite = suite.startsWith("object");
        boolean heapSuite = suite.endsWith("heap");
        boolean sequenceSuite = suite.equals("intlist") || suite.equals("intdeque");
        // Sequences: the boxed Integer values of the JDK collections count, so not DEEP_SHARED_VALUES.
        Footprint.Mode footprintMode = objectKeySuite ? Footprint.Mode.DEEP_SHARED_KEYS
                : sortedSuite || linkedSuite || heapSuite ? Footprint.Mode.DEEP_SHARED_VALUES
                : concurrentSuite || sequenceSuite ? Footprint.Mode.DEEP_BOXED_KEYS
                : Footprint.Mode.TABLE;
        String defaultOps = suite.equals("longheap") ? "build,sort,hold"
                : suite.equals("intlongindexedheap") ? "dijkstra,sort"
                : suite.equals("longlongindexedheap") ? "dijkstra,timers"
                : suite.equals("intlist") ? "build,get,iterate,insertRemove"
                : suite.equals("intdeque") ? "fifo,stack,window"
                : suite.equals("intintconc") || suite.equals("longlongconc")
                ? "getHit,getMiss,mixed,writeHeavy,count,countSpread,build"
                : concurrentSuite ? "getHit,getMiss,mixed,writeHeavy,computeIfAbsent,build"
                : suite.endsWith("set") ? "containsHit,containsMiss,build,churn"
                : suite.endsWith("obj") ? "getHit,getMiss,build,churn,computeIfAbsent"
                : sortedSuite ? "getHit,getMiss,floor,build,churn,iterate,rangeScan,removeRange"
                : linkedSuite && objectKeySuite ? "getHit,getMiss,build,iterate,churn,fifo,touch,count"
                : linkedSuite ? "getHit,getMiss,build,iterate,churn,fifo,touch"
                : suite.equals("objectint") ? "getHit,getMiss,build,churn,count"
                : "getHit,getMiss,addTo,build,churn";
        String defaultImpls = suite.equals("longsorted") ? String.join(",", LONG_SORTED_DEFAULT)
                : suite.equals("objectintlinked") ? String.join(",", OBJECT_INT_LINKED_DEFAULT)
                : suite.equals("longheap") ? String.join(",", LONG_HEAP_DEFAULT)
                : String.join(",", implClasses.keySet());
        Path out = Path.of(require(a, "out"));
        String label = a.getOrDefault("label", "run");
        int rounds = Integer.parseInt(a.getOrDefault("rounds", "5"));
        List<String> impls = list(a.getOrDefault("impls", defaultImpls));
        List<String> ops = list(a.getOrDefault("ops", defaultOps));
        List<String> sizes = list(a.getOrDefault("sizes",
                concurrentSuite || linkedSuite || heapSuite || sequenceSuite ? "1400,180000,1400000"
                        : "1400,180000,1400000,11000000"));
        List<String> dists = list(a.getOrDefault("dists",
                concurrentSuite || sequenceSuite || (linkedSuite && !objectKeySuite)
                        || suite.equals("longlongindexedheap") ? "RANDOM" : "RANDOM,SEQUENTIAL"));
        List<String> threadCounts = list(a.getOrDefault("threads", concurrentSuite ? "1,6,12" : "1"));
        if (!concurrentSuite && !threadCounts.equals(List.of("1"))) {
            throw new IllegalArgumentException("--threads is only available for the concurrent suites");
        }
        int wi = Integer.parseInt(a.getOrDefault("wi", "2"));
        int w = Integer.parseInt(a.getOrDefault("w", "1000"));
        int mi = Integer.parseInt(a.getOrDefault("i", "2"));
        int r = Integer.parseInt(a.getOrDefault("r", "1000"));
        long seed = Long.parseLong(a.getOrDefault("seed", "42"));
        for (String impl : impls) {
            if (!implClasses.containsKey(impl)) {
                throw new IllegalArgumentException("unknown impl: " + impl + " (known in suite " + suite + ": "
                        + implClasses.keySet() + ")");
            }
        }

        List<Cell> cells = new ArrayList<>();
        for (String op : ops) {
            for (String dist : dists) {
                for (String size : sizes) {
                    for (String threads : threadCounts) {
                        cells.add(new Cell(op, dist, Integer.parseInt(size), Integer.parseInt(threads)));
                    }
                }
            }
        }
        Random rnd = new Random(seed);
        List<String> profilers = list(a.getOrDefault("prof", ""));
        List<String> jvmArgs = list(a.getOrDefault("jvmArgs", ""));
        List<Fork> plan = new ArrayList<>();
        for (int round = 1; round <= rounds; round++) {
            List<Cell> order = new ArrayList<>(cells);
            Collections.shuffle(order, rnd);
            for (int block = 0; block < order.size(); block++) {
                List<String> implOrder = new ArrayList<>(impls);
                Collections.shuffle(implOrder, rnd);
                for (int pos = 0; pos < implOrder.size(); pos++) {
                    plan.add(new Fork(round, block, pos, order.get(block), implOrder.get(pos)));
                }
            }
        }

        Files.createDirectories(out);
        Path csv = out.resolve(label + ".csv");
        Path log = out.resolve(label + ".log");
        Path profCsv = out.resolve(label + "-prof.csv");
        Path profTxt = out.resolve(label + "-prof.txt");
        if (Files.exists(csv) && !a.containsKey("overwrite")) {
            throw new IllegalStateException(csv + " exists (use --overwrite)");
        }
        Files.deleteIfExists(profTxt);
        StandardOpenOption[] mode = {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE};
        try (PrintWriter csvOut = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8, mode), true);
             PrintWriter logOut = new PrintWriter(Files.newBufferedWriter(log, StandardCharsets.UTF_8, mode), true);
             PrintWriter profOut = profilers.isEmpty() ? null
                     : new PrintWriter(Files.newBufferedWriter(profCsv, StandardCharsets.UTF_8, mode), true)) {

            csvOut.println("seq,round,block,pos,impl,op,dist,size,threads,score,error,unit,samples,start,end");
            if (profOut != null) {
                profOut.println("seq,round,impl,op,dist,size,metric,score,error,unit");
            }
            String header = String.format(Locale.ROOT,
                    "Interleaved run '%s' (suite %s): %d rounds x %d cells x %d impls = %d forks; warmup %dx%dms, measurement %dx%dms, seed %d%s; JVM %s %s",
                    label, suite, rounds, cells.size(), impls.size(), plan.size(), wi, w, mi, r, seed,
                    (profilers.isEmpty() ? "" : "; profilers " + String.join(",", profilers))
                            + (jvmArgs.isEmpty() ? "" : "; extra JVM args " + String.join(" ", jvmArgs)),
                    System.getProperty("java.vm.name"), System.getProperty("java.runtime.version"));
            emit(logOut, header);
            Footprint.write(out.resolve(label + "-footprint.csv"), impls,
                    sizes.stream().map(Integer::valueOf).toList(), footprintMode);
            emit(logOut, "Footprint written to " + label + "-footprint.csv");

            Instant t0 = Instant.now();
            for (int n = 0; n < plan.size(); n++) {
                Fork f = plan.get(n);
                Instant start = Instant.now();
                BenchmarkResult run = runFork(f, wi, w, mi, r, profilers, jvmArgs, concurrentSuite);
                Result<?> res = run.getPrimaryResult();
                Instant end = Instant.now();

                if (profOut != null) {
                    // JMH declares the secondary results with the raw type Result
                    Map<String, ?> secondary = run.getSecondaryResults();
                    for (String name : secondary.keySet()) {
                        Result<?> s = (Result<?>) secondary.get(name);
                        String info = s.extendedInfo();
                        if (info != null && !info.isBlank()) {
                            // textual profiler output (for example "stack"): one section per fork
                            Files.writeString(profTxt, String.format(Locale.ROOT,
                                            "==== fork %d: round %d, %s %s %s %d, %s%n%s%n",
                                            n + 1, f.round, f.impl, f.cell.op, f.cell.dist, f.cell.size, name, info),
                                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        }
                        profOut.printf(Locale.ROOT, "%d,%d,%s,%s,%s,%d,%s,%.6f,%.6f,%s%n",
                                n + 1, f.round, f.impl, f.cell.op, f.cell.dist, f.cell.size,
                                name, s.getScore(), s.getScoreError(), s.getScoreUnit());
                    }
                }
                csvOut.printf(Locale.ROOT, "%d,%d,%d,%d,%s,%s,%s,%d,%d,%.6f,%.6f,%s,%d,%s,%s%n",
                        n + 1, f.round, f.block, f.pos, f.impl, f.cell.op, f.cell.dist, f.cell.size, f.cell.threads,
                        res.getScore(), res.getScoreError(), res.getScoreUnit(), res.getSampleCount(), start, end);

                Duration elapsed = Duration.between(t0, Instant.now());
                Duration eta = elapsed.dividedBy(n + 1).multipliedBy(plan.size() - n - 1);
                emit(logOut, String.format(Locale.ROOT,
                        "[%4d/%d %5.1f%%] round %d  %-7s %-10s %,10d %2dt  %-8s %12.3f %s   fork %4.1fs   elapsed %s   ETA %s",
                        n + 1, plan.size(), 100.0 * (n + 1) / plan.size(), f.round, f.cell.op, f.cell.dist, f.cell.size,
                        f.cell.threads, f.impl, res.getScore(), res.getScoreUnit(),
                        Duration.between(start, end).toMillis() / 1000.0, hms(elapsed), hms(eta)));
            }
            emit(logOut, "Done in " + hms(Duration.between(t0, Instant.now())));
        }
    }

    private static BenchmarkResult runFork(Fork f, int wi, int w, int mi, int r, List<String> profilers,
                                           List<String> jvmArgs, boolean concurrentSuite) throws RunnerException {
        String bench = implClasses.get(f.impl) + "." + f.cell.op;
        OptionsBuilder builder = new OptionsBuilder();
        for (String p : profilers) {
            int colon = p.indexOf(':');
            if (colon < 0) {
                builder.addProfiler(p);
            } else {
                builder.addProfiler(p.substring(0, colon), p.substring(colon + 1));
            }
        }
        if (!jvmArgs.isEmpty()) {
            builder.jvmArgsPrepend(jvmArgs.toArray(String[]::new));
        }
        if (concurrentSuite) {
            // build runs its own worker threads; all other benchmarks run in JMH threads
            boolean build = f.cell.op.equals("build");
            builder.param("workers", Integer.toString(build ? f.cell.threads : 1));
            builder.threads(build ? 1 : f.cell.threads);
        }
        Options opt = builder
                .include("^" + Pattern.quote(bench) + "$")
                .param("size", Integer.toString(f.cell.size))
                .param("dist", f.cell.dist)
                .forks(1)
                .warmupIterations(wi)
                .warmupTime(TimeValue.milliseconds(w))
                .measurementIterations(mi)
                .measurementTime(TimeValue.milliseconds(r))
                .verbosity(VerboseMode.SILENT)
                .shouldFailOnError(true)
                .build();
        Collection<RunResult> results = new Runner(opt).run();
        if (results.size() != 1) {
            throw new IllegalStateException("expected exactly one result for " + bench + ", got " + results.size());
        }
        return results.iterator().next().getAggregatedResult();
    }

    private static void emit(PrintWriter log, String line) {
        String stamped = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + line;
        System.out.println(stamped);
        log.println(stamped);
    }

    private static String hms(Duration d) {
        long s = d.toSeconds();
        return String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("expected --key=value: " + arg);
            }
            int eq = arg.indexOf('=');
            if (eq < 0) {
                m.put(arg.substring(2), "true");
            } else {
                m.put(arg.substring(2, eq), arg.substring(eq + 1));
            }
        }
        return m;
    }

    private static String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("missing --" + key + "=...");
        }
        return v;
    }

    private static List<String> list(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
