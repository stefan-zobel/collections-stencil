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
 * Requests a set of {@code element} values from the stencil annotation processor. The
 * processor generates the set class and its interface as source files of the compiled project,
 * by default in the package of the annotated element.
 * <pre>{@code
 * @PrimitiveSet(element = int.class)                   // IntHashSet, IntSet
 * @PrimitiveSet(element = long.class, compact = true)  // also LongCompactHashSet
 * @PrimitiveSet(element = long.class, linked = true)   // LongLinkedHashSet, LongSet
 * @PrimitiveSet(element = long.class, sorted = true)   // LongTreeSet, LongSortedSet, LongSet
 * package com.example.collections;
 * }</pre>
 * Several annotations for the same class, also on different elements, generate it once.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(PrimitiveSet.List.class)
public @interface PrimitiveSet {

    /**
     * Element type: {@code int.class} or {@code long.class}.
     *
     * @return {@code int.class} or {@code long.class}
     */
    Class<?> element();

    /**
     * Whether to generate the tagged-block layout ({@code ...CompactHashSet}) in addition to the
     * linear-probing set. Available for {@code long} elements only.
     *
     * @return {@code true} to generate the compact layout as well
     */
    boolean compact() default false;

    /**
     * Whether to generate the insertion-ordered set ({@code ...LinkedHashSet}) instead of the hash
     * set. Not together with {@code compact}.
     *
     * @return {@code true} to generate the insertion-ordered set
     */
    boolean linked() default false;

    /**
     * Whether to generate the sorted set ({@code ...TreeSet}, a B+ tree) instead of the hash set.
     * Not together with {@code compact} or {@code linked}.
     *
     * @return {@code true} to generate the sorted set
     */
    boolean sorted() default false;

    /**
     * Package of the generated classes; empty means the package of the annotated element.
     *
     * @return the target package, or {@code ""}
     */
    String packageName() default "";

    /**
     * Whether the generated classes are package-private instead of public, for a library that
     * must not export them. Applies to all classes generated into the target package: requests
     * for the same package that differ in this attribute are an error.
     *
     * @return {@code true} to generate package-private classes
     */
    boolean packagePrivate() default false;

    /** Container of repeated {@link PrimitiveSet} annotations. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.PACKAGE})
    @interface List {

        /**
         * The repeated annotations.
         *
         * @return the repeated annotations
         */
        PrimitiveSet[] value();
    }
}
