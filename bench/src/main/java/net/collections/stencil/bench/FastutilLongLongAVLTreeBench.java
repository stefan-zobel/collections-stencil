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

import it.unimi.dsi.fastutil.longs.Long2LongAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2LongSortedMap;

/** fastutil {@link Long2LongAVLTreeMap}: AVL tree of entry objects with primitive keys and values. */
public class FastutilLongLongAVLTreeBench extends FastutilLongLongRBTreeBench {

    @Override
    protected Long2LongSortedMap newMap() {
        return new Long2LongAVLTreeMap();
    }
}
