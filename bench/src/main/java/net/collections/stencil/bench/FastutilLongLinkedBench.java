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

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

/** fastutil {@link Long2ObjectLinkedOpenHashMap}: a linear-probing table with a link word per slot. */
public class FastutilLongLinkedBench extends AbstractLongLinkedBench {

    private Long2ObjectLinkedOpenHashMap<Object> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        Long2ObjectLinkedOpenHashMap<Object> m = map;
        int n = 0;
        for (long k : hits) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        Long2ObjectLinkedOpenHashMap<Object> m = map;
        int n = 0;
        for (long k : misses) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    public Long2ObjectLinkedOpenHashMap<Object> build(GcBeforeLargeBuild gc) {
        Long2ObjectLinkedOpenHashMap<Object> m = new Long2ObjectLinkedOpenHashMap<>();
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public long iterate() {
        sink = 0;
        map.long2ObjectEntrySet().fastForEach(e -> sink += e.getLongKey());
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int churn() {
        Long2ObjectLinkedOpenHashMap<Object> m = map;
        long[] p = present;
        int next = nextNew;
        for (int j : victims) {
            m.remove(p[j]);
            long k = key(next++);
            m.put(k, value(k));
            p[j] = k;
        }
        nextNew = next;
        return m.size();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int fifo() {
        Long2ObjectLinkedOpenHashMap<Object> m = map;
        int next = nextNew;
        for (int j = 0; j < OPS; j++) {
            m.removeFirst();
            long k = key(next++);
            m.put(k, value(k));
        }
        nextNew = next;
        return m.size();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long touch() {
        Long2ObjectLinkedOpenHashMap<Object> m = map;
        for (long k : hits) {
            m.putAndMoveToLast(k, value(k));
        }
        return m.lastLongKey();
    }
}
