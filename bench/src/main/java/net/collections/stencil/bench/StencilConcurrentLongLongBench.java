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

import net.generated.example.LongLongConcurrentHashMap;

/**
 * stencil {@link LongLongConcurrentHashMap}: segments of linear-probing tables with long keys
 * and values in one array, lookups without lock (seqlock per segment), {@code addTo} under the
 * segment's lock; default concurrency level.
 */
public class StencilConcurrentLongLongBench extends AbstractConcurrentLongLongBench<LongLongConcurrentHashMap> {

    @Override
    protected LongLongConcurrentHashMap create() {
        return new LongLongConcurrentHashMap();
    }

    @Override
    protected long get(LongLongConcurrentHashMap m, long key) {
        return m.get(key);
    }

    @Override
    protected void put(LongLongConcurrentHashMap m, long key, long value) {
        m.put(key, value);
    }

    @Override
    protected void remove(LongLongConcurrentHashMap m, long key) {
        m.remove(key);
    }

    @Override
    protected long addOne(LongLongConcurrentHashMap m, long key) {
        return m.addTo(key, 1L);
    }
}
