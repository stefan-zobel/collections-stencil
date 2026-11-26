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

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2LongSortedMap;
import it.unimi.dsi.fastutil.longs.Long2LongSortedMaps;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;

/**
 * fastutil {@link Long2LongRBTreeMap}: red-black tree of entry objects with primitive keys and
 * values. fastutil has no floor/ceiling methods; navigation uses the bidirectional key iterator
 * that starts at a given key, ranges use the {@code subMap} view.
 */
public class FastutilLongLongRBTreeBench extends AbstractLongSortedBench {

    private Long2LongSortedMap map;

    @Override
    protected void populate() {
        map = build(null);
    }

    /** Creates an empty map. Subclasses select another tree. */
    protected Long2LongSortedMap newMap() {
        return new Long2LongRBTreeMap();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        Long2LongSortedMap m = map;
        long sum = 0;
        for (long k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        Long2LongSortedMap m = map;
        long sum = 0;
        for (long k : misses) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        Long2LongSortedMap m = map;
        long sum = 0;
        for (long k : misses) {
            LongBidirectionalIterator it = m.keySet().iterator(k);
            if (it.hasPrevious()) {
                sum += it.previousLong();
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        Long2LongSortedMap m = map;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(key(next - n));
            m.put(key(next), longValue(next));
        }
        churnNext = next;
    }

    @Benchmark
    public Long2LongSortedMap build(GcBeforeLargeBuild gc) {
        Long2LongSortedMap m = newMap();
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], longValue(i));
        }
        return m;
    }

    @Benchmark
    public long iterate() {
        long sum = 0;
        for (Long2LongMap.Entry e : Long2LongSortedMaps.fastIterable(map)) {
            sum += e.getLongKey() + e.getLongValue();
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(RANGE_SCANS * RANGE_LEN)
    public long rangeScan() {
        Long2LongSortedMap m = map;
        long[] s = sorted;
        long sum = 0;
        for (int j : rangeStarts) {
            for (Long2LongMap.Entry e : Long2LongSortedMaps.fastIterable(m.subMap(s[j], s[j + RANGE_LEN]))) {
                sum += e.getLongKey() + e.getLongValue();
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        Long2LongSortedMap m = map;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            Long2LongSortedMap sub = m.subMap(s[j], s[j + RANGE_LEN]);
            sub.clear();
            for (int i = j; i < j + RANGE_LEN; i++) {
                m.put(s[i], longValue(i));
            }
        }
        return m.size();
    }
}
