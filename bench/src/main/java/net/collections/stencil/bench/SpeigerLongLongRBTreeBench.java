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

import speiger.src.collections.longs.functions.consumer.LongLongConsumer;
import speiger.src.collections.longs.maps.impl.tree.Long2LongRBTreeMap;

/**
 * Speiger Primitive-Collections {@link Long2LongRBTreeMap}: red-black tree of entry objects with
 * primitive keys and values.
 * <p>
 * The map reports a missing floor key through a configurable key value; the adapter sets it to
 * {@code Long.MIN_VALUE}, which is not a key of the benchmark. Lookups use
 * {@code getOrDefault(key, 0)}, so that an absent key yields {@code 0} as in the other maps.
 */
public class SpeigerLongLongRBTreeBench extends AbstractLongSortedBench {

    private static final long NONE = Long.MIN_VALUE;

    private Long2LongRBTreeMap map;
    private final LongLongConsumer visit = (k, v) -> sink += k + v;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        Long2LongRBTreeMap m = map;
        long sum = 0;
        for (long k : hits) {
            sum += m.getOrDefault(k, 0L);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getMiss() {
        Long2LongRBTreeMap m = map;
        long sum = 0;
        for (long k : misses) {
            sum += m.getOrDefault(k, 0L);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        Long2LongRBTreeMap m = map;
        long sum = 0;
        for (long k : misses) {
            long f = m.floorKey(k);
            if (f != NONE) {
                sum += f;
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        Long2LongRBTreeMap m = map;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(key(next - n));
            m.put(key(next), longValue(next));
        }
        churnNext = next;
    }

    @Benchmark
    public Long2LongRBTreeMap build(GcBeforeLargeBuild gc) {
        Long2LongRBTreeMap m = new Long2LongRBTreeMap();
        m.setDefaultMaxValue(NONE);
        m.setDefaultMinValue(NONE);
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
        Long2LongRBTreeMap m = map;
        long[] s = sorted;
        sink = 0;
        for (int j : rangeStarts) {
            m.subMap(s[j], true, s[j + RANGE_LEN], false).forEach(visit);
        }
        return sink;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        Long2LongRBTreeMap m = map;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            m.subMap(s[j], true, s[j + RANGE_LEN], false).clear();
            for (int i = j; i < j + RANGE_LEN; i++) {
                m.put(s[i], longValue(i));
            }
        }
        return m.size();
    }
}
