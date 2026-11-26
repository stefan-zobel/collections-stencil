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
 * Function of a {@code KType} key and an object value, e.g. the remapping function of
 * {@code compute}.
 * <p>
 * The key is passed as a primitive parameter, so the call never boxes it.
 *
 * @param <V> the type of the value
 * @param <R> the type of the result
 */
@FunctionalInterface
public interface KTypeObjectFunction<V, R> {

    /**
     * Applies this function to a key and a value.
     *
     * @param key the key
     * @param value the value
     * @return the result
     */
    R apply(KType key, V value);
}
