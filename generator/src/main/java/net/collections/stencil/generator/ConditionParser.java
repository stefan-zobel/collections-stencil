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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Recursive-descent parser for {@link Condition}. */
final class ConditionParser {
    private final String text;
    private final List<String> tokens;
    private int pos;

    ConditionParser(String text) {
        this.text = text;
        this.tokens = tokenize(text);
    }

    Condition expr() {
        Condition left = and();
        while (accept("||")) {
            Condition l = left;
            Condition r = and();
            left = b -> l.test(b) || r.test(b);
        }
        return left;
    }

    private Condition and() {
        Condition left = unary();
        while (accept("&&")) {
            Condition l = left;
            Condition r = unary();
            left = b -> l.test(b) && r.test(b);
        }
        return left;
    }

    private Condition unary() {
        if (accept("!")) {
            Condition c = unary();
            return b -> !c.test(b);
        }
        return primary();
    }

    private Condition primary() {
        if (accept("(")) {
            Condition c = expr();
            expect(")");
            return c;
        }
        String dim = identifier("dimension");
        if (accept(".")) {
            String property = identifier("property");
            return switch (property) {
                case "numeric" -> b -> bound(b, dim).isNumeric();
                case "integral" -> b -> bound(b, dim).isIntegral();
                case "floating" -> b -> bound(b, dim).isFloating();
                default -> throw error("unknown property '" + property + "'");
            };
        }
        boolean negate;
        if (accept("==")) {
            negate = false;
        } else if (accept("!=")) {
            negate = true;
        } else {
            throw error("expected '.', '==' or '!=' after '" + dim + "'");
        }
        Set<ElementType> types = EnumSet.noneOf(ElementType.class);
        do {
            String name = identifier("type");
            try {
                types.add(ElementType.ofKeyword(name));
            } catch (IllegalArgumentException e) {
                throw error("unknown type '" + name + "'");
            }
        } while (accept("|"));
        return b -> types.contains(bound(b, dim)) != negate;
    }

    private static ElementType bound(Map<String, ElementType> bindings, String dim) {
        ElementType t = bindings.get(dim);
        if (t == null) {
            throw new IllegalArgumentException("dimension '" + dim + "' is not bound in this template");
        }
        return t;
    }

    void expectEnd() {
        if (pos < tokens.size()) {
            throw error("unexpected '" + tokens.get(pos) + "'");
        }
    }

    private boolean accept(String token) {
        if (pos < tokens.size() && tokens.get(pos).equals(token)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expect(String token) {
        if (!accept(token)) {
            throw error("expected '" + token + "'");
        }
    }

    private String identifier(String what) {
        if (pos < tokens.size() && Character.isJavaIdentifierStart(tokens.get(pos).charAt(0))) {
            return tokens.get(pos++);
        }
        throw error("expected " + what);
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid condition '" + text + "': " + message);
    }

    private static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (Character.isJavaIdentifierStart(c)) {
                int start = i;
                while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) {
                    i++;
                }
                out.add(text.substring(start, i));
            } else if (text.startsWith("||", i) || text.startsWith("&&", i)
                    || text.startsWith("==", i) || text.startsWith("!=", i)) {
                out.add(text.substring(i, i + 2));
                i += 2;
            } else if ("()!|.".indexOf(c) >= 0) {
                out.add(String.valueOf(c));
                i++;
            } else {
                throw new IllegalArgumentException("Invalid condition '" + text + "': unexpected character '" + c + "'");
            }
        }
        return out;
    }
}
