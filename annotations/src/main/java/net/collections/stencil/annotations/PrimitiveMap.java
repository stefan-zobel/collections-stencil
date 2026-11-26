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

package net.collections.stencil.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requests a hash map (or, with {@code sorted = true}, a sorted map, with
 * {@code concurrent = true}, a thread-safe map, with {@code linked = true}, an insertion-ordered
 * map) from {@code key} to {@code value} from the stencil annotation processor.
 * The processor generates the map class, its interface and the functional interfaces it uses
 * as source files of the compiled project, by default in the package of the annotated element.
 * <pre>{@code
 * @PrimitiveMap(key = int.class, value = long.class)                  // IntLongHashMap, IntLongMap, ...
 * @PrimitiveMap(key = long.class, value = Object.class)               // LongObjectHashMap<V>, ...
 * @PrimitiveMap(key = long.class, value = long.class, compact = true) // also LongLongCompactHashMap
 * @PrimitiveMap(key = long.class, value = Object.class, sorted = true) // LongObjectTreeMap<V>, ...
 * @PrimitiveMap(key = long.class, value = long.class, sorted = true)   // LongLongTreeMap, ...
 * @PrimitiveMap(key = Object.class, value = int.class)               // ObjectIntHashMap<K>, ...
 * @PrimitiveMap(key = int.class, value = Object.class, concurrent = true) // IntObjectConcurrentHashMap<V>, ...
 * @PrimitiveMap(key = long.class, value = long.class, concurrent = true)  // LongLongConcurrentHashMap, ...
 * @PrimitiveMap(key = long.class, value = Object.class, linked = true)    // LongObjectLinkedHashMap<V>, ...
 * @PrimitiveMap(key = Object.class, value = int.class, linked = true)     // ObjectIntLinkedHashMap<K>, ...
 * package com.example.collections;
 * }</pre>
 * With {@code packagePrivate = true}, the generated classes are package-private, for a library
 * that uses them internally:
 * <pre>{@code
 * @PrimitiveMap(key = int.class, value = int.class, packagePrivate = true)
 * package com.example.lib.internal;
 * }</pre>
 * Several annotations for the same class, also on different elements, generate it once.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(PrimitiveMap.List.class)
public @interface PrimitiveMap {

    /**
     * Key type: {@code int.class}, {@code long.class}, or {@code Object.class} for object keys.
     * Object keys are available with {@code int}, {@code long} and {@code double} values, also
     * with {@code linked}, and neither with {@code compact} nor with {@code sorted}.
     *
     * @return {@code int.class}, {@code long.class} or {@code Object.class}
     */
    Class<?> key();

    /**
     * Value type: any primitive type, or {@code Object.class} for object values.
     *
     * @return a primitive class or {@code Object.class}
     */
    Class<?> value();

    /**
     * Whether to generate the tagged-block layout ({@code ...CompactHashMap}) in addition to the
     * linear-probing map. Not available for object values.
     *
     * @return {@code true} to generate the compact layout as well
     */
    boolean compact() default false;

    /**
     * Whether to generate the sorted map ({@code ...TreeMap}, a B+ tree) instead of the hash map.
     * Available for {@code int} and {@code long} keys with any value type, and not together with
     * {@code compact}.
     *
     * @return {@code true} to generate the sorted map
     */
    boolean sorted() default false;

    /**
     * Whether to generate the thread-safe map ({@code ...ConcurrentHashMap}) instead of the hash
     * map. Available for {@code int} and {@code long} keys with object, {@code int},
     * {@code long} and {@code double} values, and neither together with {@code compact} nor
     * with {@code sorted}.
     *
     * @return {@code true} to generate the thread-safe map
     */
    boolean concurrent() default false;

    /**
     * Whether to generate the insertion-ordered map ({@code ...LinkedHashMap}) instead of the hash
     * map. Available for {@code int} and {@code long} keys with any value type, for object keys
     * with {@code int}, {@code long} and {@code double} values, and not together with
     * {@code compact}, {@code sorted} or {@code concurrent}.
     *
     * @return {@code true} to generate the insertion-ordered map
     */
    boolean linked() default false;

    /**
     * Package of the generated classes; empty means the package of the annotated element.
     *
     * @return the target package, or {@code ""}
     */
    String packageName() default "";

    /**
     * Whether the generated classes are package-private instead of public, for a library that
     * must not export them. Applies to all classes generated into the target package (they share
     * interfaces): requests for the same package that differ in this attribute are an error.
     *
     * @return {@code true} to generate package-private classes
     */
    boolean packagePrivate() default false;

    /** Container of repeated {@link PrimitiveMap} annotations. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.PACKAGE})
    @interface List {

        /**
         * The repeated annotations.
         *
         * @return the repeated annotations
         */
        PrimitiveMap[] value();
    }
}
