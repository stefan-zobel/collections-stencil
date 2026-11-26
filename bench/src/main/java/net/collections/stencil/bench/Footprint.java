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

package net.collections.stencil.bench;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

import org.openjdk.jmh.annotations.Fork;

/**
 * Measures the table footprint of every map or set after the benchmark's own population
 * step (growth from the default capacity), so that speed can be compared at equal memory.
 * <p>
 * Counts the payload of all array fields of the collection object: {@code length * elementSize}
 * per array, without object headers. This covers the hash tables of all primitive maps and
 * sets. Boxed {@code HashMap}/{@code HashSet} entries live in separate node objects and are not
 * counted, so the JDK collections are skipped. No timing is involved; the result is
 * deterministic.
 * <p>
 * Sorted maps are trees of node objects in most libraries, so the {@code longsorted} suite
 * measures the <em>deep</em> size instead: every object reachable from the map (nodes, boxed
 * keys, arrays) with its header and alignment, assuming compressed references and class
 * pointers (12-byte object headers, 16-byte array headers, 8-byte alignment). The value
 * objects come from a shared pool and are not counted ({@link Mode#DEEP_SHARED_VALUES}). The
 * {@code longlongsorted} suite uses the same mode; its {@code long} values are part of the
 * map, and the boxed {@link Long} values of {@code java.util.TreeMap} count. So does the
 * {@code longsetsorted} suite (sorted sets), where the boxed {@link Long} elements of
 * {@code java.util.TreeSet} count. The
 * {@code longlinked} suite (insertion-ordered maps) is measured the same way, so that the nodes
 * and boxed keys of {@code java.util.LinkedHashMap} count. The
 * {@code objectint} suite measures the deep size as well, without the {@link String} keys,
 * which the application owns anyway, but with the boxed {@link Integer} values of the JDK map,
 * which exist only because of the map ({@link Mode#DEEP_SHARED_KEYS}); so does the
 * {@code objectintlinked} suite (insertion-ordered maps with {@link String} keys). The priority
 * queue suites ({@code longheap}, {@code intlongindexedheap}, {@code longlongindexedheap}) measure
 * the deep size of a full queue ({@link Mode#DEEP_SHARED_VALUES}), with the boxed values and entry
 * records of the JDK queues, and with the maps from keys to ids of the workarounds.
 * The list and deque suites ({@code intlist}, {@code intdeque}) measure the deep size of a full
 * collection ({@link Mode#DEEP_BOXED_KEYS}), so that the boxed {@link Integer} values of the JDK
 * collections count. Walking the nodes of
 * {@code java.util.HashMap} and {@code java.util.TreeMap} requires
 * {@code --add-opens java.base/java.util=ALL-UNNAMED}; without it, the JDK map is skipped with a
 * warning. The concurrent suites ({@link Mode#DEEP_BOXED_KEYS}) count the boxed keys of
 * {@code ConcurrentHashMap} but not the shared values, and need
 * {@code java.util.concurrent}, {@code java.util.concurrent.locks} and
 * {@code java.util.concurrent.atomic} opened as well, for the nodes, the locks and the
 * {@code LongAdder} values.
 * <p>
 * Each implementation is measured in a child JVM that gets the options of the runner JVM plus
 * the {@code @Fork(jvmArgsAppend)} options of its benchmark class, so that variants configured
 * through system properties (such as the node capacities of the tree experiment) are measured
 * with their own configuration.
 */
final class Footprint {

    private Footprint() {
    }

    /** What is measured. */
    enum Mode {
        /** Payload of the array fields of the collection object; JDK collections are skipped. */
        TABLE,
        /** Deep size without the {@link Integer} values (shared value pool of the benchmarks). */
        DEEP_SHARED_VALUES,
        /** Deep size without the {@link String} keys; boxed {@link Integer} values count (16 bytes each). */
        DEEP_SHARED_KEYS,
        /**
         * Deep size without the {@link SharedValue} values of the benchmarks' pool; boxed
         * {@link Integer} keys count (16 bytes each).
         */
        DEEP_BOXED_KEYS
    }

    static void write(Path csv, List<String> impls, List<Integer> sizes, Mode mode) throws IOException {
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv, StandardCharsets.UTF_8))) {
            out.println("impl,size,table_bytes,arrays");
            for (String impl : impls) {
                if (impl.equals("jdk") && mode == Mode.TABLE) {
                    continue;
                }
                for (String line : measureInChildJvm(impl, InterleavedRunner.implClasses.get(impl), sizes, mode)) {
                    out.println(line);
                }
            }
        }
    }

    /** Runs {@link #main} for one implementation in a child JVM and returns its CSV lines. */
    private static List<String> measureInChildJvm(String impl, String className, List<Integer> sizes, Mode mode)
            throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
        try {
            Fork fork = Class.forName(className).getAnnotation(Fork.class);
            if (fork != null) {
                cmd.addAll(List.of(fork.jvmArgsAppend()));
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("unknown benchmark class " + className, e);
        }
        cmd.addAll(List.of("-cp", System.getProperty("java.class.path"), Footprint.class.getName(),
                impl, className, mode.name()));
        sizes.forEach(size -> cmd.add(Integer.toString(size)));
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line; (line = in.readLine()) != null; ) {
                lines.add(line);
            }
        }
        try {
            int rc = p.waitFor();
            if (rc != 0) {
                System.err.println("footprint of " + impl + " failed (exit " + rc + ")");
                return List.of();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return lines;
    }

    /** Child JVM entry point: {@code impl className mode size...}; prints CSV lines to standard output. */
    public static void main(String[] args) {
        String impl = args[0];
        String className = args[1];
        Mode mode = Mode.valueOf(args[2]);
        for (int a = 3; a < args.length; a++) {
            int size = Integer.parseInt(args[a]);
            Object map = populatedMap(className, size);
            if (mode != Mode.TABLE) {
                long bytes;
                try {
                    bytes = deepBytes(map, mode);
                } catch (RuntimeException e) {
                    System.err.println("footprint of " + impl + " skipped: " + e);
                    return;
                }
                System.out.printf(Locale.ROOT, "%s,%d,%d,deep%n", impl, size, bytes);
            } else {
                List<String> arrays = new ArrayList<>();
                long bytes = arrayBytes(map, arrays);
                System.out.printf(Locale.ROOT, "%s,%d,%d,%s%n", impl, size, bytes, String.join(" + ", arrays));
            }
        }
    }

    /**
     * Bytes of all objects reachable from {@code root}, headers and alignment included, except
     * the {@link Integer} value objects of the benchmarks' shared pool.
     */
    static long deepBytes(Object root) {
        return deepBytes(root, Mode.DEEP_SHARED_VALUES);
    }

    /**
     * Bytes of all objects reachable from {@code root}, headers and alignment included, except
     * the shared objects that {@code mode} excludes ({@link Integer} or {@link String}).
     */
    static long deepBytes(Object root, Mode mode) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        ArrayDeque<Object> pending = new ArrayDeque<>();
        pending.push(root);
        long total = 0;
        while (!pending.isEmpty()) {
            Object o = pending.pop();
            Class<?> c = o.getClass();
            if (seen.put(o, Boolean.TRUE) != null) {
                continue;
            }
            if (c == SharedValue.class) {
                continue;
            }
            if (c == Integer.class) {
                if (mode == Mode.DEEP_SHARED_KEYS || mode == Mode.DEEP_BOXED_KEYS) {
                    total += 16; // header and int, aligned; not walked (java.lang is not open)
                }
                continue;
            }
            if (c == String.class && mode == Mode.DEEP_SHARED_KEYS) {
                continue;
            }
            if (c.isArray()) {
                Class<?> component = c.getComponentType();
                int length = Array.getLength(o);
                total += align(16 + (long) length * elementSize(component));
                if (!component.isPrimitive()) {
                    for (int i = 0; i < length; i++) {
                        Object e = Array.get(o, i);
                        if (e != null) {
                            pending.push(e);
                        }
                    }
                }
                continue;
            }
            long bytes = 12;
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                for (Field f : k.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    bytes += elementSize(f.getType());
                    if (!f.getType().isPrimitive()) {
                        f.setAccessible(true);
                        Object v;
                        try {
                            v = f.get(o);
                        } catch (IllegalAccessException e) {
                            throw new IllegalStateException("cannot read " + f, e);
                        }
                        if (v != null) {
                            pending.push(v);
                        }
                    }
                }
            }
            total += align(bytes);
        }
        return total;
    }

    private static long align(long bytes) {
        return (bytes + 7) & ~7L;
    }

    private static Object populatedMap(String className, int size) {
        try {
            Class<?> cls = Class.forName(className);
            Object bench = cls.getDeclaredConstructor().newInstance();
            findField(cls, "size").setInt(bench, size);
            findField(cls, "dist").set(bench, KeyDist.RANDOM);
            cls.getMethod("setUpData").invoke(bench);
            Field f;
            try {
                f = findField(cls, "map");
            } catch (NoSuchFieldException e) {
                f = findField(cls, "set");
            }
            f.setAccessible(true);
            return f.get(bench);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot populate " + className, e);
        }
    }

    private static long arrayBytes(Object map, List<String> arrays) {
        long total = 0;
        for (Class<?> c = map.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || !f.getType().isArray()) {
                    continue;
                }
                f.setAccessible(true);
                Object a;
                try {
                    a = f.get(map);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("cannot read " + f, e);
                }
                if (a == null) {
                    continue;
                }
                Class<?> component = a.getClass().getComponentType();
                int length = Array.getLength(a);
                total += (long) length * elementSize(component);
                arrays.add(component.getSimpleName() + "[" + length + "]");
            }
        }
        return total;
    }

    private static int elementSize(Class<?> component) {
        if (component == long.class || component == double.class) {
            return 8;
        }
        if (component == int.class || component == float.class) {
            return 4;
        }
        if (component == short.class || component == char.class) {
            return 2;
        }
        if (component == byte.class || component == boolean.class) {
            return 1;
        }
        return 4; // compressed references
    }

    private static Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                // continue with the superclass
            }
        }
        throw new NoSuchFieldException(name + " in " + cls.getName());
    }
}
