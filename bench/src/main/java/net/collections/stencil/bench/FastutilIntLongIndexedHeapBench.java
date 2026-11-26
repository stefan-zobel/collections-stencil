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

import it.unimi.dsi.fastutil.longs.LongHeapIndirectPriorityQueue;
import org.openjdk.jmh.annotations.Benchmark;

/**
 * fastutil {@link LongHeapIndirectPriorityQueue}: a binary heap of indices into an external
 * {@code long[]} of priorities, with an inverse array for {@code contains} and
 * {@code changed(index)} ("decrease key" after writing the new priority into the array).
 */
public class FastutilIntLongIndexedHeapBench extends AbstractIntLongIndexedHeapBench {

    /** A queue with all ids, for the footprint measurement (it references its priority array). */
    private LongHeapIndirectPriorityQueue map;
    /** The queue of {@code dijkstra}, empty between invocations, over {@link #tentative}. */
    private LongHeapIndirectPriorityQueue queue;
    /** Tentative distances of {@code dijkstra}: the priorities of {@link #queue}. */
    private long[] tentative;
    private boolean[] settled;

    @Override
    protected void populate() {
        tentative = new long[size];
        settled = new boolean[size];
        queue = new LongHeapIndirectPriorityQueue(tentative, size);
        LongHeapIndirectPriorityQueue all = new LongHeapIndirectPriorityQueue(priorities.clone(), size);
        for (int i = 0; i < size; i++) {
            all.enqueue(i);
        }
        map = all;
    }

    @Benchmark
    public long dijkstra() {
        final LongHeapIndirectPriorityQueue q = queue;
        final long[] ref = tentative;
        final boolean[] done = settled;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        Arrays.fill(done, false);
        int s = nextSource();
        ref[s] = 0;
        q.enqueue(s);
        long sum = 0;
        while (!q.isEmpty()) {
            int u = q.dequeue();
            long du = ref[u];
            done[u] = true;
            sum += du;
            for (int e = st[u], end = st[u + 1]; e < end; e++) {
                int v = tt[e];
                if (!done[v]) {
                    long nd = du + w[e];
                    if (!q.contains(v)) {
                        ref[v] = nd;
                        q.enqueue(v);
                    } else if (nd < ref[v]) {
                        ref[v] = nd;
                        q.changed(v);
                    }
                }
            }
        }
        return sum;
    }

    @Benchmark
    public long sort() {
        final long[] p = priorities;
        LongHeapIndirectPriorityQueue q = new LongHeapIndirectPriorityQueue(p, p.length);
        for (int i = 0; i < p.length; i++) {
            q.enqueue(i);
        }
        long check = 0;
        for (int k = 1; !q.isEmpty(); k++) {
            check += (long) q.dequeue() * k;
        }
        return check;
    }
}
