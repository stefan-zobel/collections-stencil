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

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

/** fastutil {@link Int2IntOpenHashMap}: separate key/value arrays, linear probing, LF 0.75. */
public class FastutilBench extends AbstractIntIntBench {

    private Int2IntOpenHashMap map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        Int2IntOpenHashMap m = map;
        int sum = 0;
        for (int k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        Int2IntOpenHashMap m = map;
        int sum = 0;
        for (int k : misses) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void addTo() {
        Int2IntOpenHashMap m = map;
        for (int k : hits) {
            m.addTo(k, 1);
        }
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        Int2IntOpenHashMap m = map;
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
    public Int2IntOpenHashMap build(GcBeforeLargeBuild gc) {
        Int2IntOpenHashMap m = new Int2IntOpenHashMap();
        for (int k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }

    @Benchmark
    public Int2IntOpenHashMap buildPresized(GcBeforeLargeBuild gc) {
        Int2IntOpenHashMap m = new Int2IntOpenHashMap(size);
        for (int k : nextBuildKeys()) {
            m.put(k, k);
        }
        return m;
    }
}
