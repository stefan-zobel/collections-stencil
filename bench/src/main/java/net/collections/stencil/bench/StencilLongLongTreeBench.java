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
import net.generated.example.LongLongTreeMap;
import net.generated.example.function.LongLongConsumer;

/** stencil {@link LongLongTreeMap}: B+ tree over node pools with a {@code long[]} of values. */
public class StencilLongLongTreeBench extends AbstractLongSortedBench {

    private LongLongTreeMap map;
    private final LongLongConsumer visit = (k, v) -> sink += k + v;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        LongLongTreeMap m = map;
        long sum = 0;
        for (long k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        LongLongTreeMap m = map;
        long sum = 0;
        for (long k : misses) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        LongLongTreeMap m = map;
        long sum = 0;
        for (long k : misses) {
            int i = m.floorIndex(k);
            if (i >= 0) {
                sum += m.indexKey(i);
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        LongLongTreeMap m = map;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(key(next - n));
            m.put(key(next), longValue(next));
        }
        churnNext = next;
    }

    @Benchmark
    public LongLongTreeMap build(GcBeforeLargeBuild gc) {
        LongLongTreeMap m = new LongLongTreeMap();
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], longValue(i));
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
    @OperationsPerInvocation(RANGE_SCANS * RANGE_LEN)
    public long rangeScan() {
        LongLongTreeMap m = map;
        long[] s = sorted;
        sink = 0;
        for (int j : rangeStarts) {
            m.forEachInRange(s[j], s[j + RANGE_LEN], visit);
        }
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        LongLongTreeMap m = map;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            m.removeRange(s[j], s[j + RANGE_LEN]);
            for (int i = j; i < j + RANGE_LEN; i++) {
                m.put(s[i], longValue(i));
            }
        }
        return m.size();
    }
}
