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
import net.generated.example.LongLongIndexedHeap;

/** stencil {@link LongLongIndexedHeap}: a 4-ary heap that finds arbitrary {@code long} keys in its own hash table. */
public class StencilLongLongIndexedHeapBench extends AbstractLongLongIndexedHeapBench {

    /** The timers of {@code timers}; also measured by the footprint. */
    private LongLongIndexedHeap map;
    /** The queue of {@code dijkstra}, empty between invocations. */
    private LongLongIndexedHeap queue;
    private boolean[] settled;

    @Override
    protected void populate() {
        queue = new LongLongIndexedHeap(size);
        settled = new boolean[size];
        LongLongIndexedHeap timers = new LongLongIndexedHeap(size);
        for (int i = 0; i < size; i++) {
            timers.put(live[i], initialPriorities[i]);
        }
        map = timers;
    }

    @Benchmark
    public long dijkstra() {
        final LongLongIndexedHeap h = queue;
        final boolean[] done = settled;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        java.util.Arrays.fill(done, false);
        h.put(key(nextSource()), 0L);
        long sum = 0;
        while (!h.isEmpty()) {
            long du = h.peekPriority();
            int u = (int) number(h.poll());
            done[u] = true;
            sum += du;
            for (int e = st[u], end = st[u + 1]; e < end; e++) {
                int v = tt[e];
                if (!done[v]) {
                    h.putIfBetter(key(v), du + w[e]);
                }
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long timers() {
        final LongLongIndexedHeap h = map;
        final long[] lv = live;
        final int[] pk = picks;
        final int[] dl = delays;
        long t = now;
        long seq = sequence;
        long gen = generation;
        long check = 0;
        for (int j = 0; j < OPS; j++) {
            int p = pk[j];
            if (p >= 0) {
                h.put(lv[p], priority(t + dl[j], seq++));
            } else {
                t = deadline(h.peekPriority());
                long expired = h.poll();
                check += expired;
                int i = (int) number(expired);
                long fresh = key((++gen << 32) | i);
                lv[i] = fresh;
                h.put(fresh, priority(t + dl[j], seq++));
            }
        }
        now = t;
        sequence = seq;
        generation = gen;
        return check + t;
    }
}
