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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorTest {

    private static final List<Generator.GeneratedFile> ALL =
            Generator.generate(Generator.bundledTemplates(), name -> true, false);

    private static Set<String> names(List<Generator.GeneratedFile> files) {
        return files.stream().map(Generator.GeneratedFile::className).collect(Collectors.toCollection(TreeSet::new));
    }

    private static Map<String, Generator.GeneratedFile> byName(List<Generator.GeneratedFile> files) {
        return files.stream().collect(Collectors.toMap(Generator.GeneratedFile::className, Function.identity()));
    }

    @Test
    void selectAddsUsedTypesTransitively() {
        assertEquals(Set.of("IntLongHashMap", "IntLongMap", "IntLongConsumer", "IntLongPredicate"),
                names(Generator.select(ALL, List.of("IntLongHashMap"))));
        assertEquals(Set.of("LongCompactHashSet", "LongSet"), names(Generator.select(ALL, List.of("LongCompactHashSet"))));
        assertEquals(Set.of("IntTreeSet", "IntSortedSet", "IntSet"), names(Generator.select(ALL, List.of("IntTreeSet"))));
        assertEquals(Set.of("IntObjectHashMap", "IntObjectMap", "IntObjectConsumer", "IntObjectPredicate"),
                names(Generator.select(ALL, List.of("IntObjectHashMap"))));
        assertEquals(Set.of("LongObjectTreeMap", "LongObjectSortedMap", "LongObjectMap", "LongObjectConsumer",
                        "LongObjectPredicate"),
                names(Generator.select(ALL, List.of("LongObjectTreeMap"))));
        assertEquals(Set.of("LongLongTreeMap", "LongLongSortedMap", "LongLongMap", "LongLongConsumer",
                        "LongLongPredicate"),
                names(Generator.select(ALL, List.of("LongLongTreeMap"))));
        assertEquals(Set.of("IntBooleanTreeMap", "IntBooleanSortedMap", "IntBooleanMap", "IntBooleanConsumer",
                        "IntBooleanPredicate"),
                names(Generator.select(ALL, List.of("IntBooleanTreeMap"))));
        assertEquals(Set.of("ObjectIntHashMap", "ObjectIntMap", "ObjectIntConsumer", "ObjectIntPredicate"),
                names(Generator.select(ALL, List.of("ObjectIntHashMap"))));
        assertEquals(Set.of("ObjectIntLinkedHashMap", "ObjectIntMap", "ObjectIntConsumer", "ObjectIntPredicate"),
                names(Generator.select(ALL, List.of("ObjectIntLinkedHashMap"))));
        assertEquals(Set.of("LongHeap"), names(Generator.select(ALL, List.of("LongHeap"))));
        assertEquals(Set.of("LongArrayList"), names(Generator.select(ALL, List.of("LongArrayList"))));
        assertEquals(Set.of("LongArrayDeque"), names(Generator.select(ALL, List.of("LongArrayDeque"))));
        assertEquals(Set.of("IntLongIndexedHeap", "IntLongConsumer"),
                names(Generator.select(ALL, List.of("IntLongIndexedHeap"))));
        assertEquals(Set.of("LongLongIndexedHeap", "LongLongConsumer"),
                names(Generator.select(ALL, List.of("LongLongIndexedHeap"))));
        assertEquals(Set.of("IntObjectConcurrentHashMap", "IntObjectConcurrentMap", "IntObjectMap",
                        "IntObjectConsumer", "IntObjectPredicate", "IntObjectFunction"),
                names(Generator.select(ALL, List.of("IntObjectConcurrentHashMap"))));
        assertEquals(Set.of("LongLongConcurrentHashMap", "LongLongConcurrentMap", "LongLongMap", "LongLongConsumer",
                        "LongLongPredicate"),
                names(Generator.select(ALL, List.of("LongLongConcurrentHashMap"))));
        assertEquals(Set.of("IntIntConcurrentHashMap", "IntIntConcurrentMap", "IntIntMap", "IntIntConsumer",
                        "IntIntPredicate"),
                names(Generator.select(ALL, List.of("IntIntConcurrentHashMap"))));
        assertEquals(Set.of("LongLongHashMap", "LongLongCompactHashMap", "LongLongMap", "LongLongConsumer",
                        "LongLongPredicate"),
                names(Generator.select(ALL, List.of("LongLongHashMap", "LongLongCompactHashMap"))));
        assertEquals(Set.of("LongLongLinkedHashMap", "LongLongMap", "LongLongConsumer", "LongLongPredicate"),
                names(Generator.select(ALL, List.of("LongLongLinkedHashMap"))));
        assertEquals(Set.of("IntObjectLinkedHashMap", "IntObjectMap", "IntObjectConsumer", "IntObjectPredicate"),
                names(Generator.select(ALL, List.of("IntObjectLinkedHashMap"))));
        assertEquals(Set.of("LongLinkedHashSet", "LongSet"), names(Generator.select(ALL, List.of("LongLinkedHashSet"))));
        assertThrows(IllegalArgumentException.class, () -> Generator.select(ALL, List.of("IntCompactHashSet")));
    }

    @Test
    void selectTurnsLinksToUnselectedTypesIntoCode() {
        Map<String, Generator.GeneratedFile> sel = byName(Generator.select(ALL, List.of("IntLongHashMap")));
        String map = sel.get("IntLongMap").content();
        assertTrue(map.contains("{@link IntLongHashMap}"), "link to a selected type stays");
        assertTrue(map.contains("{@code IntLongCompactHashMap}"), "link to an unselected type becomes code");
        assertFalse(map.contains("{@link IntLongCompactHashMap"));
        String impl = sel.get("IntLongHashMap").content();
        assertFalse(impl.contains("{@link LongLongHashMap"), "the int-key wide map refers to the long one in Javadoc");
        Map<String, Generator.GeneratedFile> both =
                byName(Generator.select(ALL, List.of("IntLongHashMap", "IntLongCompactHashMap")));
        assertTrue(both.get("IntLongMap").content().contains("{@link IntLongCompactHashMap}"));
    }

    @Test
    void relocateRewritesPackagesAndDropsInternalImports() {
        List<Generator.GeneratedFile> moved =
                Generator.relocate(Generator.select(ALL, List.of("IntLongHashMap")), "com.example.coll");
        for (Generator.GeneratedFile f : moved) {
            assertEquals("com.example.coll", f.packageName());
            assertTrue(f.content().contains("package com.example.coll;"), f.className());
            assertFalse(f.content().contains("net.generated.example"), f.className());
            assertFalse(f.content().contains("\n\n\n"), f.className());
            assertEquals("com/example/coll/" + f.className() + ".java", f.relativePath());
        }
        String impl = byName(moved).get("IntLongHashMap").content();
        assertTrue(impl.contains("import java.util.Arrays;"), "other imports stay");
        assertFalse(impl.contains("import com.example"));
    }

    @Test
    void packagePrivateRemovesOnlyTheTopLevelModifier() {
        List<Generator.GeneratedFile> hidden = Generator.packagePrivate(ALL);
        assertEquals(ALL.size(), hidden.size());
        for (int i = 0; i < ALL.size(); i++) {
            Generator.GeneratedFile before = ALL.get(i);
            Generator.GeneratedFile after = hidden.get(i);
            assertEquals(before.className(), after.className());
            assertEquals(before.packageName(), after.packageName());
            assertEquals(before.content().length() - "public ".length(), after.content().length(), before.className());
            assertFalse(after.content().lines().anyMatch(l -> l.startsWith("public ")), before.className());
            assertTrue(after.content().lines().anyMatch(l -> l.matches("(final class|interface) "
                    + before.className() + "\\b.*")), before.className());
        }
        String map = byName(hidden).get("IntLongHashMap").content();
        assertTrue(map.contains("\n    public long get(int key) {"), "members keep their modifiers");
    }

    @Test
    void packagePrivateNeedsExactlyOnePublicTopLevelDeclaration() {
        Generator.GeneratedFile none = new Generator.GeneratedFile("A", "p", "package p;\n\nclass A {\n}\n", "t/A.java");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Generator.packagePrivate(List.of(none)));
        assertTrue(e.getMessage().startsWith("t/A.java: no public"), e.getMessage());
        Generator.GeneratedFile nested = new Generator.GeneratedFile("A", "p",
                "package p;\n\npublic final class A {\n    public static final class B {\n    }\n}\n", "t/A.java");
        assertEquals("package p;\n\nfinal class A {\n    public static final class B {\n    }\n}\n",
                Generator.packagePrivate(List.of(nested)).get(0).content());
        Generator.GeneratedFile two = new Generator.GeneratedFile("A", "p",
                "package p;\n\npublic final class A {\n}\n\npublic interface B {\n}\n", "t/A.java");
        assertThrows(IllegalArgumentException.class, () -> Generator.packagePrivate(List.of(two)));
    }

    @Test
    void commandLinePackagePrivate(@TempDir Path out) throws Exception {
        Main.main(new String[] {"--out=" + out, "--include=LongLongConcurrentHashMap", "--package=com.example",
            "--package-private", "--no-header"});
        List<Path> files;
        try (Stream<Path> walk = Files.walk(out)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        assertEquals(5, files.size(), files.toString());
        for (Path f : files) {
            assertEquals(out.resolve("com/example"), f.getParent());
            String content = Files.readString(f);
            assertTrue(content.startsWith("package com.example;"), f.toString());
            assertFalse(content.lines().anyMatch(l -> l.startsWith("public ")), f.toString());
        }
    }

    @Test
    void rebaseKeepsTheSubpackages() {
        List<Generator.GeneratedFile> moved = Generator.rebase(Generator.select(ALL, List.of("IntLongHashMap")),
                Generator.TEMPLATE_PACKAGE, "com.example.coll");
        Map<String, Generator.GeneratedFile> byName = byName(moved);
        assertEquals("com.example.coll", byName.get("IntLongHashMap").packageName());
        assertEquals("com.example.coll.function", byName.get("IntLongConsumer").packageName());
        assertEquals("com/example/coll/function/IntLongConsumer.java", byName.get("IntLongConsumer").relativePath());
        for (Generator.GeneratedFile f : moved) {
            assertTrue(f.content().contains("package " + f.packageName() + ";"), f.className());
            assertFalse(f.content().contains(Generator.TEMPLATE_PACKAGE), f.className());
        }
        assertTrue(byName.get("IntLongHashMap").content().contains("import com.example.coll.function.IntLongConsumer;"));
    }

    @Test
    void rebaseLeavesLongerNamesAlone() {
        Generator.GeneratedFile f = new Generator.GeneratedFile("A", "p.q", """
                package p.q;

                import p.q.function.F;
                import p.qr.G;
                import x.p.q.H;

                public final class A {
                }
                """, "t/A.java");
        Generator.GeneratedFile moved = Generator.rebase(List.of(f), "p.q", "r").get(0);
        assertEquals("r", moved.packageName());
        assertEquals("""
                package r;

                import r.function.F;
                import p.qr.G;
                import x.p.q.H;

                public final class A {
                }
                """, moved.content());
        assertThrows(IllegalArgumentException.class, () -> Generator.rebase(List.of(f), "p.q", ""));
    }

    @Test
    void commandLineBasePackage(@TempDir Path out) throws Exception {
        Main.main(new String[] {"--out=" + out, "--include=IntIntHashMap", "--base-package=com.example.coll",
            "--no-header"});
        assertTrue(Files.readString(out.resolve("com/example/coll/IntIntHashMap.java"))
                .startsWith("package com.example.coll;"));
        assertTrue(Files.readString(out.resolve("com/example/coll/function/IntIntConsumer.java"))
                .startsWith("package com.example.coll.function;"));
        assertFalse(Files.exists(out.resolve("net")));
    }

    @Test
    void commandLineRejectsBadBasePackages(@TempDir Path out) {
        assertThrows(IllegalArgumentException.class, () -> Main.main(new String[] {"--out=" + out,
            "--base-package=com.example", "--package=com.example"}));
        for (String name : new String[] {"", "com..example", "com.example.", "1com", "com.ex-ample"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> Main.main(new String[] {"--out=" + out, "--base-package=" + name}), name);
        }
    }
}
