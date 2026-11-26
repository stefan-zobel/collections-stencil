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

import java.util.SplittableRandom;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import net.generated.example.ObjectIntLinkedHashMap;

/**
 * Diagnostic variant of the insertion-ordered {@code String -> int} suite: stencil's
 * {@link ObjectIntLinkedHashMap}, populated in a random order, so that the keys with low ranks
 * (the frequent keys of {@code count}) do not occupy neighboring positions. {@code getHit} and
 * {@code count} only; not part of the default implementations.
 */
public class StencilShuffledObjectIntLinkedBench extends AbstractObjectIntLinkedBench {

    private ObjectIntLinkedHashMap<String> map;

    @Override
    protected void populate() {
        int n = keys.length;
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        SplittableRandom rnd = new SplittableRandom(0x0DDBA11L);
        for (int j = n - 1; j > 0; j--) {
            int k = rnd.nextInt(j + 1);
            int t = order[j];
            order[j] = order[k];
            order[k] = t;
        }
        ObjectIntLinkedHashMap<String> m = new ObjectIntLinkedHashMap<>();
        for (int i : order) {
            m.put(keys[i], i);
        }
        map = m;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long getHit() {
        ObjectIntLinkedHashMap<String> m = map;
        long sum = 0;
        for (String k : hits) {
            sum += m.get(k);
        }
        return sum;
    }

    @Benchmark
    @OperationsPerInvocation(OPS)
    public long count() {
        ObjectIntLinkedHashMap<String> m = map;
        long sum = 0;
        for (String k : counted) {
            sum += m.addTo(k, 1);
        }
        return sum;
    }
}
