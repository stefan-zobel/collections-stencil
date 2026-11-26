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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** {@code ConcurrentHashMap<Long, Object>}: the JDK's map, with boxed keys. */
public class JdkConcurrentLongObjectBench extends AbstractConcurrentLongObjectBench<ConcurrentHashMap<Long, Object>> {

    private static final Function<Long, Object> COMPUTE = AbstractConcurrentLongObjectBench::value;

    @Override
    protected ConcurrentHashMap<Long, Object> create() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Object get(ConcurrentHashMap<Long, Object> m, long key) {
        return m.get(key);
    }

    @Override
    protected void put(ConcurrentHashMap<Long, Object> m, long key, Object value) {
        m.put(key, value);
    }

    @Override
    protected void remove(ConcurrentHashMap<Long, Object> m, long key) {
        m.remove(key);
    }

    @Override
    protected Object computeIfAbsent(ConcurrentHashMap<Long, Object> m, long key) {
        return m.computeIfAbsent(key, COMPUTE);
    }
}
