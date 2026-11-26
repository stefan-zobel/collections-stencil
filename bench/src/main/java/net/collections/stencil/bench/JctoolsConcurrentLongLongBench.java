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

import java.util.concurrent.atomic.AtomicLong;

import org.jctools.maps.NonBlockingHashMapLong;

/**
 * JCTools {@link NonBlockingHashMapLong} with {@link AtomicLong} values: lock-free table, counting
 * with {@code incrementAndGet} on the value object (JCTools has no map with primitive values).
 */
public class JctoolsConcurrentLongLongBench extends AbstractConcurrentLongLongBench<NonBlockingHashMapLong<AtomicLong>> {

    @Override
    protected NonBlockingHashMapLong<AtomicLong> create() {
        return new NonBlockingHashMapLong<>();
    }

    @Override
    protected long get(NonBlockingHashMapLong<AtomicLong> m, long key) {
        AtomicLong a = m.get(key);
        return a == null ? 0 : a.get();
    }

    @Override
    protected void put(NonBlockingHashMapLong<AtomicLong> m, long key, long value) {
        m.put(key, new AtomicLong(value));
    }

    @Override
    protected void remove(NonBlockingHashMapLong<AtomicLong> m, long key) {
        m.remove(key);
    }

    @Override
    protected long addOne(NonBlockingHashMapLong<AtomicLong> m, long key) {
        AtomicLong a = m.get(key);
        if (a == null) {
            AtomicLong created = new AtomicLong();
            a = m.putIfAbsent(key, created);
            if (a == null) {
                a = created;
            }
        }
        return a.incrementAndGet();
    }
}
