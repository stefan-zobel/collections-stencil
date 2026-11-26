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

package net.collections.stencil.processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import net.collections.stencil.annotations.PrimitiveMap;

/** Compiles annotated example sources in-process with javac and this processor. */
class PrimitiveCollectionsProcessorTest {

    @TempDir
    Path tmp;

    private record Result(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path generated,
                          Path classes) {

        String errors() {
            return diagnostics.stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                    .map(d -> d.getMessage(Locale.ROOT)).collect(Collectors.joining("\n"));
        }

        String all() {
            return diagnostics.stream().map(d -> d.getKind() + ": " + d.getMessage(Locale.ROOT))
                    .collect(Collectors.joining("\n"));
        }

        /** Generated files as paths relative to the generated source root, with forward slashes. */
        Set<String> generatedFiles() throws IOException {
            if (!Files.isDirectory(generated)) {
                return Set.of();
            }
            try (Stream<Path> walk = Files.walk(generated)) {
                return walk.filter(Files::isRegularFile)
                        .map(p -> generated.relativize(p).toString().replace('\\', '/'))
                        .collect(Collectors.toCollection(TreeSet::new));
            }
        }
    }

    private static Path annotationsLocation() throws URISyntaxException {
        return Path.of(PrimitiveMap.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    /** Compiles {@code sources} (relative path to content) with the processor. */
    private Result compile(Map<String, String> sources, boolean modular) throws Exception {
        Path src = tmp.resolve("src");
        Path gen = tmp.resolve("gen");
        Path out = tmp.resolve("out");
        Files.createDirectories(gen);
        Files.createDirectories(out);
        List<Path> files = new ArrayList<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            Path f = src.resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
            files.add(f);
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fm = javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> options = new ArrayList<>(List.of(
                    "-d", out.toString(), "-s", gen.toString(), "-Xlint:all", "-Werror",
                    modular ? "--module-path" : "-classpath", annotationsLocation().toString()));
            JavaCompiler.CompilationTask task = javac.getTask(null, fm, diagnostics, options, null,
                    fm.getJavaFileObjectsFromPaths(files));
            task.setProcessors(List.of(new PrimitiveCollectionsProcessor()));
            boolean success = task.call();
            return new Result(success, diagnostics.getDiagnostics(), gen, out);
        }
    }

    private static final String USE = """
            package com.example.coll;

            public class Use {
                private Use() {
                }

                public static String run() {
                    IntLongHashMap m = new IntLongHashMap();
                    m.put(1, 10L);
                    m.addTo(1, 5L);
                    LongObjectHashMap<String> o = new LongObjectHashMap<>();
                    o.computeIfAbsent(7L, k -> "v" + k);
                    LongLongCompactHashMap c = new LongLongCompactHashMap();
                    c.put(3L, 4L);
                    LongCompactHashSet s = new LongCompactHashSet();
                    s.add(5L);
                    LongSet copy = new LongHashSet(s);
                    return m.get(1) + " " + o.get(7L) + " " + c.get(3L) + " " + copy.contains(5L);
                }
            }
            """;

    private static final String PACKAGE_INFO = """
            @PrimitiveMap(key = int.class, value = long.class)
            @PrimitiveMap(key = long.class, value = Object.class)
            @PrimitiveMap(key = long.class, value = long.class, compact = true)
            @PrimitiveSet(element = long.class, compact = true)
            package com.example.coll;

            import net.collections.stencil.annotations.PrimitiveMap;
            import net.collections.stencil.annotations.PrimitiveSet;
            """;

    private static final Set<String> EXPECTED = Stream.of(
                    "IntLongHashMap", "IntLongMap", "IntLongConsumer", "IntLongPredicate",
                    "LongObjectHashMap", "LongObjectMap", "LongObjectConsumer", "LongObjectPredicate",
                    "LongLongHashMap", "LongLongCompactHashMap", "LongLongMap", "LongLongConsumer", "LongLongPredicate",
                    "LongHashSet", "LongCompactHashSet", "LongSet")
            .map(n -> "com/example/coll/" + n + ".java").collect(Collectors.toCollection(TreeSet::new));

    @Test
    void generatesRequestedClassesWithTheirDependencies() throws Exception {
        Result r = compile(Map.of("com/example/coll/package-info.java", PACKAGE_INFO,
                "com/example/coll/Use.java", USE), false);
        assertTrue(r.success(), r.all());
        assertEquals(EXPECTED, r.generatedFiles());
        String header = Files.readString(r.generated().resolve("com/example/coll/IntLongMap.java"));
        assertTrue(header.startsWith("// Generated by the stencil annotation processor from template api/"));
        assertTrue(header.contains("package com.example.coll;"));
        assertFalse(header.contains("net.generated.example"), "no reference to the library packages");
        assertTrue(header.contains("{@code IntLongCompactHashMap}"), "link to a class that was not requested");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Use").getMethod("run").invoke(null);
            assertEquals("15 v7 4 true", result);
        }
    }

    @Test
    void modularProject() throws Exception {
        Result r = compile(Map.of(
                "module-info.java", """
                        module example {
                            requires static net.collections.stencil.annotations;
                            exports com.example.coll;
                        }
                        """,
                "com/example/coll/package-info.java", PACKAGE_INFO,
                "com/example/coll/Use.java", USE), true);
        assertTrue(r.success(), r.all());
        assertEquals(EXPECTED, r.generatedFiles());
    }

    @Test
    void requestsFromSeveralElementsGenerateEachClassOnce() throws Exception {
        String a = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = int.class)
                class A {
                    IntIntHashMap map = new IntIntHashMap();
                }
                """;
        String b = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = int.class, compact = true)
                @net.collections.stencil.annotations.PrimitiveSet(element = int.class)
                class B {
                    IntIntMap map = new IntIntCompactHashMap();
                    IntSet set = new IntHashSet();
                }
                """;
        Result r = compile(Map.of("com/example/coll/A.java", a, "com/example/coll/B.java", b), false);
        assertTrue(r.success(), r.all());
        assertEquals(Set.of("IntIntHashMap", "IntIntCompactHashMap", "IntIntMap", "IntIntConsumer", "IntIntPredicate",
                        "IntHashSet", "IntSet").stream().map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        String map = Files.readString(r.generated().resolve("com/example/coll/IntIntMap.java"));
        assertTrue(map.contains("{@link IntIntCompactHashMap}"), "both requests are merged before generating");
    }

    @Test
    void packageNameAttribute() throws Exception {
        String source = """
                package com.example.app;

                import com.example.gen.IntHashSet;

                @net.collections.stencil.annotations.PrimitiveSet(element = int.class, packageName = "com.example.gen")
                class App {
                    IntHashSet set = new IntHashSet();
                }
                """;
        Result r = compile(Map.of("com/example/app/App.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Set.of("com/example/gen/IntHashSet.java", "com/example/gen/IntSet.java"), r.generatedFiles());
    }

    @Test
    void sortedMap() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = Object.class, sorted = true)
                public class Sorted {
                    public static String run() {
                        LongObjectSortedMap<String> m = new LongObjectTreeMap<>();
                        m.put(3L, "c");
                        m.put(1L, "a");
                        m.put(2L, "b");
                        return m.firstKey() + " " + m.floorValue(5L) + " " + m.countRange(1L, 3L) + " " + m;
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Sorted.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("LongObjectTreeMap", "LongObjectSortedMap", "LongObjectMap", "LongObjectConsumer",
                                "LongObjectPredicate").map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Sorted").getMethod("run").invoke(null);
            assertEquals("1 c 2 {1=a, 2=b, 3=c}", result);
        }
    }

    @Test
    void sortedMapWithPrimitiveValues() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = long.class, sorted = true)
                public class SortedLong {
                    public static String run() {
                        LongLongSortedMap m = new LongLongTreeMap();
                        m.put(3L, 30L);
                        m.addTo(1L, 10L);
                        m.put(2L, 20L);
                        return m.firstKey() + " " + m.floorValue(5L, -1L) + " " + m.lowerValue(1L, -1L) + " "
                                + m.countRange(1L, 3L) + " " + m.pollFirst() + " " + m;
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/SortedLong.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("LongLongTreeMap", "LongLongSortedMap", "LongLongMap", "LongLongConsumer",
                                "LongLongPredicate").map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.SortedLong").getMethod("run").invoke(null);
            assertEquals("1 30 -1 2 10 {2=20, 3=30}", result);
        }
    }

    @Test
    void sortedSet() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveSet(element = long.class, sorted = true)
                public class SortedSet {
                    public static String run() {
                        LongSortedSet s = new LongTreeSet();
                        s.add(30L);
                        s.add(10L);
                        s.add(20L);
                        s.add(50L);
                        return s.first() + " " + s.floor(25L, -1L) + " " + s.lower(10L, -1L) + " "
                                + s.countRange(10L, 30L) + " " + s.pollLast() + " " + s;
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/SortedSet.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("LongTreeSet", "LongSortedSet", "LongSet").map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.SortedSet").getMethod("run").invoke(null);
            assertEquals("10 20 -1 2 50 [10, 20, 30]", result);
        }
    }

    @Test
    void linkedMapsAndSet() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = Object.class, linked = true)
                @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = double.class, linked = true)
                @net.collections.stencil.annotations.PrimitiveSet(element = long.class, linked = true)
                @net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = int.class, linked = true)
                public class Linked {
                    public static String run() {
                        LongObjectLinkedHashMap<String> m = new LongObjectLinkedHashMap<>();
                        m.put(3L, "c");
                        m.put(1L, "a");
                        m.put(2L, "b");
                        m.moveToLast(3L);
                        IntDoubleLinkedHashMap d = new IntDoubleLinkedHashMap();
                        d.addTo(7, 1.5);
                        d.addTo(4, 1.0);
                        d.addTo(7, 1.0);
                        LongLinkedHashSet s = new LongLinkedHashSet();
                        s.add(9L);
                        s.add(0L);
                        s.add(5L);
                        s.remove(0L);
                        ObjectIntLinkedHashMap<String> c = new ObjectIntLinkedHashMap<>();
                        c.addTo("b", 1);
                        c.addTo("a", 1);
                        c.addTo("b", 1);
                        c.addTo(null, 3);
                        c.putLast("b", 5);
                        return m.firstKey() + " " + m.pollLast() + " " + m + " " + d + " " + s + " " + s.pollFirst()
                                + " " + c + " " + c.firstKey();
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Linked.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("LongObjectLinkedHashMap", "LongObjectMap", "LongObjectConsumer", "LongObjectPredicate",
                                "IntDoubleLinkedHashMap", "IntDoubleMap", "IntDoubleConsumer", "IntDoublePredicate",
                                "LongLinkedHashSet", "LongSet",
                                "ObjectIntLinkedHashMap", "ObjectIntMap", "ObjectIntConsumer", "ObjectIntPredicate")
                        .map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Linked").getMethod("run").invoke(null);
            assertEquals("1 c {1=a, 2=b} {7=2.5, 4=1.0} [9, 5] 9 {a=1, null=3, b=5} a", result);
        }
    }

    @Test
    void heaps() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveHeap(priority = long.class)
                @net.collections.stencil.annotations.PrimitiveHeap(priority = double.class, indexed = true)
                @net.collections.stencil.annotations.PrimitiveHeap(priority = double.class, indexed = true, key = long.class)
                public class Heaps {
                    public static String run() {
                        LongHeap h = new LongHeap(4, true);
                        h.push(3L);
                        h.push(9L);
                        h.push(1L);
                        long top = h.poll();
                        long kept = h.pushPoll(5L);
                        IntDoubleIndexedHeap q = new IntDoubleIndexedHeap();
                        q.put(4, 2.5);
                        q.put(7, 1.0);
                        q.putIfBetter(4, 0.5);
                        boolean worse = q.putIfBetter(7, 3.0);
                        LongDoubleIndexedHeap k = new LongDoubleIndexedHeap(0);
                        k.put(-5_000_000_000L, 2.0);
                        k.put(0L, 1.5);
                        k.put(Long.MAX_VALUE, 0.5);
                        k.remove(0L);
                        return top + " " + kept + " " + h + " " + worse + " " + q + " " + q.poll()
                                + " " + k + " " + k.peekKey();
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Heaps.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("LongHeap", "IntDoubleIndexedHeap", "IntDoubleConsumer", "LongDoubleIndexedHeap",
                                "LongDoubleConsumer")
                        .map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Heaps").getMethod("run").invoke(null);
            assertEquals("9 5 [3, 1] false {4=0.5, 7=1.0} 4 {9223372036854775807=0.5, -5000000000=2.0} "
                    + "9223372036854775807", result);
        }
    }

    @Test
    void sequences() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveList(element = int.class)
                @net.collections.stencil.annotations.PrimitiveDeque(element = double.class)
                public class Sequences {
                    public static String run() {
                        IntArrayList l = IntArrayList.of(5, 3, 8);
                        l.add(1, 7);
                        l.removeValue(8);
                        l.sort();
                        int at = l.binarySearch(5);
                        DoubleArrayDeque d = new DoubleArrayDeque(2);
                        d.addLast(1.5);
                        d.addFirst(0.5);
                        d.addLast(2.5);
                        double first = d.removeFirst();
                        d.discardLast(1);
                        return l + " " + at + " " + first + " " + d + " " + d.get(0);
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Sequences.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("IntArrayList", "DoubleArrayDeque")
                        .map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Sequences").getMethod("run").invoke(null);
            assertEquals("[3, 5, 7] 1 0.5 [1.5] 1.5", result);
        }
    }

    @Test
    void objectKeyMap() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = int.class)
                public class Counter {
                    public static String run() {
                        ObjectIntMap<String> m = new ObjectIntHashMap<>();
                        for (String w : "a b a c a b".split(" ")) {
                            m.addTo(w, 1);
                        }
                        return m.get("a") + " " + m.get("b") + " " + m.getOrDefault("z", -1) + " " + m.size();
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Counter.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("ObjectIntHashMap", "ObjectIntMap", "ObjectIntConsumer", "ObjectIntPredicate")
                        .map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Counter").getMethod("run").invoke(null);
            assertEquals("3 2 -1 3", result);
        }
    }

    @Test
    void concurrentMap() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = Object.class, concurrent = true)
                public class Cache {
                    public static String run() throws InterruptedException {
                        IntObjectConcurrentMap<String> m = new IntObjectConcurrentHashMap<>();
                        Thread[] threads = new Thread[4];
                        for (int t = 0; t < threads.length; t++) {
                            threads[t] = new Thread(() -> {
                                for (int i = 0; i < 1000; i++) {
                                    m.computeIfAbsent(i % 10, k -> "v" + k);
                                    m.merge(-1, "x", (a, b) -> a.length() < 5 ? a + b : a);
                                }
                            });
                            threads[t].start();
                        }
                        for (Thread t : threads) {
                            t.join();
                        }
                        return m.get(3) + " " + m.get(-1) + " " + m.size();
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Cache.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("IntObjectConcurrentHashMap", "IntObjectConcurrentMap", "IntObjectMap",
                                "IntObjectConsumer", "IntObjectPredicate", "IntObjectFunction")
                        .map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Cache").getMethod("run").invoke(null);
            assertEquals("v3 xxxxx 11", result);
        }
    }

    @Test
    void concurrentCounterMap() throws Exception {
        String source = """
                package com.example.coll;

                @net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = long.class, concurrent = true)
                public class Counters {
                    public static String run() throws InterruptedException {
                        LongLongConcurrentMap m = new LongLongConcurrentHashMap();
                        Thread[] threads = new Thread[4];
                        for (int t = 0; t < threads.length; t++) {
                            threads[t] = new Thread(() -> {
                                for (int i = 0; i < 10_000; i++) {
                                    m.addTo(i % 7, 1);
                                }
                            });
                            threads[t].start();
                        }
                        for (Thread t : threads) {
                            t.join();
                        }
                        long total = 0;
                        for (long v : m.valuesToArray()) {
                            total += v;
                        }
                        return total + " " + m.size() + " " + m.get(3);
                    }
                }
                """;
        Result r = compile(Map.of("com/example/coll/Counters.java", source), false);
        assertTrue(r.success(), r.all());
        assertEquals(Stream.of("LongLongConcurrentHashMap", "LongLongConcurrentMap", "LongLongMap", "LongLongConsumer",
                                "LongLongPredicate")
                        .map(n -> "com/example/coll/" + n + ".java")
                        .collect(Collectors.toCollection(TreeSet::new)),
                r.generatedFiles());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Counters").getMethod("run").invoke(null);
            assertEquals("40000 7 5716", result);
        }
    }

    private static final String PACKAGE_PRIVATE_INFO = """
            @PrimitiveMap(key = int.class, value = long.class, packagePrivate = true)
            @PrimitiveMap(key = long.class, value = Object.class, sorted = true, packagePrivate = true)
            @PrimitiveMap(key = Object.class, value = int.class, packagePrivate = true)
            @PrimitiveMap(key = long.class, value = long.class, concurrent = true, packagePrivate = true)
            @PrimitiveSet(element = long.class, compact = true, packagePrivate = true)
            package com.example.coll;

            import net.collections.stencil.annotations.PrimitiveMap;
            import net.collections.stencil.annotations.PrimitiveSet;
            """;

    private static final String PACKAGE_PRIVATE_USE = """
            package com.example.coll;

            public class Internal {
                private Internal() {
                }

                public static String run() {
                    IntLongMap m = new IntLongHashMap();
                    m.addTo(1, 5L);
                    LongObjectTreeMap<String> t = new LongObjectTreeMap<>();
                    t.put(2L, "b");
                    ObjectIntHashMap<String> o = new ObjectIntHashMap<>();
                    o.addTo("a", 3);
                    LongLongConcurrentMap c = new LongLongConcurrentHashMap();
                    c.addTo(4L, 7L);
                    LongSet s = new LongCompactHashSet();
                    s.add(9L);
                    return m.get(1) + " " + t.get(2L) + " " + o.get("a") + " " + c.get(4L) + " " + s.contains(9L);
                }
            }
            """;

    /** Lines that start a public top-level declaration. */
    private static List<String> publicTopLevel(Path generated) throws IOException {
        List<String> result = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(generated)) {
            for (Path p : walk.filter(Files::isRegularFile).toList()) {
                for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                    if (line.startsWith("public ")) {
                        result.add(p.getFileName() + ": " + line);
                    }
                }
            }
        }
        return result;
    }

    @Test
    void packagePrivateOutput() throws Exception {
        Result r = compile(Map.of("com/example/coll/package-info.java", PACKAGE_PRIVATE_INFO,
                "com/example/coll/Internal.java", PACKAGE_PRIVATE_USE), false);
        assertTrue(r.success(), r.all());
        assertTrue(r.generatedFiles().containsAll(Stream.of("IntLongHashMap", "IntLongMap", "LongObjectTreeMap",
                        "ObjectIntHashMap", "LongLongConcurrentHashMap", "LongLongConcurrentMap", "LongCompactHashSet",
                        "LongSet").map(n -> "com/example/coll/" + n + ".java").toList()),
                r.generatedFiles().toString());
        assertEquals(List.of(), publicTopLevel(r.generated()));
        String map = Files.readString(r.generated().resolve("com/example/coll/IntLongHashMap.java"));
        assertTrue(map.contains("\nfinal class IntLongHashMap implements IntLongMap {"), "package-private declaration");
        assertTrue(map.contains("    public long get(int key)"), "members keep their modifiers");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {r.classes().toUri().toURL()})) {
            Object result = loader.loadClass("com.example.coll.Internal").getMethod("run").invoke(null);
            assertEquals("5 b 3 7 true", result);
        }
    }

    @Test
    void packagePrivateClassesAreNotVisibleOutsideTheirPackage() throws Exception {
        Result r = compile(Map.of(
                "com/example/coll/package-info.java", """
                        @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = long.class, packagePrivate = true)
                        package com.example.coll;
                        """,
                "com/example/app/App.java", """
                        package com.example.app;

                        class App {
                            com.example.coll.IntLongHashMap map;
                        }
                        """), false);
        assertFalse(r.success());
        assertTrue(r.errors().contains("is not public in com.example.coll"), r.all());
    }

    @Test
    void packagePrivateInAModularProject() throws Exception {
        Result r = compile(Map.of(
                "module-info.java", """
                        module example {
                            requires static net.collections.stencil.annotations;
                            exports com.example.coll;
                        }
                        """,
                "com/example/coll/package-info.java", PACKAGE_PRIVATE_INFO,
                "com/example/coll/Internal.java", PACKAGE_PRIVATE_USE), true);
        assertTrue(r.success(), r.all());
        assertEquals(List.of(), publicTopLevel(r.generated()));
    }

    @Test
    void mixedVisibilityInOnePackageIsAnError() throws Exception {
        assertError("""
                        @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = int.class)
                        @net.collections.stencil.annotations.PrimitiveSet(element = int.class, packagePrivate = true)""",
                "packagePrivate = true conflicts with another request for package com.example.coll,"
                        + " which generates public classes");
        assertError("""
                        @net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = int.class, packagePrivate = true)
                        @net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = int.class)""",
                "packagePrivate = false conflicts with another request for package com.example.coll,"
                        + " which generates package-private classes");
    }

    @Test
    void visibilityIsPerPackage() throws Exception {
        String source = """
                package com.example.app;

                import com.example.pub.IntHashSet;

                @net.collections.stencil.annotations.PrimitiveSet(element = int.class, packageName = "com.example.pub")
                @net.collections.stencil.annotations.PrimitiveSet(element = long.class, packagePrivate = true)
                class App {
                    IntHashSet ints = new IntHashSet();
                    LongHashSet longs = new LongHashSet();
                }
                """;
        Result r = compile(Map.of("com/example/app/App.java", source), false);
        assertTrue(r.success(), r.all());
        assertTrue(Files.readString(r.generated().resolve("com/example/pub/IntHashSet.java"))
                .contains("\npublic final class IntHashSet"));
        assertTrue(Files.readString(r.generated().resolve("com/example/app/LongHashSet.java"))
                .contains("\nfinal class LongHashSet"));
    }

    private void assertError(String annotation, String expectedMessage) throws Exception {
        String source = "package com.example.coll;\n\n" + annotation + "\nclass Bad {\n}\n";
        Result r = compile(Map.of("com/example/coll/Bad.java", source), false);
        assertFalse(r.success(), annotation);
        assertTrue(r.errors().contains(expectedMessage), r.all());
    }

    @Test
    void invalidRequestsAreErrors() throws Exception {
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = float.class, value = int.class)",
                "must be int.class, long.class or Object.class");
        assertError("@net.collections.stencil.annotations.PrimitiveSet(element = Object.class)",
                "must be int.class or long.class");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = Object.class)",
                "needs a primitive value type");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = int.class, compact = true)",
                "not available for object keys");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = int.class, sorted = true)",
                "not available for object keys");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = boolean.class)",
                "stencil cannot generate ObjectBooleanHashMap");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = String.class)",
                "must be a primitive type or Object.class");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = Object.class, compact = true)",
                "not available for object values");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = long.class, sorted = true,"
                + " compact = true)", "cannot be combined with compact = true");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = int.class, value = Object.class, concurrent = true,"
                + " compact = true)", "cannot be combined with compact = true");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = Object.class, concurrent = true,"
                + " sorted = true)", "cannot be combined with sorted = true");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = float.class, concurrent = true)",
                "available for int and long keys with object, int, long and double values");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = int.class, concurrent = true)",
                "available for int and long keys with object, int, long and double values");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = long.class, linked = true,"
                + " compact = true)", "linked = true) cannot be combined with compact = true");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = Object.class, linked = true,"
                + " sorted = true)", "linked = true) cannot be combined with sorted = true");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = long.class, value = Object.class, linked = true,"
                + " concurrent = true)", "linked = true) cannot be combined with concurrent = true");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = byte.class, linked = true)",
                "stencil cannot generate ObjectByteLinkedHashMap");
        assertError("@net.collections.stencil.annotations.PrimitiveMap(key = Object.class, value = Object.class, linked = true)",
                "needs a primitive value type");
        assertError("@net.collections.stencil.annotations.PrimitiveSet(element = long.class, linked = true, compact = true)",
                "linked = true) cannot be combined with compact = true");
        assertError("@net.collections.stencil.annotations.PrimitiveSet(element = long.class, sorted = true, compact = true)",
                "sorted = true) cannot be combined with compact = true");
        assertError("@net.collections.stencil.annotations.PrimitiveSet(element = long.class, sorted = true, linked = true)",
                "sorted = true) cannot be combined with linked = true");
        assertError("@net.collections.stencil.annotations.PrimitiveSet(element = int.class, compact = true)",
                "available for long elements only");
        assertError("@net.collections.stencil.annotations.PrimitiveSet(element = long.class, packageName = \"1abc\")",
                "not a valid package name");
        assertError("@net.collections.stencil.annotations.PrimitiveHeap(priority = byte.class)",
                "must be int.class, long.class or double.class");
        assertError("@net.collections.stencil.annotations.PrimitiveHeap(priority = Object.class, indexed = true)",
                "must be int.class, long.class or double.class");
        assertError("@net.collections.stencil.annotations.PrimitiveHeap(priority = long.class, indexed = true, key = double.class)",
                "@PrimitiveHeap(key = ...) must be int.class or long.class");
        assertError("@net.collections.stencil.annotations.PrimitiveHeap(priority = long.class, key = long.class)",
                "@PrimitiveHeap(key = long.class) requires indexed = true");
        assertError("@net.collections.stencil.annotations.PrimitiveList(element = byte.class)",
                "@PrimitiveList(element = ...) must be int.class, long.class or double.class");
        assertError("@net.collections.stencil.annotations.PrimitiveDeque(element = Object.class)",
                "@PrimitiveDeque(element = ...) must be int.class, long.class or double.class");
    }

    @Test
    void unnamedPackageNeedsPackageName() throws Exception {
        Result r = compile(Map.of("Bad.java", """
                @net.collections.stencil.annotations.PrimitiveSet(element = int.class)
                class Bad {
                }
                """), false);
        assertFalse(r.success());
        assertTrue(r.errors().contains("unnamed package"), r.all());
    }
}
