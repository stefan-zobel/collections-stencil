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

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** fastutil {@link IntArrayFIFOQueue}: a ring buffer that also removes at the end. */
public class FastutilIntDequeBench extends AbstractIntDequeBench {

    /** The deque of {@code fifo} and {@code window} (named {@code map} for the footprint measurement). */
    private IntArrayFIFOQueue map;

    @Override
    protected void populate() {
        map = filled(nextValues());
    }

    private static IntArrayFIFOQueue filled(int[] values) {
        IntArrayFIFOQueue d = new IntArrayFIFOQueue();
        for (int v : values) {
            d.enqueue(v);
        }
        return d;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long fifo() {
        IntArrayFIFOQueue d = map;
        long sum = 0;
        for (int v : opsValues) {
            d.enqueue(v);
            sum += d.dequeueInt();
        }
        return sum;
    }

    @Benchmark
    public long stack() {
        IntArrayFIFOQueue d = filled(nextValues());
        long check = 0;
        for (int k = 1; !d.isEmpty(); k++) {
            check += (long) d.dequeueLastInt() * k;
        }
        return check;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long window() {
        IntArrayFIFOQueue d = map;
        final int[] values = opsValues;
        for (int j = 0; j < OPS; j += WINDOW_BLOCK) {
            for (int k = j; k < j + WINDOW_BLOCK; k++) {
                d.enqueue(values[k]);
            }
            for (int k = 0; k < WINDOW_BLOCK; k++) {
                d.dequeueInt();
            }
        }
        return (long) d.firstInt() + d.size();
    }
}
