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

import java.util.PriorityQueue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** JDK {@link PriorityQueue} of boxed {@link Long} values: a binary heap. {@code hold} polls and offers. */
public class JdkLongHeapBench extends AbstractLongHeapBench {

    /** The queue of {@code hold} (named {@code map} for the footprint measurement). */
    private PriorityQueue<Long> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    public PriorityQueue<Long> build(GcBeforeLargeBuild gc) {
        PriorityQueue<Long> h = new PriorityQueue<>();
        for (long v : nextValues()) {
            h.offer(v);
        }
        return h;
    }

    @Benchmark
    public long sort() {
        PriorityQueue<Long> h = new PriorityQueue<>();
        for (long v : nextValues()) {
            h.offer(v);
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
        PriorityQueue<Long> h = map;
        long sum = 0;
        for (long inc : increments) {
            long t = h.poll();
            h.offer(t + inc);
            sum += t;
        }
        return sum;
    }
}
