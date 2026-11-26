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

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;

/** fastutil {@link Long2LongOpenHashMap}: separate key/value arrays, linear probing, LF 0.75. */
public class FastutilLongBench extends AbstractLongLongBench {

    private Long2LongOpenHashMap map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        Long2LongOpenHashMap m = map;
        long sum = 0;
        for (long k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        Long2LongOpenHashMap m = map;
        long sum = 0;
        for (long k : misses) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void addTo() {
        Long2LongOpenHashMap m = map;
        for (long k : hits) {
            m.addTo(k, 1);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        Long2LongOpenHashMap m = map;
        KeyDist d = dist;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(d.longKey(next - n));
            long k = d.longKey(next);
            m.put(k, k);
        }
        churnNext = next;
    }

    @Benchmark
    public Long2LongOpenHashMap build(GcBeforeLargeBuild gc) {
        Long2LongOpenHashMap m = new Long2LongOpenHashMap();
        for (long k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }

    @Benchmark
    public Long2LongOpenHashMap buildPresized(GcBeforeLargeBuild gc) {
        Long2LongOpenHashMap m = new Long2LongOpenHashMap(size);
        for (long k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }
}
