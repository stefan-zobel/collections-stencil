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

/** JDK {@link HashMap}{@code <String, Integer>}: node per entry, boxed values. */
public class JdkObjectIntBench extends AbstractObjectIntBench {

    private HashMap<String, Integer> map;

    @Override
    protected void populate() {
        HashMap<String, Integer> m = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], i);
        }
        map = m;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        HashMap<String, Integer> m = map;
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
        HashMap<String, Integer> m = map;
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
    @OperationsPerInvocation(OPS)
    public void churn() {
        HashMap<String, Integer> m = map;
        String[] pool = churnPool;
        int next = churnNext;
        int oldest = churnOldest;
        for (int j = 0; j < OPS; j++) {
            m.remove(pool[oldest]);
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
        HashMap<String, Integer> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.merge(k, 1, Integer::sum);
        }
        return sum;
    }

    @Benchmark
    public HashMap<String, Integer> build(GcBeforeLargeBuild gc) {
        HashMap<String, Integer> m = new HashMap<>();
        String[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], i);
        }
        return m;
    }
}
