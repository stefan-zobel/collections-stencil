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

import com.carrotsearch.hppc.IntObjectHashMap;

/**
 * HPPC {@link IntObjectHashMap}: separate key/value arrays, seeded mixing, linear probing, LF 0.75.
 * HPPC has no {@code computeIfAbsent}; the benchmark uses its index API (one lookup).
 */
public class HppcIntObjectBench extends AbstractIntObjectBench {

    private IntObjectHashMap<Object> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        IntObjectHashMap<Object> m = map;
        int n = 0;
        for (int k : hits) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getMiss() {
        IntObjectHashMap<Object> m = map;
        int n = 0;
        for (int k : misses) {
            if (m.get(k) != null) {
                n++;
            }
        }
        return n;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public void churn() {
        IntObjectHashMap<Object> m = map;
        KeyDist d = dist;
        int n = size;
        int next = churnNext;
        for (int j = 0; j < OPS; j++, next++) {
            m.remove(d.key(next - n));
            m.put(d.key(next), value(next));
        }
        churnNext = next;
    }

    @Benchmark
    public IntObjectHashMap<Object> build(GcBeforeLargeBuild gc) {
        IntObjectHashMap<Object> m = new IntObjectHashMap<>();
        int[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public IntObjectHashMap<Object> buildPresized(GcBeforeLargeBuild gc) {
        IntObjectHashMap<Object> m = new IntObjectHashMap<>(size);
        int[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public IntObjectHashMap<Object> computeIfAbsent(GcBeforeLargeBuild gc) {
        IntObjectHashMap<Object> m = new IntObjectHashMap<>();
        int[] ks = nextBuildKeys();
        for (int k : ks) {
            int i = m.indexOf(k);
            if (!m.indexExists(i)) {
                m.indexInsert(i, k, COMPUTE.apply(k));
            }
        }
        for (int k : ks) {
            int i = m.indexOf(k);
            if (!m.indexExists(i)) {
                m.indexInsert(i, k, COMPUTE.apply(k));
            }
        }
        return m;
    }
}
