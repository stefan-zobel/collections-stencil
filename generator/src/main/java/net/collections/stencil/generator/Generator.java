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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Instantiates templates into Java source files.
 * <p>
 * The templates of the library are bundled as resources under
 * {@code net/collections/stencil/generator/templates/} and listed in {@code index.txt} there. Other
 * templates (for example the test templates of a module) can be read from a directory.
 */
public final class Generator {

    /**
     * The package that the bundled templates declare. The functional interfaces are in its
     * subpackage {@code function}. {@link #rebase} moves both to another package.
     */
    public static final String TEMPLATE_PACKAGE = "net.generated.example";

    private static final String BUNDLE = "templates/";

    private Generator() {
    }

    /**
     * One generated source file.
     *
     * @param className simple name of the generated class
     * @param packageName package of the generated class
     * @param content the Java source
     * @param templatePath relative path of the template it was generated from
     */
    public record GeneratedFile(String className, String packageName, String content, String templatePath) {

        /**
         * Path relative to a source root, for example {@code org/stencil/IntIntHashMap.java}.
         *
         * @return the relative path
         */
        public String relativePath() {
            String dir = packageName.isEmpty() ? "" : packageName.replace('.', '/') + "/";
            return dir + className + ".java";
        }
    }

    /**
     * The templates bundled with the generator.
     *
     * @return the templates listed in {@code index.txt}
     */
    public static List<Template> bundledTemplates() {
        List<Template> result = new ArrayList<>();
        for (String line : readResource("index.txt").split("\n")) {
            String path = line.strip();
            if (!path.isEmpty() && !path.startsWith("#")) {
                result.add(Template.parse(path, readResource(path)));
            }
        }
        return result;
    }

    /**
     * All {@code *.java} templates below {@code dir}; paths are relative to {@code dir}.
     *
     * @param dir the directory to search
     * @return the parsed templates
     */
    public static List<Template> templatesIn(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            List<Path> sorted = files.filter(f -> f.toString().endsWith(".java")).sorted().toList();
            List<Template> result = new ArrayList<>();
            for (Path f : sorted) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                result.add(Template.parse(rel, Files.readString(f, StandardCharsets.UTF_8)));
            }
            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Instantiates every template for every instantiation it declares.
     *
     * @param templates the templates to instantiate
     * @param classFilter selects output classes by simple name
     * @param markGenerated whether to start each file with a comment naming its template
     * @return the generated files
     * @throws IllegalArgumentException if two instantiations produce the same class
     */
    public static List<GeneratedFile> generate(List<Template> templates, Predicate<String> classFilter,
                                               boolean markGenerated) {
        List<GeneratedFile> result = new ArrayList<>();
        Map<String, String> origin = new HashMap<>();
        for (Template t : templates) {
            for (Map<String, ElementType> bindings : t.instantiations()) {
                String name = t.outputName(bindings);
                if (!classFilter.test(name)) {
                    continue;
                }
                String pkg = t.outputPackage();
                String qualified = pkg.isEmpty() ? name : pkg + "." + name;
                String previous = origin.putIfAbsent(qualified, t.path());
                if (previous != null) {
                    throw new IllegalArgumentException(qualified + " is generated by both " + previous
                            + " and " + t.path());
                }
                String content = t.render(bindings);
                if (markGenerated) {
                    content = "// Generated from template " + t.path() + ". Edit the template, not this file.\n"
                            + content;
                }
                result.add(new GeneratedFile(name, pkg, content, t.path()));
            }
        }
        return result;
    }

    /**
     * The files named in {@code roots} plus every generated type they use, transitively.
     * References in comments do not count. A Javadoc {@code {@link}} to a generated type that
     * is not part of the selection becomes {@code {@code}}, so that the selection documents
     * cleanly on its own.
     *
     * @param all every file that could be selected, for example the result of
     *            {@link #generate} without a filter
     * @param roots simple class names
     * @return the selected files
     * @throws IllegalArgumentException if a root is not among {@code all}
     */
    public static List<GeneratedFile> select(List<GeneratedFile> all, Collection<String> roots) {
        Map<String, GeneratedFile> byName = new LinkedHashMap<>();
        for (GeneratedFile f : all) {
            byName.put(f.className(), f);
        }
        Set<String> selected = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        for (String root : roots) {
            if (!byName.containsKey(root)) {
                throw new IllegalArgumentException("No generated class " + root);
            }
            pending.add(root);
        }
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (!selected.add(name)) {
                continue;
            }
            Matcher m = IDENTIFIER.matcher(withoutCommentsAndLiterals(byName.get(name).content()));
            while (m.find()) {
                String used = m.group();
                if (byName.containsKey(used) && !selected.contains(used)) {
                    pending.add(used);
                }
            }
        }
        List<GeneratedFile> result = new ArrayList<>();
        for (GeneratedFile f : all) {
            if (selected.contains(f.className())) {
                String content = LINK.matcher(f.content()).replaceAll(r -> {
                    String target = r.group(1);
                    boolean missing = byName.containsKey(target) && !selected.contains(target);
                    return Matcher.quoteReplacement(missing ? "{@code " + target : r.group());
                });
                result.add(new GeneratedFile(f.className(), f.packageName(), content, f.templatePath()));
            }
        }
        return result;
    }

    /**
     * Moves files into the package {@code targetPackage}: rewrites their {@code package}
     * declarations and drops the imports between them, which are no longer needed.
     *
     * @param files the files to move
     * @param targetPackage the new package
     * @return the moved files
     */
    public static List<GeneratedFile> relocate(List<GeneratedFile> files, String targetPackage) {
        Set<String> qualified = new HashSet<>();
        for (GeneratedFile f : files) {
            qualified.add(f.packageName().isEmpty() ? f.className() : f.packageName() + "." + f.className());
        }
        List<GeneratedFile> result = new ArrayList<>();
        for (GeneratedFile f : files) {
            StringBuilder out = new StringBuilder(f.content().length());
            for (String line : f.content().split("\n", -1)) {
                Matcher imp = IMPORT.matcher(line);
                if (imp.matches() && qualified.contains(imp.group(1))) {
                    continue;
                }
                if (PACKAGE.matcher(line).matches()) {
                    line = targetPackage.isEmpty() ? "" : "package " + targetPackage + ";";
                }
                out.append(line).append('\n');
            }
            out.setLength(out.length() - 1); // the split kept the final segment
            // two blank lines may remain where an import group disappeared
            String content = out.toString().replaceAll("\n\n\n+", "\n\n");
            result.add(new GeneratedFile(f.className(), targetPackage, content, f.templatePath()));
        }
        return result;
    }

    /**
     * Moves files from the package {@code fromPackage} and its subpackages to {@code toPackage}
     * and the corresponding subpackages: {@code fromPackage.function} becomes
     * {@code toPackage.function}. Rewrites every qualified name that starts with
     * {@code fromPackage}, in package declarations, imports, code, and comments. Unlike
     * {@link #relocate}, the files keep their package structure.
     *
     * @param files the files to move
     * @param fromPackage the package to replace, for example {@link #TEMPLATE_PACKAGE}
     * @param toPackage the new package, not empty
     * @return the moved files
     * @throws IllegalArgumentException if {@code fromPackage} or {@code toPackage} is empty
     */
    public static List<GeneratedFile> rebase(List<GeneratedFile> files, String fromPackage, String toPackage) {
        if (fromPackage.isEmpty() || toPackage.isEmpty()) {
            throw new IllegalArgumentException("rebase needs named packages");
        }
        Pattern qualified = Pattern.compile("(?<![\\w.])" + Pattern.quote(fromPackage) + "(?![\\w])");
        String replacement = Matcher.quoteReplacement(toPackage);
        List<GeneratedFile> result = new ArrayList<>();
        for (GeneratedFile f : files) {
            String pkg = f.packageName();
            if (pkg.equals(fromPackage) || pkg.startsWith(fromPackage + ".")) {
                pkg = toPackage + pkg.substring(fromPackage.length());
            }
            String content = qualified.matcher(f.content()).replaceAll(replacement);
            result.add(new GeneratedFile(f.className(), pkg, content, f.templatePath()));
        }
        return result;
    }

    /**
     * Makes the top-level type of every file package-private: removes the {@code public}
     * modifier of its declaration. Members keep their modifiers; public members of a
     * package-private type are accessible only within its package. Meant for libraries that
     * must not export the generated types; all files of a package should be treated alike,
     * since the classes use the interfaces generated with them.
     *
     * @param files the files to change
     * @return the changed files
     * @throws IllegalArgumentException if a file does not contain exactly one public top-level
     *                                  declaration (at the start of a line)
     */
    public static List<GeneratedFile> packagePrivate(List<GeneratedFile> files) {
        List<GeneratedFile> result = new ArrayList<>();
        for (GeneratedFile f : files) {
            Matcher m = PUBLIC_TOP_LEVEL.matcher(f.content());
            if (!m.find()) {
                throw new IllegalArgumentException(f.templatePath() + ": no public top-level declaration in "
                        + f.className());
            }
            int start = m.start();
            int end = m.end();
            if (m.find()) {
                throw new IllegalArgumentException(f.templatePath() + ": more than one public top-level declaration in "
                        + f.className());
            }
            String content = f.content().substring(0, start) + f.content().substring(end);
            result.add(new GeneratedFile(f.className(), f.packageName(), content, f.templatePath()));
        }
        return result;
    }

    /** The {@code public} modifier of a top-level declaration; nested declarations are indented. */
    private static final Pattern PUBLIC_TOP_LEVEL = Pattern.compile(
            "^public\\s+(?=(?:(?:final|abstract|sealed|non-sealed|strictfp)\\s+)*(?:class|interface|enum|record|@interface)\\b)",
            Pattern.MULTILINE);
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");
    /** The start of {@code {@link Name}} or {@code {@link Name#member}}; group 1 is the class name. */
    private static final Pattern LINK = Pattern.compile("\\{@link\\s+([A-Z][A-Za-z0-9_]*)(?=[#}\\s])");
    private static final Pattern IMPORT = Pattern.compile("import\\s+([\\w.]+)\\s*;\\s*");
    private static final Pattern PACKAGE = Pattern.compile("package\\s+[\\w.]+\\s*;\\s*");
    private static final Pattern COMMENT_OR_LITERAL = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'|/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    private static String withoutCommentsAndLiterals(String source) {
        return COMMENT_OR_LITERAL.matcher(source).replaceAll(" ");
    }

    private static String readResource(String name) {
        try (InputStream in = Generator.class.getResourceAsStream(BUNDLE + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing template resource: " + BUNDLE + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
