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
//#types V=int|long|double
package net.generated.example.function;

/**
 * Consumer of an object key and a {@code VType} value, e.g. an entry of a map.
 * <p>
 * The value is passed as a primitive parameter. No entry object is created, so the call never
 * allocates, whether or not it gets inlined.
 *
 * @param <K> the type of the key
 */
@FunctionalInterface
public interface ObjectVTypeConsumer<K> {

    /**
     * Performs this operation on a key and its value.
     *
     * @param key the key
     * @param value the value
     */
    void accept(K key, VType value);
}
