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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Command line entry point.
 *
 * <pre>
 * java -jar collections.stencil-generator.jar --out=DIR [--templates=DIR] [--include=REGEX]
 *         [--package=NAME | --base-package=NAME] [--package-private] [--clean] [--no-header]
 * </pre>
 * <ul>
 *   <li>{@code --out}: source root to write to (the package directories are created).</li>
 *   <li>{@code --templates}: read templates from this directory instead of the bundled ones.</li>
 *   <li>{@code --include}: only classes whose simple name matches the regular expression, plus
 *       the generated types they use (interfaces, consumers, predicates).</li>
 *   <li>{@code --package}: put all generated classes into this package instead of the packages
 *       of the templates (for code you take over).</li>
 *   <li>{@code --base-package}: replace the package of the templates,
 *       {@value Generator#TEMPLATE_PACKAGE}, with this package and keep the subpackages below
 *       it: {@code --base-package=com.example.coll} puts the classes into
 *       {@code com.example.coll} and the functional interfaces into
 *       {@code com.example.coll.function}. Not together with {@code --package}.</li>
 *   <li>{@code --package-private}: make the generated top-level types package-private (for a
 *       library that must not export them).</li>
 *   <li>{@code --clean}: delete {@code *.java} files below {@code --out} that were not generated
 *       in this run (for build directories only, never for hand-written sources).</li>
 *   <li>{@code --no-header}: omit the comment that names the template (for code you take over).</li>
 * </ul>
 * Files whose content did not change are not rewritten, so their timestamps stay unchanged.
 */
public final class Main {

    /** A package name such as {@code com.example.coll}: Java identifiers separated by dots. */
    private static final Pattern QUALIFIED_NAME = Pattern.compile(
            "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*(\\.\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*)*");

    private Main() {
    }

    /**
     * Runs the generator with the given command line options.
     *
     * @param args the options described above
     * @throws IOException if a file cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        Path out = null;
        Path templateDir = null;
        Predicate<String> include = null;
        String targetPackage = null;
        String basePackage = null;
        boolean packagePrivate = false;
        boolean clean = false;
        boolean header = true;
        for (String arg : args) {
            if (arg.startsWith("--out=")) {
                out = Path.of(arg.substring("--out=".length()));
            } else if (arg.startsWith("--templates=")) {
                templateDir = Path.of(arg.substring("--templates=".length()));
            } else if (arg.startsWith("--include=")) {
                include = Pattern.compile(arg.substring("--include=".length())).asMatchPredicate();
            } else if (arg.startsWith("--package=")) {
                targetPackage = arg.substring("--package=".length());
            } else if (arg.startsWith("--base-package=")) {
                basePackage = arg.substring("--base-package=".length());
                if (!QUALIFIED_NAME.matcher(basePackage).matches()) {
                    throw new IllegalArgumentException("--base-package needs a qualified package name: " + basePackage);
                }
            } else if (arg.equals("--package-private")) {
                packagePrivate = true;
            } else if (arg.equals("--clean")) {
                clean = true;
            } else if (arg.equals("--no-header")) {
                header = false;
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg + "\nUsage: --out=DIR [--templates=DIR]"
                        + " [--include=REGEX] [--package=NAME | --base-package=NAME] [--package-private] [--clean]"
                        + " [--no-header]");
            }
        }
        if (out == null) {
            throw new IllegalArgumentException("--out=DIR is required");
        }
        if (targetPackage != null && basePackage != null) {
            throw new IllegalArgumentException("--package and --base-package exclude each other");
        }
        List<Template> templates = templateDir == null ? Generator.bundledTemplates() : Generator.templatesIn(templateDir);
        List<Generator.GeneratedFile> files = Generator.generate(templates, name -> true, header);
        if (include != null) {
            List<String> roots = files.stream().map(Generator.GeneratedFile::className).filter(include).toList();
            files = Generator.select(files, roots);
        }
        if (targetPackage != null) {
            files = Generator.relocate(files, targetPackage);
        }
        if (basePackage != null) {
            files = Generator.rebase(files, Generator.TEMPLATE_PACKAGE, basePackage);
        }
        if (packagePrivate) {
            files = Generator.packagePrivate(files);
        }

        Set<Path> written = new HashSet<>();
        int changed = 0;
        for (Generator.GeneratedFile f : files) {
            Path target = out.resolve(f.relativePath());
            written.add(target.toAbsolutePath().normalize());
            byte[] bytes = f.content().getBytes(StandardCharsets.UTF_8);
            if (Files.exists(target) && java.util.Arrays.equals(Files.readAllBytes(target), bytes)) {
                continue;
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
            changed++;
        }
        int deleted = 0;
        if (clean && Files.isDirectory(out)) {
            List<Path> stale = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(out)) {
                walk.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !written.contains(p.toAbsolutePath().normalize()))
                        .forEach(stale::add);
            }
            for (Path p : stale) {
                Files.delete(p);
                deleted++;
            }
        }
        System.out.printf("collections.stencil-generator: %d files from %d templates into %s (%d written, %d stale deleted)%n",
                files.size(), templates.size(), out, changed, deleted);
    }
}
