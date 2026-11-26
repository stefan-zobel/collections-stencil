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
import net.generated.example.LongObjectTreeMap;
import net.generated.example.function.LongObjectConsumer;

/** stencil {@link LongObjectTreeMap}: B+ tree over node pools, navigation through index handles. */
public class StencilLongTreeBench extends AbstractLongSortedBench {

    private LongObjectTreeMap<Object> map;
    private final LongObjectConsumer<Object> visit = (k, v) -> sink += k;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        LongObjectTreeMap<Object> m = map;
        int n = 0;
        for (long k : hits) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        LongObjectTreeMap<Object> m = map;
        int n = 0;
        for (long k : misses) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        LongObjectTreeMap<Object> m = map;
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
        LongObjectTreeMap<Object> m = map;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(key(next - n));
            m.put(key(next), value(next));
        }
        churnNext = next;
    }

    @Benchmark
    public LongObjectTreeMap<Object> build(GcBeforeLargeBuild gc) {
        LongObjectTreeMap<Object> m = new LongObjectTreeMap<>();
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
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
        LongObjectTreeMap<Object> m = map;
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
        LongObjectTreeMap<Object> m = map;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            m.removeRange(s[j], s[j + RANGE_LEN]);
            for (int i = j; i < j + RANGE_LEN; i++) {
                m.put(s[i], value(i));
            }
        }
        return m.size();
    }
}
