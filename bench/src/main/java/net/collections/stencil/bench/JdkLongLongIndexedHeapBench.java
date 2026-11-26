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

import java.util.HashMap;
import java.util.PriorityQueue;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/**
 * JDK {@link PriorityQueue} of {@code (priority, key)} records with lazy deletion, the usual form
 * with the JDK: a {@link HashMap} holds the current priority of every key; a changed priority
 * adds a new entry, and entries whose priority is no longer current are skipped when they reach
 * the head of the queue.
 */
public class JdkLongLongIndexedHeapBench extends AbstractLongLongIndexedHeapBench {

    /** A queue entry, ordered by priority. */
    record Entry(long priority, long key) implements Comparable<Entry> {
        @Override
        public int compareTo(Entry o) {
            return Long.compare(priority, o.priority);
        }
    }

    /** The current priority of every key, and the queue with current and stale entries. */
    static final class LazyQueue {
        final HashMap<Long, Long> current;
        final PriorityQueue<Entry> queue;

        LazyQueue(int capacity) {
            current = new HashMap<>(capacity * 4 / 3 + 1);
            queue = new PriorityQueue<>(capacity);
        }

        void put(long key, long p) {
            current.put(key, p);
            queue.add(new Entry(p, key));
        }

        void putIfBetter(long key, long p) {
            Long old = current.get(key);
            if (old == null || p < old) {
                put(key, p);
            }
        }

        /** Removes stale entries from the head; returns the current head, or {@code null}. */
        Entry head() {
            for (Entry e; (e = queue.peek()) != null; queue.poll()) {
                Long p = current.get(e.key());
                if (p != null && p == e.priority()) {
                    return e;
                }
            }
            return null;
        }

        long poll() {
            Entry e = head();
            queue.poll();
            current.remove(e.key());
            return e.key();
        }
    }

    /** The timers of {@code timers}; also measured by the footprint. */
    private LazyQueue map;
    /** The queue of {@code dijkstra}, empty between invocations. */
    private LazyQueue queue;
    private boolean[] settled;

    @Override
    protected void populate() {
        queue = new LazyQueue(size);
        settled = new boolean[size];
        LazyQueue timers = new LazyQueue(size);
        for (int i = 0; i < size; i++) {
            timers.put(live[i], initialPriorities[i]);
        }
        map = timers;
    }

    @Benchmark
    public long dijkstra() {
        final LazyQueue h = queue;
        final boolean[] done = settled;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        java.util.Arrays.fill(done, false);
        h.put(key(nextSource()), 0L);
        long sum = 0;
        for (Entry x; (x = h.head()) != null; ) {
            long du = x.priority();
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
        final LazyQueue h = map;
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
                t = deadline(h.head().priority());
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
