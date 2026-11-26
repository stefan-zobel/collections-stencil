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

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;

/** fastutil {@link Object2IntLinkedOpenHashMap}: a linear-probing table with a link word per slot. */
public class FastutilObjectIntLinkedBench extends AbstractObjectIntLinkedBench {

    private Object2IntLinkedOpenHashMap<String> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        Object2IntLinkedOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : hits) {
            sum += m.getInt(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        Object2IntLinkedOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : misses) {
            sum += m.getInt(k);
        }
        return sum;
    }

    @Benchmark
    public Object2IntLinkedOpenHashMap<String> build(GcBeforeLargeBuild gc) {
        Object2IntLinkedOpenHashMap<String> m = new Object2IntLinkedOpenHashMap<>();
        String[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], i);
        }
        return m;
    }

    @Benchmark
    public long iterate() {
        sink = 0;
        map.object2IntEntrySet().fastForEach(e -> sink += e.getIntValue());
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int churn() {
        Object2IntLinkedOpenHashMap<String> m = map;
        String[] p = present;
        String[] sp = spare;
        int c = spareCursor;
        for (int j : victims) {
            String old = p[j];
            m.removeInt(old);
            String k = sp[c];
            m.put(k, j);
            p[j] = k;
            sp[c] = old;
            c = c + 1 == sp.length ? 0 : c + 1;
        }
        spareCursor = c;
        return m.size();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int fifo() {
        Object2IntLinkedOpenHashMap<String> m = map;
        String[] pool = fifoPool;
        int next = fifoNext;
        for (int j = 0; j < OPS; j++) {
            m.removeFirstInt();
            m.put(pool[next], next);
            if (++next == pool.length) {
                next = 0;
            }
        }
        fifoNext = next;
        return m.size();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public String touch() {
        Object2IntLinkedOpenHashMap<String> m = map;
        int j = 0;
        for (String k : hits) {
            m.putAndMoveToLast(k, j++);
        }
        return m.lastKey();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count() {
        Object2IntLinkedOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.addTo(k, 1) + 1; // fastutil returns the old value
        }
        return sum;
    }
}
