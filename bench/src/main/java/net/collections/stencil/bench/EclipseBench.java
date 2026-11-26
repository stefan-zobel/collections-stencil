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

import org.eclipse.collections.impl.map.mutable.primitive.IntIntHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** Eclipse Collections {@link IntIntHashMap}: interleaved int[] key/value, hybrid probing, LF 0.5. */
public class EclipseBench extends AbstractIntIntBench {

    private IntIntHashMap map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        IntIntHashMap m = map;
        int sum = 0;
        for (int k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        IntIntHashMap m = map;
        int sum = 0;
        for (int k : misses) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void addTo() {
        IntIntHashMap m = map;
        for (int k : hits) {
            m.addToValue(k, 1);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        IntIntHashMap m = map;
        KeyDist d = dist;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(d.key(next - n));
            int k = d.key(next);
            m.put(k, k);
        }
        churnNext = next;
    }

    @Benchmark
    public IntIntHashMap build(GcBeforeLargeBuild gc) {
        IntIntHashMap m = new IntIntHashMap();
        for (int k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }

    @Benchmark
    public IntIntHashMap buildPresized(GcBeforeLargeBuild gc) {
        IntIntHashMap m = new IntIntHashMap(size);
        for (int k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }
}
