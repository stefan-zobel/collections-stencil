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

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectRBTreeMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectSortedMaps;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;

/**
 * fastutil {@link Long2ObjectRBTreeMap}: red-black tree of entry objects with primitive keys.
 * fastutil has no floor/ceiling methods; navigation uses the bidirectional key iterator that
 * starts at a given key, ranges use the {@code subMap} view.
 */
public class FastutilLongRBTreeBench extends AbstractLongSortedBench {

    private Long2ObjectSortedMap<Object> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    /** Creates an empty map. Subclasses select another tree. */
    protected Long2ObjectSortedMap<Object> newMap() {
        return new Long2ObjectRBTreeMap<>();
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        Long2ObjectSortedMap<Object> m = map;
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
        Long2ObjectSortedMap<Object> m = map;
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
        Long2ObjectSortedMap<Object> m = map;
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
        Long2ObjectSortedMap<Object> m = map;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(key(next - n));
            m.put(key(next), value(next));
        }
        churnNext = next;
    }

    @Benchmark
    public Long2ObjectSortedMap<Object> build(GcBeforeLargeBuild gc) {
        Long2ObjectSortedMap<Object> m = newMap();
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public long iterate() {
        long sum = 0;
        for (Long2ObjectMap.Entry<Object> e : Long2ObjectSortedMaps.fastIterable(map)) {
            sum += e.getLongKey();
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(RANGE_SCANS * RANGE_LEN)
    public long rangeScan() {
        Long2ObjectSortedMap<Object> m = map;
        long[] s = sorted;
        long sum = 0;
        for (int j : rangeStarts) {
            for (Long2ObjectMap.Entry<Object> e : Long2ObjectSortedMaps.fastIterable(m.subMap(s[j], s[j + RANGE_LEN]))) {
                sum += e.getLongKey();
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        Long2ObjectSortedMap<Object> m = map;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            Long2ObjectSortedMap<Object> sub = m.subMap(s[j], s[j + RANGE_LEN]);
            sub.clear();
            for (int i = j; i < j + RANGE_LEN; i++) {
                m.put(s[i], value(i));
            }
        }
        return m.size();
    }
}
