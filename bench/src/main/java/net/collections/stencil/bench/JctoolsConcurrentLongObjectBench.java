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

import org.jctools.maps.NonBlockingHashMapLong;

/**
 * JCTools {@link NonBlockingHashMapLong}: lock-free open addressing with {@code long} keys
 * (also used for {@code int} keys; JCTools has no {@code int} variant).
 * {@code computeIfAbsent} is a lookup followed by {@code putIfAbsent}, as in the default
 * method of {@link java.util.concurrent.ConcurrentMap}, without boxing the key.
 */
public class JctoolsConcurrentLongObjectBench extends AbstractConcurrentLongObjectBench<NonBlockingHashMapLong<Object>> {

    @Override
    protected NonBlockingHashMapLong<Object> create() {
        return new NonBlockingHashMapLong<>();
    }

    @Override
    protected Object get(NonBlockingHashMapLong<Object> m, long key) {
        return m.get(key);
    }

    @Override
    protected void put(NonBlockingHashMapLong<Object> m, long key, Object value) {
        m.put(key, value);
    }

    @Override
    protected void remove(NonBlockingHashMapLong<Object> m, long key) {
        m.remove(key);
    }

    @Override
    protected Object computeIfAbsent(NonBlockingHashMapLong<Object> m, long key) {
        Object v = m.get(key);
        if (v == null) {
            Object computed = value(key);
            v = m.putIfAbsent(key, computed);
            if (v == null) {
                v = computed;
            }
        }
        return v;
    }
}
