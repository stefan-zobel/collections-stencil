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

import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongRBTreeSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;

/**
 * fastutil {@link LongRBTreeSet}: red-black tree of entry objects with primitive elements.
 * fastutil has no floor/ceiling methods; navigation uses the bidirectional iterator that starts
 * at a given element, ranges use the {@code subSet} view.
 */
public class FastutilLongRBTreeSetBench extends AbstractLongSortedBench {

    private LongSortedSet set;

    @Override
    protected void populate() {
        set = build(null);
    }

    /** Creates an empty set. Subclasses select another tree. */
    protected LongSortedSet newSet() {
        return new LongRBTreeSet();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        LongSortedSet s = set;
        int found = 0;
        for (long k : hits) {
            found += s.contains(k) ? 1 : 0;
        }
        return found;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        LongSortedSet s = set;
        int found = 0;
        for (long k : misses) {
            found += s.contains(k) ? 1 : 0;
        }
        return found;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        LongSortedSet s = set;
        long sum = 0;
        for (long k : misses) {
            LongBidirectionalIterator it = s.iterator(k);
            if (it.hasPrevious()) {
                sum += it.previousLong();
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        LongSortedSet s = set;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            s.remove(key(next - n));
            s.add(key(next));
        }
        churnNext = next;
    }

    @Benchmark
    public LongSortedSet build(GcBeforeLargeBuild gc) {
        LongSortedSet s = newSet();
        for (long k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }

    @Benchmark
    public long iterate() {
        long sum = 0;
        for (LongIterator it = set.iterator(); it.hasNext(); ) {
            sum += it.nextLong();
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(RANGE_SCANS * RANGE_LEN)
    public long rangeScan() {
        LongSortedSet t = set;
        long[] s = sorted;
        long sum = 0;
        for (int j : rangeStarts) {
            for (LongIterator it = t.subSet(s[j], s[j + RANGE_LEN]).iterator(); it.hasNext(); ) {
                sum += it.nextLong();
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        LongSortedSet t = set;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            t.subSet(s[j], s[j + RANGE_LEN]).clear();
            for (int i = j; i < j + RANGE_LEN; i++) {
                t.add(s[i]);
            }
        }
        return t.size();
    }
}
