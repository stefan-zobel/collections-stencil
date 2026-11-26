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

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;

/**
 * Diagnostic variant of the insertion-ordered {@code String -> int} suite: fastutil's
 * {@link Object2IntOpenHashMap} (no insertion order) on the same data, for {@code getHit} and
 * {@code count} only. Not part of the default implementations.
 */
public class FastutilHashObjectIntLinkedBench extends AbstractObjectIntLinkedBench {

    private Object2IntOpenHashMap<String> map;

    @Override
    protected void populate() {
        Object2IntOpenHashMap<String> m = new Object2IntOpenHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], i);
        }
        map = m;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        Object2IntOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : hits) {
            sum += m.getInt(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count() {
        Object2IntOpenHashMap<String> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.addTo(k, 1) + 1; // fastutil returns the old value
        }
        return sum;
    }
}
