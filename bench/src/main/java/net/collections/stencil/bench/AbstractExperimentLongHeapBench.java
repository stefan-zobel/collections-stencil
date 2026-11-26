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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/**
 * Benchmarks of the heap variants of the arity experiment ({@link ExperimentLongHeaps}), with
 * the operations of {@link AbstractLongHeapBench}. Each subclass names one variant; a fork
 * loads only one, so the calls through {@link ExperimentLongHeaps.Heap} are monomorphic. The
 * variants: {@link Arity2LongHeapBench}, {@link Arity4LongHeapBench},
 * {@link Arity4BottomUpLongHeapBench}, {@link Arity8BottomUpLongHeapBench},
 * {@link Arity4BottomUpLoopLongHeapBench}, {@link Arity8LongHeapBench},
 * {@link Arity16BottomUpLongHeapBench}, and {@link Arity4LoopLongHeapBench}.
 */
public abstract class AbstractExperimentLongHeapBench extends AbstractLongHeapBench {

    /** The queue of {@code hold} (named {@code map} for the footprint measurement). */
    private ExperimentLongHeaps.Heap map;

    /**
     * A new, empty heap of the variant.
     *
     * @return the heap
     */
    abstract ExperimentLongHeaps.Heap newHeap();

    @Override
    protected void populate() {
        map = (ExperimentLongHeaps.Heap) build(null);
    }

    @Benchmark
    public Object build(GcBeforeLargeBuild gc) {
        ExperimentLongHeaps.Heap h = newHeap();
        for (long v : nextValues()) {
            h.push(v);
        }
        return h;
    }

    @Benchmark
    public long sort() {
        ExperimentLongHeaps.Heap h = newHeap();
        for (long v : nextValues()) {
            h.push(v);
        }
        long check = 0;
        for (int k = 1; !h.isEmpty(); k++) {
            check += h.poll() * k;
        }
        return check;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long hold() {
        ExperimentLongHeaps.Heap h = map;
        long sum = 0;
        for (long inc : increments) {
            long t = h.peek();
            h.replaceTop(t + inc);
            sum += t;
        }
        return sum;
    }
}
