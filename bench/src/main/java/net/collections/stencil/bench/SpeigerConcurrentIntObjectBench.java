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

import speiger.src.collections.ints.functions.function.IntFunction;
import speiger.src.collections.ints.maps.impl.concurrent.Int2ObjectConcurrentOpenHashMap;

/**
 * Speiger Primitive-Collections {@link Int2ObjectConcurrentOpenHashMap}: segments of
 * linear-probing tables, each a {@link java.util.concurrent.locks.StampedLock}; lookups take
 * the read lock. Created with as many segments as the stencil map uses by default (its own
 * default is 4).
 */
public class SpeigerConcurrentIntObjectBench extends AbstractConcurrentIntObjectBench<Int2ObjectConcurrentOpenHashMap<Object>> {

    private static final IntFunction<Object> COMPUTE = AbstractConcurrentIntObjectBench::value;

    /** The default concurrency level of the stencil map: 4 per processor, a power of two, at most 256. */
    static int stencilSegments() {
        int wanted = 4 * Runtime.getRuntime().availableProcessors();
        return Math.min(256, Integer.highestOneBit(wanted - 1) << 1);
    }

    @Override
    protected Int2ObjectConcurrentOpenHashMap<Object> create() {
        return new Int2ObjectConcurrentOpenHashMap<>(16, 0.75f, stencilSegments());
    }

    @Override
    protected Object get(Int2ObjectConcurrentOpenHashMap<Object> m, int key) {
        return m.get(key);
    }

    @Override
    protected void put(Int2ObjectConcurrentOpenHashMap<Object> m, int key, Object value) {
        m.put(key, value);
    }

    @Override
    protected void remove(Int2ObjectConcurrentOpenHashMap<Object> m, int key) {
        m.remove(key);
    }

    @Override
    protected Object computeIfAbsent(Int2ObjectConcurrentOpenHashMap<Object> m, int key) {
        return m.computeIfAbsent(key, COMPUTE);
    }
}
