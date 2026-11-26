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
//#types K=int V=boolean|byte|short|char|int|long|float|double
//#types K=long V=boolean|byte|short|char|int|long|float|double
package net.generated.example.function;

/**
 * Predicate on a pair of a {@code KType} and a {@code VType}, e.g. a key/value entry of a map.
 * Key and value are passed as two primitive parameters, without an entry object.
 */
@FunctionalInterface
public interface KTypeVTypePredicate {

    /**
     * Evaluates this predicate on a key and its value.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if the entry matches
     */
    boolean test(KType key, VType value);
}
