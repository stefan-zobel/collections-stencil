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
import net.generated.example.LongObjectHashMap;

/** stencil {@link LongObjectHashMap}: long[] keys + Object[] values, Fibonacci hashing, linear probing, LF 0.75. */
public class StencilLongObjectBench extends AbstractLongObjectBench {

    private LongObjectHashMap<Object> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    /** Creates an empty map for {@code expectedSize} entries. Subclasses vary the configuration. */
    protected LongObjectHashMap<Object> newMap(int expectedSize) {
        return new LongObjectHashMap<>(expectedSize);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        LongObjectHashMap<Object> m = map;
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
        LongObjectHashMap<Object> m = map;
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
    public void churn() {
        LongObjectHashMap<Object> m = map;
        KeyDist d = dist;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(d.longKey(next - n));
            m.put(d.longKey(next), value(next));
        }
        churnNext = next;
    }

    @Benchmark
    public LongObjectHashMap<Object> build(GcBeforeLargeBuild gc) {
        LongObjectHashMap<Object> m = newMap(LongObjectHashMap.DEFAULT_EXPECTED_SIZE);
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public LongObjectHashMap<Object> buildPresized(GcBeforeLargeBuild gc) {
        LongObjectHashMap<Object> m = newMap(size);
        long[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public LongObjectHashMap<Object> computeIfAbsent(GcBeforeLargeBuild gc) {
        LongObjectHashMap<Object> m = newMap(LongObjectHashMap.DEFAULT_EXPECTED_SIZE);
        long[] ks = nextBuildKeys();
        for (long k : ks) {
            m.computeIfAbsent(k, COMPUTE);
        }
        for (long k : ks) {
            m.computeIfAbsent(k, COMPUTE);
        }
        return m;
    }
}
