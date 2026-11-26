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

import java.util.TreeSet;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** {@code TreeSet<Long>} as the boxed reference point: a {@code TreeMap} with boxed keys. */
public class JdkLongTreeSetBench extends AbstractLongSortedBench {

    private TreeSet<Long> set;

    @Override
    protected void populate() {
        set = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        TreeSet<Long> s = set;
        int found = 0;
        for (long k : hits) {
            found += s.contains(k) ? 1 : 0;
        }
        return found;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        TreeSet<Long> s = set;
        int found = 0;
        for (long k : misses) {
            found += s.contains(k) ? 1 : 0;
        }
        return found;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long floor() {
        TreeSet<Long> s = set;
        long sum = 0;
        for (long k : misses) {
            Long f = s.floor(k);
            if (f != null) {
                sum += f;
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        TreeSet<Long> s = set;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            s.remove(key(next - n));
            s.add(key(next));
        }
        churnNext = next;
    }

    @Benchmark
    public TreeSet<Long> build(GcBeforeLargeBuild gc) {
        TreeSet<Long> s = new TreeSet<>();
        for (long k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }

    @Benchmark
    public long iterate() {
        long sum = 0;
        for (long k : set) {
            sum += k;
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(RANGE_SCANS * RANGE_LEN)
    public long rangeScan() {
        TreeSet<Long> t = set;
        long[] s = sorted;
        long sum = 0;
        for (int j : rangeStarts) {
            for (long k : t.subSet(s[j], true, s[j + RANGE_LEN], false)) {
                sum += k;
            }
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(REMOVE_ROUNDS * RANGE_LEN)
    public int removeRange() {
        TreeSet<Long> t = set;
        long[] s = sorted;
        for (int r = 0; r < REMOVE_ROUNDS; r++) {
            int j = nextRangeStart();
            t.subSet(s[j], true, s[j + RANGE_LEN], false).clear();
            for (int i = j; i < j + RANGE_LEN; i++) {
                t.add(s[i]);
            }
        }
        return t.size();
    }
}
