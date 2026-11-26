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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

/** fastutil {@link Object2IntOpenHashMap}: Object[] keys + int[] values, mixed hashCode(), linear probing, LF 0.75. */
public class FastutilObjectIntBench extends AbstractObjectIntBench {

    private Object2IntOpenHashMap<String> map;

    @Override
    protected void populate() {
        Object2IntOpenHashMap<String> m = new Object2IntOpenHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], i);
        }
        map = m;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        Object2IntOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : hits) {
            sum += m.getInt(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        Object2IntOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : misses) {
            sum += m.getInt(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        Object2IntOpenHashMap<String> m = map;
        String[] pool = churnPool;
        int next = churnNext;
        int oldest = churnOldest;
        for (int j = 0; j < OPS; j++) {
            m.removeInt(pool[oldest]);
            m.put(pool[next], next);
            if (++oldest == pool.length) {
                oldest = 0;
            }
            if (++next == pool.length) {
                next = 0;
            }
        }
        churnNext = next;
        churnOldest = oldest;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count() {
        Object2IntOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.addTo(k, 1) + 1; // fastutil returns the old value
        }
        return sum;
    }

    @Benchmark
    public Object2IntOpenHashMap<String> build(GcBeforeLargeBuild gc) {
        Object2IntOpenHashMap<String> m = new Object2IntOpenHashMap<>();
        String[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], i);
        }
        return m;
    }
}
