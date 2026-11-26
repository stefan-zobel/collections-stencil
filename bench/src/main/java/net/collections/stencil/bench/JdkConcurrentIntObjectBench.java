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

/** {@code ConcurrentHashMap<Integer, Object>}: the JDK's map, with boxed keys. */
public class JdkConcurrentIntObjectBench extends AbstractConcurrentIntObjectBench<ConcurrentHashMap<Integer, Object>> {

    private static final Function<Integer, Object> COMPUTE = AbstractConcurrentIntObjectBench::value;

    @Override
    protected ConcurrentHashMap<Integer, Object> create() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected Object get(ConcurrentHashMap<Integer, Object> m, int key) {
        return m.get(key);
    }

    @Override
    protected void put(ConcurrentHashMap<Integer, Object> m, int key, Object value) {
        m.put(key, value);
    }

    @Override
    protected void remove(ConcurrentHashMap<Integer, Object> m, int key) {
        m.remove(key);
    }

    @Override
    protected Object computeIfAbsent(ConcurrentHashMap<Integer, Object> m, int key) {
        return m.computeIfAbsent(key, COMPUTE);
    }
}
