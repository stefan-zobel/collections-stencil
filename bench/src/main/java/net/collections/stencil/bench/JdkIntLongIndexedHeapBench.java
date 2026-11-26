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
import java.util.PriorityQueue;

import org.openjdk.jmh.annotations.Benchmark;

/**
 * JDK {@link PriorityQueue} of {@code (priority, id)} records, without "decrease key":
 * {@code dijkstra} uses lazy deletion (a node is pushed again when its distance shrinks, stale
 * entries are skipped when polled), the usual form with the JDK.
 */
public class JdkIntLongIndexedHeapBench extends AbstractIntLongIndexedHeapBench {

    /** A queue entry, ordered by priority. */
    record Entry(long priority, int id) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry o) {
            return Long.compare(priority, o.priority);
        }
    }

    /** A queue with all ids, for the footprint measurement. */
    private PriorityQueue<Entry> map;
    /** The queue of {@code dijkstra}, empty between invocations. */
    private PriorityQueue<Entry> queue;
    /** Best distances found so far by {@code dijkstra}. */
    private long[] best;
    private boolean[] settled;

    @Override
    protected void populate() {
        queue = new PriorityQueue<>(size);
        best = new long[size];
        settled = new boolean[size];
        PriorityQueue<Entry> all = new PriorityQueue<>(size);
        for (int i = 0; i < size; i++) {
            all.add(new Entry(priorities[i], i));
        }
        map = all;
    }

    @Benchmark
    public long dijkstra() {
        final PriorityQueue<Entry> q = queue;
        final long[] b = best;
        final boolean[] done = settled;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        Arrays.fill(b, Long.MAX_VALUE);
        Arrays.fill(done, false);
        int s = nextSource();
        b[s] = 0;
        q.add(new Entry(0, s));
        long sum = 0;
        for (Entry x; (x = q.poll()) != null; ) {
            int u = x.id();
            if (done[u]) {
                continue; // stale
            }
            done[u] = true;
            long du = x.priority();
            sum += du;
            for (int e = st[u], end = st[u + 1]; e < end; e++) {
                int v = tt[e];
                if (!done[v]) {
                    long nd = du + w[e];
                    if (nd < b[v]) {
                        b[v] = nd;
                        q.add(new Entry(nd, v));
                    }
                }
            }
        }
        return sum;
    }

    @Benchmark
    public long sort() {
        final long[] p = priorities;
        PriorityQueue<Entry> q = new PriorityQueue<>(p.length);
        for (int i = 0; i < p.length; i++) {
            q.add(new Entry(p[i], i));
        }
        long check = 0;
        for (int k = 1; !q.isEmpty(); k++) {
            check += (long) q.poll().id() * k;
        }
        return check;
    }
}
