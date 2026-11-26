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

import speiger.src.collections.longs.queues.LongHeapPriorityQueue;

/** Speiger's Primitive Collections {@link LongHeapPriorityQueue}: a binary heap. {@code hold} polls and pushes. */
public class SpeigerLongHeapBench extends AbstractLongHeapBench {

    /** The queue of {@code hold} (named {@code map} for the footprint measurement). */
    private LongHeapPriorityQueue map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    public LongHeapPriorityQueue build(GcBeforeLargeBuild gc) {
        LongHeapPriorityQueue h = new LongHeapPriorityQueue();
        for (long v : nextValues()) {
            h.enqueue(v);
        }
        return h;
    }

    @Benchmark
    public long sort() {
        LongHeapPriorityQueue h = new LongHeapPriorityQueue();
        for (long v : nextValues()) {
            h.enqueue(v);
        }
        long check = 0;
        for (int k = 1; !h.isEmpty(); k++) {
            check += h.dequeue() * k;
        }
        return check;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long hold() {
        LongHeapPriorityQueue h = map;
        long sum = 0;
        for (long inc : increments) {
            long t = h.dequeue();
            h.enqueue(t + inc);
            sum += t;
        }
        return sum;
    }
}
