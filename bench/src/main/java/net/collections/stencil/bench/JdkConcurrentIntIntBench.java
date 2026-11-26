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

/** {@code ConcurrentHashMap<Integer, Integer>}: the JDK's map with boxed keys and values; counting with {@code merge}. */
public class JdkConcurrentIntIntBench extends AbstractConcurrentIntIntBench<ConcurrentHashMap<Integer, Integer>> {

    private static final BiFunction<Integer, Integer, Integer> SUM = Integer::sum;

    @Override
    protected ConcurrentHashMap<Integer, Integer> create() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected int get(ConcurrentHashMap<Integer, Integer> m, int key) {
        Integer v = m.get(key);
        return v == null ? 0 : v;
    }

    @Override
    protected void put(ConcurrentHashMap<Integer, Integer> m, int key, int value) {
        m.put(key, value);
    }

    @Override
    protected void remove(ConcurrentHashMap<Integer, Integer> m, int key) {
        m.remove(key);
    }

    @Override
    protected int addOne(ConcurrentHashMap<Integer, Integer> m, int key) {
        return m.merge(key, 1, SUM);
    }
}
