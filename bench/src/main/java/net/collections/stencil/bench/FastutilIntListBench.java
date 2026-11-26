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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** fastutil {@link IntArrayList}. */
public class FastutilIntListBench extends AbstractIntListBench {

    /** The list of {@code get}, {@code iterate}, and {@code insertRemove} (named {@code map} for the footprint measurement). */
    private IntArrayList map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    public IntArrayList build(GcBeforeLargeBuild gc) {
        IntArrayList l = new IntArrayList();
        for (int v : nextValues()) {
            l.add(v);
        }
        return l;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long get() {
        IntArrayList l = map;
        long sum = 0;
        for (int i : indexes) {
            sum += l.getInt(i);
        }
        return sum;
    }

    @Benchmark
    public long iterate() {
        long[] sum = {0};
        map.forEach((int v) -> {
            sum[0] += v;
        });
        return sum[0];
    }

    @Benchmark
    @OperationsPerInvocation(SHIFT_OPS)
    public long insertRemove() {
        IntArrayList l = map;
        long sum = 0;
        for (int j = 0; j < SHIFT_OPS; j++) {
            l.add(insertAt[j], shiftValues[j]);
            sum += l.removeInt(removeAt[j]);
        }
        return sum;
    }
}
