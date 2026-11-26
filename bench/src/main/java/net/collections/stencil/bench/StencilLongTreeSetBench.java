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

import java.util.function.LongConsumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import net.generated.example.LongTreeSet;

/** stencil {@link LongTreeSet}: B+ tree over node pools, leaves with a {@code long[]} of elements only. */
public class StencilLongTreeSetBench extends AbstractLongSortedBench {

    private LongTreeSet set;
    private final LongConsumer visit = k -> sink += k;

    @Override
    protected void populate() {
        set = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        LongTreeSet s = set;
        int found = 0;
        for (long k : hits) {
            found += s.contains(k) ? 1 : 0;
        }
        return found;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        LongTreeSet s = set;
        int found = 0;
        for (long k : misses) {
            found += s.contains(k) ? 1 : 0;
        }
        return found;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        LongTreeSet s = set;
        long sum = 0;
        for (long k : misses) {
            int i = s.floorIndex(k);
            if (i >= 0) {
                sum += s.indexKey(i);
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        LongTreeSet s = set;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            s.remove(key(next - n));
            s.add(key(next));
        }
        churnNext = next;
    }

    @Benchmark
    public LongTreeSet build(GcBeforeLargeBuild gc) {
        LongTreeSet s = new LongTreeSet();
        for (long k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }

    @Benchmark
    public long iterate() {
        sink = 0;
        set.forEach(visit);
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(RANGE_SCANS * RANGE_LEN)
    public long rangeScan() {
        LongTreeSet t = set;
        long[] s = sorted;
        sink = 0;
        for (int j : rangeStarts) {
            t.forEachInRange(s[j], s[j + RANGE_LEN], visit);
        }
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        LongTreeSet t = set;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            t.removeRange(s[j], s[j + RANGE_LEN]);
            for (int i = j; i < j + RANGE_LEN; i++) {
                t.add(s[i]);
            }
        }
        return t.size();
    }
}
