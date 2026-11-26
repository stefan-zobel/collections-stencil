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

/**
 * stencil {@link IntIntBlockHashMap} with hashed home slots (Fibonacci hashing with a
 * per-instance seed), LF 0.75. Separates the block layout from the identity home slots.
 * Inherits all benchmark methods from {@link StencilBlockBench}.
 */
public class StencilBlockHashBench extends StencilBlockBench {

    @Override
    protected IntIntBlockHashMap newMap(int expectedSize) {
        return new IntIntBlockHashMap(expectedSize, IntIntBlockHashMap.DEFAULT_LOAD_FACTOR, false);
    }
}
