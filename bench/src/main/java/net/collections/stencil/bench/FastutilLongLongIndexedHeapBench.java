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

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongHeapIndirectPriorityQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/**
 * The workaround with fastutil: a {@link Long2IntOpenHashMap} assigns a dense id to every key
 * in the queue, a {@link LongHeapIndirectPriorityQueue} orders the ids by an external
 * {@code long[]} of priorities ({@code changed(id)} after a priority changed), a {@code long[]}
 * gives the key of an id, and a stack recycles the ids of removed keys.
 */
public class FastutilLongLongIndexedHeapBench extends AbstractLongLongIndexedHeapBench {

    /** Keys to dense ids, the queue of ids with its priorities, the key of every id, and the free ids. */
    static final class KeyedQueue {
        final Long2IntOpenHashMap ids;
        final long[] prios;
        final LongHeapIndirectPriorityQueue queue;
        final long[] keys;
        final int[] free;
        int freeCount;
        int nextId;

        KeyedQueue(int capacity) {
            ids = new Long2IntOpenHashMap(capacity);
            ids.defaultReturnValue(-1);
            prios = new long[capacity];
            queue = new LongHeapIndirectPriorityQueue(prios, capacity);
            keys = new long[capacity];
            free = new int[capacity];
        }

        /** A new id for {@code key}, with priority {@code p}, in the queue. */
        void add(long key, long p) {
            int id = freeCount > 0 ? free[--freeCount] : nextId++;
            ids.put(key, id);
            keys[id] = key;
            prios[id] = p;
            queue.enqueue(id);
        }

        void put(long key, long p) {
            int id = ids.get(key);
            if (id >= 0) {
                prios[id] = p;
                queue.changed(id);
            } else {
                add(key, p);
            }
        }

        void putIfBetter(long key, long p) {
            int id = ids.get(key);
            if (id < 0) {
                add(key, p);
            } else if (p < prios[id]) {
                prios[id] = p;
                queue.changed(id);
            }
        }

        long firstPriority() {
            return prios[queue.first()];
        }

        long poll() {
            int id = queue.dequeue();
            long key = keys[id];
            ids.remove(key);
            free[freeCount++] = id;
            return key;
        }
    }

    /** The timers of {@code timers}; also measured by the footprint. */
    private KeyedQueue map;
    /** The queue of {@code dijkstra}, empty between invocations. */
    private KeyedQueue queue;
    private boolean[] settled;

    @Override
    protected void populate() {
        queue = new KeyedQueue(size);
        settled = new boolean[size];
        KeyedQueue timers = new KeyedQueue(size);
        for (int i = 0; i < size; i++) {
            timers.put(live[i], initialPriorities[i]);
        }
        map = timers;
    }

    @Benchmark
    public long dijkstra() {
        final KeyedQueue h = queue;
        final boolean[] done = settled;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        java.util.Arrays.fill(done, false);
        h.put(key(nextSource()), 0L);
        long sum = 0;
        while (!h.queue.isEmpty()) {
            long du = h.firstPriority();
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
        final KeyedQueue h = map;
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
                t = deadline(h.firstPriority());
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
