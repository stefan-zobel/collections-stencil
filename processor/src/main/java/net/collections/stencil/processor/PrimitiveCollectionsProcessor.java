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

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import net.collections.stencil.generator.Generator;
import net.collections.stencil.generator.Generator.GeneratedFile;

/**
 * Generates the collection classes requested with {@code @PrimitiveMap}, {@code @PrimitiveSet},
 * {@code @PrimitiveHeap}, {@code @PrimitiveList}, and {@code @PrimitiveDeque} as source files of
 * the compiled project.
 * <p>
 * All requests of a compilation are collected per target package. For each package, the
 * processor generates the requested classes plus the interfaces and functional interfaces they
 * use, moved into that package. A class requested several times is generated once, with all
 * requesting elements as its originating elements (an aggregating processor in the sense of
 * incremental builds). With {@code packagePrivate = true}, the top-level types of the package
 * are package-private; all requests for one package must agree on this. Invalid requests are
 * reported as errors at the annotation.
 */
@SupportedAnnotationTypes({
        PrimitiveCollectionsProcessor.MAP, PrimitiveCollectionsProcessor.MAP_LIST,
        PrimitiveCollectionsProcessor.SET, PrimitiveCollectionsProcessor.SET_LIST,
        PrimitiveCollectionsProcessor.HEAP, PrimitiveCollectionsProcessor.HEAP_LIST,
        PrimitiveCollectionsProcessor.LIST, PrimitiveCollectionsProcessor.LIST_LIST,
        PrimitiveCollectionsProcessor.DEQUE, PrimitiveCollectionsProcessor.DEQUE_LIST})
public final class PrimitiveCollectionsProcessor extends AbstractProcessor {

    static final String MAP = "net.collections.stencil.annotations.PrimitiveMap";
    static final String MAP_LIST = "net.collections.stencil.annotations.PrimitiveMap.List";
    static final String SET = "net.collections.stencil.annotations.PrimitiveSet";
    static final String SET_LIST = "net.collections.stencil.annotations.PrimitiveSet.List";
    static final String HEAP = "net.collections.stencil.annotations.PrimitiveHeap";
    static final String HEAP_LIST = "net.collections.stencil.annotations.PrimitiveHeap.List";
    static final String LIST = "net.collections.stencil.annotations.PrimitiveList";
    static final String LIST_LIST = "net.collections.stencil.annotations.PrimitiveList.List";
    static final String DEQUE = "net.collections.stencil.annotations.PrimitiveDeque";
    static final String DEQUE_LIST = "net.collections.stencil.annotations.PrimitiveDeque.List";

    /** Every class the bundled templates can generate, created on first use. */
    private List<GeneratedFile> catalog;
    private Set<String> catalogNames;
    /** Qualified names written in earlier rounds. */
    private final Set<String> written = new HashSet<>();
    /** Whether the classes of a target package are package-private, fixed by its first request. */
    private final Map<String, Boolean> packagePrivateByPackage = new HashMap<>();

    /**
     * What one target package needs: the requested classes, the elements that requested them,
     * and whether the classes are package-private.
     */
    private record PackageRequest(Set<String> classes, Set<Element> origins, boolean packagePrivate) {
    }

    /** Creates the processor. The compiler finds it through {@code META-INF/services}. */
    public PrimitiveCollectionsProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (annotations.isEmpty()) {
            return false;
        }
        Map<String, PackageRequest> requests = new TreeMap<>();
        for (Element element : round.getElementsAnnotatedWithAny(annotations.toArray(new TypeElement[0]))) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                switch (nameOf(mirror)) {
                    case MAP -> request(element, mirror, Kind.MAP, requests);
                    case SET -> request(element, mirror, Kind.SET, requests);
                    case HEAP -> request(element, mirror, Kind.HEAP, requests);
                    case LIST -> request(element, mirror, Kind.LIST, requests);
                    case DEQUE -> request(element, mirror, Kind.DEQUE, requests);
                    case MAP_LIST -> nested(mirror).forEach(m -> request(element, m, Kind.MAP, requests));
                    case SET_LIST -> nested(mirror).forEach(m -> request(element, m, Kind.SET, requests));
                    case HEAP_LIST -> nested(mirror).forEach(m -> request(element, m, Kind.HEAP, requests));
                    case LIST_LIST -> nested(mirror).forEach(m -> request(element, m, Kind.LIST, requests));
                    case DEQUE_LIST -> nested(mirror).forEach(m -> request(element, m, Kind.DEQUE, requests));
                    default -> {
                        // not ours
                    }
                }
            }
        }
        requests.forEach(this::generate);
        return true;
    }

    // ------------------------------------------------------------------
    // Reading the annotations
    // ------------------------------------------------------------------

    /** The kind of an annotation: which classes it can request. */
    private enum Kind {
        MAP, SET, HEAP, LIST, DEQUE
    }

    private void request(Element element, AnnotationMirror mirror, Kind kind, Map<String, PackageRequest> requests) {
        List<String> classes = switch (kind) {
            case MAP, SET -> mapOrSetClasses(element, mirror, kind == Kind.MAP);
            case HEAP -> heapClasses(element, mirror);
            case LIST -> sequenceClasses(element, mirror, "@PrimitiveList", "ArrayList");
            case DEQUE -> sequenceClasses(element, mirror, "@PrimitiveDeque", "ArrayDeque");
        };
        if (classes == null) {
            return; // reported
        }
        for (String c : classes) {
            if (!catalogNames().contains(c)) {
                error(element, mirror, "stencil cannot generate " + c);
                return;
            }
        }
        String pkg = (String) value(mirror, "packageName");
        if (pkg.isEmpty()) {
            PackageElement own = processingEnv.getElementUtils().getPackageOf(element);
            if (own.isUnnamed()) {
                error(element, mirror, "the annotated element is in the unnamed package; set packageName");
                return;
            }
            pkg = own.getQualifiedName().toString();
        } else if (!SourceVersion.isName(pkg)) {
            error(element, mirror, "packageName is not a valid package name: " + pkg);
            return;
        }
        boolean packagePrivate = (Boolean) value(mirror, "packagePrivate");
        Boolean fixed = packagePrivateByPackage.putIfAbsent(pkg, packagePrivate);
        if (fixed != null && fixed != packagePrivate) {
            error(element, mirror, "packagePrivate = " + packagePrivate + " conflicts with another request for package "
                    + pkg + ", which generates " + (fixed ? "package-private" : "public") + " classes;"
                    + " all classes of a package have the same visibility");
            return;
        }
        PackageRequest r = requests.computeIfAbsent(pkg,
                p -> new PackageRequest(new LinkedHashSet<>(), new LinkedHashSet<>(), packagePrivate));
        r.classes().addAll(classes);
        r.origins().add(element);
    }

    /** The classes of a {@code @PrimitiveHeap} request, or {@code null} after reporting an error. */
    private List<String> heapClasses(Element element, AnnotationMirror mirror) {
        String priority = switch (((TypeMirror) value(mirror, "priority")).getKind()) {
            case INT -> "Int";
            case LONG -> "Long";
            case DOUBLE -> "Double";
            default -> null;
        };
        if (priority == null) {
            error(element, mirror, "@PrimitiveHeap(priority = ...) must be int.class, long.class or double.class");
            return null;
        }
        boolean indexed = (Boolean) value(mirror, "indexed");
        String key = switch (((TypeMirror) value(mirror, "key")).getKind()) {
            case INT -> "Int";
            case LONG -> "Long";
            default -> null;
        };
        if (key == null) {
            error(element, mirror, "@PrimitiveHeap(key = ...) must be int.class or long.class");
            return null;
        }
        if (!indexed && key.equals("Long")) {
            error(element, mirror, "@PrimitiveHeap(key = long.class) requires indexed = true");
            return null;
        }
        return List.of(indexed ? key + priority + "IndexedHeap" : priority + "Heap");
    }

    /**
     * The class of a {@code @PrimitiveList} or {@code @PrimitiveDeque} request, or {@code null}
     * after reporting an error.
     */
    private List<String> sequenceClasses(Element element, AnnotationMirror mirror, String annotation, String suffix) {
        String type = switch (((TypeMirror) value(mirror, "element")).getKind()) {
            case INT -> "Int";
            case LONG -> "Long";
            case DOUBLE -> "Double";
            default -> null;
        };
        if (type == null) {
            error(element, mirror, annotation + "(element = ...) must be int.class, long.class or double.class");
            return null;
        }
        return List.of(type + suffix);
    }

    /** The classes of a {@code @PrimitiveMap} or {@code @PrimitiveSet} request, or {@code null} after reporting an error. */
    private List<String> mapOrSetClasses(Element element, AnnotationMirror mirror, boolean map) {
        String keyAttribute = map ? "key" : "element";
        String key = keyTitle((TypeMirror) value(mirror, keyAttribute));
        if (key == null || (!map && key.equals("Object"))) {
            error(element, mirror, map
                    ? "@PrimitiveMap(key = ...) must be int.class, long.class or Object.class"
                    : "@PrimitiveSet(element = ...) must be int.class or long.class");
            return null;
        }
        boolean compact = (Boolean) value(mirror, "compact");
        boolean linked = (Boolean) value(mirror, "linked");
        List<String> classes = new ArrayList<>();
        if (map) {
            String value = valueTitle((TypeMirror) value(mirror, "value"));
            if (value == null) {
                error(element, mirror, "@PrimitiveMap(value = ...) must be a primitive type or Object.class");
                return null;
            }
            boolean sorted = (Boolean) value(mirror, "sorted");
            boolean concurrent = (Boolean) value(mirror, "concurrent");
            if (concurrent) {
                if (compact || sorted) {
                    error(element, mirror, "@PrimitiveMap(concurrent = true) cannot be combined with "
                            + (compact ? "compact" : "sorted") + " = true");
                    return null;
                }
                if (key.equals("Object") || !List.of("Object", "Int", "Long", "Double").contains(value)) {
                    error(element, mirror, "@PrimitiveMap(concurrent = true) is available for int and long keys"
                            + " with object, int, long and double values");
                    return null;
                }
            }
            if (linked && (compact || sorted || concurrent)) {
                error(element, mirror, "@PrimitiveMap(linked = true) cannot be combined with "
                        + (compact ? "compact" : sorted ? "sorted" : "concurrent") + " = true");
                return null;
            }
            if (key.equals("Object")) {
                if (value.equals("Object")) {
                    error(element, mirror, "@PrimitiveMap(key = Object.class) needs a primitive value type;"
                            + " use java.util.HashMap for object keys and values");
                    return null;
                }
                if (compact || sorted) {
                    error(element, mirror, "@PrimitiveMap(" + (compact ? "compact" : "sorted")
                            + " = true) is not available for object keys");
                    return null;
                }
            }
            if (compact && value.equals("Object")) {
                error(element, mirror, "@PrimitiveMap(compact = true) is not available for object values");
                return null;
            }
            if (sorted && compact) {
                error(element, mirror, "@PrimitiveMap(sorted = true) cannot be combined with compact = true");
                return null;
            }
            classes.add(key + value + (sorted ? "TreeMap" : concurrent ? "ConcurrentHashMap"
                    : linked ? "LinkedHashMap" : "HashMap"));
            if (compact) {
                classes.add(key + value + "CompactHashMap");
            }
        } else {
            boolean sorted = (Boolean) value(mirror, "sorted");
            if (sorted && (compact || linked)) {
                error(element, mirror, "@PrimitiveSet(sorted = true) cannot be combined with "
                        + (compact ? "compact" : "linked") + " = true");
                return null;
            }
            if (linked && compact) {
                error(element, mirror, "@PrimitiveSet(linked = true) cannot be combined with compact = true");
                return null;
            }
            classes.add(key + (sorted ? "TreeSet" : linked ? "LinkedHashSet" : "HashSet"));
            if (compact) {
                if (key.equals("Int")) {
                    error(element, mirror, "@PrimitiveSet(compact = true) is available for long elements only:"
                            + " with int elements, the compact layout is slower than IntHashSet at the same memory");
                    return null;
                }
                classes.add(key + "CompactHashSet");
            }
        }
        return classes;
    }

    private static String nameOf(AnnotationMirror mirror) {
        return ((TypeElement) mirror.getAnnotationType().asElement()).getQualifiedName().toString();
    }

    /** The annotations inside a container annotation. */
    private List<AnnotationMirror> nested(AnnotationMirror container) {
        List<AnnotationMirror> result = new ArrayList<>();
        for (Object v : (List<?>) value(container, "value")) {
            result.add((AnnotationMirror) ((AnnotationValue) v).getValue());
        }
        return result;
    }

    private Object value(AnnotationMirror mirror, String name) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
                : processingEnv.getElementUtils().getElementValuesWithDefaults(mirror).entrySet()) {
            if (e.getKey().getSimpleName().contentEquals(name)) {
                return e.getValue().getValue();
            }
        }
        throw new IllegalStateException("missing attribute " + name + " in " + mirror);
    }

    /** {@code Int}, {@code Long} or {@code Object} for the supported key types, otherwise {@code null}. */
    private static String keyTitle(TypeMirror type) {
        return switch (type.getKind()) {
            case INT -> "Int";
            case LONG -> "Long";
            case DECLARED -> isObject(type) ? "Object" : null;
            default -> null;
        };
    }

    private static boolean isObject(TypeMirror type) {
        return ((TypeElement) ((DeclaredType) type).asElement()).getQualifiedName().contentEquals("java.lang.Object");
    }

    /** The title of a value type ({@code Int}, {@code Object}, ...), or {@code null}. */
    private static String valueTitle(TypeMirror type) {
        return switch (type.getKind()) {
            case BOOLEAN -> "Boolean";
            case BYTE -> "Byte";
            case SHORT -> "Short";
            case CHAR -> "Char";
            case INT -> "Int";
            case LONG -> "Long";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            case DECLARED -> isObject(type) ? "Object" : null;
            default -> null;
        };
    }

    // ------------------------------------------------------------------
    // Generating
    // ------------------------------------------------------------------

    private void generate(String pkg, PackageRequest request) {
        List<GeneratedFile> files;
        try {
            files = Generator.relocate(Generator.select(catalog(), request.classes()), pkg);
            if (request.packagePrivate()) {
                files = Generator.packagePrivate(files);
            }
        } catch (RuntimeException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "stencil: cannot generate " + request.classes() + " in " + pkg + ": " + e.getMessage());
            return;
        }
        Element[] origins = request.origins().toArray(new Element[0]);
        for (GeneratedFile f : files) {
            String qualified = pkg + "." + f.className();
            if (!written.add(qualified)) {
                continue;
            }
            try {
                JavaFileObject source = processingEnv.getFiler().createSourceFile(qualified, origins);
                try (Writer w = source.openWriter()) {
                    w.write("// Generated by the stencil annotation processor from template " + f.templatePath()
                            + ". Do not edit.\n");
                    w.write(f.content());
                }
            } catch (IOException e) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "stencil: cannot write " + qualified + ": " + e.getMessage(), origins[0]);
            }
        }
    }

    private List<GeneratedFile> catalog() {
        if (catalog == null) {
            catalog = Generator.generate(Generator.bundledTemplates(), name -> true, false);
        }
        return catalog;
    }

    private Set<String> catalogNames() {
        if (catalogNames == null) {
            catalogNames = new HashSet<>();
            for (GeneratedFile f : catalog()) {
                catalogNames.add(f.className());
            }
        }
        return catalogNames;
    }

    private void error(Element element, AnnotationMirror mirror, String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element, mirror);
    }
}
