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

import java.util.concurrent.atomic.AtomicInteger;

import org.jctools.maps.NonBlockingHashMapLong;

/**
 * JCTools {@link NonBlockingHashMapLong} with {@link AtomicInteger} values: lock-free table, counting
 * with {@code incrementAndGet} on the value object (JCTools has no map with primitive values).
 */
public class JctoolsConcurrentIntIntBench extends AbstractConcurrentIntIntBench<NonBlockingHashMapLong<AtomicInteger>> {

    @Override
    protected NonBlockingHashMapLong<AtomicInteger> create() {
        return new NonBlockingHashMapLong<>();
    }

    @Override
    protected int get(NonBlockingHashMapLong<AtomicInteger> m, int key) {
        AtomicInteger a = m.get(key);
        return a == null ? 0 : a.get();
    }

    @Override
    protected void put(NonBlockingHashMapLong<AtomicInteger> m, int key, int value) {
        m.put(key, new AtomicInteger(value));
    }

    @Override
    protected void remove(NonBlockingHashMapLong<AtomicInteger> m, int key) {
        m.remove(key);
    }

    @Override
    protected int addOne(NonBlockingHashMapLong<AtomicInteger> m, int key) {
        AtomicInteger a = m.get(key);
        if (a == null) {
            AtomicInteger created = new AtomicInteger();
            a = m.putIfAbsent(key, created);
            if (a == null) {
                a = created;
            }
        }
        return a.incrementAndGet();
    }
}
