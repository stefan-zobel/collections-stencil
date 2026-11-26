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

import java.util.Arrays;

import org.openjdk.jmh.annotations.Benchmark;
import net.generated.example.IntLongIndexedHeap;

/** stencil {@link IntLongIndexedHeap}: an implicit 4-ary heap with "decrease key" ({@code putIfBetter}). */
public class StencilIntLongIndexedHeapBench extends AbstractIntLongIndexedHeapBench {

    /** A queue with all ids, for the footprint measurement. */
    private IntLongIndexedHeap map;
    /** The queue of {@code dijkstra}, empty between invocations. */
    private IntLongIndexedHeap queue;
    /** Final distances of {@code dijkstra}, {@code -1} for nodes not settled yet. */
    private long[] distances;

    @Override
    protected void populate() {
        queue = new IntLongIndexedHeap(size);
        distances = new long[size];
        IntLongIndexedHeap all = new IntLongIndexedHeap(size);
        for (int i = 0; i < size; i++) {
            all.put(i, priorities[i]);
        }
        map = all;
    }

    @Benchmark
    public long dijkstra() {
        final IntLongIndexedHeap h = queue;
        final long[] d = distances;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        Arrays.fill(d, -1L);
        h.put(nextSource(), 0L);
        long sum = 0;
        while (!h.isEmpty()) {
            long du = h.peekPriority();
            int u = h.poll();
            d[u] = du;
            sum += du;
            for (int e = st[u], end = st[u + 1]; e < end; e++) {
                int v = tt[e];
                if (d[v] < 0) {
                    h.putIfBetter(v, du + w[e]);
                }
            }
        }
        return sum;
    }

    @Benchmark
    public long sort() {
        final long[] p = priorities;
        IntLongIndexedHeap h = new IntLongIndexedHeap(p.length);
        for (int i = 0; i < p.length; i++) {
            h.put(i, p[i]);
        }
        long check = 0;
        for (int k = 1; !h.isEmpty(); k++) {
            check += (long) h.poll() * k;
        }
        return check;
    }
}
