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
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * {@code ConcurrentHashMap<Long, LongAdder>}: the usual idiom for counters under contention,
 * {@code computeIfAbsent(key, k -> new LongAdder()).increment()}; lookups read {@code sum()}.
 */
public class JdkAdderConcurrentLongLongBench extends AbstractConcurrentLongLongBench<ConcurrentHashMap<Long, LongAdder>> {

    private static final Function<Long, LongAdder> NEW_ADDER = k -> new LongAdder();

    @Override
    protected ConcurrentHashMap<Long, LongAdder> create() {
        return new ConcurrentHashMap<>();
    }

    @Override
    protected long get(ConcurrentHashMap<Long, LongAdder> m, long key) {
        LongAdder a = m.get(key);
        return a == null ? 0 : a.sum();
    }

    @Override
    protected void put(ConcurrentHashMap<Long, LongAdder> m, long key, long value) {
        LongAdder a = new LongAdder();
        a.add(value);
        m.put(key, a);
    }

    @Override
    protected void remove(ConcurrentHashMap<Long, LongAdder> m, long key) {
        m.remove(key);
    }

    @Override
    protected long addOne(ConcurrentHashMap<Long, LongAdder> m, long key) {
        m.computeIfAbsent(key, NEW_ADDER).increment();
        return 1;
    }
}
