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
import net.generated.example.ObjectIntHashMap;

/**
 * Diagnostic variant of the insertion-ordered {@code String -> int} suite: stencil's
 * {@link ObjectIntHashMap} (no insertion order) on the same data, for {@code getHit} and
 * {@code count} only. It separates the cost of the dense layout from effects of the data and the
 * run. Not part of the default implementations.
 */
public class StencilHashObjectIntLinkedBench extends AbstractObjectIntLinkedBench {

    private ObjectIntHashMap<String> map;

    @Override
    protected void populate() {
        ObjectIntHashMap<String> m = new ObjectIntHashMap<>();
        for (int i = 0; i < keys.length; i++) {
            m.put(keys[i], i);
        }
        map = m;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        ObjectIntHashMap<String> m = map;
        long sum = 0;
        for (String k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count() {
        ObjectIntHashMap<String> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.addTo(k, 1);
        }
        return sum;
    }
}
