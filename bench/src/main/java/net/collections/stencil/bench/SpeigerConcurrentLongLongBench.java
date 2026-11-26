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

import speiger.src.collections.longs.maps.impl.concurrent.Long2LongConcurrentOpenHashMap;

/**
 * Speiger Primitive-Collections {@link Long2LongConcurrentOpenHashMap}: segments of
 * linear-probing tables, each a {@link java.util.concurrent.locks.StampedLock}; lookups take the
 * read lock, {@code addTo} the write lock. Created with as many segments as the stencil map
 * uses by default.
 */
public class SpeigerConcurrentLongLongBench extends AbstractConcurrentLongLongBench<Long2LongConcurrentOpenHashMap> {

    @Override
    protected Long2LongConcurrentOpenHashMap create() {
        Long2LongConcurrentOpenHashMap m = new Long2LongConcurrentOpenHashMap(16, 0.75f,
                SpeigerConcurrentLongObjectBench.stencilSegments());
        m.setDefaultReturnValue(0); // the default is -1
        return m;
    }

    @Override
    protected long get(Long2LongConcurrentOpenHashMap m, long key) {
        return m.get(key);
    }

    @Override
    protected void put(Long2LongConcurrentOpenHashMap m, long key, long value) {
        m.put(key, value);
    }

    @Override
    protected void remove(Long2LongConcurrentOpenHashMap m, long key) {
        m.remove(key);
    }

    @Override
    protected long addOne(Long2LongConcurrentOpenHashMap m, long key) {
        return m.addTo(key, 1L);
    }
}
