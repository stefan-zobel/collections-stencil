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

import net.generated.example.IntIntHashMap;

/**
 * stencil {@link IntIntHashMap} with load factor 0.5, the fixed load factor of Eclipse
 * Collections. Separates layout and hashing effects from the memory/speed trade-off.
 * Inherits all benchmark methods from {@link StencilBench}.
 */
public class StencilLf50Bench extends StencilBench {

    @Override
    protected IntIntHashMap newMap(int expectedSize) {
        return new IntIntHashMap(expectedSize, 0.5f);
    }
}
