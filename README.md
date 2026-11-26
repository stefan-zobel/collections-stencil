# collections-stencil

**Fast primitive collections for Java, generated as source code into your own project.**

Java's collections box every `int`, `long`, and `double`. A `HashMap<Long, Long>` stores an
entry object and two `Long` objects per entry, several times the 16 bytes of the data, and
follows pointers on every lookup. stencil gives you maps, sets, priority
queues, lists, and deques that store primitives directly, without generating a jar full of
classes you never use:

1. You declare in an annotation which collections you need, for example "a map from `long` to
   `double`".
2. At compile time, the stencil annotation processor writes exactly these classes as plain,
   readable Java sources into your package.
3. Your code uses them like any other class of your project. Nothing is added to your runtime
   classpath: the generated code depends on the JDK only.

If you would rather own and edit the code, a command line generator writes the same classes
once into your source tree.

> **Status:** pre-release (`0.1.0-SNAPSHOT`), not yet on Maven Central. To try it, build it
> locally (see [Installation](#installation)). The API may still change before 1.0.

## Contents

- [What You Get](#what-you-get)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Selecting Collections](#selecting-collections)
- [Using the Collections](#using-the-collections)
- [Command Line Generator](#command-line-generator)
- [Performance](#performance)
- [Further Documentation](#further-documentation)

## What You Get

| Kind | Example classes | Keys / elements | Values |
|------|-----------------|-----------------|--------|
| Hash maps | `IntLongHashMap`, `LongObjectHashMap<V>` | `int`, `long` | every primitive type, objects |
| Compact hash maps (for many lookups of absent keys) | `LongDoubleCompactHashMap` | `int`, `long` | every primitive type |
| Maps from object keys | `ObjectIntHashMap<K>` | objects | `int`, `long`, `double` |
| Sorted maps (B+ tree) | `LongLongTreeMap`, `IntObjectTreeMap<V>` | `int`, `long` | every primitive type, objects |
| Insertion-ordered maps | `LongObjectLinkedHashMap<V>`, `ObjectIntLinkedHashMap<K>` | `int`, `long`, objects | every primitive type, objects (object keys: `int`, `long`, `double`) |
| Thread-safe maps | `LongLongConcurrentHashMap`, `IntObjectConcurrentHashMap<V>` | `int`, `long` | `int`, `long`, `double`, objects |
| Sets: hash, compact, insertion-ordered, sorted | `IntHashSet`, `LongCompactHashSet`, `LongLinkedHashSet`, `LongTreeSet` | `int`, `long` (compact: `long` only) | – |
| Priority queues | `LongHeap`, `DoubleHeap` | `int`, `long`, `double` | – |
| Indexed priority queues ("decrease key") | `IntLongIndexedHeap` (dense `int` ids), `LongLongIndexedHeap` (any `long` keys) | `int`, `long` | priorities `int`, `long`, `double` |
| Lists | `IntArrayList`, `DoubleArrayList` | `int`, `long`, `double` | – |
| Deques with access by index | `LongArrayDeque` | `int`, `long`, `double` | – |

Each class comes with the interface it implements (`IntLongMap`, `LongSortedSet`, ...) and the
functional interfaces it uses (`IntLongConsumer`, `IntLongPredicate`, ...). All of them share
these properties:

- **No boxing:** keys, values, and elements are stored in primitive arrays.
- **No reserved values:** key `0` is a regular key, and so is `null` for object keys.
- **Lookups that do not search twice:** index handles for "find, then read, update, or insert".
- **Iteration without allocation:** `forEach` with primitive lambdas, or a loop over handles.
- **Interoperability:** conversions to and from `java.util` collections; `equals` and `hashCode`
  follow `java.util.Map`, `Set`, and `List`.
- **Readable code:** every generated class is ordinary Java with complete Javadoc. You can read
  it, debug into it, and see exactly what it does.

## Installation

Requirements:

- **Using stencil:** JDK 21 or later to compile your project. The generated sources also
  compile with `--release 19`.
- **Building stencil:** JDK 25 or later. stencil is built and tested with JDK 25.

Until the first release is on Maven Central, install it into your local Maven repository:

```shell
git clone https://github.com/stefan-zobel/collections-stencil.git
cd collections-stencil
./mvnw -B install          # Windows: .\mvnw.cmd -B install
```

This builds and tests all modules and installs `collections.stencil-annotations`,
`collections.stencil-processor`, and `collections.stencil-generator` with the version
`0.1.0-SNAPSHOT`.

## Quick Start

### 1. Add stencil to the build

**Maven:** the annotations are needed at compile time only, and the processor goes onto the
annotation processor path. Since JDK 23, `javac` runs annotation processors only when they are
configured explicitly, as here.

```xml
<dependencies>
    <dependency>
        <groupId>net.sourceforge.streamsupport</groupId>
        <artifactId>collections.stencil-annotations</artifactId>
        <version>0.1.0-SNAPSHOT</version>
        <scope>provided</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>net.sourceforge.streamsupport</groupId>
                        <artifactId>collections.stencil-processor</artifactId>
                        <version>0.1.0-SNAPSHOT</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Gradle** (Kotlin DSL); the processor supports incremental compilation:

```kotlin
repositories {
    mavenLocal()     // until the first release is on Maven Central
    mavenCentral()
}

dependencies {
    compileOnly("net.sourceforge.streamsupport:collections.stencil-annotations:0.1.0-SNAPSHOT")
    annotationProcessor("net.sourceforge.streamsupport:collections.stencil-processor:0.1.0-SNAPSHOT")
}
```

**Modular projects** add one line to their `module-info.java` (the annotations are not needed
at run time):

```java
requires static net.collections.stencil.annotations;
```

### 2. Request the collections

Annotate the package that should contain the classes, in its `package-info.java`:

```java
// src/main/java/com/example/stats/package-info.java
@PrimitiveMap(key = long.class, value = long.class)    // LongLongHashMap, LongLongMap, ...
@PrimitiveMap(key = Object.class, value = int.class)   // ObjectIntHashMap<K>, ObjectIntMap<K>, ...
@PrimitiveList(element = double.class)                 // DoubleArrayList, ...
package com.example.stats;

import net.collections.stencil.annotations.PrimitiveList;
import net.collections.stencil.annotations.PrimitiveMap;
```

### 3. Use them

Compile once; then the classes exist in `com.example.stats`:

```java
package com.example.stats;

public final class WordStats {

    public static void main(String[] args) {
        ObjectIntHashMap<String> counts = new ObjectIntHashMap<>();
        for (String word : "to be or not to be".split(" ")) {
            counts.addTo(word, 1);                     // no Integer, no merge(...)
        }
        counts.forEach((word, n) -> System.out.println(word + " " + n));

        LongLongHashMap bytesPerUser = new LongLongHashMap();
        bytesPerUser.addTo(42L, 1_500L);
        long total = bytesPerUser.get(42L);            // 0 for an absent key
    }
}
```

The generated sources are in `target/generated-sources/annotations` (Maven) or
`build/generated/sources/annotationProcessor/java/main` (Gradle). Most IDEs pick them up when
they import the build; otherwise, enable annotation processing in the IDE's compiler settings.
Do not edit them: they are written again on every build. To change the code, use the
[command line generator](#command-line-generator) instead.

## Selecting Collections

There is one annotation per kind of collection. Each one is repeatable and goes on a package
(`package-info.java`) or on a type. A class that several annotations request is generated once.

### `@PrimitiveMap(key = ..., value = ...)`

`key` is `int.class`, `long.class`, or `Object.class`; `value` is any primitive class or
`Object.class`.

| Attributes | Generated map | Available for |
|------------|---------------|---------------|
| (none) | `IntLongHashMap`, `LongObjectHashMap<V>`, ...: linear probing | `int`/`long` keys with every value type |
| `key = Object.class` | `ObjectIntHashMap<K>`, ...: linear probing with stored hashes | `int`, `long`, `double` values |
| `compact = true` | in addition `...CompactHashMap`: tagged blocks, faster for absent keys and at high load factors | `int`/`long` keys with primitive values |
| `sorted = true` | `...TreeMap` and the interface `...SortedMap` instead of the hash map: a B+ tree | `int`/`long` keys with every value type |
| `linked = true` | `...LinkedHashMap` instead of the hash map: insertion order | `int`/`long` keys with every value type; object keys with `int`, `long`, `double` values |
| `concurrent = true` | `...ConcurrentHashMap` and the interface `...ConcurrentMap` instead of the hash map: thread-safe, lookups without locks | `int`/`long` keys with object, `int`, `long`, `double` values |

`compact`, `sorted`, `linked`, and `concurrent` exclude each other.

### `@PrimitiveSet(element = ...)`

`element` is `int.class` or `long.class`. Without further attributes: `IntHashSet` and the
interface `IntSet`. `compact = true` generates `LongCompactHashSet` in addition (`long` only);
`linked = true` generates `...LinkedHashSet`, `sorted = true` generates `...TreeSet` and
`...SortedSet` instead of the hash set. The three exclude each other.

### `@PrimitiveHeap(priority = ...)`

`priority` is `int.class`, `long.class`, or `double.class`.

| Attributes | Generated queue |
|------------|-----------------|
| (none) | `LongHeap`, ...: a priority queue of values, smallest or largest first |
| `indexed = true` | `IntLongIndexedHeap`, ...: `int` ids `0, 1, 2, ...` with priorities that can be lowered while the id is queued, as in Dijkstra's algorithm |
| `indexed = true, key = long.class` | `LongLongIndexedHeap`, ...: the same for arbitrary `long` keys, such as connection ids or sparse node ids |

### `@PrimitiveList(element = ...)` and `@PrimitiveDeque(element = ...)`

`element` is `int.class`, `long.class`, or `double.class`: `IntArrayList`, ... (a growable array)
and `IntArrayDeque`, ... (a ring buffer with access by index).

### Common attributes

- `packageName = "com.example.other"` generates into another package instead of the package of
  the annotated element. All classes of a package, including the interfaces and functional
  interfaces, are generated into that one package.
- `packagePrivate = true` makes the generated classes and interfaces package-private, for a
  library that uses them internally and must not export them. All requests for one package
  must agree on this attribute.

Invalid requests are compile errors at the annotation, for example `key = float.class`, a
compact map with object values, or a sorted set that is also linked.

## Using the Collections

The generated classes have complete Javadoc; this section shows the main ideas.

**Absent keys.** Maps with primitive values return `0` from `get` for an absent key; use
`containsKey` or `getOrDefault(key, defaultValue)` to tell the cases apart. Maps with object
values return `null`.

**Find once, then act.** `indexOf` returns a handle, `>= 0` for a present key and negative for
an absent one. Reading, replacing, or inserting through the handle needs no second hash lookup:

```java
LongLongHashMap best = new LongLongHashMap();
int i = best.indexOf(key);
if (i >= 0) {
    best.indexReplace(i, Math.max(best.indexGet(i), score));
} else {
    best.indexInsert(i, key, score);      // the negative handle tells where
}
```

**Iterate without allocation.** `forEach` passes keys and values as primitives. The handle loop
can stop early and replace values on the way:

```java
for (int i = map.firstIndex(); i >= 0; i = map.nextIndex(i)) {
    long key = map.indexKey(i);
    long value = map.indexGet(i);
    ...
}
```

**Sorted maps and sets** navigate without boxing; where no key or value is free to mean "none",
the caller passes a default:

```java
LongTreeSet deadlines = new LongTreeSet();
deadlines.add(time);
long next = deadlines.ceiling(now, NO_DEADLINE);   // the next deadline, if any
deadlines.removeRange(Long.MIN_VALUE, now);        // drops all deadlines before now
```

**Insertion-ordered maps** keep the position of an entry on `put` and can move it to the end,
which makes a cache that evicts the least recently used entry:

```java
LongObjectLinkedHashMap<Page> cache = new LongObjectLinkedHashMap<>();
Page p = cache.get(id);
if (p != null) {
    cache.moveToLast(id);
} else {
    cache.put(id, p = load(id));
    if (cache.size() > MAX_PAGES) {
        cache.pollFirst();
    }
}
```

**Thread-safe maps** follow `java.util.concurrent.ConcurrentMap` and count without lost updates:

```java
LongLongConcurrentMap hitsPerUser = new LongLongConcurrentHashMap();
hitsPerUser.addTo(userId, 1);                      // from any number of threads
```

**Indexed priority queues** lower a priority in place, which shortest-path algorithms need:

```java
IntLongIndexedHeap queue = new IntLongIndexedHeap(nodeCount);
queue.put(source, 0);
while (!queue.isEmpty()) {
    long d = queue.peekPriority();
    int u = queue.poll();
    for (int e = start[u]; e < start[u + 1]; e++) {
        queue.putIfBetter(to[e], d + weight[e]);   // inserts or lowers the priority
    }
}
```

**Interoperability.** `toMap()`, `toSet()`, and `toList()` return boxed `java.util` copies;
`putAll` and `addAll` accept `java.util` collections; the copy constructors accept any
implementation of the same interface.

The collections other than the thread-safe maps are not synchronized. The map and set
interfaces are implemented by the generated classes only; later versions may add methods to
them. The functional interfaces are meant for lambdas and stay as they are.

A complete tour of the API is in
[docs/design-and-benchmarks.md](docs/design-and-benchmarks.md#api-overview).

## Command Line Generator

The generator writes the same classes once as sources into a directory: for projects that want
to own and modify the code, or that cannot use an annotation processor. The jar has no
dependencies:

```shell
java -jar generator/target/collections.stencil-generator-0.1.0-SNAPSHOT.jar \
    --out=src/main/java \
    --include='LongLongHashMap|IntArrayList' \
    --package=com.example.stats \
    --no-header
```

| Option | Meaning |
|--------|---------|
| `--out=DIR` | Source root to write to; package directories are created. Required. |
| `--include=REGEX` | Only the classes whose simple name matches, plus the interfaces and functional interfaces they use. Without it, all classes are generated. |
| `--package=NAME` | Put all generated classes into one package. |
| `--base-package=NAME` | Keep the package structure but replace the base package `net.generated.example`: the functional interfaces go into `NAME.function`. Not together with `--package`. |
| `--package-private` | Make the generated top-level types package-private. |
| `--no-header` | Omit the comment that names the template each file comes from. |
| `--clean` | Delete `*.java` files below `--out` that were not generated in this run. Only for build directories, never for hand-written sources. |

Without `--package` or `--base-package`, the classes go into `net.generated.example` and the
functional interfaces into `net.generated.example.function`. Unchanged files are not
rewritten.

## Performance

stencil is benchmarked against fastutil, HPPC, Eclipse Collections, Speiger
Primitive-Collections, JCTools, and the boxed JDK collections, with JMH. Some results:

- `LongTreeSet` was faster than fastutil's and Speiger's red-black trees and `TreeSet<Long>` in
  all 144 measured cells (lookups 1.5-6.4x, iteration 11-113x), at 16-18 instead of 32-64 bytes
  per element.
- `ObjectIntHashMap<K>` looks up absent keys in about a third of the time of fastutil and HPPC.
- `LongObjectLinkedHashMap<V>` iterates 5-260x faster than fastutil's linked map, at two thirds
  of its memory.
- `LongLongConcurrentHashMap` looks up present keys 1.5-2x faster than `ConcurrentHashMap`, at a
  quarter of its memory.
- `LongLongIndexedHeap` runs timeouts with rescheduling 1.5-2x faster than fastutil's indirect
  queue combined with a map from keys to ids.

stencil is not faster everywhere: for example, `IntArrayList` builds lists about 1.1x slower than
fastutil on average, and a linked map is slower where an entry moves to the end. All results,
including these cases, the methodology, and the memory measurements, are in
[docs/design-and-benchmarks.md](docs/design-and-benchmarks.md#results).

## Further Documentation

- [docs/design-and-benchmarks.md](docs/design-and-benchmarks.md): the complete API overview,
  the design decisions behind every layout, all benchmark results, and how to build and
  benchmark the project.
- [docs/generator.md](docs/generator.md): the template engine, the template syntax, and the
  roadmap.

## License

stencil is licensed under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
The source code that stencil generates into your project is not subject to this license: you
may use, modify, and distribute it under any terms you choose.