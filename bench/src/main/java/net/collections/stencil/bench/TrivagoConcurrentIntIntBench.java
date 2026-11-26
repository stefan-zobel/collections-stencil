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

import java.util.function.BiFunction;

import com.trivago.fastutilconcurrentwrapper.ConcurrentIntIntMapBuilder;
import com.trivago.fastutilconcurrentwrapper.IntIntMap;

/**
 * trivago {@code fastutil-concurrent-wrapper}, blocking mode: buckets of fastutil
 * {@code Int2IntOpenHashMap}, each with a {@code ReentrantReadWriteLock} (lookups take the
 * read lock), as many buckets as the stencil map has segments. The wrapper has no
 * {@code addTo} or {@code merge}: counting uses {@code computeIfPresent} with a boxing function.
 */
public class TrivagoConcurrentIntIntBench extends AbstractConcurrentIntIntBench<IntIntMap> {

    private static final BiFunction<Integer, Integer, Integer> INCREMENT = (k, v) -> v + 1;

    @Override
    protected IntIntMap create() {
        return ConcurrentIntIntMapBuilder.newBuilder()
                .withBuckets(SpeigerConcurrentLongObjectBench.stencilSegments())
                .withInitialCapacity(16)
                .withLoadFactor(0.75f)
                .withMode(ConcurrentIntIntMapBuilder.MapMode.BLOCKING)
                .withDefaultValue(0)
                .build();
    }

    @Override
    protected int get(IntIntMap m, int key) {
        return m.get(key);
    }

    @Override
    protected void put(IntIntMap m, int key, int value) {
        m.put(key, value);
    }

    @Override
    protected void remove(IntIntMap m, int key) {
        m.remove(key);
    }

    @Override
    protected int addOne(IntIntMap m, int key) {
        return m.computeIfPresent(key, INCREMENT);
    }
}
