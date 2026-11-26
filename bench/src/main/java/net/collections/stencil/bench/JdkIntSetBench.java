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

import java.util.HashSet;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** {@code HashSet<Integer>} as a boxed reference point. */
public class JdkIntSetBench extends AbstractIntSetBench {

    private HashSet<Integer> set;

    @Override
    protected void populate() {
        set = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int containsHit() {
        HashSet<Integer> s = set;
        int n = 0;
        for (int k : hits) {
            if (s.contains(k)) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int containsMiss() {
        HashSet<Integer> s = set;
        int n = 0;
        for (int k : misses) {
            if (s.contains(k)) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        HashSet<Integer> s = set;
        KeyDist d = dist;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            s.remove(d.key(next - n));
            s.add(d.key(next));
        }
        churnNext = next;
    }

    @Benchmark
    public HashSet<Integer> build(GcBeforeLargeBuild gc) {
        HashSet<Integer> s = new HashSet<>();
        for (int k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }

    @Benchmark
    public HashSet<Integer> buildPresized(GcBeforeLargeBuild gc) {
        HashSet<Integer> s = HashSet.newHashSet(size);
        for (int k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }
}
