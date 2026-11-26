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

import com.trivago.fastutilconcurrentwrapper.ConcurrentLongLongMapBuilder;
import com.trivago.fastutilconcurrentwrapper.LongLongMap;

/**
 * trivago {@code fastutil-concurrent-wrapper}, blocking mode: buckets of fastutil
 * {@code Long2LongOpenHashMap}, each with a {@code ReentrantReadWriteLock} (lookups take the
 * read lock), as many buckets as the stencil map has segments. The wrapper has no
 * {@code addTo} or {@code merge}: counting uses {@code computeIfPresent} with a boxing function.
 */
public class TrivagoConcurrentLongLongBench extends AbstractConcurrentLongLongBench<LongLongMap> {

    private static final BiFunction<Long, Long, Long> INCREMENT = (k, v) -> v + 1;

    @Override
    protected LongLongMap create() {
        return ConcurrentLongLongMapBuilder.newBuilder()
                .withBuckets(SpeigerConcurrentLongObjectBench.stencilSegments())
                .withInitialCapacity(16)
                .withLoadFactor(0.75f)
                .withMode(ConcurrentLongLongMapBuilder.MapMode.BLOCKING)
                .withDefaultValue(0)
                .build();
    }

    @Override
    protected long get(LongLongMap m, long key) {
        return m.get(key);
    }

    @Override
    protected void put(LongLongMap m, long key, long value) {
        m.put(key, value);
    }

    @Override
    protected void remove(LongLongMap m, long key) {
        m.remove(key);
    }

    @Override
    protected long addOne(LongLongMap m, long key) {
        return m.computeIfPresent(key, INCREMENT);
    }
}
