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

import speiger.src.collections.ints.maps.impl.concurrent.Int2IntConcurrentOpenHashMap;

/**
 * Speiger Primitive-Collections {@link Int2IntConcurrentOpenHashMap}: segments of
 * linear-probing tables, each a {@link java.util.concurrent.locks.StampedLock}; lookups take the
 * read lock, {@code addTo} the write lock. Created with as many segments as the stencil map
 * uses by default.
 */
public class SpeigerConcurrentIntIntBench extends AbstractConcurrentIntIntBench<Int2IntConcurrentOpenHashMap> {

    @Override
    protected Int2IntConcurrentOpenHashMap create() {
        Int2IntConcurrentOpenHashMap m = new Int2IntConcurrentOpenHashMap(16, 0.75f,
                SpeigerConcurrentLongObjectBench.stencilSegments());
        m.setDefaultReturnValue(0); // the default is -1
        return m;
    }

    @Override
    protected int get(Int2IntConcurrentOpenHashMap m, int key) {
        return m.get(key);
    }

    @Override
    protected void put(Int2IntConcurrentOpenHashMap m, int key, int value) {
        m.put(key, value);
    }

    @Override
    protected void remove(Int2IntConcurrentOpenHashMap m, int key) {
        m.remove(key);
    }

    @Override
    protected int addOne(Int2IntConcurrentOpenHashMap m, int key) {
        return m.addTo(key, 1);
    }
}
