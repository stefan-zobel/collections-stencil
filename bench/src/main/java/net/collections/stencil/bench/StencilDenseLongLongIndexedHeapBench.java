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
import net.generated.example.IntLongIndexedHeap;
import net.generated.example.LongIntHashMap;

/**
 * The usual workaround with the dense indexed heap: a stencil {@link LongIntHashMap} assigns a
 * dense id to every key in the queue, a stencil {@link IntLongIndexedHeap} orders the ids, a
 * {@code long[]} gives the key of an id, and a stack recycles the ids of removed keys.
 */
public class StencilDenseLongLongIndexedHeapBench extends AbstractLongLongIndexedHeapBench {

    /** Keys to dense ids, the heap of ids, the key of every id, and the free ids. */
    static final class KeyedHeap {
        final LongIntHashMap ids;
        final IntLongIndexedHeap heap;
        final long[] keys;
        final int[] free;
        int freeCount;
        int nextId;

        KeyedHeap(int capacity) {
            ids = new LongIntHashMap(capacity);
            heap = new IntLongIndexedHeap(capacity);
            keys = new long[capacity];
            free = new int[capacity];
        }

        /** A new id for {@code key}. */
        int add(long key) {
            int id = freeCount > 0 ? free[--freeCount] : nextId++;
            ids.put(key, id);
            keys[id] = key;
            return id;
        }

        void put(long key, long p) {
            int id = ids.getOrDefault(key, -1);
            heap.put(id >= 0 ? id : add(key), p);
        }

        void putIfBetter(long key, long p) {
            int id = ids.getOrDefault(key, -1);
            if (id >= 0) {
                heap.putIfBetter(id, p);
            } else {
                heap.put(add(key), p);
            }
        }

        long poll() {
            int id = heap.poll();
            long key = keys[id];
            ids.remove(key);
            free[freeCount++] = id;
            return key;
        }
    }

    /** The timers of {@code timers}; also measured by the footprint. */
    private KeyedHeap map;
    /** The queue of {@code dijkstra}, empty between invocations. */
    private KeyedHeap queue;
    private boolean[] settled;

    @Override
    protected void populate() {
        queue = new KeyedHeap(size);
        settled = new boolean[size];
        KeyedHeap timers = new KeyedHeap(size);
        for (int i = 0; i < size; i++) {
            timers.put(live[i], initialPriorities[i]);
        }
        map = timers;
    }

    @Benchmark
    public long dijkstra() {
        final KeyedHeap h = queue;
        final boolean[] done = settled;
        final int[] st = start;
        final int[] tt = to;
        final int[] w = weight;
        java.util.Arrays.fill(done, false);
        h.put(key(nextSource()), 0L);
        long sum = 0;
        while (!h.heap.isEmpty()) {
            long du = h.heap.peekPriority();
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
        final KeyedHeap h = map;
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
                t = deadline(h.heap.peekPriority());
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
