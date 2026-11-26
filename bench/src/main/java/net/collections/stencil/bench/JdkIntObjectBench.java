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

import java.util.HashMap;
import java.util.function.Function;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/** {@code HashMap<Integer, Object>} as a boxed reference point. */
public class JdkIntObjectBench extends AbstractIntObjectBench {

    private static final Function<Integer, Object> JDK_COMPUTE = AbstractIntObjectBench::value;

    private HashMap<Integer, Object> map;

    @Override
    protected void populate() {
        map = build(null);
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public int getHit() {
        HashMap<Integer, Object> m = map;
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
        HashMap<Integer, Object> m = map;
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
        HashMap<Integer, Object> m = map;
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
    public HashMap<Integer, Object> build(GcBeforeLargeBuild gc) {
        HashMap<Integer, Object> m = new HashMap<>();
        int[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public HashMap<Integer, Object> buildPresized(GcBeforeLargeBuild gc) {
        HashMap<Integer, Object> m = HashMap.newHashMap(size);
        int[] ks = nextBuildKeys();
        for (int i = 0; i < ks.length; i++) {
            m.put(ks[i], value(i));
        }
        return m;
    }

    @Benchmark
    public HashMap<Integer, Object> computeIfAbsent(GcBeforeLargeBuild gc) {
        HashMap<Integer, Object> m = new HashMap<>();
        int[] ks = nextBuildKeys();
        for (int k : ks) {
            m.computeIfAbsent(k, JDK_COMPUTE);
        }
        for (int k : ks) {
            m.computeIfAbsent(k, JDK_COMPUTE);
        }
        return m;
    }
}
