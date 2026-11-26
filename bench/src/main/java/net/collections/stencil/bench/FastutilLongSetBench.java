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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** fastutil {@link LongOpenHashSet}: linear probing, LF 0.75. */
public class FastutilLongSetBench extends AbstractLongSetBench {

    private LongOpenHashSet set;

    @Override
    protected void populate() {
        set = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int containsHit() {
        LongOpenHashSet s = set;
        int n = 0;
        for (long k : hits) {
            if (s.contains(k)) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int containsMiss() {
        LongOpenHashSet s = set;
        int n = 0;
        for (long k : misses) {
            if (s.contains(k)) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        LongOpenHashSet s = set;
        KeyDist d = dist;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            s.remove(d.longKey(next - n));
            s.add(d.longKey(next));
        }
        churnNext = next;
    }

    @Benchmark
    public LongOpenHashSet build(GcBeforeLargeBuild gc) {
        LongOpenHashSet s = new LongOpenHashSet();
        for (long k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }

    @Benchmark
    public LongOpenHashSet buildPresized(GcBeforeLargeBuild gc) {
        LongOpenHashSet s = new LongOpenHashSet(size);
        for (long k : nextBuildKeys()) {
            s.add(k);
        }
        return s;
    }
}
