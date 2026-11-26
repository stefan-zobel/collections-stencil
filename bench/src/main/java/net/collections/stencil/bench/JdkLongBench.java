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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** {@code HashMap<Long, Long>} as a boxed reference point. */
public class JdkLongBench extends AbstractLongLongBench {

    private HashMap<Long, Long> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        HashMap<Long, Long> m = map;
        long sum = 0;
        for (long k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        HashMap<Long, Long> m = map;
        long sum = 0;
        for (long k : misses) {
            Long v = m.get(k);
            if (v != null) {
                sum += v;
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void addTo() {
        HashMap<Long, Long> m = map;
        for (long k : hits) {
            m.merge(k, 1L, Long::sum);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        HashMap<Long, Long> m = map;
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
    public HashMap<Long, Long> build(GcBeforeLargeBuild gc) {
        HashMap<Long, Long> m = new HashMap<>();
        for (long k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }

    @Benchmark
    public HashMap<Long, Long> buildPresized(GcBeforeLargeBuild gc) {
        HashMap<Long, Long> m = HashMap.newHashMap(size);
        for (long k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }
}
