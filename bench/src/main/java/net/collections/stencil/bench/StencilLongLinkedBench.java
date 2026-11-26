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
import net.generated.example.LongObjectLinkedHashMap;
import net.generated.example.function.LongObjectConsumer;

/** stencil {@link LongObjectLinkedHashMap}: entries stored densely in insertion order, with an index table. */
public class StencilLongLinkedBench extends AbstractLongLinkedBench {

    private LongObjectLinkedHashMap<Object> map;
    private final LongObjectConsumer<Object> visit = (k, v) -> sink += k;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        LongObjectLinkedHashMap<Object> m = map;
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
        LongObjectLinkedHashMap<Object> m = map;
        int n = 0;
        for (long k : misses) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    public LongObjectLinkedHashMap<Object> build(GcBeforeLargeBuild gc) {
        LongObjectLinkedHashMap<Object> m = new LongObjectLinkedHashMap<>();
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public long iterate() {
        sink = 0;
        map.forEach(visit);
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int churn() {
        LongObjectLinkedHashMap<Object> m = map;
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
        LongObjectLinkedHashMap<Object> m = map;
        int next = nextNew;
        for (int j = 0; j < OPS; j++) {
            m.pollFirst();
            long k = key(next++);
            m.put(k, value(k));
        }
        nextNew = next;
        return m.size();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long touch() {
        LongObjectLinkedHashMap<Object> m = map;
        for (long k : hits) {
            m.putLast(k, value(k));
        }
        return m.lastKey();
    }
}
