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

import java.util.function.IntFunction;

import net.generated.example.IntObjectConcurrentHashMap;

/**
 * stencil {@link IntObjectConcurrentHashMap}: segments of linear-probing tables, lookups
 * without lock (seqlock per segment), default concurrency level.
 */
public class StencilConcurrentIntObjectBench extends AbstractConcurrentIntObjectBench<IntObjectConcurrentHashMap<Object>> {

    private static final IntFunction<Object> COMPUTE = AbstractConcurrentIntObjectBench::value;

    @Override
    protected IntObjectConcurrentHashMap<Object> create() {
        return new IntObjectConcurrentHashMap<>();
    }

    @Override
    protected Object get(IntObjectConcurrentHashMap<Object> m, int key) {
        return m.get(key);
    }

    @Override
    protected void put(IntObjectConcurrentHashMap<Object> m, int key, Object value) {
        m.put(key, value);
    }

    @Override
    protected void remove(IntObjectConcurrentHashMap<Object> m, int key) {
        m.remove(key);
    }

    @Override
    protected Object computeIfAbsent(IntObjectConcurrentHashMap<Object> m, int key) {
        return m.computeIfAbsent(key, COMPUTE);
    }
}
