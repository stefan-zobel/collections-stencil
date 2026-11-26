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

import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** {@code TreeMap<Long, Object>} as the boxed reference point. */
public class JdkLongTreeBench extends AbstractLongSortedBench {

    private TreeMap<Long, Object> map;
    private final BiConsumer<Long, Object> visit = (k, v) -> sink += k;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        TreeMap<Long, Object> m = map;
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
        TreeMap<Long, Object> m = map;
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
        TreeMap<Long, Object> m = map;
        long sum = 0;
        for (long k : misses) {
            Long f = m.floorKey(k);
            if (f != null) {
                sum += f;
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        TreeMap<Long, Object> m = map;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(key(next - n));
            m.put(key(next), value(next));
        }
        churnNext = next;
    }

    @Benchmark
    public TreeMap<Long, Object> build(GcBeforeLargeBuild gc) {
        TreeMap<Long, Object> m = new TreeMap<>();
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
        TreeMap<Long, Object> m = map;
        long[] s = sorted;
        long sum = 0;
        for (int j : rangeStarts) {
            for (Map.Entry<Long, Object> e : m.subMap(s[j], true, s[j + RANGE_LEN], false).entrySet()) {
                sum += e.getKey();
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        TreeMap<Long, Object> m = map;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            Map<Long, Object> sub = m.subMap(s[j], true, s[j + RANGE_LEN], false);
            sub.clear();
            for (int i = j; i < j + RANGE_LEN; i++) {
                m.put(s[i], value(i));
            }
        }
        return m.size();
    }
}
