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

import org.eclipse.collections.impl.map.mutable.primitive.ObjectIntHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** Eclipse Collections {@link ObjectIntHashMap}: Object[] keys + int[] values, open addressing with sentinels. */
public class EclipseObjectIntBench extends AbstractObjectIntBench {

    private ObjectIntHashMap<String> map;

    @Override
    protected void populate() {
        ObjectIntHashMap<String> m = new ObjectIntHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], i);
        }
        map = m;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        ObjectIntHashMap<String> m = map;
        long sum = 0;
        for (String k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        ObjectIntHashMap<String> m = map;
        long sum = 0;
        for (String k : misses) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        ObjectIntHashMap<String> m = map;
        String[] pool = churnPool;
        int next = churnNext;
        int oldest = churnOldest;
        for (int j = 0; j < OPS; j++) {
            m.removeKey(pool[oldest]);
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
        ObjectIntHashMap<String> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.addToValue(k, 1);
        }
        return sum;
    }

    @Benchmark
    public ObjectIntHashMap<String> build(GcBeforeLargeBuild gc) {
        ObjectIntHashMap<String> m = new ObjectIntHashMap<>();
        String[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], i);
        }
        return m;
    }
}
