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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A source template: Java code with line directives and type placeholders.
 *
 * <h2>Directives</h2>
 * A directive is a line whose first non-blank characters are {@code //#}. Directive lines
 * never appear in the output.
 * <ul>
 *   <li>{@code //#types K=int V=int|float|boolean} declares instantiations: the cartesian
 *       product of the listed types. Several {@code //#types} lines add up.</li>
 *   <li>{@code //#if cond}, {@code //#elif cond}, {@code //#else}, {@code //#end} select lines
 *       (see {@link Condition} for the syntax of {@code cond}). Blocks nest.</li>
 *   <li>{@code //#-- text} is a template comment.</li>
 * </ul>
 *
 * <h2>Placeholders</h2>
 * Identifiers are replaced everywhere, including comments and string literals. For a
 * dimension {@code D} ({@code K} or {@code V}) bound to, say, {@code int}:
 * <ul>
 *   <li>{@code DType} alone becomes the type keyword: {@code int}.</li>
 *   <li>{@code DType} inside a longer identifier becomes the title:
 *       {@code KTypeVTypeHashMap} becomes {@code IntIntHashMap}.</li>
 *   <li>{@code DBoxed} becomes the wrapper class: {@code Integer}.</li>
 *   <li>{@code DZero} becomes a zero literal of the type: {@code 0}, {@code 0L},
 *       {@code 0.0f}, {@code (short) 0}, {@code false}.</li>
 * </ul>
 * The file name of a template (without {@code .java}) is the pattern of the output class
 * name; the output package is the one declared in the template.
 */
public final class Template {

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final String[] DIMENSIONS = {"K", "V"};

    private final String path;
    private final String namePattern;
    private final List<Line> lines;
    private final List<Map<String, ElementType>> instantiations;

    private Template(String path, String namePattern, List<Line> lines, List<Map<String, ElementType>> instantiations) {
        this.path = path;
        this.namePattern = namePattern;
        this.lines = lines;
        this.instantiations = instantiations;
    }

    /** One line of the template: text, or a directive with its parsed condition. */
    private record Line(int number, Kind kind, String text, Condition condition) {
    }

    private enum Kind { TEXT, IF, ELIF, ELSE, END }

    /**
     * Parses a template.
     *
     * @param path relative path of the template (for example {@code linear/KTypeVTypeHashMap.java});
     *             the file name determines the output class name pattern
     * @param text the template source
     * @return the parsed template
     */
    public static Template parse(String path, String text) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (!fileName.endsWith(".java")) {
            throw new IllegalArgumentException(path + ": template file names must end with .java");
        }
        String namePattern = fileName.substring(0, fileName.length() - ".java".length());
        String[] raw = text.replace("\r\n", "\n").split("\n", -1);
        int count = raw.length;
        if (count > 0 && raw[count - 1].isEmpty()) {
            count--; // the final line terminator does not start another line
        }
        List<Line> lines = new ArrayList<>(count);
        List<Map<String, ElementType>> instantiations = new ArrayList<>();
        int depth = 0;
        Deque<Integer> open = new ArrayDeque<>();
        for (int n = 0; n < count; n++) {
            String line = raw[n];
            int lineNo = n + 1;
            String trimmed = line.strip();
            if (!trimmed.startsWith("//#")) {
                lines.add(new Line(lineNo, Kind.TEXT, line, null));
                continue;
            }
            String directive = trimmed.substring(3);
            String word = directive.split("\\s+", 2)[0];
            String arg = directive.substring(word.length()).strip();
            try {
                switch (word) {
                    case "types" -> {
                        if (depth > 0) {
                            throw new IllegalArgumentException("//#types must not be inside //#if");
                        }
                        instantiations.addAll(parseTypes(arg));
                    }
                    case "if" -> {
                        lines.add(new Line(lineNo, Kind.IF, null, Condition.parse(arg)));
                        open.push(lineNo);
                        depth++;
                    }
                    case "elif" -> {
                        requireOpen(depth, word);
                        lines.add(new Line(lineNo, Kind.ELIF, null, Condition.parse(arg)));
                    }
                    case "else" -> {
                        requireOpen(depth, word);
                        requireNoArgument(word, arg);
                        lines.add(new Line(lineNo, Kind.ELSE, null, null));
                    }
                    case "end" -> {
                        requireOpen(depth, word);
                        requireNoArgument(word, arg);
                        lines.add(new Line(lineNo, Kind.END, null, null));
                        open.pop();
                        depth--;
                    }
                    case "--" -> {
                        // template comment
                    }
                    default -> throw new IllegalArgumentException("unknown directive //#" + word);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ":" + lineNo + ": " + e.getMessage(), e);
            }
        }
        if (depth > 0) {
            throw new IllegalArgumentException(path + ":" + open.peek() + ": //#if without //#end");
        }
        if (instantiations.isEmpty()) {
            throw new IllegalArgumentException(path + ": no //#types directive");
        }
        return new Template(path, namePattern, List.copyOf(lines), List.copyOf(instantiations));
    }

    private static void requireOpen(int depth, String word) {
        if (depth == 0) {
            throw new IllegalArgumentException("//#" + word + " without //#if");
        }
    }

    private static void requireNoArgument(String word, String arg) {
        if (!arg.isEmpty()) {
            throw new IllegalArgumentException("//#" + word + " takes no argument");
        }
    }

    /** {@code K=int V=int|float} -> the cartesian product of the alternatives. */
    private static List<Map<String, ElementType>> parseTypes(String arg) {
        List<Map<String, ElementType>> product = new ArrayList<>();
        product.add(new LinkedHashMap<>());
        for (String part : arg.split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("expected DIMENSION=type|type..., got '" + part + "'");
            }
            String dim = part.substring(0, eq);
            if (!List.of(DIMENSIONS).contains(dim)) {
                throw new IllegalArgumentException("unknown dimension '" + dim + "' (use K or V)");
            }
            List<Map<String, ElementType>> next = new ArrayList<>();
            for (Map<String, ElementType> partial : product) {
                if (partial.containsKey(dim)) {
                    throw new IllegalArgumentException("dimension '" + dim + "' given twice");
                }
                for (String type : part.substring(eq + 1).split("\\|")) {
                    Map<String, ElementType> m = new LinkedHashMap<>(partial);
                    m.put(dim, ElementType.ofKeyword(type));
                    next.add(m);
                }
            }
            product = next;
        }
        List<Map<String, ElementType>> result = new ArrayList<>();
        for (Map<String, ElementType> m : product) {
            result.add(Collections.unmodifiableMap(m));
        }
        return result;
    }

    /**
     * Relative path of the template, for example {@code linear/KTypeVTypeHashMap.java}.
     *
     * @return the relative path
     */
    public String path() {
        return path;
    }

    /**
     * The dimension bindings declared by the {@code //#types} directives, in order.
     *
     * @return the bindings of each instantiation
     */
    public List<Map<String, ElementType>> instantiations() {
        return instantiations;
    }

    /**
     * Output class name for {@code bindings}, for example {@code IntIntHashMap}.
     *
     * @param bindings the element type bound to each dimension
     * @return the simple class name
     */
    public String outputName(Map<String, ElementType> bindings) {
        try {
            return substitute(namePattern, bindings);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(path + ": " + e.getMessage(), e);
        }
    }

    /**
     * Output package, as declared in the template.
     *
     * @return the package name, or {@code ""} for the unnamed package
     */
    public String outputPackage() {
        for (Line line : lines) {
            if (line.kind == Kind.TEXT) {
                Matcher m = PACKAGE.matcher(line.text);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        return "";
    }

    /**
     * Instantiates the template: selects the active lines and replaces the placeholders.
     *
     * @param bindings the element type bound to each dimension
     * @return the generated Java source
     */
    public String render(Map<String, ElementType> bindings) {
        StringBuilder out = new StringBuilder();
        // Each frame: {parentActive, branchTaken, active}
        Deque<boolean[]> stack = new ArrayDeque<>();
        boolean active = true;
        for (Line line : lines) {
            try {
                switch (line.kind) {
                    case TEXT -> {
                        if (active) {
                            out.append(substitute(line.text, bindings)).append('\n');
                        }
                    }
                    case IF -> {
                        boolean taken = active && line.condition.test(bindings);
                        stack.push(new boolean[] {active, taken});
                        active = taken;
                    }
                    case ELIF -> {
                        boolean[] f = stack.element();
                        active = f[0] && !f[1] && line.condition.test(bindings);
                        f[1] |= active;
                    }
                    case ELSE -> {
                        boolean[] f = stack.element();
                        active = f[0] && !f[1];
                        f[1] = true;
                    }
                    case END -> active = stack.pop()[0];
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(path + ":" + line.number + ": " + e.getMessage(), e);
            }
        }
        return out.toString();
    }

    /** Replaces the placeholders in {@code text}. */
    private static String substitute(String text, Map<String, ElementType> bindings) {
        StringBuilder out = null;
        int i = 0;
        int copied = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (!Character.isJavaIdentifierPart(c)) {
                i++;
                continue;
            }
            int start = i;
            while (i < text.length() && Character.isJavaIdentifierPart(text.charAt(i))) {
                i++;
            }
            if (!Character.isJavaIdentifierStart(c)) {
                continue; // a number literal such as 0x7F or 1L
            }
            String word = text.substring(start, i);
            String replacement = replace(word, bindings);
            if (replacement != null) {
                if (out == null) {
                    out = new StringBuilder(text.length() + 16);
                }
                out.append(text, copied, start).append(replacement);
                copied = i;
            }
        }
        if (out == null) {
            return text;
        }
        return out.append(text, copied, text.length()).toString();
    }

    /** Replacement for one identifier, or {@code null} if it contains no placeholder. */
    private static String replace(String word, Map<String, ElementType> bindings) {
        if (word.length() >= 5 && List.of(DIMENSIONS).contains(word.substring(0, 1))) {
            String dim = word.substring(0, 1);
            switch (word.substring(1)) {
                case "Type" -> {
                    return binding(bindings, dim, word).keyword();
                }
                case "Boxed" -> {
                    return binding(bindings, dim, word).boxed();
                }
                case "Zero" -> {
                    return binding(bindings, dim, word).zero();
                }
                default -> {
                    // not a standalone placeholder; check for embedded ones below
                }
            }
        }
        String result = word;
        for (String dim : DIMENSIONS) {
            String token = dim + "Type";
            if (result.contains(token)) {
                result = result.replace(token, binding(bindings, dim, word).title());
            }
        }
        return result.equals(word) ? null : result;
    }

    private static ElementType binding(Map<String, ElementType> bindings, String dim, String word) {
        ElementType t = bindings.get(dim);
        if (t == null) {
            throw new IllegalArgumentException("'" + word + "' uses dimension " + dim
                    + ", which this template does not bind");
        }
        return t;
    }

    @Override
    public String toString() {
        return path;
    }
}
