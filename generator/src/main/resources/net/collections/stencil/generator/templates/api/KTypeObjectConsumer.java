//#-- Copyright 2026 The collections-stencil authors
//#--
//#-- Licensed under the Apache License, Version 2.0 (the "License");
//#-- you may not use this file except in compliance with the License.
//#-- You may obtain a copy of the License at
//#--
//#--     https://www.apache.org/licenses/LICENSE-2.0
//#--
//#-- Unless required by applicable law or agreed to in writing, software
//#-- distributed under the License is distributed on an "AS IS" BASIS,
//#-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//#-- See the License for the specific language governing permissions and
//#-- limitations under the License.
//#--
//#types K=int|long
package net.generated.example.function;

/**
 * Consumer of a {@code KType} key and an object value, e.g. an entry of a map.
 * <p>
 * The key is passed as a primitive parameter. No entry object is created, so the call never
 * allocates, whether or not it gets inlined.
 *
 * @param <V> the type of the value
 */
@FunctionalInterface
public interface KTypeObjectConsumer<V> {

    /**
     * Performs this operation on a key and its value.
     *
     * @param key the key
     * @param value the value
     */
    void accept(KType key, V value);
}
