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

import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** {@code LinkedHashMap<String, Integer>} as the boxed reference point. */
public class JdkObjectIntLinkedBench extends AbstractObjectIntLinkedBench {

    private LinkedHashMap<String, Integer> map;
    private final BiConsumer<String, Integer> visit = (k, v) -> sink += v;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        LinkedHashMap<String, Integer> m = map;
        long sum = 0;
        for (String k : hits) {
            Integer v = m.get(k);
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        LinkedHashMap<String, Integer> m = map;
        long sum = 0;
        for (String k : misses) {
            Integer v = m.get(k);
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }

    @Benchmark
    public LinkedHashMap<String, Integer> build(GcBeforeLargeBuild gc) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        String[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], i);
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
        LinkedHashMap<String, Integer> m = map;
        String[] p = present;
        String[] sp = spare;
        int c = spareCursor;
        for (int j : victims) {
            String old = p[j];
            m.remove(old);
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
        LinkedHashMap<String, Integer> m = map;
        String[] pool = fifoPool;
        int next = fifoNext;
        for (int j = 0; j < OPS; j++) {
            m.pollFirstEntry();
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
        LinkedHashMap<String, Integer> m = map;
        int j = 0;
        for (String k : hits) {
            m.putLast(k, j++);
        }
        return m.lastEntry().getKey();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count() {
        LinkedHashMap<String, Integer> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.merge(k, 1, Integer::sum);
        }
        return sum;
    }
}
