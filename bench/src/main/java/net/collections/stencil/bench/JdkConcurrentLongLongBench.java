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
import java.util.function.BiFunction;

/** {@code ConcurrentHashMap<Long, Long>}: the JDK's map with boxed keys and values; counting with {@code merge}. */
public class JdkConcurrentLongLongBench extends AbstractConcurrentLongLongBench<ConcurrentHashMap<Long, Long>> {

    private static final BiFunction<Long, Long, Long> SUM = Long::sum;

    @Override
    protected ConcurrentHashMap<Long, Long> create() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected long get(ConcurrentHashMap<Long, Long> m, long key) {
        Long v = m.get(key);
        return v == null ? 0 : v;
    }

    @Override
    protected void put(ConcurrentHashMap<Long, Long> m, long key, long value) {
        m.put(key, value);
    }

    @Override
    protected void remove(ConcurrentHashMap<Long, Long> m, long key) {
        m.remove(key);
    }

    @Override
    protected long addOne(ConcurrentHashMap<Long, Long> m, long key) {
        return m.merge(key, 1L, SUM);
    }
}
