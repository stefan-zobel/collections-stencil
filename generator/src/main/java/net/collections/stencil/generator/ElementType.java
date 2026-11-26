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

package net.collections.stencil.generator;

import java.util.Locale;

/**
 * An element type that a template dimension ({@code K} for keys, {@code V} for values) can
 * be bound to.
 */
public enum ElementType {

    /** {@code boolean}. */
    BOOLEAN("boolean", "Boolean", "Boolean", "false", 1),
    /** {@code byte}. */
    BYTE("byte", "Byte", "Byte", "(byte) 0", 8),
    /** {@code short}. */
    SHORT("short", "Short", "Short", "(short) 0", 16),
    /** {@code char}. */
    CHAR("char", "Char", "Character", "(char) 0", 16),
    /** {@code int}. */
    INT("int", "Int", "Integer", "0", 32),
    /** {@code long}. */
    LONG("long", "Long", "Long", "0L", 64),
    /** {@code float}. */
    FLOAT("float", "Float", "Float", "0.0f", 32),
    /** {@code double}. */
    DOUBLE("double", "Double", "Double", "0.0", 64);

    private final String keyword;
    private final String title;
    private final String boxed;
    private final String zero;
    private final int bits;

    ElementType(String keyword, String title, String boxed, String zero, int bits) {
        this.keyword = keyword;
        this.title = title;
        this.boxed = boxed;
        this.zero = zero;
        this.bits = bits;
    }

    /**
     * The Java type as written in declarations, for example {@code int}.
     *
     * @return the keyword
     */
    public String keyword() {
        return keyword;
    }

    /**
     * The name as part of a class name, for example {@code Int} in {@code IntIntHashMap}.
     *
     * @return the title-case name
     */
    public String title() {
        return title;
    }

    /**
     * The wrapper class, for example {@code Integer}.
     *
     * @return the simple name of the wrapper class
     */
    public String boxed() {
        return boxed;
    }

    /**
     * A literal of this type with value zero ({@code false} for {@code boolean}).
     *
     * @return the zero literal
     */
    public String zero() {
        return zero;
    }

    /**
     * Width of the value in bits ({@code 1} for {@code boolean}).
     *
     * @return the width in bits
     */
    public int bits() {
        return bits;
    }

    /**
     * All types except {@code boolean}.
     *
     * @return {@code true} unless this is {@code boolean}
     */
    public boolean isNumeric() {
        return this != BOOLEAN;
    }

    /**
     * {@code byte}, {@code short}, {@code char}, {@code int}, {@code long}.
     *
     * @return {@code true} for the integral types
     */
    public boolean isIntegral() {
        return isNumeric() && !isFloating();
    }

    /**
     * {@code float} and {@code double}.
     *
     * @return {@code true} for {@code float} and {@code double}
     */
    public boolean isFloating() {
        return this == FLOAT || this == DOUBLE;
    }

    /**
     * Looks up a type by its keyword, for example {@code "int"}.
     *
     * @param keyword the Java keyword
     * @return the type
     */
    public static ElementType ofKeyword(String keyword) {
        for (ElementType t : values()) {
            if (t.keyword.equals(keyword)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown element type: " + keyword);
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
