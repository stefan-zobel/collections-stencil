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
 * Requests a list of {@code element} values in a growable array from the stencil annotation
 * processor. The processor generates the class as a source file of the compiled project, by
 * default in the package of the annotated element.
 * <pre>{@code
 * @PrimitiveList(element = int.class)    // IntArrayList
 * @PrimitiveList(element = double.class) // DoubleArrayList
 * package com.example.collections;
 * }</pre>
 * Several annotations for the same class, also on different elements, generate it once.
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Repeatable(PrimitiveList.List.class)
public @interface PrimitiveList {

    /**
     * Element type: {@code int.class}, {@code long.class}, or {@code double.class}.
     *
     * @return {@code int.class}, {@code long.class}, or {@code double.class}
     */
    Class<?> element();

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

    /** Container of repeated {@link PrimitiveList} annotations. */
    @Documented
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.TYPE, ElementType.PACKAGE})
    @interface List {

        /**
         * The repeated annotations.
         *
         * @return the repeated annotations
         */
        PrimitiveList[] value();
    }
}
