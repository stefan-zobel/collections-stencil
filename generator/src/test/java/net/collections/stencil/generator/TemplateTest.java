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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class TemplateTest {

    private static final Map<String, ElementType> INT_FLOAT = Map.of("K", ElementType.INT, "V", ElementType.FLOAT);
    private static final Map<String, ElementType> INT_SHORT = Map.of("K", ElementType.INT, "V", ElementType.SHORT);

    @Test
    void conditions() {
        assertTrue(Condition.parse("V == float").test(INT_FLOAT));
        assertFalse(Condition.parse("V != float").test(INT_FLOAT));
        assertTrue(Condition.parse("V == byte|short|char").test(INT_SHORT));
        assertFalse(Condition.parse("V == byte|short|char").test(INT_FLOAT));
        assertTrue(Condition.parse("V.numeric && V.integral").test(INT_SHORT));
        assertTrue(Condition.parse("V.floating || K == long").test(INT_FLOAT));
        assertFalse(Condition.parse("!(V.floating || K == long)").test(INT_FLOAT));
        assertTrue(Condition.parse("K == int && (V == boolean || !V.integral)").test(INT_FLOAT));
    }

    @Test
    void invalidConditions() {
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("V =="));
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("V == integer"));
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("V.signed"));
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("(V == int"));
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("V == int K == int"));
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("V = int"));
        assertThrows(IllegalArgumentException.class, () -> Condition.parse("X == int").test(INT_FLOAT));
    }

    @Test
    void typesDirectiveBuildsCartesianProduct() {
        Template t = Template.parse("a/KTypeVTypeBox.java", """
                //#types K=int|long V=float|int
                //#types K=char V=char
                class KTypeVTypeBox {}
                """);
        List<Map<String, ElementType>> inst = t.instantiations();
        assertEquals(5, inst.size());
        assertEquals(List.of("IntFloatBox", "IntIntBox", "LongFloatBox", "LongIntBox", "CharCharBox"),
                inst.stream().map(t::outputName).toList());
    }

    @Test
    void placeholders() {
        Template t = Template.parse("KTypeVTypeBox.java", """
                //#types K=int V=short
                package p.q;
                /** Maps {@code KType} to {@code VType}. */
                class KTypeVTypeBox implements KTypeVTypeMap {
                    VType v = VZero; VBoxed b; KBoxed kb; KType k = KZero;
                    KTypeVTypeConsumer c; long x = 0x7FL + 1L; String s = "KType";
                    int VTypeCount, AType, Types, KTypes;
                }
                """);
        assertEquals("p.q", t.outputPackage());
        assertEquals("""
                package p.q;
                /** Maps {@code int} to {@code short}. */
                class IntShortBox implements IntShortMap {
                    short v = (short) 0; Short b; Integer kb; int k = 0;
                    IntShortConsumer c; long x = 0x7FL + 1L; String s = "int";
                    int ShortCount, AType, Types, Ints;
                }
                """, t.render(INT_SHORT));
    }

    @Test
    void nestedConditionals() {
        Template t = Template.parse("X.java", """
                //#types K=int V=boolean|short|int|float
                a
                //#if V.numeric
                b
                //#if V == short
                c
                //#elif V == int
                d
                //#else
                e
                //#end
                f
                //#elif V == boolean
                g
                //#end
                //#-- template comment
                h
                """);
        assertEquals("a\ng\nh\n", t.render(Map.of("K", ElementType.INT, "V", ElementType.BOOLEAN)));
        assertEquals("a\nb\nc\nf\nh\n", t.render(INT_SHORT));
        assertEquals("a\nb\nd\nf\nh\n", t.render(Map.of("K", ElementType.INT, "V", ElementType.INT)));
        assertEquals("a\nb\ne\nf\nh\n", t.render(INT_FLOAT));
    }

    @Test
    void conditionsInInactiveBranchesAreNotEvaluated() {
        Template t = Template.parse("X.java", """
                //#types K=int
                //#if K == long
                //#if V == int
                x
                //#end
                //#end
                y
                """);
        assertEquals("y\n", t.render(Map.of("K", ElementType.INT)));
    }

    @Test
    void structuralErrorsNameTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Template.parse("t/X.java", "//#types K=int\n//#if K == int\nx\n"));
        assertTrue(e.getMessage().startsWith("t/X.java:2:"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> Template.parse("t/X.java", "//#types K=int\n//#end\n"));
        assertTrue(e.getMessage().startsWith("t/X.java:2:"), e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> Template.parse("t/X.java", "//#types K=int\n//#foo\n"));
        assertTrue(e.getMessage().contains("unknown directive"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> Template.parse("t/X.java", "class X {}\n"));
        assertThrows(IllegalArgumentException.class, () -> Template.parse("t/X.java", "//#types K=int K=long\n"));
        assertThrows(IllegalArgumentException.class, () -> Template.parse("t/X.txt", "//#types K=int\n"));
    }

    @Test
    void unboundDimensionIsAnError() {
        Template t = Template.parse("t/KTypeSet.java", "//#types K=int\nclass KTypeSet { VType v; }\n");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> t.render(Map.of("K", ElementType.INT)));
        assertTrue(e.getMessage().startsWith("t/KTypeSet.java:2:"), e.getMessage());
    }

    @Test
    void duplicateOutputIsAnError() {
        Template a = Template.parse("a/KTypeBox.java", "//#types K=int\npackage p;\n");
        Template b = Template.parse("b/KTypeBox.java", "//#types K=int|long\npackage p;\n");
        assertThrows(IllegalArgumentException.class, () -> Generator.generate(List.of(a, b), n -> true, false));
        assertEquals(1, Generator.generate(List.of(a, b), n -> n.startsWith("Long"), false).size());
    }

    @Test
    void generatedFileHeaderAndPath() {
        Template t = Template.parse("a/KTypeBox.java", "//#types K=long\npackage p.q;\nclass KTypeBox {}\n");
        Generator.GeneratedFile f = Generator.generate(List.of(t), n -> true, true).getFirst();
        assertEquals("p/q/LongBox.java", f.relativePath());
        assertTrue(f.content().startsWith("// Generated from template a/KTypeBox.java."), f.content());
        assertTrue(f.content().endsWith("package p.q;\nclass LongBox {}\n"), f.content());
    }

    @Test
    void bundledTemplatesAreListedAndRender() throws IOException {
        Path root = Path.of("src/main/resources/net/collections/stencil/generator/templates");
        List<String> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(p -> p.toString().endsWith(".java"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
        List<Template> bundled = Generator.bundledTemplates();
        assertEquals(files, bundled.stream().map(Template::path).sorted().toList(), "index.txt must list every template");
        List<Generator.GeneratedFile> out = Generator.generate(bundled, n -> true, false);
        assertFalse(out.isEmpty());
        for (Generator.GeneratedFile f : out) {
            assertFalse(f.content().contains("KType") || f.content().contains("VType"), f.relativePath());
            assertFalse(f.content().contains("//#"), f.relativePath());
        }
    }
}
