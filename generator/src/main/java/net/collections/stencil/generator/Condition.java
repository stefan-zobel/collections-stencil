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

import java.util.Map;

/**
 * A boolean condition of an {@code //#if} or {@code //#elif} directive, evaluated against the
 * dimension bindings of one instantiation.
 *
 * <pre>
 * expr       := and ('||' and)*
 * and        := unary ('&amp;&amp;' unary)*
 * unary      := '!' unary | primary
 * primary    := '(' expr ')' | DIM '.' property | DIM ('==' | '!=') type ('|' type)*
 * property   := numeric | integral | floating
 * </pre>
 * Examples: {@code V == int}, {@code V == byte|short|char}, {@code V.numeric && V != float}.
 */
@FunctionalInterface
public interface Condition {

    /**
     * Evaluates the condition for one instantiation.
     *
     * @param bindings the element type bound to each dimension
     * @return {@code true} if the condition holds
     */
    boolean test(Map<String, ElementType> bindings);

    /**
     * Parses {@code text}; throws {@link IllegalArgumentException} with a message on syntax errors.
     *
     * @param text the condition, for example {@code V == int}
     * @return the parsed condition
     */
    static Condition parse(String text) {
        ConditionParser p = new ConditionParser(text);
        Condition c = p.expr();
        p.expectEnd();
        return c;
    }
}
