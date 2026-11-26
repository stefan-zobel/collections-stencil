# collections-stencil – Design, Implementation, and Benchmarks

Background for contributors and for readers interested in the details: the status of the
project, the API in depth, the design decisions behind every layout, the benchmark results,
and how to build and benchmark the project. For using the annotation processor or the
command line generator, see the [README](../README.md).

Version 1.0.0. Goal: primitive collections that match or beat fastutil, HPPC, and Eclipse
Collections. All modules are compiled for JDK 21 (class files and APIs) and built with JDK 25;
the tests pass on JDK 21 and 25, and the generated classes also compile with `--release 19`. The
classes are generated from one hand-tuned template per layout (see
[generator.md](generator.md)), in two ways: by an annotation processor into a user's project, and
as copyable sources from the command line. The `core` module instantiates all templates into the
example package `net.generated.example` (test bed, benchmark subject, and a showcase of the
generated code); it is not published. The generator always emits readable Java source code,
never bytecode.

## Overview

stencil currently provides:

- **Hash maps** with `int` and `long` keys to every primitive value type and to objects, each
  with a linear-probing default and, for primitive values, a tagged-block alternative for
  miss-heavy workloads; **maps from object keys** to `int`, `long`, and `double` values.
- **Sorted maps** (B+ tree), **insertion-ordered maps**, and **thread-safe maps**.
- **Sets** of `int` and `long`: hash sets, a tagged-block `LongCompactHashSet`, insertion-ordered
  sets, and sorted sets.
- **Priority queues**, **indexed priority queues**, **lists**, and **deques**.
- **A complete API** without boxing: index handles, allocation-free iteration, `removeIf`, bulk
  operations, conversions to and from `java.util` (see [API Overview](#api-overview)).
- **Two delivery modes:** the annotation processor and the generator's command line, both
  optionally with package-private classes. The instantiations in `core` are an example and are
  not published.
- **Benchmarks** against fastutil, HPPC, Eclipse Collections, Speiger Primitive-Collections,
  JCTools, and the boxed JDK collections (see [Results](#results)), with an interleaved runner
  that keeps thermal throttling out of the comparisons.

Not included: the Vector API (still incubating in JDK 25), FFM/off-heap memory, and keys of
type `float`, `double`, `byte`, `short`, or `char`. The open items are listed in the roadmap in
[generator.md](generator.md).

**Sorted maps:** sorted maps with `int` and `long` keys and object values, implemented
as a B+ tree (`IntObjectTreeMap<V>`, `LongObjectTreeMap<V>`, see
[Design Decisions](#intobjecttreemapv-longobjecttreemapv-design-decisions)). Implementation,
tests, the benchmark suite (`--suite=longsorted`), and the tuning of the nodes are done; see
[Results](#results) for the comparison with fastutil's tree maps and `TreeMap`. The same tree
with primitive values (`IntLongTreeMap`, `LongDoubleTreeMap`, ..., every primitive value type)
stores the values in an array of their own type next to the keys, 16 bytes per entry for
`long` keys and values before the leaf fill, without an object per entry. Against fastutil's
and Speiger's red-black trees and `TreeMap<Long,Long>` (`--suite=longlongsorted`, 1,400 to
1,400,000 entries), `LongLongTreeMap` was faster in every cell but one: lookups 1.5-7x,
iteration 17-78x, range scans 2.6-15x, ascending builds 2.8-11x, at 31-34 instead of 40, 48, and
88 bytes per entry; see [Results](#results). The sorted sets `IntTreeSet` and `LongTreeSet` are
the same tree without a value array, 8 bytes per `long` element before the leaf fill. Against
fastutil's and Speiger's red-black trees and `TreeSet<Long>` (`--suite=longsetsorted`, 1,400 to
1,400,000 elements), `LongTreeSet` was faster in all 144 cells: lookups 1.5-6.4x, iteration
11-113x, range scans 3-12x, ascending builds 2.7-18x, at 16-18 instead of 32, 40, and 64 bytes
per element; see [Results](#results).

**Insertion-ordered maps and sets:** `IntLongLinkedHashMap`, `LongObjectLinkedHashMap<V>`,
`LongLinkedHashSet`, ... for `int` and `long` keys with every primitive value type and with
object values (see
[Design Decisions](#insertion-ordered-maps-and-sets-design-decisions)). The entries are stored
densely in insertion order, with a separate index table. Against fastutil's linked map (`long`
keys and object values, 1,400 to 1,400,000 entries), `LongObjectLinkedHashMap` iterates 5-260x
faster, builds maps of 180,000 and more entries 2.3-3x faster, replaces entries 1.7-3.7x
faster, looks up absent keys 1.6x faster, and needs two thirds of the memory. It is slower
where an entry moves to the end (1.4-2x) and for lookups of present keys in maps far beyond
the CPU caches (1.4x at 1,400,000 entries); see [Results](#results). The same layout with
object keys and `int`, `long`, or `double` values (`ObjectIntLinkedHashMap<K>`, ...) stores the
hash of every key, so that growing, compacting, and removing at the ends never call `hashCode`.
Against fastutil's and Speiger's linked maps (`String` keys, `int` values, 1,400 to 1,400,000
entries), `ObjectIntLinkedHashMap` iterates 1.8-38x faster, replaces entries 1.7-8x faster,
builds 1.3-4.5x faster, and looks up present keys 1.2-1.7x faster, at 19.5 instead of 24 bytes
per entry; it is slower for `count` on keys with Zipf-like ranks (up to 1.8x at 1,400,000
entries) and, against fastutil, where an entry moves to the end (up to 1.6x); see
[Results](#results).

**Priority queues:** `IntHeap`, `LongHeap`, and `DoubleHeap` (a 4-ary heap of values, smallest
or largest first), and the indexed priority queues `IntIntIndexedHeap`, `IntLongIndexedHeap`,
and `IntDoubleIndexedHeap` (`int` ids with priorities that can decrease while the ids are in
the queue, as Dijkstra's algorithm needs; see
[Design Decisions](#priority-queues-design-decisions)). Against fastutil's and Speiger's binary
heaps (`long` values, 1,400 to 1,400,000 values), `LongHeap` sorts 1.1-2x faster and runs the
event-queue loop "poll, schedule a later event" 1.16-2x faster, for random and for ascending
event times, and builds 1.2-1.35x faster from random values; pushing ascending values is about
even (up to 1.09x slower). `PriorityQueue<Long>` is up to 9.5x slower at three times the memory.
Against fastutil's indirect queue and `PriorityQueue` with lazy deletion, `IntLongIndexedHeap`
computes shortest paths up to 1.44x and 1.8-2.5x faster. `LongIntIndexedHeap`,
`LongLongIndexedHeap`, and `LongDoubleIndexedHeap` take arbitrary `long` keys instead of dense
ids (connection ids, sparse 64-bit node ids), which they find in their own hash table; no other
library offers such a queue. Against the workaround with fastutil (a map from keys to dense ids
plus the indirect queue), `LongLongIndexedHeap` runs timeouts with rescheduling 1.5-2x faster
and shortest paths 1.5-1.65x faster, at 36 instead of 46 bytes per key; see [Results](#results).

**Lists and deques:** `IntArrayList`, `LongArrayList`, and `DoubleArrayList` (a growable array),
and `IntArrayDeque`, `LongArrayDeque`, and `DoubleArrayDeque` (a ring buffer that also reads and
writes any element by its index; see [Design Decisions](#lists-and-deques-design-decisions)).
Against fastutil, HPPC, Eclipse Collections, and Speiger Primitive-Collections (`int` elements,
1,400 to 1,400,000 elements), `IntArrayList` reads, iterates, and inserts in the middle about as
fast (within 5%); building a list from the default capacity is 1.1x slower than fastutil on
average, depending on where the size falls in the growth sequence, and up to 1.6x slower for
lists of 1,000 to 2,000 elements on Windows (not on Linux). `ArrayList<Integer>` is 1.45-6x
slower at 3.6 times the memory. `IntArrayDeque` is 1.15-1.5x faster than fastutil's and
Speiger's `IntArrayFIFOQueue` and up to 1.16x slower than HPPC's `IntArrayDeque`;
`ArrayDeque<Integer>` is about 4x slower; see [Results](#results).

**Object keys with primitive values:** `ObjectIntHashMap<K>`, `ObjectLongHashMap<K>`, and
`ObjectDoubleHashMap<K>`, for counters and sums per key (see
[Design Decisions](#objectinthashmapk-objectlonghashmapk-objectdoublehashmapk-design-decisions)),
are implemented and tested. Against fastutil and HPPC, lookups of absent keys take about a
third of the time and churn about half; see [Results](#results).

**Thread-safe maps:** `IntObjectConcurrentHashMap<V>` and `LongObjectConcurrentHashMap<V>`
(segments of linear-probing tables, lookups without locks; see
[Design Decisions](#intobjectconcurrenthashmapv-longobjectconcurrenthashmapv-design-decisions))
are implemented and tested. Against `ConcurrentHashMap` they build 2-3x faster, look up present
keys up to 1.4x faster with several threads, and need a third of the memory; JCTools reads faster at twice the
memory, but writes up to 10x slower; Speiger Primitive-Collections is slower everywhere (see
[Results](#results)). The thread-safe maps with `int`, `long`, and `double` values
(`IntIntConcurrentHashMap`, `LongLongConcurrentHashMap`, ..., with an atomic `addTo` for counters
and sums) are implemented and tested. They look up present keys as fast as JCTools (faster with
12 threads) and 1.5-2x faster than `ConcurrentHashMap`, at a quarter of its memory. `addTo` on a
present key works without lock: counting a few hot keys from 12 threads is about 1.4x faster than
with `ConcurrentHashMap` and `LongAdder` values and 1.65x slower than JCTools (`AtomicLong`
values, more than twice the memory).

## Modules

| Module  | Contents |
|---------|----------|
| `generator` | The template engine, its command line, and the library templates. No dependencies besides the JDK. |
| `annotations` | `@PrimitiveMap`, `@PrimitiveSet`, `@PrimitiveHeap`: select the classes the annotation processor generates (compile time only). |
| `processor` | The annotation processor: generates the selected classes as sources of the user's project, in the user's package. |
| `core`  | All templates instantiated at build time into the example package `net.generated.example` (functional interfaces in `net.generated.example.function`; JPMS module of the same name). The package is the property `stencil.example.package` in `core/pom.xml`. The module is the test bed, the benchmark subject, and a showcase of the generated code; it is not published. Currently: the interface `Int<V>Map`, `Int<V>HashMap` (linear probing), and `Int<V>CompactHashMap` (tagged blocks) for `V` ∈ {`boolean`, `byte`, `short`, `char`, `int`, `float`}; `Long<V>Map`, `Long<V>HashMap` (linear probing over interleaved key/value words), and `Long<V>CompactHashMap` (tagged 128-byte blocks) for all eight primitive value types, and the same two layouts for `int` keys with `long` and `double` values; `IntObjectMap<V>`/`LongObjectMap<V>` with `IntObjectHashMap<V>`/`LongObjectHashMap<V>` (object values); the sorted maps `IntObjectSortedMap<V>`/`LongObjectSortedMap<V>` with `IntObjectTreeMap<V>`/`LongObjectTreeMap<V>` (B+ tree); the maps from object keys `ObjectIntMap<K>`/`ObjectLongMap<K>`/`ObjectDoubleMap<K>` with `ObjectIntHashMap<K>`/`ObjectLongHashMap<K>`/`ObjectDoubleHashMap<K>` (linear probing); the thread-safe maps `IntObjectConcurrentMap<V>`/`LongObjectConcurrentMap<V>` with `IntObjectConcurrentHashMap<V>`/`LongObjectConcurrentHashMap<V>` (segments with lookups without locks), and `Int<V>ConcurrentMap`/`Long<V>ConcurrentMap` with `Int<V>ConcurrentHashMap`/`Long<V>ConcurrentHashMap` for `V` ∈ {`int`, `long`, `double`} (the same design over the layouts of the primitive maps); the sets `IntSet`/`LongSet` with `IntHashSet`/`LongHashSet` (linear probing) and `LongCompactHashSet` (tagged 64-byte blocks); the sorted sets `IntSortedSet`/`LongSortedSet` with `IntTreeSet`/`LongTreeSet` (B+ tree); the priority queues `IntHeap`/`LongHeap`/`DoubleHeap` and the indexed priority queues `IntIntIndexedHeap`/`IntLongIndexedHeap`/`IntDoubleIndexedHeap` and `LongIntIndexedHeap`/`LongLongIndexedHeap`/`LongDoubleIndexedHeap` (4-ary heaps; dense `int` ids or arbitrary `long` keys); the lists `IntArrayList`/`LongArrayList`/`DoubleArrayList` and the deques `IntArrayDeque`/`LongArrayDeque`/`DoubleArrayDeque` (ring buffers). |
| `bench` | JMH benchmarks against fastutil, HPPC, Eclipse Collections, and the boxed JDK collections (`HashMap`, `TreeMap`), plus the interleaved runner and the hand-written experimental `IntIntBlockHashMap` (not part of the library). |

The baseline versions are defined in the parent `pom.xml` (as of now: fastutil 8.5.19,
Eclipse Collections 13.0.0, HPPC 0.13.0, JMH 1.37).

## Annotation Processor

A project generates just the classes it needs into its own package. The generated sources depend only on the JDK.

```java
// src/main/java/com/example/coll/package-info.java
@PrimitiveMap(key = int.class, value = long.class)                      // IntLongHashMap, IntLongMap, ...
@PrimitiveMap(key = long.class, value = Object.class)                   // LongObjectHashMap<V>, ...
@PrimitiveMap(key = long.class, value = double.class, compact = true)   // also LongDoubleCompactHashMap
@PrimitiveMap(key = long.class, value = Object.class, sorted = true)    // LongObjectTreeMap<V>, LongObjectSortedMap<V>, ...
@PrimitiveMap(key = long.class, value = long.class, sorted = true)      // LongLongTreeMap, LongLongSortedMap, ...
@PrimitiveMap(key = Object.class, value = int.class)                    // ObjectIntHashMap<K>, ObjectIntMap<K>, ...
@PrimitiveMap(key = int.class, value = Object.class, concurrent = true) // IntObjectConcurrentHashMap<V>, ...
@PrimitiveMap(key = long.class, value = long.class, concurrent = true)  // LongLongConcurrentHashMap, ...
@PrimitiveMap(key = long.class, value = Object.class, linked = true)    // LongObjectLinkedHashMap<V>, ...
@PrimitiveMap(key = Object.class, value = int.class, linked = true)     // ObjectIntLinkedHashMap<K>, ...
@PrimitiveSet(element = int.class)                                      // IntHashSet, IntSet
@PrimitiveSet(element = long.class, linked = true)                      // LongLinkedHashSet, LongSet
@PrimitiveSet(element = long.class, sorted = true)                      // LongTreeSet, LongSortedSet, LongSet
@PrimitiveHeap(priority = long.class)                                   // LongHeap
@PrimitiveHeap(priority = long.class, indexed = true)                   // IntLongIndexedHeap, IntLongConsumer
@PrimitiveHeap(priority = long.class, indexed = true, key = long.class) // LongLongIndexedHeap, LongLongConsumer
@PrimitiveList(element = int.class)                                     // IntArrayList
@PrimitiveDeque(element = long.class)                                   // LongArrayDeque
package com.example.coll;

import net.collections.stencil.annotations.PrimitiveDeque;
import net.collections.stencil.annotations.PrimitiveHeap;
import net.collections.stencil.annotations.PrimitiveList;
import net.collections.stencil.annotations.PrimitiveMap;
import net.collections.stencil.annotations.PrimitiveSet;
```

Maven (the annotations are needed at compile time only; since JDK 23, annotation processors run
only when configured explicitly, as here):

```xml
<dependency>
    <groupId>net.sourceforge.streamsupport</groupId>
    <artifactId>collections.stencil-annotations</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
...
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>net.sourceforge.streamsupport</groupId>
                <artifactId>collections.stencil-processor</artifactId>
                <version>1.0.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Gradle: `compileOnly` for `collections.stencil-annotations`, `annotationProcessor` for `collections.stencil-processor`
(the processor supports incremental compilation as an aggregating processor). A modular project
adds `requires static net.collections.stencil.annotations;` to its `module-info.java`.

- The annotations go on a package (`package-info.java`) or a type; `packageName = "..."` selects
  another target package. The processor generates each requested class plus the interfaces and
  functional interfaces it uses, once per class, even if several elements request it.
- `sorted = true` generates the sorted map (`...TreeMap`) instead of the hash map; it is
  available for `int` and `long` keys with every value type, and not together with `compact`.
  For sets, it generates the sorted set (`...TreeSet`) instead of the hash set, not together
  with `compact` or `linked`.
- `key = Object.class` generates a hash map from object keys to `int`, `long`, or `double`
  values (`ObjectIntHashMap<K>`, ...), with `linked = true` the insertion-ordered map
  (`ObjectIntLinkedHashMap<K>`, ...), and is not available with `compact` or `sorted`.
- `concurrent = true` generates the thread-safe map (`...ConcurrentHashMap`) instead of the hash
  map; it is available for `int` and `long` keys with object, `int`, `long`, or `double` values.
- `linked = true` generates the insertion-ordered map (`...LinkedHashMap`) or set
  (`...LinkedHashSet`) instead of the hash map or set; it is available for `int` and `long` keys
  with every value type and for object keys with `int`, `long`, and `double` values, and not
  together with `compact`, `sorted`, or `concurrent`.
- `@PrimitiveHeap(priority = ...)` generates the priority queue of `int`, `long`, or `double`
  values (`IntHeap`, `LongHeap`, `DoubleHeap`); with `indexed = true`, the indexed priority queue
  of `int` ids with priorities of that type (`IntLongIndexedHeap`, ...), and with
  `key = long.class` in addition, the indexed priority queue of arbitrary `long` keys
  (`LongLongIndexedHeap`, ...). `key` is `int.class` by default and needs `indexed = true`.
- `@PrimitiveList(element = ...)` and `@PrimitiveDeque(element = ...)` generate the list
  (`IntArrayList`, ...) or the double-ended queue (`IntArrayDeque`, ...) of `int`, `long`, or
  `double` elements.
- `packagePrivate = true` generates package-private classes and interfaces instead of public
  ones, for a library that uses them internally and must not export them. The visibility applies
  to all classes of a target package, because they share their interfaces: requests for the
  same package that differ in `packagePrivate` are a compile error.
- Invalid requests are compile errors at the annotation, for example `key = float.class`, a
  compact set of `int` elements, a compact map with object values, a sorted map with
  object keys, a set that is both sorted and linked, a map with object keys and object
  values, or a priority queue or a list of `byte` values.
- The same selection is available without a build integration: the generator's command line
  (`--include`, `--package` or `--base-package`, `--package-private`, see
  [generator.md](generator.md))
  writes the sources once into a project ("eject" mode).

## API Overview

All maps of a key/value pair share one interface (`IntIntMap`, `LongDoubleMap`, `IntObjectMap<V>`,
`ObjectIntMap<K>`, ...), all sets of an element type another (`IntSet`, `LongSet`). Every implementation offers:

- **Basic operations:** `get`/`getOrDefault`/`containsKey`, `put`/`putIfAbsent`/`remove`, `addTo`
  (numeric values), `computeIfAbsent` (object values); for sets `add`/`contains`/`remove`. Key 0 is
  a regular key, and so is `null` for object keys.
- **Index handles:** `indexOf`, then `indexGet`/`indexReplace`/`indexInsert`/`indexRemove`, for
  "find, then read, update or insert" with a single hash computation.
- **Iteration without allocation:** `forEach` with a primitive consumer, or the handle loop
  `for (int i = m.firstIndex(); i >= 0; i = m.nextIndex(i))` with `indexKey(i)`/`indexGet(i)`, which
  can stop early and replace values on the way.
- **Bulk operations:** `removeIf` with a primitive predicate (each entry is tested exactly once,
  without allocation), `putAll`/`addAll` from another stencil collection or a `java.util.Map`/
  `Collection`, `keysToArray`/`valuesToArray`/`toArray` (for object keys and values with an
  array constructor, `keysToArray(String[]::new)`), and copy constructors from any
  implementation. Sets also offer `of(...)` (duplicates are added once; the set stays
  modifiable) and `stream()` (a stream of a copy, in iteration order).
- **Interoperability:** `toMap()`/`toSet()` return a new boxed `HashMap`/`HashSet`, and for
  collections with an order a collection with the same iteration order (`LinkedHashMap`/
  `LinkedHashSet` for the insertion-ordered ones, `TreeMap`/`TreeSet` for the sorted ones);
  `equals` and `hashCode` follow `java.util.Map`/`java.util.Set` and work across
  implementations.
- **Sorted maps** (`IntObjectSortedMap<V>`, `LongObjectSortedMap<V>`, extending the map
  interfaces) iterate in key order and add: navigation through handles (`floorIndex`,
  `ceilingIndex`, `lowerIndex`, `higherIndex`, `lastIndex`, `prevIndex`), `pollFirst`/`pollLast`,
  range operations (`forEachInRange`, `removeRange`, `countRange`), copies (`subMapCopy`,
  `toNavigableMap`), and convenience methods (`firstKey`, `floorKey(key, defaultKey)`,
  `floorValue(key)`, ...). They have no live views such as `subMap` or `headMap`.
- **Sorted maps with primitive values** (`IntLongSortedMap`, `LongDoubleSortedMap`, ...,
  extending the map interfaces of their key/value pair) offer the same methods. Where the maps
  with object values return `null` for "no entry", they follow three rules, because no key and
  no value is reserved: a handle is `-1`; a key is the default passed by the caller
  (`floorKey(key, defaultKey)`); a value is the default passed by the caller
  (`floorValue(key, defaultValue)`, likewise `ceilingValue`, `lowerValue`, `higherValue`).
  `firstKey`/`lastKey` and `pollFirst()`/`pollLast()` throw a `NoSuchElementException` for an
  empty map, in all sorted and insertion-ordered maps; `pollFirst(consumer)`/
  `pollLast(consumer)` return `false` instead and also deliver the key:
  ```java
  LongLongTreeMap bids = new LongLongTreeMap();        // price -> quantity
  bids.addTo(price, quantity);
  long best = bids.isEmpty() ? NO_PRICE : bids.lastKey();
  long atOrBelow = bids.floorValue(limit, 0L);         // 0 if no price <= limit
  while (bids.pollLast((p, q) -> fill(p, q))) { }      // from the highest price down
  ```
- **Sorted sets** (`IntSortedSet`, `LongSortedSet`, extending the set interfaces; implemented by
  `IntTreeSet`, `LongTreeSet`) iterate in ascending order and offer the same navigation, range
  operations, and copies (`subSetCopy`, `toNavigableSet`). An element without a match is the
  default passed by the caller (`floor(key, defaultElement)`, likewise `ceiling`, `lower`,
  `higher`); `first`, `last`, `pollFirst`, and `pollLast` throw a `NoSuchElementException` for
  an empty set, as in the insertion-ordered sets, because the element itself is the result and
  no value is free to mean "none"; the handles are `-1`:
  ```java
  LongTreeSet deadlines = new LongTreeSet();
  deadlines.add(time);
  long next = deadlines.ceiling(now, NO_DEADLINE);     // the next deadline, if any
  deadlines.removeRange(Long.MIN_VALUE, now);          // drops all deadlines before now
  ```
- **Insertion-ordered maps and sets** (`IntLongLinkedHashMap`, `LongObjectLinkedHashMap<V>`,
  `LongLinkedHashSet`, ...) implement the map and set interfaces and iterate in insertion order,
  through `forEach`, the handle loop, and the array conversions. `put` on a present key keeps the
  position of its entry, as in `java.util.LinkedHashMap`. The classes add `firstKey`/`lastKey`
  (`first`/`last` for sets), `pollFirst`/`pollLast` (all four throw a `NoSuchElementException`
  for an empty map or set), iteration from the end (`lastIndex`,
  `prevIndex`), and `putLast`/`moveToLast` (`addLast` for sets), which make an entry the newest
  one. There is no `putFirst` and no access-order mode. A cache that evicts the entry that was
  used least recently:
  ```java
  LongObjectLinkedHashMap<Page> cache = new LongObjectLinkedHashMap<>();
  Page p = cache.get(id);
  if (p != null) {
      cache.moveToLast(id);          // the most recently used entry is the last one
  } else {
      cache.put(id, p = load(id));
      if (cache.size() > MAX_PAGES) {
          cache.pollFirst();         // evicts the least recently used entry
      }
  }
  ```
  With object keys (`ObjectIntLinkedHashMap<K>`, ...), a counter reports its keys in the order
  of their first occurrence, and `null` is a key with its place in that order:
  ```java
  ObjectIntLinkedHashMap<String> counts = new ObjectIntLinkedHashMap<>();
  for (String word : words) {
      counts.addTo(word, 1);
  }
  counts.forEach((word, n) -> System.out.println(word + " " + n)); // in order of first occurrence
  ```
- **Maps from object keys** (`ObjectIntMap<K>`, `ObjectLongMap<K>`, `ObjectDoubleMap<K>`) compare
  keys with `equals` and accept any object for lookups, like `java.util.Map`. The typical use is a
  counter per key without boxing:
  ```java
  ObjectIntMap<String> counts = new ObjectIntHashMap<>();
  for (String word : words) {
      counts.addTo(word, 1);
  }
  ```
- **Thread-safe maps** (`IntObjectConcurrentMap<V>`, `LongObjectConcurrentMap<V>`) follow
  `java.util.concurrent.ConcurrentMap`: no `null` values, atomic `putIfAbsent`, `replace`,
  `remove(key, value)`, `computeIfAbsent`, `computeIfPresent`, `compute`, and `merge`, and weakly
  consistent `size`, `forEach`, `removeIf`, and conversions. They have no index handles. As in
  `ConcurrentMap`, `putIfAbsent` returns the current value (`null` if the key was absent) and
  `replace(key, value)` the previous one; the single-threaded `IntObjectMap<V>` returns a
  `boolean` from `putIfAbsent` instead, because its values may be `null`. A cache of objects per
  `long` id:
  ```java
  LongObjectConcurrentMap<Customer> cache = new LongObjectConcurrentHashMap<>();
  Customer c = cache.computeIfAbsent(id, repository::load); // load runs at most once per id
  ```
  The thread-safe maps with `int`, `long`, and `double` values (`IntIntConcurrentMap`,
  `LongLongConcurrentMap`, `LongDoubleConcurrentMap`, ...) have the same contract with primitive
  values (an absent key reads as 0, as in the other primitive maps) and add `addTo`, which
  counts without losing updates when many threads count the same key. Since every value,
  including 0, can be stored, `putIfAbsent` and `replace(key, value)` return a `boolean`;
  `computeIfAbsent` takes the JDK function from key to value (`LongUnaryOperator`,
  `IntToDoubleFunction`, ...) and returns the current value:
  ```java
  LongLongConcurrentMap hitsPerUser = new LongLongConcurrentHashMap();
  hitsPerUser.addTo(userId, 1); // from any number of threads
  ```
- **Priority queues** (`IntHeap`, `LongHeap`, `DoubleHeap`) return the smallest value first, or
  the largest with `new LongHeap(expectedSize, true)`: `push`, `peek`, `poll`, `pushPoll` (push,
  then poll, in one step), `replaceTop` (poll, then push), `pushAll` (in linear time into an
  empty queue), `toSortedArray`, and `forEach` in an unspecified order. `peek` and `poll` throw a
  `NoSuchElementException` for an empty queue; `double` queues reject `NaN`. Like
  `java.util.PriorityQueue`, a queue is equal only to itself. The 100 largest values of a stream:
  ```java
  LongHeap top = new LongHeap(100);         // smallest first: the weakest of the 100 is on top
  for (long x : stream) {
      if (top.size() < 100) {
          top.push(x);
      } else {
          top.pushPoll(x);                  // keeps x only if it beats the weakest
      }
  }
  long[] best = top.toSortedArray();        // ascending
  ```
  The indexed priority queues (`IntIntIndexedHeap`, `IntLongIndexedHeap`, `IntDoubleIndexedHeap`)
  hold `int` ids `0, 1, 2, ...`, each at most once, with a priority that can change:
  `put(id, p)`, `putIfBetter(id, p)` ("decrease key": inserts the id or lowers its priority),
  `remove(id)`, `contains`, `priority`, `peekId`, `peekPriority`, `poll` (returns the id),
  `ensureCapacity`, and `trimToSize`.
  Dijkstra's algorithm:
  ```java
  IntLongIndexedHeap queue = new IntLongIndexedHeap(nodes);
  queue.put(source, 0);
  while (!queue.isEmpty()) {
      long d = queue.peekPriority();
      int u = queue.poll();
      dist[u] = d;                                     // final
      for (int e = start[u]; e < start[u + 1]; e++) {
          if (!settled(to[e])) {
              queue.putIfBetter(to[e], d + weight[e]); // relax
          }
      }
  }
  ```
  `LongIntIndexedHeap`, `LongLongIndexedHeap`, and `LongDoubleIndexedHeap` have the same
  operations for arbitrary `long` keys (`peekKey` instead of `peekId`), including `0` and
  negative keys. Idle timeouts per connection, where every activity pushes the deadline back:
  ```java
  LongLongIndexedHeap timeouts = new LongLongIndexedHeap();
  timeouts.put(connectionId, now + IDLE_NANOS);         // on open and on every activity
  timeouts.remove(connectionId);                        // on close
  while (!timeouts.isEmpty() && timeouts.peekPriority() <= now) {
      closeIdle(timeouts.poll());                       // the connection that expired first
  }
  ```
- **Lists** (`IntArrayList`, `LongArrayList`, `DoubleArrayList`): `add`, `add(index, value)`,
  `addAll`, `get`, `set`, `getFirst`/`getLast`, `removeAt(index)`, `removeValue(value)`,
  `removeLast`, `removeRange`, `removeIf`, `indexOf`/`lastIndexOf`/`contains`, `sort`,
  `binarySearch`, `reverse`, `replaceAll`, `fill`, `truncate`, `forEach`, `stream`, `toArray`,
  and `toList()` (a boxed `ArrayList`). `wrap(array, size)` turns an array into a list without
  copying it, and `elements()` returns the array of the list. `equals` compares with another
  list of the same type; `hashCode` follows `java.util.List`.
  ```java
  IntArrayList ids = IntArrayList.of(42, 7, 19);
  ids.sort();
  int at = ids.binarySearch(19);       // as Arrays.binarySearch
  ids.removeAt(0);                     // by index; ids.removeValue(19) removes by value
  ```
- **Deques** (`IntArrayDeque`, `LongArrayDeque`, `DoubleArrayDeque`): `addFirst`/`addLast`,
  `getFirst`/`getLast`, `removeFirst`/`removeLast` (they throw a `NoSuchElementException` for
  an empty deque), `get(index)`/`set(index, value)` in constant time, `discardFirst(count)`/
  `discardLast(count)` (any number of elements in constant time), `addAllLast` (from an array
  or another deque), `of(...)`, `removeIf`, `indexOf`/`contains`, `forEach`/`forEachReversed`,
  `stream`, `toArray`, and `toList()`. A
  sliding window over time stamps:
  ```java
  LongArrayDeque window = new LongArrayDeque();
  for (long t : timestamps) {
      window.addLast(t);
      while (window.getFirst() <= t - SPAN) {
          window.removeFirst();                    // drops the events that left the window
      }
      long median = window.get(window.size() / 2); // access by index
  }
  ```
- **Interfaces are implemented only by the library:** the map and set interfaces (`IntLongMap`,
  `LongObjectSortedMap<V>`, `ObjectIntMap<K>`, `IntSet`, ...) are implemented by the classes of
  stencil, including the classes that the annotation processor generates, and are not meant to
  be implemented elsewhere: later versions may add methods to them, also without a default
  implementation. The functional interfaces (`IntLongConsumer`, `IntLongPredicate`, ...) are
  meant for lambdas and stay as they are.

The design sections below describe the `int` → `int` classes, which are the benchmarked
reference instantiations of their templates. The other value types share the code; only the
encoding of the value bits differs.

## `IntIntHashMap`: Design Decisions

- **Packed layout:** one `long` per slot (key in the low bits, value in the high bits), so a lookup needs a single memory access.
- **Key 0** is stored outside the table (as in fastutil and HPPC). An empty slot has key bits 0.
- **Fibonacci hashing** with a per-instance seed. This prevents quadratic clustering when copying between maps.
- **Linear probing** with backward-shift deletion and no tombstones.
- **Load factor 0.75** by default. For maximum speed use `new IntIntHashMap(n, 0.5f)`: in the benchmarks it matches Eclipse Collections at the same memory (EC always runs at most half full), with faster churn.
- **Index handles** (`indexOf`/`indexGet`/`indexReplace`/`indexInsert`/`indexRemove`): find and modify with a single hash computation and no allocation.
- **`forEach(IntIntConsumer)`** passes key and value as two primitive parameters, with no entry object.
- **No identity hashing** (home slot taken directly from the key, as in the first probe of Eclipse Collections). Fibonacci hashing already places dense keys almost without collisions (1.0-1.2 probes per hit at the benchmark fill, see Results), so identity hashing would only add memory locality. That pays off only when dense keys are also accessed in key order, for example a build in ascending order (up to 1.8x in Eclipse Collections) or lookups of absent keys just past the occupied range; for keys that are dense from 0, a plain array is better still. In return, identity hashing makes linear probing vulnerable to strided keys (multiples of a power of two form huge runs); Eclipse Collections counters that with several probe functions and tombstones, which does not fit tombstone-free backward-shift deletion.

## `IntIntCompactHashMap`: Design Decisions

The second layout class, for high load factors and miss-heavy workloads.

- **Blocks** of eight `long` words (64 bytes, one cache line): one control word plus seven packed slots.
- **Control word:** seven one-byte tags (empty, or a flag bit plus seven hash bits) and an 8-bit overflow counter.
- **Lookup:** one SWAR comparison of the control word with the key's tag yields the candidate slots. A hit usually reads one key, a miss usually reads no key at all, and both stay within one cache line.
- **Fibonacci hashing** with a per-instance seed: the highest bits select the home block, the next seven bits form the tag.
- **Overflow** along a per-key sequence of blocks (first the neighbor block, then double hashing), with saturating counters (in the style of F14). Removal decrements the counters; no tombstones.
- **Default load factor 0.85.** At the same memory as `IntIntHashMap` with load factor 0.75, misses are several times faster; hits cost a few more instructions.
- Same API as `IntIntHashMap`, including index handles.

## `LongLongHashMap`: Design Decisions

The reference instantiation of the layout for wide entries (`long` keys with any primitive value).

- **Interleaved layout:** one `long[]` with the key of slot `s` at index `2s` and the value bits at `2s + 1`. A pair is 16-byte aligned, so key and value share a cache line (as in Eclipse Collections; fastutil and HPPC use two separate arrays, i.e. two cache lines per hit).
- Otherwise as `IntIntHashMap`: key 0 outside the table, Fibonacci hashing with a per-instance 64-bit seed (the high bits of the product depend on all 64 key bits), linear probing with backward-shift deletion, index handles, load factor 0.75 by default.

## `LongLongCompactHashMap`: Design Decisions

The tagged-block layout for `long` keys, for high load factors and miss-heavy workloads.

- **Blocks of 128 bytes** (two cache lines): the first line holds the control word (seven tags and the overflow counter, as in `IntIntCompactHashMap`) and the seven keys, the second line the seven values. The value of the key at index `i` is at `i + 8`.
- A miss usually reads only the first line of one block; a hit reads one key and its value within the same 128-byte-aligned block.
- **Default load factor 0.85.** At the benchmark sizes the table has the same size as `LongLongHashMap` (load factor 0.75), fastutil, and HPPC.
- Same overflow scheme (neighbor block, then double hashing; saturating counters, no tombstones) and the same API as the other maps.

## `IntObjectHashMap<V>`, `LongObjectHashMap<V>`: Design Decisions

- **Parallel arrays:** an `int[]`/`long[]` of keys and an `Object[]` of values (as in fastutil, HPPC, and Eclipse Collections). Probing reads only the dense key array; a hit then reads the value slot (a second cache line) and usually the value object itself.
- Otherwise as the linear-probing maps: key 0 outside the table, Fibonacci hashing with a per-instance seed, backward-shift deletion (which also clears the value reference, so removed values do not stay reachable), index handles, load factor 0.75 by default.
- **`null` values** are allowed; `get` returns `null` for absent keys, `containsKey` and the index handles tell the two apart.
- **`computeIfAbsent(key, IntFunction)`** with the semantics of `java.util.Map` (an absent key or a key mapped to `null` gets the computed value unless it is `null`), with a single hash lookup.
- `equals` compares values with `Object.equals`; `hashCode` is the one of `java.util.Map`.

## `ObjectIntHashMap<K>`, `ObjectLongHashMap<K>`, `ObjectDoubleHashMap<K>`: Design Decisions

- **Stored hashes:** three parallel arrays, an `int[]` of stored hashes, an `Object[]` of keys, and an `int[]`/`long[]`/`double[]` of values. Probing reads only the dense hash array and calls `equals` only on a key whose stored hash is equal; removal and growth never touch the keys. fastutil and HPPC store no hashes, so every slot on a probe chain costs them an `equals` call, which loads the key object from memory (for a string also its character array); that `String` caches its hash code does not help, because reading it loads the string as well. In an interleaved comparison with string keys (a short experiment run of 2 rounds), stored hashes made lookups of absent keys 2.3x faster, churn 2x, lookups of present keys 1.35x, and builds 1.3x, at 4 more bytes per slot (about 17.6 instead of 11.8 bytes per entry; `HashMap<String,Integer>`: 54).
- **`null` key:** a stored hash of 0 marks an empty slot, so the `null` key is stored outside the table, like key 0 in the primitive maps. It is a regular key for all methods. (A key whose mixed hash is 0 is stored with hash 1.)
- **Hashing:** the murmur3 finalizer of `hashCode() ^ seed`, with a per-instance seed; the slot comes from the high bits. A nonlinear mixer is needed for object keys: the hash codes of strings such as decimal numbers form arithmetic patterns that the multiplicative (Fibonacci) hashing of the primitive maps keeps, which gave probe chains of 35 instead of 5 slots for absent keys among 1.4 million decimal strings.
- **Key comparison:** stored hash first, then by reference, then with `equals`.
- **Lookups take `Object`** (`get`, `getOrDefault`, `containsKey`, `remove`, `indexOf`), like `java.util.Map`; `put`, `putIfAbsent`, `addTo`, and `indexInsert` take `K`.
- **`addTo(key, increment)`** inserts an absent key with the increment and returns the new value, with a single hash lookup. With `java.util.HashMap`, the same counter needs `merge(key, 1, Integer::sum)` and a boxed `Integer` for every count above 127.
- Otherwise as the other linear-probing maps: backward-shift deletion (which also clears the key reference), index handles, load factor 0.75 by default; `equals` and `hashCode` follow `java.util.Map`.
- **Still slower than `HashMap` for absent keys:** linear probing at a fill of about 0.7 reads about five slots per absent key, while about half of the lookups in `HashMap` end at an empty bucket. (With SEQUENTIAL string keys, `HashMap` additionally profits from keeping consecutive numbers in neighboring buckets, so the absent keys of the benchmark stay in the cache.)

## `IntObjectConcurrentHashMap<V>`, `LongObjectConcurrentHashMap<V>`: Design Decisions

- **Segments:** a fixed, power-of-two number of segments (the `concurrencyLevel`; by default four per available processor, at most 256). The high bits of a key's hash select the segment, the following bits the slot. Each segment is a linear-probing table as in `IntObjectHashMap<V>` (key array and value array, key 0 outside the table, backward-shift deletion without tombstones), so the memory per entry is the same as in the single-threaded map. A segment grows on its own; there is no global resize.
- **Updates** take the lock of their segment, a mutex whose state lives in the segment object (an `AbstractQueuedSynchronizer`). Updates of different segments run in parallel; a few keys that many threads update all the time do not scale beyond their segment. Every segment ends with 64 bytes of padding, so that neighboring segments never share a cache line (the segments are allocated one after the other).
- **Lookups do not lock (seqlock).** Each segment has a version counter: an update makes it odd while it changes the table and even again afterwards. A lookup reads the version, probes the arrays, and accepts the result only if the version is unchanged and even; otherwise it retries, and after a few attempts it takes the lock. A lookup only waits while an update of the same segment is changing the table. Readers that take a read lock instead (as Speiger's segments do, with `StampedLock.readLock()`) write to the lock word on every lookup, and that cache line bounces between the cores.
- **Growth without blocking lookups:** a segment builds its larger table aside; lookups continue on the old arrays, and only the switch to the new arrays changes the version.
- **Atomic operations:** `computeIfAbsent`, `compute`, `computeIfPresent`, and `merge` run their function under the segment's lock, so it is called at most once; lookups continue meanwhile. `computeIfAbsent` and `putIfAbsent` first look up the key without lock. A function that updates the same segment gets an `IllegalStateException` ("recursive update").
- **No `null` values**, as in `ConcurrentHashMap`: `get` returning `null` always means "absent", so check-then-act needs no second call.
- **Weakly consistent aggregates:** `forEach`, `removeIf`, the conversions, `equals`, `hashCode`, and `toString` copy one segment at a time and run the action on the copy, without a lock, so the action may update the map. `removeIf` removes an entry only if its value is still the one the predicate saw. `size` adds up the sizes of the segments.
- **Small maps:** segments allocate their table on the first insertion, but every segment has an object and a lock, so an empty map takes a few kilobytes with the default concurrency level; many small maps should pass a lower `concurrencyLevel`.
- **Primitive values** (`IntIntConcurrentHashMap`, `LongLongConcurrentHashMap`, ...): the same segments, seqlock, locks, and padding, but each segment uses the layout of the single-threaded primitive map: one `long[]` per segment with interleaved key and value words (`long` keys, and `int` keys with `long` or `double` values) or packed 64-bit slots (`int` → `int`). A lookup therefore reads one array and one cache line.
- **Counting without lock:** `addTo` on a present key adds to its value word with one atomic instruction (`getAndAdd`, a compare-and-set loop for `double`), without the lock and without changing the version. Before, the adder increments a counter of active adders in the segment and then checks that the lock is free; every update takes the lock first and then waits until that counter is 0. Either the update sees the adder and waits for it, or the adder sees the lock and takes it as well (a Dekker handshake), so no update moves or copies a value word while an adder is active. New keys and key 0 are counted under the lock; `merge` and `computeIfAbsent` run their function under the lock. In the experiment run, this made counting a few hot keys with 6 and 12 threads 2.3-3x faster than with the lock, faster than `ConcurrentHashMap` with `LongAdder` values, and 1.6-1.8x slower than JCTools' `AtomicLong` values (two more atomic operations per increment: announcing and leaving), while insertions, removals, and builds became at most 5 % slower. A variant with per-thread counter stripes on the map instead of one counter per segment was unstable (as fast as JCTools with 6 threads, twice as slow with 12).
- **Cost of a lookup:** single-threaded, a lookup takes up to twice as long as in `LongObjectHashMap` (at 1,400,000 entries about 27 instead of 14 ns for present keys). Measured with one copy of the class per variant: the version check costs about 3 ns, a probe loop with an iteration counter about 2 ns (C2 splits counted loops for safepoint polls; the loop now stops when it is back at its start slot instead), and the rest comes from loading the segment, its arrays, and their lengths for every lookup, which the unsynchronized map loads once per loop of the caller. Neither the number of segments nor table arrays kept on the map (instead of in the segments) changed that. A load factor of 0.5 halves the time for absent keys at twice the memory.

## `IntObjectTreeMap<V>`, `LongObjectTreeMap<V>`: Design Decisions

The sorted maps, a B+ tree.

- **Node pools instead of node objects:** all leaves share one key array (`int[]`/`long[]`) and one parallel `Object[]` of values, with a fixed stride per leaf; the inner nodes share one array of separator keys and one of child ids. Leaves are linked in both directions. There is no object per node or entry, and no boxed key.
- **Handles are array indices** (`leaf * capacity + position`): `indexKey`/`indexGet` are single array reads, and stepping to the next entry within a leaf is `index + 1`.
- **Memory:** key and value reference take 12 bytes per entry with `long` keys, divided by the leaf fill (about 70% after random inserts, close to 100% after ascending inserts), plus a small share for inner nodes and the unused part of the node pools, which grow by a factor of 1.5. Measured deep size after random inserts: 22.6-25.8 bytes per entry (11,000,000 down to 1,400 entries), versus 64 bytes for `TreeMap<Long,V>` (40-byte entry plus a 24-byte `Long`) and 40 bytes for fastutil's red-black and AVL trees.
- **Splits:** a full leaf is split in halves, except when a key is appended behind the last key of the map: then the full leaf is kept and the new leaf starts with the new key, so ascending inserts fill leaves completely.
- **Presizing:** the node pools grow by a factor of 1.5 and are copied on every growth step. For large maps, create the map with `new LongObjectTreeMap<>(expectedSize)`: above 1 MB the pools are humongous objects for G1, and each growth step is a humongous allocation (see [Results](#results)).
- **Removal:** a node with less than a quarter of its capacity is merged with a neighbor if both fit into three quarters of a node, otherwise entries move over from the neighbor (the gap between the two thresholds avoids alternating splits and merges). Removal clears the value references.
- **Range operations:** `removeRange` unlinks the leaves inside the range as a whole; `removeIf` packs the remaining entries into full leaves and rebuilds the inner levels; `subMapCopy` and the copy constructor from a sorted map build the tree bottom-up.
- **No live views.** Their common uses (iterating a range, `headMap(k).clear()`, checking a range for entries) are covered by `forEachInRange`, `removeRange`, and `countRange`. Ranges are half-open `[from, to)`; the overloads with `fromInclusive`/`toInclusive` cover all bound combinations (including `Long.MAX_VALUE`); `from > to` is an `IllegalArgumentException`, as in `NavigableMap.subMap`.
- **`null` values** are allowed, as in the hash maps. `pollFirst()`/`pollLast()` throw a `NoSuchElementException` for an empty map, like `firstKey`/`lastKey`, and return `null` for a removed `null` value; `pollFirst(consumer)` returns `false` for an empty map instead.
- **Handles** stay valid until the next structural modification, as in the hash maps; `indexRemove` invalidates them. To remove entries while iterating, use `removeIf`, `removeRange`, or `pollFirst`/`pollLast`.
- **Node capacities:** 64 entries per leaf and 64 children per inner node, the best compromise in an experiment with 32 and 128 for both (`--suite=longsorted`, 180,000 and 11,000,000 entries): leaves of 128 entries made range scans up to a quarter faster, but lookups and random inserts at 11 million entries 5-7% slower; leaves of 32 entries were slower almost everywhere; the inner capacity made no difference beyond the noise. Linear search inside the nodes was 1.5-1.9x slower than binary search.
- **Blocked search inside a node:** the keys at positions 8, 16, ..., 56 are compared first, as independent loads, so a node that is not in the cache requests its cache lines at once; their count selects a block of eight keys, which is searched without branches. Compared with a plain binary search, lookups take about half the time while the nodes are in the cache (1,400 and 180,000 entries); at 11 million random keys, `floor` and random inserts are 16-20% faster and hits 13% slower. A branchless binary search over the whole node was as fast in the cache but up to a third slower at 11 million entries (each load depends on the previous comparison), and its timings varied between runs.
- **Appending:** a key greater than every key goes directly into the last leaf, without a search. This makes ascending builds 3-4x faster (about 10-14 ns per entry instead of 32-53 ns) and sliding windows (remove the smallest key, insert a new greatest one) 15-20% faster; random inserts pay one comparison with a cached key (within the noise).
- **Primitive values** (`IntLongTreeMap`, `LongDoubleTreeMap`, ...; one template for all 16 combinations of `int`/`long` keys and the eight primitive value types): the same tree with an array of the value type instead of the `Object[]`, so a `long -> long` entry takes 16 bytes divided by the leaf fill, and a `long -> boolean` entry 9. Nothing is cleared on removal, there is no `computeIfAbsent`, and numeric values have `addTo`, which descends once, like `put`. The node capacities are those of the maps with object values: the search reads only the key arrays, which are the same. A method that cannot return `null` for "no entry" takes a default from the caller (`floorValue(key, defaultValue)`); `pollFirst()`/`pollLast()` throw for an empty map, like `firstKey`/`lastKey`, and `pollFirst(consumer)` and the handles are the forms without an exception (see [API Overview](#api-overview)). Measured in an experiment (see [Results](#results)): unlike the maps with object values, the tree is also faster than the node-based trees for inserts and removals in small maps, because shifting a `long[]` needs no garbage-collector barriers (the one exception: random builds of 1,400 entries against Speiger's tree, 1.26x the time). Measured deep size after random inserts: 30.7-33.5 bytes per entry (1,400,000 down to 1,400 entries), about 1.9x the 16 bytes of key and value, as for the maps with object values: the leaf fill plus the unused part of the node pools.
- **Sorted sets** (`IntTreeSet`, `LongTreeSet`): the same tree without a value array, so a `long` element takes 8 bytes divided by the leaf fill. The node capacities are those of the maps, without a sweep of their own: the search reads only the key arrays, which are the same; whether larger leaves pay off without values is not measured. The copy constructor sorts the elements of a hash set and builds the tree bottom-up with full leaves. `pollFirst`/`pollLast` remove from the first or last leaf, with one descent to record the path for rebalancing. Measured in an experiemnt (see [Results](#results)): faster than the node-based trees in every cell, including inserts and removals in small sets, as with primitive values. Measured deep size after random inserts: 15.7-18.1 bytes per element (1,400,000 down to 1,400 elements), about 2x the 8 bytes of the element: the leaf fill plus the unused part of the node pools.

## Insertion-Ordered Maps and Sets: Design Decisions

`IntLongLinkedHashMap`, `LongObjectLinkedHashMap<V>`, `LongLinkedHashSet`,
`ObjectIntLinkedHashMap<K>`, and the other instantiations of the templates `linked`,
`linked-object`, `linked-set`, and `linked-objectkey`.

- **Dense entries, separate index table:** the keys and the values are two parallel arrays in insertion order (`long[]`/`int[]` keys, an array of the value type or an `Object[]`); a new entry is appended. An `int[]` index table with linear probing maps a key to the position of its entry. Iteration is a sequential scan of the entry arrays, and there is no link per entry.
- **Tags in the index table:** a slot holds the position plus one in its low bits and hash bits in the remaining high bits. A lookup compares these bits before it reads a key, so a lookup of an absent key usually reads only the index table. A lookup of a present key reads the index table and then the entry, one dependent memory access more than in a plain hash map.
- **Removal without moving entries:** a removed entry leaves a hole in the entry arrays and a tombstone in the index table. A bitmap of the holes is allocated with the first hole inside the entry range; removals at either end (`pollFirst`, `pollLast`) just shrink the range. When the entry arrays are full, they are compacted in place if at least a quarter is unused, and grow otherwise; both rebuild the index table, which removes the tombstones. A queue of constant size therefore never grows.
- **Key 0** needs no special case, because no key value marks an empty slot.
- **`moveToLast`** appends the entry again and leaves a hole at its old position, so a workload that only moves entries compacts regularly.
- **Measured against the alternative** (experiment v17: `long` keys, object values, RANDOM keys, 1,400 to 1,400,000 entries, two rounds on Windows, both layouts as experiment classes): a linear-probing table with a link word per slot, the layout of fastutil's and Speiger's linked maps. The dense layout iterated in 0.3 ns per entry at every size (linked slots: 1.7 ns in the cache, 100 ns at 1,400,000 entries, where every step is a cache miss), built maps of 180,000 and more entries 2.4-2.6x faster, was 1.5-2.9x faster for "remove a random key, insert a new one" and for "remove the oldest entry, insert a new one", and 1.6x faster for absent keys at 1,400,000 entries, at 19.5 instead of 30 bytes per entry (`LinkedHashMap<Long,V>`: 70). The link layout won where the dense layout pays its extra memory access or its compaction: lookups of present keys (1.4x faster up to 180,000 entries, 2.5x at 1,400,000 in this run with two rounds; experiment v18 of the generated class measured 21 ns instead of 39 ns there, 1.4x the time of fastutil) and `putLast` on a present key (1.6-2.5x faster). The link layout was a faster fastutil (5-35% in most cells), but had no advantage of its own, so the dense layout was adopted.
- `equals` and `hashCode` do not depend on the order and work across implementations, as in `java.util.LinkedHashMap`.
- **Object keys** (`ObjectIntLinkedHashMap<K>`, `ObjectLongLinkedHashMap<K>`, `ObjectDoubleLinkedHashMap<K>`): the same layout with an `Object[]` of keys and an `int[]` with the stored hash of every entry, hashed like the hash maps with object keys (the murmur3 finalizer of `hashCode() ^ seed`; the high bits select the home slot, the bits below them form the tag). Rebuilding the index table (growth, compaction) and removals by position (`pollFirst`, `pollLast`, `removeIf`, `indexRemove`) take the hash from the entry and find the index slot by its position, so they call neither `hashCode` nor `equals`; a lookup calls `equals` only when the tag matches. The stored hashes cost 4 bytes per entry position. The `null` key is an ordinary entry with the hash code 0, so it has its place in the order. A removal clears the key reference, and so does compaction for the positions it gives up. Measured in experiment v21 (see [Results](#results)): `getHit` and `getMiss` cost about as much as in `ObjectIntHashMap` (experiment v12: 82 against 76 ns and 68 against 60 ns at 1,400,000 RANDOM keys), but `count` costs more than in fastutil's and Speiger's linked maps. The diagnosis run `20261124-diag-count` (`count` and `getHit` on the data of the suite, with `ObjectIntHashMap` and fastutil's `Object2IntOpenHashMap` as further subjects) shows why. First, `count` scatters strongly between runs and rounds (up to 30%): at 1,400,000 entries the linked map took 58 ns there, as much as `ObjectIntHashMap` (59 ns). Second, the benchmark inserts the keys in the order of their ranks, so the frequent keys come first, and fastutil's linked map refills its table in insertion order when it grows: the frequent keys sit in their home slots. A `count` visits 1.1 slots on average there, against 1.5 in fastutil's hash map with the same keys (uniform lookups: 2.0-2.1 slots in both). Since every slot visited compares a key, fastutil's linked map counts 22-34% faster than its hash map. stencil compares the tag first, so short probe sequences save less, and it pays the extra access through the index table. Populated in random order, `ObjectIntLinkedHashMap` counts 12-29% slower, so the order helps it too, through neighboring positions of the frequent entries. The remaining gap (43 against 26 ns at 180,000 entries, 58 against 50 ns at 1,400,000) is the cost of the index table, the price of the dense layout.

## Sets: Design Decisions

`IntSet` and `LongSet`, generated from the same templates as the maps' layouts.

- **`IntHashSet`, `LongHashSet`:** one plain key array, otherwise as the linear-probing maps (element 0 outside the table, Fibonacci hashing with a per-instance seed, backward-shift deletion, load factor 0.75 by default).
- **`LongCompactHashSet`:** blocks of 64 bytes (one cache line): the control word (seven byte tags and the overflow counter) and seven elements. Hits and misses usually read a single cache line.
- **Default load factor 0.85.** The table then has about the same size as `LongHashSet` at 0.75.
- **No compact layout for `int` elements.** With 4-byte elements, the control word costs too much memory per slot. At the memory of `IntHashSet`, only 4-bit tags fit (fourteen slots per 64-byte block); their false matches made hits about twice as slow as linear probing, and lookups of absent elements were faster only for sets far beyond the CPU caches. Blocks with byte tags (six slots per 32 bytes, or twelve slots with two control words per 64 bytes) need a load factor of about 0.92 for the same memory, where overflow chains make every operation slower and the saturating counters degrade under churn. With twice the memory, none of these layouts beats `IntHashSet` with load factor 0.5.
- `equals` and `hashCode` follow `java.util.Set` and work across implementations.

## Priority Queues: Design Decisions

`IntHeap`, `LongHeap`, `DoubleHeap` (template `heap`), `IntIntIndexedHeap`,
`IntLongIndexedHeap`, `IntDoubleIndexedHeap` (template `indexed-heap`), and `LongIntIndexedHeap`,
`LongLongIndexedHeap`, `LongDoubleIndexedHeap` (template `indexed-heap-long`).

- **4-ary implicit heap:** one array; the children of position `i` are at `4i + 1 .. 4i + 4`. The heap has half the levels of a binary heap, so a push moves fewer values, and the four children that a removal scans for the smallest lie next to each other in memory (32 bytes for `long` values).
- **Measured against the alternatives** (experiments v22a, v22b, and v22c, the diagnosis run `20261124-diag-hold-sequential`: `long` values, 1,400 to 1,400,000 values, as experiment classes): binary, 4-ary, 8-ary, and 16-ary heaps, each with the standard removal and with the "bottom-up" removal (Floyd: move the hole down to a leaf, then the value up). The code that finds the smallest child mattered most. A 4-ary heap that compared its children in a small tournament was slower than the binary heap (sorting up to 1.5x). With a plain loop over the children, the 4-ary and the 8-ary heap were both much faster than fastutil's binary heap on random values, the 8-ary heap another 10-25% at its best; the 16-ary heap sorted and ran the event loop about twice as slowly; the bottom-up removal gained nothing.
- **Why not 8-ary:** the speed of the 8-ary heap depended on the data the JIT saw when it compiled the removal. Compiled while the queue was in ascending order (the first child always the smallest), it stayed about twice as slow for any data afterwards: the event loop at 180,000 values took 108 ns per step instead of 53 ns, whether the queue measured then was random or ascending. A queue that is first filled with ascending time stamps, a common case, would keep the slow code for the life of the JVM. Branch-free selections of the smallest child (bit masks, `Math.min`) were slow for all data. The loop over four children does not depend on the profile (62 against 63 ns at 180,000 values), so the 4-ary heap with the standard removal was adopted.
- **Order without a comparator:** the array always holds a min-heap. A queue that returns the largest value first stores `~x` (`int`, `long`; exact, without overflow) or `-x` (`double`) instead of `x`, so the comparisons are plain `<` without a branch on the order. `double` queues reject `NaN`; `-0.0` and `0.0` are the same priority.
- **Indexed queues:** the ids and the priorities are two parallel arrays in heap order, so that a sift compares priorities without following the ids. A third `int[]`, indexed by id, holds the heap position plus one (0: not in the queue), so `contains`, `priority`, `putIfBetter`, and `remove` find an id in constant time. The ids are meant to be dense (`0` to `n - 1`, such as the nodes of a graph): the position array covers every id up to the largest one, 4 bytes per id; an entry costs 8 (`int` priorities) or 12 bytes. `clear` resets only the positions of the ids in the queue.
- **Arbitrary `long` keys:** an open-addressing hash table (linear probing, Fibonacci hashing with a random seed per queue, load factor 0.75, backward-shift deletion) in one `long[]` holds the key of a slot and its heap position plus one side by side, so a lookup reads one cache line. A slot is empty when its position word is 0, not its key, so every `long` can be a key without a special case for `0`. The heap stores, instead of an id, the table slot of each entry, so the table slot plays the role of the dense id: a sift writes the new position into the slot directly, without hashing; only the lookup of the key at the start of an operation hashes. Deleting a key moves later keys of its probe chain back and updates their heap entries through their positions; growing the table reinserts the keys of the heap entries. Memory: about 36 bytes per key with `long` priorities (21 for the table, 12 for the heap arrays), against 16 bytes per id for the dense queue. Against the usual workaround, a map from keys to dense ids plus the dense queue, it saves one hash table, the key array, and the management of free ids (experiment v28a: 1.05-1.38x faster than this workaround built from stencil classes, at 36 instead of 52 bytes per key).
- `pushAll` builds bottom-up in linear time when it adds at least as many values as the queue holds.

## Lists and Deques: Design Decisions

`IntArrayList`, `LongArrayList`, `DoubleArrayList` (template `list`) and `IntArrayDeque`,
`LongArrayDeque`, `DoubleArrayDeque` (template `deque`).

- **Index or value:** no method takes an `int` that could mean either. The lists have no `remove(int)`: `removeAt(index)` removes at an index, `removeValue(value)` the first element equal to a value. The deques remove several elements at an end with `discardFirst(count)` and `discardLast(count)`, not with an overload of `removeFirst`. (In HPPC, `IntArrayDeque.removeFirst(int)` removes the first element equal to the argument, and `IntArrayList.add(int, int)` appends two elements.)
- **Names of `java.util.SequencedCollection`** at the ends (`addFirst`, `getLast`, `removeFirst`, ...), without aliases such as `offer`, `poll`, `push`, or `pop`, so that every call names its end.
- **List:** one array with the elements at positions `0 .. size`, growing by half its length (to at least 16); a new list allocates no array until its first element. Inserting or removing at an index moves the elements behind it with one `System.arraycopy`. `wrap(array, size)` and `elements()` share the array with code that works on arrays.
- **Deque:** a ring buffer whose length is a power of two, so element `i` is at `(head + i) & (length - 1)`, without a branch or a division. Adding and removing at both ends, `get`, and `set` take constant time (`ArrayDeque` has no access by index). When the array is full, it doubles and the elements start at position 0 again; the capacity is at most 2<sup>30</sup>. The memory is up to twice the size times the element width; `trimToSize` shrinks the array to the next power of two of the size.
- **Equality:** a list or a deque equals another list (deque) of the same type with the same elements in the same order; the hash code is the one of `java.util.List` on the boxed elements, so it equals the hash code of `toList()`. Unlike `ArrayDeque`, which only knows identity, deques are compared by value. `double` elements compare like `Double.equals` (bitwise: `NaN` equals `NaN`, `-0.0` does not equal `0.0`), in `equals`, `indexOf`, `contains`, and `removeValue`, as in a `List<Double>`.

## `IntIntBlockHashMap` (experimental layout class)

A research class in the `bench` module (package `net.collections.stencil.bench`), not part of the library.

- **Blocks** of eight packed slots (64 bytes, one cache line). A key is stored in its home slot if possible, otherwise anywhere in that slot's block. A lookup compares the whole block without data-dependent branches.
- **Overflow** to further blocks along a per-key double-hashing sequence, with a saturating 8-bit overflow counter per block (in the style of F14). Lookups stop at the first block with counter zero; removals decrement the counters on the way. No tombstones, no backward shift, no long probe runs at high load.
- **Home slot** as a variation point: *identity* (the key with its upper bits folded in, as the first probe of Eclipse Collections; sequential keys occupy consecutive slots) or *hashed* (Fibonacci hashing with a per-instance seed).
- **Memory:** 8 bytes per slot plus one counter byte per block (about 1.6 %).
- Not yet implemented: index handles.

## Results

Measured on one machine: Zulu JDK 25.0.5, fastutil 8.5.19, HPPC 0.13.0, Eclipse Collections 13.0.0.
Interleaved runs with three
rounds (see [Benchmarks](#benchmarks)). Each value is the **paired ratio stencil / baseline**,
median over the rounds, geometric mean over the four sizes 1,400 to 11,000,000. **Below 1 means
stencil is faster.** RANDOM and SEQUENTIAL keys are shown separately, because they behave very
differently. "Equal memory" means the same table size at all benchmark sizes (see the footprint
section of each report). Full reports: `results/<run>/report.md`.

**`int -> int` maps**

| Comparison | Memory | Keys | getHit | getMiss | addTo | build | churn | Run |
|---|---|---|---:|---:|---:|---:|---:|---|
| `IntIntHashMap` / fastutil | equal | RANDOM | 0.93 | 1.29 | 0.93 | 0.95 | 1.02 | v3 |
| | | SEQUENTIAL | 0.31 | 0.44 | 0.39 | 0.40 | 0.36 | |
| `IntIntHashMap` (load factor 0.5) / Eclipse Collections | equal | RANDOM | 0.96 | 0.92 | 0.94 | 0.86 | 0.63 | v4 |
| | | SEQUENTIAL | 0.88 | 1.48 | 1.14 | 1.82 | 1.21 | |
| `IntIntCompactHashMap` / `IntIntHashMap` | equal | RANDOM | 0.97 | 0.50 | 1.15 | 1.23 | 1.00 | v6 |
| | | SEQUENTIAL | 2.16 | 0.57 | 2.20 | 1.94 | 1.69 | |

HPPC was not part of the `int -> int` runs after the benchmark fixes.

**`long -> long` maps**

| Comparison | Memory | Keys | getHit | getMiss | addTo | build | churn | Run |
|---|---|---|---:|---:|---:|---:|---:|---|
| `LongLongHashMap` / fastutil | equal | RANDOM | 0.81 | 0.96 | 0.84 | 0.86 | 0.93 | v7 |
| | | SEQUENTIAL | 0.24 | 0.30 | 0.25 | 0.45 | 0.31 | |
| `LongLongHashMap` / HPPC | equal | RANDOM | 0.96 | 1.26 | 0.93 | 0.96 | 1.06 | v7 |
| | | SEQUENTIAL | 0.24 | 0.27 | 0.26 | 0.50 | 0.31 | |
| `LongLongHashMap` (load factor 0.5) / Eclipse Collections | equal | RANDOM | 0.58 | 0.73 | 0.86 | 0.80 | 0.58 | v7 |
| | | SEQUENTIAL | 1.03 | 1.54 | 0.99 | 1.83 | 1.14 | |
| `LongLongCompactHashMap` / HPPC | equal | RANDOM | 0.96 | 0.67 | 1.23 | 1.29 | 1.07 | v8 |
| | | SEQUENTIAL | 0.72 | 0.18 | 0.86 | 0.89 | 0.49 | |
| `LongLongCompactHashMap` / `LongLongHashMap` | equal | RANDOM | 1.00 | 0.49 | 1.36 | 1.31 | 1.02 | v8 |
| | | SEQUENTIAL | 2.17 | 0.81 | 2.64 | 1.80 | 1.81 | |

**Maps with object values** (`computeIfAbsent`: grouping, one inserting and one finding call per key)

| Comparison | Memory | Keys | getHit | getMiss | build | churn | computeIfAbsent | Run |
|---|---|---|---:|---:|---:|---:|---:|---|
| `IntObjectHashMap` / fastutil | equal | RANDOM | 0.92 | 1.00 | 1.04 | 1.10 | 1.05 | v10 |
| | | SEQUENTIAL | 0.39 | 0.37 | 0.87 | 0.41 | 0.75 | |
| `IntObjectHashMap` / HPPC | equal | RANDOM | 0.99 | 0.98 | 0.93 | 1.10 | 1.12 | v10 |
| | | SEQUENTIAL | 0.39 | 0.40 | 0.84 | 0.42 | 0.82 | |
| `IntObjectHashMap` (load factor 0.5) / Eclipse Collections | equal | RANDOM | 0.87 | 1.07 | 1.03 | 0.62 | 0.99 | v10 |
| | | SEQUENTIAL | 1.31 | 2.07 | 1.03 | 0.47 | 1.12 | |
| `LongObjectHashMap` / fastutil | equal | RANDOM | 0.77 | 0.76 | 0.94 | 0.92 | 0.92 | v10 |
| | | SEQUENTIAL | 0.31 | 0.29 | 0.91 | 0.35 | 0.71 | |
| `LongObjectHashMap` / HPPC | equal | RANDOM | 0.96 | 1.02 | 0.99 | 1.02 | 1.01 | v10 |
| | | SEQUENTIAL | 0.31 | 0.25 | 0.95 | 0.33 | 0.78 | |
| `LongObjectHashMap` (load factor 0.5) / Eclipse Collections | equal | RANDOM | 0.62 | 0.94 | 0.91 | 0.56 | 1.00 | v10 |
| | | SEQUENTIAL | 1.20 | 1.30 | 1.24 | 0.43 | 1.30 | |

**Sets** (`containsHit`, `containsMiss`, `build`, `churn`)

| Comparison | Memory | Keys | hit | miss | build | churn | Run |
|---|---|---|---:|---:|---:|---:|---|
| `IntHashSet` / fastutil | equal | RANDOM | 1.01 | 1.00 | 1.05 | 1.08 | v9 |
| | | SEQUENTIAL | 0.30 | 0.45 | 0.48 | 0.28 | |
| `IntHashSet` / HPPC | equal | RANDOM | 1.02 | 1.01 | 1.05 | 1.05 | v9 |
| | | SEQUENTIAL | 0.29 | 0.46 | 0.46 | 0.28 | |
| `IntHashSet` (load factor 0.5) / Eclipse Collections | equal | RANDOM | 0.75 | 0.86 | 0.86 | 0.68 | v9 |
| | | SEQUENTIAL | 0.27 | 0.27 | 0.48 | 0.26 | |
| `LongHashSet` / fastutil | equal | RANDOM | 0.76 | 0.73 | 0.89 | 0.91 | v9 |
| | | SEQUENTIAL | 0.28 | 0.30 | 0.48 | 0.28 | |
| `LongHashSet` / HPPC | equal | RANDOM | 0.89 | 0.97 | 0.95 | 1.02 | v9 |
| | | SEQUENTIAL | 0.29 | 0.25 | 0.48 | 0.27 | |
| `LongHashSet` (load factor 0.5) / Eclipse Collections | equal | RANDOM | 0.40 | 0.54 | 0.71 | 0.53 | v9 |
| | | SEQUENTIAL | 0.23 | 0.25 | 0.47 | 0.23 | |
| `LongCompactHashSet` / HPPC | equal | RANDOM | 0.95 | 0.50 | 1.16 | 1.01 | v9 |
| | | SEQUENTIAL | 0.63 | 0.17 | 0.89 | 0.48 | |
| `LongCompactHashSet` / `LongHashSet` | equal | RANDOM | 1.05 | 0.52 | 1.21 | 0.97 | v9 |
| | | SEQUENTIAL | 2.17 | 0.69 | 1.85 | 1.77 | |

**Sorted maps** (`floor`: the greatest key `<=` an absent key; `iterate`, `rangeScan`, and
`removeRange` per entry; see [Benchmarks](#benchmarks))

| Comparison | Memory | Keys | getHit | getMiss | floor | build | churn | iterate | rangeScan | removeRange | Run |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `LongObjectTreeMap` / fastutil `Long2ObjectRBTreeMap` | less | RANDOM | 0.53 | 0.34 | 0.33 | 0.75 | 0.59 | 0.05 | 0.19 | 0.51 | v11 |
| | | SEQUENTIAL | 0.46 | 0.31 | 0.31 | 0.17 | 0.80 | 0.05 | 0.30 | 0.48 | |
| `LongObjectTreeMap` / fastutil `Long2ObjectAVLTreeMap` | less | RANDOM | 0.54 | 0.34 | 0.33 | 0.82 | 0.64 | 0.05 | 0.19 | 0.65 | v11 |
| | | SEQUENTIAL | 0.46 | 0.29 | 0.30 | 0.33 | 1.07 | 0.04 | 0.30 | 0.66 | |
| `LongObjectTreeMap` / `TreeMap<Long,Object>` | less | RANDOM | 0.42 | 0.30 | 0.30 | 0.79 | 0.46 | 0.04 | 0.16 | 0.96 | v11 |
| | | SEQUENTIAL | 0.36 | 0.25 | 0.25 | 0.23 | 0.99 | 0.05 | 0.25 | 0.98 | |

Memory of the sorted maps, deep size per entry at 11,000,000 entries (nodes, boxed keys, and
arrays with their headers; the shared values are not counted): `LongObjectTreeMap` 22.6 bytes,
fastutil's red-black and AVL trees 40.0 bytes, `TreeMap<Long,Object>` 64.0 bytes.

**Sorted maps with primitive values** (`long` keys and values; geometric mean over 1,400,
180,000, and 1,400,000 entries; the lookups and scans read the values)

| Comparison | Memory | Keys | getHit | getMiss | floor | build | churn | iterate | rangeScan | removeRange | Run |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `LongLongTreeMap` / fastutil `Long2LongRBTreeMap` | less | RANDOM | 0.56 | 0.30 | 0.31 | 0.53 | 0.51 | 0.04 | 0.15 | 0.37 | v19 |
| | | SEQUENTIAL | 0.49 | 0.29 | 0.29 | 0.13 | 0.48 | 0.05 | 0.33 | 0.32 | |
| `LongLongTreeMap` / Speiger `Long2LongRBTreeMap` | less | RANDOM | 0.54 | 0.35 | 0.36 | 0.77 | 0.53 | 0.04 | 0.15 | 0.73 | v19 |
| | | SEQUENTIAL | 0.50 | 0.30 | 0.32 | 0.21 | 0.73 | 0.04 | 0.26 | 0.68 | |
| `LongLongTreeMap` / `TreeMap<Long,Long>` | less | RANDOM | 0.38 | 0.24 | 0.27 | 0.55 | 0.34 | 0.02 | 0.12 | 0.61 | v19 |
| | | SEQUENTIAL | 0.34 | 0.22 | 0.23 | 0.20 | 0.61 | 0.04 | 0.26 | 0.57 | |

`LongLongTreeMap` was faster in 143 of the 144 cells behind this table; the exception is the
random build of 1,400 entries against Speiger's tree (98 ns per entry against 78 ns; 0.89
against fastutil and 0.94 against `TreeMap`). The advantage grows with the size: at 1,400,000
random keys, a lookup of an absent key takes 150 ns instead of 700-930 ns, and a range scan
5 ns per entry instead of 62-81 ns. Ascending builds take 14-17 ns per entry at every size
(fastutil 79-157 ns, Speiger 42-105 ns, `TreeMap` 48-115 ns).

Memory, deep size per entry at 1,400,000 entries after random inserts (`TreeMap` with its boxed
keys and values): `LongLongTreeMap` 30.7 bytes, fastutil 40.0 bytes, Speiger 48.0 bytes,
`TreeMap<Long,Long>` 88.0 bytes.

**Sorted sets** (`long` elements; geometric mean over 1,400, 180,000, and 1,400,000 elements;
`getHit` and `getMiss` are `contains`)

| Comparison | Memory | Keys | getHit | getMiss | floor | build | churn | iterate | rangeScan | removeRange | Run |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `LongTreeSet` / fastutil `LongRBTreeSet` | less | RANDOM | 0.45 | 0.32 | 0.30 | 0.46 | 0.36 | 0.04 | 0.17 | 0.34 | v20 |
| | | SEQUENTIAL | 0.40 | 0.30 | 0.28 | 0.09 | 0.32 | 0.06 | 0.28 | 0.27 | |
| `LongTreeSet` / Speiger `LongRBTreeSet` | less | RANDOM | 0.40 | 0.38 | 0.40 | 0.54 | 0.35 | 0.03 | 0.15 | 0.67 | v20 |
| | | SEQUENTIAL | 0.40 | 0.35 | 0.37 | 0.16 | 0.52 | 0.06 | 0.29 | 0.65 | |
| `LongTreeSet` / `TreeSet<Long>` | less | RANDOM | 0.35 | 0.29 | 0.33 | 0.45 | 0.24 | 0.02 | 0.13 | 0.61 | v20 |
| | | SEQUENTIAL | 0.26 | 0.24 | 0.25 | 0.12 | 0.38 | 0.02 | 0.20 | 0.61 | |

`LongTreeSet` was faster in all 144 cells behind this table; the closest is the random build
of 1,400 elements against Speiger's tree (68 ns per element against 72 ns). The advantage grows
with the size: at 1,400,000 random elements, `contains` takes 147-152 ns instead of 530-970 ns,
`churn` 410 ns instead of 1,600-2,600 ns, and a range scan 3.9 ns per element instead of
34-45 ns. Ascending builds take 8-14 ns per element (fastutil 74-147 ns, Speiger 39-82 ns,
`TreeSet` 51-114 ns).

Memory, deep size per element at 1,400,000 elements after random inserts (`TreeSet` with its
boxed elements): `LongTreeSet` 15.7 bytes, fastutil 32.0 bytes, Speiger 40.0 bytes,
`TreeSet<Long>` 64.0 bytes.

**Insertion-ordered maps** (`long` keys, object values, RANDOM keys; `iterate` per entry;
`churn`: remove a random key, insert a new one; `fifo`: remove the oldest entry, insert a new
one; `touch`: `putLast` on a present key; see [Benchmarks](#benchmarks))

| Comparison | Memory | Size | getHit | getMiss | build | iterate | churn | fifo | touch | Run |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `LongObjectLinkedHashMap` / fastutil `Long2ObjectLinkedOpenHashMap` | less | 1,400 | 0.96 | 0.63 | 0.72 | 0.19 | 0.59 | 0.48 | 2.02 | v18 |
| | | 180,000 | 0.92 | 0.64 | 0.33 | 0.02 | 0.59 | 0.46 | 1.68 | |
| | | 1,400,000 | 1.42 | 0.61 | 0.44 | 0.004 | 0.49 | 0.27 | 1.35 | |
| `LongObjectLinkedHashMap` / Speiger `Long2ObjectLinkedOpenHashMap` | less | 1,400 | 0.88 | 0.59 | 0.78 | 0.17 | 0.58 | 0.47 | (0.38) | v18 |
| | | 180,000 | 0.98 | 0.63 | 0.36 | 0.02 | 0.61 | 0.46 | (0.46) | |
| | | 1,400,000 | 1.42 | 0.60 | 0.46 | 0.004 | 0.48 | 0.27 | (0.35) | |
| `LongObjectLinkedHashMap` / `LinkedHashMap<Long,Object>` | less | 1,400 | 0.74 | 0.95 | 0.62 | 0.15 | 0.70 | 0.73 | 0.90 | v18 |
| | | 180,000 | 0.51 | 0.91 | 0.28 | 0.13 | 0.44 | 0.51 | 0.86 | |
| | | 1,400,000 | 0.54 | 0.89 | 0.37 | 0.08 | 0.58 | 0.49 | 0.62 | |

Speiger's `touch` is in parentheses: its adapter removes the key and inserts it again, because
`putAndMoveToLast` fails in version 1.0.1 (see [Benchmarks](#benchmarks)). In absolute numbers,
`LongObjectLinkedHashMap` iterates in 0.25-0.30 ns per entry at every size (fastutil: 1.3 ns at
1,400 entries, 79 ns at 1,400,000, where every step is a cache miss), and a lookup of a present
key at 1,400,000 entries takes 21 ns (fastutil 15 ns, `LinkedHashMap` 39 ns).

Memory of the insertion-ordered maps, deep size per entry at 1,400,000 entries (the shared
values are not counted): `LongObjectLinkedHashMap` 19.5 bytes, fastutil and Speiger 30.0 bytes,
`LinkedHashMap<Long,Object>` 70.0 bytes.

**Insertion-ordered maps with object keys** (`String` keys, `int` values; geometric mean over
1,400, 180,000, and 1,400,000 entries; operations as above, plus `count`: `addTo(key, 1)` on
keys with Zipf-like ranks)

| Comparison | Memory | Keys | getHit | getMiss | build | iterate | churn | fifo | touch | count | Run |
|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `ObjectIntLinkedHashMap` / fastutil `Object2IntLinkedOpenHashMap` | less | RANDOM | 0.73 | 0.40 | 0.41 | 0.12 | 0.41 | 0.23 | 1.15 | 1.31 | v21 |
| | | SEQUENTIAL | 0.75 | 0.46 | 0.42 | 0.12 | 0.44 | 0.24 | 1.23 | 1.15 | |
| `ObjectIntLinkedHashMap` / Speiger `Object2IntLinkedOpenHashMap` | less | RANDOM | 0.75 | 0.39 | 0.43 | 0.08 | 0.39 | 0.21 | (0.33) | 1.39 | v21 |
| | | SEQUENTIAL | 0.71 | 0.45 | 0.40 | 0.08 | 0.43 | 0.20 | (0.38) | 1.12 | |
| `ObjectIntLinkedHashMap` / `LinkedHashMap<String,Integer>` | less | RANDOM | 1.01 | 3.12 | 0.50 | 0.39 | 0.82 | 0.62 | 1.21 | 0.95 | v21 |
| | | SEQUENTIAL | 0.94 | 3.35 | 0.67 | 0.37 | 0.87 | 0.91 | 1.28 | 1.03 | |

Speiger's `touch` is in parentheses for the same reason as above. In absolute numbers at
1,400,000 entries: iteration 2.5 ns per entry (fastutil 74-86 ns, Speiger 95 ns,
`LinkedHashMap` 5 ns), `fifo` 90 ns (fastutil 560-600 ns, Speiger 670-730 ns, `LinkedHashMap`
97-181 ns), builds 95-118 ns per entry (fastutil and Speiger 250-277 ns, `LinkedHashMap`
125-213 ns). `LinkedHashMap` looks up absent keys 3-4x faster (14-20 ns against 59-68 ns), as
`HashMap` against `ObjectIntHashMap` in experiment v12: most of its buckets are empty, where open
addressing probes. `count` takes 69-72 ns (fastutil 44-53 ns, Speiger 40-53 ns,
`LinkedHashMap` 54-63 ns); it scatters strongly between runs, and the keys with low ranks, the
frequent ones, are inserted first, which favors fastutil's and Speiger's linked maps (see the
design bullet on object keys). `touch` is 1.0-1.6x slower than fastutil, as with `long` keys.

Memory of the insertion-ordered maps with object keys, deep size per entry at 1,400,000
entries (the `String` keys are not counted, the boxed values of the JDK map are):
`ObjectIntLinkedHashMap` 19.5 bytes, fastutil and Speiger 24.0 bytes,
`LinkedHashMap<String,Integer>` 62.0 bytes.

**Priority queues** (`long` values; geometric mean over 1,400, 180,000, and 1,400,000 values;
`build`: push all values into a queue with the default capacity, per value; `sort`: push all
values, then poll them all, per value; `hold`: on a queue of `size` events, poll the earliest at
time `t` and schedule a new one at `t` plus a random increment, the classic event-queue
benchmark; stencil uses `replaceTop` there, the others poll and push. RANDOM: event times drawn
uniformly; SEQUENTIAL: ascending event times)

| Comparison | Memory | Values | build | sort | hold | Run |
|---|---|---|---:|---:|---:|---|
| `LongHeap` / fastutil `LongHeapPriorityQueue` | less | RANDOM | 0.79 | 0.66 | 0.69 | v24 |
| | | SEQUENTIAL | 1.06 | 0.79 | 0.67 | |
| `LongHeap` / Speiger `LongHeapPriorityQueue` | less | RANDOM | 0.77 | 0.59 | 0.62 | v24 |
| | | SEQUENTIAL | 0.99 | 0.73 | 0.61 | |
| `LongHeap` / `PriorityQueue<Long>` | less | RANDOM | 0.32 | 0.25 | 0.22 | v24 |
| | | SEQUENTIAL | 0.39 | 0.44 | 0.22 | |

In absolute numbers at 1,400,000 RANDOM values: `hold` 108 ns per step (fastutil 125 ns, Speiger
145 ns, `PriorityQueue` 702 ns), `sort` 98 ns per value (130, 135, 524 ns), `build` 11.4 ns per
value (15.2, 15.3, 82.4 ns). `hold` takes as long for ascending event times (104 ns). Ascending
values need no sifting on push, so all primitive queues build them about equally fast; these are
the only cells where `LongHeap` is slower than fastutil (up to 1.09x). The first version of the
queue, an 8-ary heap (experiments v22 and v23), was up to 1.26x slower than fastutil in `hold` on
ascending values; see the design decisions for the cause. Memory per value: `LongHeap` 8.6-8.8 bytes,
fastutil and Speiger 9.0-9.2 bytes, `PriorityQueue<Long>` 29 bytes.

**Indexed priority queues** (`int` ids, `long` priorities; geometric mean over 1,400, 180,000,
and 1,400,000 ids; `dijkstra`: shortest paths from one source to all nodes of a graph with 8
edges per node and weights from 1 to 1,000, per node; RANDOM: edges to random nodes; SEQUENTIAL:
edges to nodes within the next 64 ids, like the local structure of a road network; `sort`:
insert all ids with random (RANDOM) or ascending (SEQUENTIAL) priorities, then poll them all, per
id)

| Comparison | Memory | Graph | dijkstra | sort | Run |
|---|---|---|---:|---:|---|
| `IntLongIndexedHeap` / fastutil `LongHeapIndirectPriorityQueue` | same | RANDOM | 0.78 | 0.53 | v25 |
| | | SEQUENTIAL | 0.79 | 0.66 | |
| `IntLongIndexedHeap` / `PriorityQueue` of `(priority, id)` records | less | RANDOM | 0.45 | 0.41 | v25 |
| | | SEQUENTIAL | 0.43 | 0.52 | |

fastutil's indirect queue keeps the priorities in an array of the caller and needs `contains`
and `changed(id)` for "decrease key"; the JDK queue has no "decrease key", so its Dijkstra pushes
a node again when its distance shrinks and skips stale entries (lazy deletion), the usual form.
`IntLongIndexedHeap` was faster in 23 of 24 cells and even in the last one (`sort` of 1,400
ascending ids, 1.01x fastutil's time). In absolute numbers at 1,400,000 nodes: `dijkstra` 791 ns
per node on the RANDOM graph (fastutil 810 ns, `PriorityQueue` 1,401 ns), where the cache misses
of the graph itself dominate, and 124 ns on the SEQUENTIAL graph (150 and 274 ns). Memory of a queue with all ids: 16 bytes per id for
stencil and for fastutil (counting its priority array), 28 bytes for `PriorityQueue`.

**Indexed priority queues with arbitrary keys** (`long` keys spread over the whole `long` range,
`long` priorities; geometric mean over 1,400, 180,000, and 1,400,000 keys; `dijkstra` as above on
the RANDOM graph, node `u` having the key `u * 0x9E3779B97F4A7C15`, per node; `timers`: a steady
state of `size` timers, where three of four steps push the deadline of a random timer back and
the fourth lets the earliest expire and starts a timer with a new key, per step)

| Comparison | Memory | dijkstra | timers | Run |
|---|---|---:|---:|---|
| `LongLongIndexedHeap` / stencil `LongIntHashMap` + `IntLongIndexedHeap` (key to id) | less | 0.80 | 0.83 | v28a |
| `LongLongIndexedHeap` / fastutil `Long2IntOpenHashMap` + `LongHeapIndirectPriorityQueue` | less | 0.65 | 0.60 | v28a |
| `LongLongIndexedHeap` / `PriorityQueue` of records + `HashMap<Long, Long>` (lazy deletion) | less | 0.28 | 0.18 | v28a |

No library has an indexed priority queue with arbitrary keys, so the comparison is with the
workarounds: a map assigns a dense id to every key in the queue, the dense indexed queue orders
the ids, an array gives the key of an id, and a stack recycles the ids of removed keys; with the
JDK, a `HashMap` holds the current priority of every key and the `PriorityQueue` skips entries
that are no longer current. `LongLongIndexedHeap` was faster in all 6 cells against each of them.
In absolute numbers at 1,400,000 keys: `timers` 285 ns per step (stencil workaround 344 ns,
fastutil 427 ns, JDK 846 ns), `dijkstra` 812 ns per node (1,117, 1,208, and 2,452 ns); at
180,000 keys `timers` 73 ns per step (99, 144, and 993 ns). Memory per key: 36 bytes, the
stencil workaround 52 bytes, the fastutil workaround 46 bytes, the JDK 122 bytes.

**Lists** (`int` elements, random values; geometric mean over 1,400, 180,000, and 1,400,000
elements; `build`: append all elements to a list with the default capacity, per element; `get`:
reads at random indexes; `iterate`: sum of all elements with the library's `forEach`, per
element; `insertRemove`: insert at a random index, then remove at another, per step)

| Comparison | Memory | build | get | iterate | insertRemove | Run |
|---|---|---:|---:|---:|---:|---|
| `IntArrayList` / fastutil `IntArrayList` | more | 1.10 | 1.02 | 1.01 | 0.96 | v26 |
| `IntArrayList` / HPPC `IntArrayList` | same | 0.99 | 1.05 | 1.01 | 0.98 | v26 |
| `IntArrayList` / Eclipse `IntArrayList` | more | 1.25 | 1.02 | 1.00 | 1.00 | v26 |
| `IntArrayList` / Speiger `IntArrayList` | more | 1.13 | 0.99 | 1.01 | 0.97 | v26 |
| `IntArrayList` / `ArrayList<Integer>` | less | 0.40 | 0.41 | 0.17 | 0.69 | v26 |

All primitive lists read, iterate, and move elements at the speed of the memory; HPPC reads
about 5% faster because it checks the index against the size only with `assert`. `build`
depends on where the size falls in each library's growth sequence: stencil's (16, 24, 36, ...)
has to grow shortly before 1,400, 180,000, and 1,400,000 elements, while Eclipse's lands just
above them, so Eclipse copies and allocates about a third less there (4.4 against 5.9 bytes per
element). At 250,000 elements, `IntArrayList` builds fastest (0.85x the time of Eclipse; diagnosis
run `20261124-diag-list-build`). Small lists are a different matter: on Windows, building 1,000
to 2,000 elements takes 1.3-1.6x the time of the fastest library and 1.25-1.4x that of HPPC,
although both allocate the same 24.8 KB per list of 2,000 elements (GC profiler), and the C2 code
of the loop (PrintAssembly) is no longer than HPPC's. The gap does not depend on the loop
alignment (16, 32, and 64 bytes) or on the JDK (Zulu 25.0.5 and 25.0.2, OpenJDK 28-ea), and it
does not exist on Linux: under WSL with OpenJDK 25.0.5, `IntArrayList` and HPPC build equally
fast, also in a profile with `perfasm`, which shows the same distribution of the time over the
loop, the growth, and the copy routine. The cause is not known. Memory per element: stencil and
HPPC 5.9 bytes, fastutil and Speiger 5.3, Eclipse 4.4, `ArrayList<Integer>` 21.3.

**Deques** (`int` elements; geometric mean over 1,400, 180,000, and 1,400,000 elements; `fifo`:
on a queue of `size` elements, add one at the end and remove the first, per step; `stack`: add
`size` elements at the end of a new deque, then remove them all from the end, per element;
`window`: add elements at the end and remove the first 64 after every 64, per element)

| Comparison | Memory | fifo | stack | window | Run |
|---|---|---:|---:|---:|---|
| `IntArrayDeque` / fastutil `IntArrayFIFOQueue` | less | 0.74 | 0.66 | 0.83 | diag, v27 |
| `IntArrayDeque` / HPPC `IntArrayDeque` | more | 1.16 | 0.94 | 1.04 | diag, v27 |
| `IntArrayDeque` / Speiger `IntArrayFIFOQueue` | less | 0.87 | 0.78 | 0.86 | diag, v27 |
| `IntArrayDeque` / `ArrayDeque<Integer>` | less | 0.26 | 0.25 | 0.25 | diag, v27 |

`fifo` and `window` come from the diagnosis run `20261124-diag-deque-fifo` (four warmup
iterations, five rounds), `stack` from v27. In v27, with two warmup iterations, single forks of
`fifo` and `window` took up to three times as long for stencil, fastutil, and Speiger (not for
HPPC), so its medians of two rounds were not reliable; with the longer warmup, the values are
stable. `window` removes the 64 elements with one `discardFirst(64)` in stencil and one by one
in the other libraries. In absolute numbers: `fifo` 1.67-1.75 ns per step (HPPC 1.46-1.52,
fastutil 2.34-2.37, `ArrayDeque` 5.7-7.8 ns). Memory per element: stencil 5.8-6.0 bytes,
fastutil and Speiger 7.3-7.5, HPPC 4.8-4.9, `ArrayDeque<Integer>` 21.3-21.4.

**Maps with object keys** (`String` keys, `int` values; `count`: `addTo(key, 1)` on keys with
Zipf-like ranks; geometric mean over the three sizes 1,400 to 1,400,000, see
[Benchmarks](#benchmarks))

| Comparison | Memory | Keys | getHit | getMiss | build | churn | count | Run |
|---|---|---|---:|---:|---:|---:|---:|---|
| `ObjectIntHashMap` / fastutil `Object2IntOpenHashMap` | more | RANDOM | 0.80 | 0.35 | 0.70 | 0.53 | 0.84 | v12 |
| | | SEQUENTIAL | 0.68 | 0.38 | 0.71 | 0.54 | 0.83 | |
| `ObjectIntHashMap` / HPPC `ObjectIntHashMap` | more | RANDOM | 0.86 | 0.36 | 0.77 | 0.55 | 0.88 | v12 |
| | | SEQUENTIAL | 0.73 | 0.39 | 0.79 | 0.58 | 0.82 | |
| `ObjectIntHashMap` / Eclipse Collections `ObjectIntHashMap` | less | RANDOM | 0.75 | 1.15 | 0.64 | 0.92 | 0.70 | v12 |
| | | SEQUENTIAL | 0.69 | 1.39 | 0.95 | 1.24 | 0.64 | |
| `ObjectIntHashMap` / `HashMap<String,Integer>` | less | RANDOM | 1.10 | 2.64 | 0.62 | 1.25 | 0.79 | v12 |
| | | SEQUENTIAL | 0.90 | 2.91 | 0.85 | 1.83 | 0.84 | |

Memory of the maps with object keys, deep size per entry at 1,400,000 entries (arrays, nodes,
and boxed values with their headers; the `String` keys are not counted): `ObjectIntHashMap`
18.0 bytes, fastutil and HPPC 12.0 bytes, Eclipse Collections 24.0 bytes,
`HashMap<String,Integer>` 54.0 bytes.

**Thread-safe maps** (`long` keys, object values, RANDOM keys; geometric mean over the three
sizes 1,400 to 1,400,000; scores are ns per operation and thread, so the ratios compare the
throughput at the same number of threads; `mixed`: 14 lookups, one insertion and one removal of
16 operations, `writeHeavy`: 2 of 4 lookups; see [Benchmarks](#benchmarks))

| Comparison | Memory | Threads | getHit | getMiss | mixed | writeHeavy | computeIfAbsent | build | Run |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| `LongObjectConcurrentHashMap` / `ConcurrentHashMap<Long,Object>` | less | 1 | 0.75 | 1.44 | 0.59 | 0.73 | 0.77 | 0.43 | v13 |
| | | 6 | 0.76 | 1.07 | 0.96 | 1.11 | 1.01 | 0.48 | |
| | | 12 | 0.70 | 0.92 | 0.78 | 1.04 | 0.90 | 0.43 | |
| `LongObjectConcurrentHashMap` / JCTools `NonBlockingHashMapLong` | less | 1 | 1.63 | 3.13 | 0.62 | 0.14 | 2.75 | 0.27 | v13 |
| | | 6 | 1.55 | 2.91 | 1.04 | 0.09 | 3.70 | 0.11 | |
| | | 12 | 1.34 | 2.55 | 0.61 | 0.15 | 3.26 | 0.09 | |
| `LongObjectConcurrentHashMap` / Speiger `Long2ObjectConcurrentOpenHashMap` | less | 1 | 0.84 | 0.82 | 0.80 | 0.82 | 0.86 | 0.77 | v13 |
| | | 6 | 0.49 | 0.55 | 0.62 | 0.70 | 0.50 | 0.75 | |
| | | 12 | 0.51 | 0.58 | 0.51 | 0.56 | 0.42 | 0.72 | |
| `IntObjectConcurrentHashMap` / `ConcurrentHashMap<Integer,Object>` | less | 1 | 1.03 | 1.67 | 0.85 | 0.71 | 0.80 | 0.35 | v13a |
| | | 12 | 0.76 | 1.03 | 0.93 | 1.08 | 0.80 | 0.39 | |
| `IntObjectConcurrentHashMap` / JCTools `NonBlockingHashMapLong` | less | 1 | 1.30 | 2.94 | 0.61 | 0.11 | 2.74 | 0.21 | v13a |
| | | 12 | 1.31 | 1.96 | 0.69 | 0.11 | 3.15 | 0.08 | |
| `IntObjectConcurrentHashMap` / Speiger `Int2ObjectConcurrentOpenHashMap` | less | 1 | 0.78 | 0.95 | 0.85 | 0.76 | 0.82 | 0.80 | v13a |
| | | 12 | 0.47 | 0.57 | 0.48 | 0.60 | 0.45 | 0.72 | |

**Thread-safe maps with primitive values** (counters: `long` keys and values, and `int` keys and
values; `count`: every thread adds one to present keys with Zipf-like ranks; otherwise as
above). Runs v14 and v14a measured `addTo` under the segment's lock; for counting since `addTo`
works without lock, see the table below this one.

| Comparison | Memory | Threads | getHit | getMiss | mixed | writeHeavy | count | build | Run |
|---|---|---:|---:|---:|---:|---:|---:|---:|---|
| `LongLongConcurrentHashMap` / `ConcurrentHashMap<Long,Long>` (`merge`) | less | 1 | 0.65 | 1.55 | 0.51 | 0.63 | 0.21 | 0.40 | v14 |
| | | 6 | 0.57 | 1.40 | 0.84 | 0.92 | 0.77 | 0.42 | |
| | | 12 | 0.52 | 1.24 | 0.73 | 0.80 | 0.85 | 0.37 | |
| `LongLongConcurrentHashMap` / `ConcurrentHashMap<Long,LongAdder>` | less | 1 | 0.57 | 1.65 | 0.51 | 0.59 | 0.46 | 0.36 | v14 |
| | | 6 | 0.54 | 1.54 | 0.84 | 0.87 | 1.66 | 0.39 | |
| | | 12 | 0.50 | 1.25 | 0.70 | 0.71 | 2.01 | 0.41 | |
| `LongLongConcurrentHashMap` / JCTools `NonBlockingHashMapLong<AtomicLong>` | less | 1 | 1.05 | 3.68 | 0.58 | 0.17 | 1.13 | 0.22 | v14 |
| | | 6 | 0.95 | 3.46 | 0.48 | 0.12 | 4.34 | 0.12 | |
| | | 12 | 0.87 | 3.16 | 0.21 | 0.08 | 5.02 | 0.10 | |
| `LongLongConcurrentHashMap` / Speiger `Long2LongConcurrentOpenHashMap` | less | 1 | 0.81 | 0.96 | 0.82 | 0.81 | 1.12 | 0.75 | v14 |
| | | 6 | 0.40 | 0.70 | 0.60 | 0.64 | 1.22 | 0.74 | |
| | | 12 | 0.42 | 0.75 | 0.49 | 0.50 | 1.16 | 0.72 | |
| `LongLongConcurrentHashMap` / trivago `ConcurrentLongLongMap` | same | 1 | 0.56 | 0.76 | 0.66 | 0.71 | 0.56 | 0.94 | v14 |
| | | 6 | 0.30 | 0.55 | 0.45 | 0.54 | 0.95 | 0.90 | |
| | | 12 | 0.26 | 0.49 | 0.29 | 0.36 | 0.88 | 0.91 | |
| `IntIntConcurrentHashMap` / `ConcurrentHashMap<Integer,Integer>` (`merge`) | less | 1 | 0.82 | 2.17 | 0.85 | 0.61 | 0.31 | 0.32 | v14a |
| | | 12 | 0.65 | 1.23 | 0.81 | 0.81 | 0.91 | 0.28 | |
| `IntIntConcurrentHashMap` / `ConcurrentHashMap<Integer,LongAdder>` | less | 1 | 0.74 | 2.31 | 0.71 | 0.47 | 0.57 | 0.27 | v14a |
| | | 12 | 0.64 | 1.21 | 0.73 | 0.78 | 2.40 | 0.32 | |
| `IntIntConcurrentHashMap` / JCTools `NonBlockingHashMapLong<AtomicInteger>` | less | 1 | 1.01 | 3.05 | 0.65 | 0.19 | 1.09 | 0.17 | v14a |
| | | 12 | 0.87 | 2.25 | 0.34 | 0.10 | 4.71 | 0.07 | |
| `IntIntConcurrentHashMap` / Speiger `Int2IntConcurrentOpenHashMap` | less | 1 | 0.74 | 1.10 | 0.85 | 0.69 | 1.06 | 0.80 | v14a |
| | | 12 | 0.40 | 0.56 | 0.46 | 0.50 | 1.26 | 0.70 | |
| `IntIntConcurrentHashMap` / trivago `ConcurrentIntIntMap` | same | 1 | 0.60 | 0.91 | 0.78 | 0.70 | 0.63 | 1.00 | v14a |
| | | 12 | 0.27 | 0.42 | 0.26 | 0.31 | 0.97 | 0.86 | |

**Counting since `addTo` works without lock** (`long` keys and values; `countSpread`: like
`count`, but on uniformly sampled present keys; experiment v16, 2 rounds)

| Comparison | Threads | count | countSpread | getHit | mixed | Run |
|---|---:|---:|---:|---:|---:|---|
| `LongLongConcurrentHashMap` / `ConcurrentHashMap<Long,LongAdder>` | 1 | 0.37 | 0.39 | 0.63 | 0.48 | v16 |
| | 12 | 0.70 | 0.44 | 0.55 | 0.71 | |
| `LongLongConcurrentHashMap` / JCTools `NonBlockingHashMapLong<AtomicLong>` | 1 | 0.95 | 0.90 | 0.97 | 0.54 | v16 |
| | 12 | 1.65 | 1.27 | 0.85 | 0.43 | |
| `LongLongConcurrentHashMap` / Speiger `Long2LongConcurrentOpenHashMap` | 1 | 1.00 | 1.02 | 0.81 | 0.75 | v16 |
| | 12 | 0.44 | 0.60 | 0.43 | 0.50 | |

Memory of the thread-safe counters at 1,400,000 entries (deep size per entry, boxes, adders and
atomic values included): `LongLongConcurrentHashMap` 24.0 bytes (trivago 24.0, Speiger 36.0,
JCTools 60.0, `ConcurrentHashMap` with `Long` values 86.0, with `LongAdder` values 94.0);
`IntIntConcurrentHashMap` 12.0 bytes (trivago 12.0, Speiger 24.0, JCTools 52.0,
`ConcurrentHashMap` 70.0, with `LongAdder` values 86.0).

Memory of the thread-safe maps, deep size per entry at 1,400,000 entries (the shared value
objects are not counted, the boxed keys are): `LongObjectConcurrentHashMap` 18.0 bytes,
`IntObjectConcurrentHashMap` 12.0 bytes, Speiger 30.0 / 24.0 bytes (`long` / `int` keys),
JCTools 36.0 bytes, `ConcurrentHashMap<Long,Object>` 62.0 bytes,
`ConcurrentHashMap<Integer,Object>` 54.0 bytes.

Runs: v3 = `20261122-v3-block`, v4 = `20261122-v4-compact`, v6 = `20261122-v6-compact-hyb`,
v7 = `20261122-v7-long`, v8 = `20261122-v8-long-compact`, v9 = `20261122-v9-intset` and
`20261122-v9-longset`, v10 = `20261123-v10-intobj` and `20261123-v10-longobj`, v11 =
`20261123-v11-sorted` (warmup and measurement iterations of 500 ms instead of 1 s). The
experiments behind the design of the sorted maps are in `20261123-v11a-tune` (node capacities),
`20261123-v11b-branchless` and `20261123-v11c-search` (search within a node), and
`20261123-v11d-append` (append fast path); the diagnosis runs of the G1 costs in
`20261123-diag-sorted-g1`, `-diag-sorted-parallel`, and `-diag-sorted-gclog`. v12 =
`20261123-v12-objectint` (sizes 1,400 to 1,400,000 only, iterations of 500 ms). v13 =
`20261123-v13-longconc` (1, 6, and 12 threads, 3 rounds), v13a = `20261123-v13a-intconc` (1 and
12 threads, 2 rounds), both with iterations of 500 ms. The single-threaded lookup experiments
behind the design of the thread-safe maps are in `20261123-v13b-lookup-variants` (lookup without
version check, without probe bound, both), `20261123-v13c-flat-tables` (table arrays on the
map), and `20261123-v13d-lf50` (load factor 0.5). v14 = `20261123-v14-longlongconc` (1, 6, and 12
threads, 3 rounds), v14a = `20261123-v14a-intintconc` (1 and 12 threads, 2 rounds). The experiment
with `addTo` without lock is `20261124-v15-addto` (`long` → `long`; lock, counter per segment,
counter stripes per thread, JCTools, `LongAdder`; 2 rounds). v16 = `20261124-v16-longlongconc`
(after `addTo` without lock: `count`, `countSpread`, `getHit`, `mixed` with 1 and 12 threads
against `LongAdder` values, JCTools, and Speiger; 2 rounds). The layout experiment for the
insertion-ordered maps is `20261124-v17-longlinked-layouts` (dense entries against a link word
per slot, both as experiment classes; 2 rounds), v18 = `20261124-v18-longlinked` (the generated
`LongObjectLinkedHashMap`; sizes 1,400 to 1,400,000, RANDOM keys, 3 rounds, Windows). v19 =
`20261124-v19-longlongsorted` (`LongLongTreeMap` against fastutil's and Speiger's red-black
trees and `TreeMap<Long,Long>`; sizes 1,400 to 1,400,000, 2 rounds, warmup and measurement
iterations of 500 ms, Windows). v20 = `20261124-v20-longsetsorted` (`LongTreeSet` against
fastutil's and Speiger's red-black trees and `TreeSet<Long>`; sizes 1,400 to 1,400,000, 2 rounds,
warmup and measurement iterations of 500 ms, Windows). v21 = `20261124-v21-objectintlinked`
(`ObjectIntLinkedHashMap` against fastutil's and Speiger's `Object2IntLinkedOpenHashMap` and
`LinkedHashMap<String,Integer>`; sizes 1,400 to 1,400,000, RANDOM and SEQUENTIAL keys, 2 rounds,
warmup and measurement iterations of 500 ms, Windows); the diagnosis of `count` in
`20261124-diag-count` (180,000 and 1,400,000 entries, RANDOM keys, 3 rounds; implementations
`stencil-hash`, `fastutil-hash`, and `stencil-shuffled` of the suite). The experiments on the
arity of the priority queues are `20261124-v22a-heap-arity` and `20261124-v22b-heap-arity-loop`
(RANDOM values, 2 rounds each), `20261124-diag-hold-sequential` (`hold` only, 180,000 and
1,400,000 values, both distributions, 3 rounds), and v22c = `20261124-v22c-heap-robust` (the
4-ary and 8-ary candidates, all operations, both distributions, 2 rounds). v22 and v23 =
`20261124-v22-longheap` and `20261124-v23-intlongindexedheap` measured the first, 8-ary version.
v24 = `20261124-v24-longheap` (`LongHeap` against fastutil's and Speiger's
`LongHeapPriorityQueue` and `PriorityQueue<Long>`), v25 = `20261124-v25-intlongindexedheap`
(`IntLongIndexedHeap` against fastutil's `LongHeapIndirectPriorityQueue` and `PriorityQueue` with
lazy deletion); all with sizes 1,400 to 1,400,000, RANDOM and SEQUENTIAL, 2 rounds, warmup and
measurement iterations of 500 ms, Windows. v26 = `20261124-v26-intlist` (`IntArrayList` against
the lists of fastutil, HPPC, Eclipse, and Speiger and `ArrayList<Integer>`) and v27 =
`20261124-v27-intdeque` (`IntArrayDeque` against fastutil's and Speiger's `IntArrayFIFOQueue`,
HPPC's `IntArrayDeque`, and `ArrayDeque<Integer>`): sizes 1,400 to 1,400,000, RANDOM, 2 rounds,
500 ms, Windows. The diagnosis of the list `build`: `20261124-diag-list-build` (sizes 1,000 to
250,000, 3 rounds), `20261124-diag-list-build-gc` (GC profiler), `20261124-diag-list-build-asm`
(PrintAssembly), `20261124-diag-align-default`, `-align-32`, and `-align-64` (loop alignment),
`20261124-diag-list-build-jdk28` and `-zulu2502` (other JDKs), and under WSL
`20261124-diag-list-build-perfasm` (`perfasm` with `cpu-clock` samples) and
`20261124-diag-list-build-wsl`; of the deque: `20261124-diag-deque-fifo` (`fifo` and `window`,
four warmup iterations, 5 rounds). v28a = `20261124-v28a-longlongindexedheap`
(`LongLongIndexedHeap` against the workarounds with stencil, fastutil, and the JDK): sizes 1,400
to 1,400,000, RANDOM, 2 rounds, 500 ms, Windows. Its first attempt,
`20261124-v28-longlongindexedheap`, is not used: other work on the machine slowed its last 24
forks by up to 3x.

Caveat for `build` and `computeIfAbsent` at 11,000,000 entries in v3 to v10: one invocation
takes one to two seconds there, so a measurement iteration holds a single invocation, and a G1
pause for the garbage of earlier invocations (up to about 2 s) can double the value. The
allocation pattern of each implementation is deterministic, so the same implementation was hit
in every round. Example: v10 reported 178 ns per insert for `LongObjectHashMap` with sequential
keys (fastutil 110, HPPC 101); measured in one process with GC pauses excluded, it is 59 ns
(fastutil 98, HPPC 88-103). These cells therefore shift the `build` and `computeIfAbsent`
columns by a few percent in either direction. Since then, the build benchmarks run a full
collection before every invocation at 10,000,000 entries and above, outside the measured time
(`GcBeforeLargeBuild`).

What the numbers say:

- **Random keys, equal memory:** the linear-probing classes are on par with fastutil and HPPC
  for `int` keys (within about 10 %, except lookups of absent keys in `IntIntHashMap`, where the
  packed 8-byte slots scan half as many keys per cache line as a separate key array). With
  `long` keys, the interleaved layout (key and value in the same cache line) makes hits and
  updates 5-20 % faster, but large tables are slower than HPPC on absent keys, because every
  probe step covers 16 bytes instead of 8.
- **Sequential (dense) keys:** Fibonacci hashing spreads consecutive keys almost evenly over
  the table, so few of them collide: at the benchmark fill of 0.69, a hit needs 1.0-1.2 probes
  on average instead of about 2.1 for random keys (simulated with the same hash function and
  per-instance seeds). The mixing functions of fastutil and HPPC scatter such keys like random
  ones. This is where the large factors come from (0.2-0.5).
- **Load factor 0.5 vs. Eclipse Collections** (fixed load factor 0.5, so the same memory):
  faster with random keys in every operation. With sequential keys, Eclipse Collections keeps
  dense keys next to each other (its first probe uses the key itself) and wins clearly on maps
  for absent keys and inserts; its sets do not show this advantage.
- **Object values:** all four libraries use the same layout here (a key array and a parallel
  `Object[]`), so with random keys the results are close (within about 10 %, `LongObjectHashMap`
  up to 25 % faster than fastutil on lookups). Inserts are dominated by the reference stores
  into the large `Object[]` and by growth; with load factor 0.5 (and in Eclipse Collections),
  `build` and `computeIfAbsent` take up to twice as long from 180,000 entries on.
- **Compact layouts:** lookups of absent keys take about half the time of linear probing at the
  same memory, and random hits cost the same. Inserts and updates (`addTo`, `build`) are
  15-35 % slower, and with sequential keys they lose the single-probe advantage of Fibonacci
  hashing (about 2x slower). Use them for large tables with many lookups of absent keys, not as
  the default.
- **Sorted maps:** the B+ tree beats the binary trees of fastutil and the JDK in every lookup
  cell: hits take about half the time or less, absent keys and `floor` a quarter to a third
  (a fifth at 1,400,000 entries). Iteration takes about 1/20 of the time and range scans a
  fifth to a third, because consecutive entries share cache lines instead of living in
  separate node objects. From 180,000 entries on, random inserts are faster as well (almost
  2x at 11,000,000), and ascending builds take 12-17 ns per entry with the append fast path.
  Weak spots: small maps under inserts and removals (at 1,400 entries, random builds and the
  sliding-window churn are about 2x slower than the best binary tree), the ascending build at
  1,400,000 entries (on par, against 0.14-0.36 at the other sizes), and `removeRange`, which is
  on par with `TreeMap` because the benchmark inserts the 64 removed entries again one by one.
  The first two are costs of the G1 collector, not of the tree (diagnosis runs with one round,
  `stencil` and `TreeMap` only, ns per operation, G1 vs. `-XX:+UseParallelGC`):

  | Cell | `LongObjectTreeMap` G1 | `LongObjectTreeMap` Parallel | `TreeMap` G1 | `TreeMap` Parallel |
  |---|---:|---:|---:|---:|
  | build, RANDOM, 1,400 | 154 | 92 | 76 | 83 |
  | churn, RANDOM, 1,400 | 254 | 146 | 212 | 218 |
  | churn, SEQUENTIAL, 1,400 | 127 | 55 | 92 | 97 |
  | build, SEQUENTIAL, 1,400,000 | 52 | 11 | 105 | 141 |

  - *Write barriers:* every insert or removal shifts part of a leaf's `Object[]` of values,
    and G1 runs its post-write barrier for these reference stores (card marking and enqueuing
    for the concurrent refinement). The churn allocates nothing at all, yet takes about 100 ns
    longer per step under G1. A binary tree relinks a single node instead and hardly pays for
    this. The layout keeps the values next to their sorted keys, so this cost cannot be avoided
    without giving up the layout.
  - *Humongous allocations:* the node pools are a few large arrays. G1 allocates every array
    larger than half a region (1 MB with a 4 GB heap) as a humongous object, and the growing
    pools of a 1,400,000-entry map allocate about 65 MB of them per build; in the benchmark,
    which builds such maps over and over, this triggers collections with pauses of up to 450 ms
    (`G1 Humongous Allocation` in the GC log). An application that builds a large map once
    hardly notices; creating it with `new LongObjectTreeMap<>(expectedSize)` avoids the growth
    steps altogether. At 11,000,000 entries the benchmark runs a full collection before every
    build, which is why that size is not affected.
- **Object keys:** the stored hashes decide. fastutil and HPPC store none, so every slot on a
  probe chain costs them an `equals` call on a key object somewhere in memory: lookups of
  absent keys take about three times as long as in `ObjectIntHashMap` and churn twice as long,
  at a third less memory. Eclipse Collections (load factor 0.5, a third more memory than
  stencil) is close for absent keys and churn, slower in everything else. Against
  `HashMap<String,Integer>`, which also stores hashes but needs three times the memory,
  `ObjectIntHashMap` is faster at building and counting, at 1,400,000 entries also for hits,
  and from 180,000 entries on for churn with random keys; `HashMap` stays clearly ahead for absent keys (about five
  slots per lookup with linear probing, while about half of `HashMap`'s lookups end at an empty
  bucket) and for churn in small maps. With SEQUENTIAL keys, `HashMap` keeps consecutive
  numbers in neighboring buckets, so the absent keys of the benchmark stay in the cache there.
  `count`, the counter use case, is the fastest of all five libraries in every cell.
- **Thread-safe maps:** the segments with lookups without locks pay off against the other
  designs with locks: Speiger's segments take a read lock for every lookup and lose by up to 2x
  with 12 threads; `ConcurrentHashMap` needs a node and a boxed key per entry (three times the
  memory) and loses on lookups of present keys and on builds, while updates under contention
  (`writeHeavy`, `computeIfAbsent` on hot keys) are about even. JCTools reads faster, especially
  absent keys (2-3x), because its lock-free table needs no validation and is kept sparse (twice
  the memory); its writes leave tombstones and trigger copies of the table, so `writeHeavy` is
  7-11x and `build` 4-12x slower than the segmented map. Single-threaded, a lookup costs up to
  twice as much as in the unsynchronized `LongObjectHashMap` (see the design decisions).
- **Thread-safe counters:** with key and value in one array, a lookup of a present key costs as
  much as in JCTools with one thread and less with 12, and 1.5-2x less than in
  `ConcurrentHashMap`; absent keys stay 2-3.7x slower than in JCTools' sparse table. Insertions
  and removals (`mixed`, `writeHeavy`, `build`) are faster than in all other maps (builds about
  even with trivago's wrapper). Counting under the segment's lock (v14) lost 4-5x against JCTools
  when 6 or 12 threads counted the same few keys; since `addTo` on a present key works without
  lock (v15, v16), counting hot keys from 12 threads is 1.65x slower than JCTools (which
  increments an `AtomicLong` and needs more than twice the memory), 1.4x faster than
  `ConcurrentHashMap` with `LongAdder` values, and more than twice as fast as Speiger's maps.
- **Not measured yet:** `int` keys with `long`/`double` values (same code paths as the
  `long -> long` maps), other value types, sorted maps with `int` keys, with value types
  other than `long`, or at 11,000,000 entries with primitive values, sorted sets with `int`
  elements or at 11,000,000 elements, fastutil's AVL trees in the suites with primitive values
  and sets, maps with object keys
  at 11,000,000 entries and with `long`/`double` values, insertion-ordered maps with object keys
  and `long`/`double` values or at 11,000,000 entries, priority queues of `int` and `double`
  values, indexed priority queues with `int` and `double` priorities, lists and deques of `long`
  and `double` elements, thread-safe maps at 11,000,000 entries and with SEQUENTIAL keys, other
  CPUs, and Linux (most of the runs since experiment v2 are Windows only).

## Build

The build requires JDK 25 or later (`JAVA_HOME`), the tests run on it; the artifacts run on
JDK 21 or later (`maven.compiler.release` is 21, untested there). Maven comes with the wrapper.

```powershell
.\mvnw.cmd -B install         # Windows: generate sources, run all tests, bench\target\benchmarks.jar
./mvnw -B install             # Linux/macOS
```

`install` (rather than `verify`) also puts the modules into the local Maven repository, which
projects that use the annotation processor need. The generated example classes are checked in
under `core\src\generated\java`, in the package `net.generated.example`; the build regenerates
them, so a template change shows up in their diff. The generated tests are in
`core\target\generated-test-sources\stencil`. Another package (written to `target`, so that the
checked-in classes stay untouched):
`.\mvnw.cmd -B -pl core clean verify -Dstencil.example.package=com.example.coll "-Dstencil.generated.main=target/generated-sources/stencil"`
(the `bench` module imports the classes from the default package).
To change a generated class, edit its template.

The main sources of `generator`, `annotations`, `processor`, and `core` (including the
generated classes) are compiled with `-Xdoclint:all/protected -Werror`: the Javadoc of every
public and protected element must be complete (`@param`, `@return`, `@throws`) and well-formed,
otherwise the build fails. `javadoc -Xdoclint:all` on these modules reports no warnings.

## Benchmarks

| Benchmark | Measures |
|-----------|----------|
| `getHit`  | 65,536 lookups of present keys in random order |
| `getMiss` | 65,536 lookups of absent keys in random order |
| `addTo`   | 65,536 increments of present keys |
| `build`   | Building a map with `size` entries from the default capacity (reported divided by `size`) |
| `buildPresized` | Like `build`, but the map is created for `size` entries, so it never grows (optional, not in the default set) |
| `churn`   | 65,536 steps of "remove the oldest key, insert a new one" at constant size (steady state, including tombstone cleanup) |
| `floor`, `iterate`, `rangeScan`, `removeRange` | Sorted maps and sets only, see below |

Parameters: `size` ∈ {1,400, 180,000, 1,400,000, 11,000,000} (L1 → L3 → RAM),
`dist` ∈ {RANDOM, SEQUENTIAL}. Each size is about 2/3 of a power of two: maps with load
factor 0.75 run near their maximum fill, maps with load factor 0.5 already use twice the
memory. Each implementation has its own benchmark class, and every parameter combination
runs in its own fork, so all call sites are monomorphic.

From 10,000,000 entries on, the build benchmarks run a full garbage collection before every
invocation, outside the measured time, so that a single G1 pause for earlier garbage does not
land in a measurement that consists of one invocation (`GcBeforeLargeBuild`).

The build benchmarks use a different key set in each invocation (at least 2^20 keys in
total, in rotation). Rebuilding the same small map from the same keys over and over lets the
branch predictor memorize the complete sequence of probe outcomes. Only maps with a
deterministic hash function profit from that: with 1,400 random keys, identical insert code
took about 3 ns per insert with a fixed hash seed and about 8 ns with a per-instance random
seed.

Implementations: `stencil` (load factor 0.75), `stencil-lf50` (load factor 0.5, same memory
as Eclipse Collections), `stencil-block` and `stencil-block-hash` (`IntIntBlockHashMap` with
identity or hashed home slots, load factor 0.75), `stencil-compact` (`IntIntCompactHashMap`, load
factor 0.85, same table size as `stencil` at the benchmark sizes), `fastutil`, `hppc`, `eclipse`,
`jdk` (boxed `HashMap`). With `--suite=long`, the runner measures the `long -> long` maps
under the same names (`stencil`, `stencil-lf50`, `stencil-compact`, `fastutil`, `hppc`, `eclipse`, `jdk`), with 64-bit
keys (RANDOM: murmur3 `fmix64` of 1..n). With `--suite=intset` or `--suite=longset`, it
measures the sets (`IntHashSet`/`LongHashSet` as `stencil` and `stencil-lf50`, `LongCompactHashSet`
as `stencil-compact`, and the sets of the other libraries) on the same key data, with the
operations `containsHit`, `containsMiss`, `build`, and `churn` (the set counterparts of the map
benchmarks; `buildPresized` is also available). With `--suite=intobj` or `--suite=longobj`, it
measures the maps with object values (`IntObjectHashMap`/`LongObjectHashMap` as `stencil` and
`stencil-lf50`, and the corresponding classes of the other libraries) with `getHit`, `getMiss`,
`build`, `churn`, and `computeIfAbsent` (grouping: one inserting and one finding call per
distinct key, reported per key; HPPC uses its index API, Eclipse Collections
`getIfAbsentPutWithKey`). The values come from a small pool of shared objects, and lookups do not
dereference them, so the numbers show the maps, not the value objects. The runner
also writes `<label>-footprint.csv` with the table bytes of every map, and the report shows
bytes per entry and the effective fill next to the timings.

With `--suite=longsorted`, it measures the sorted maps: `stencil` (`LongObjectTreeMap`),
`fastutil-rb` and `fastutil-avl` (`Long2ObjectRBTreeMap`, `Long2ObjectAVLTreeMap`), and `jdk`
(`TreeMap<Long,Object>`). SEQUENTIAL keys are the even numbers, so that absent keys lie between
present ones. Besides `getHit`, `getMiss`, `build` (random or ascending order), and `churn`
(with SEQUENTIAL keys a sliding window), the operations are `floor` (greatest key `<=` an absent
key; fastutil has no floor method and uses its key iterator from a given key), `iterate` (all
entries in key order, per entry), `rangeScan` (scans of 64 consecutive entries from random start
keys, per entry; `subMap` views for fastutil and the JDK), and `removeRange` (removing 64
consecutive entries as one range and inserting them again, per entry). The footprint of this
suite is the deep size (nodes, boxed keys, and arrays with headers; values excluded), which needs
`--add-opens java.base/java.util=ALL-UNNAMED` for the JDK map (the scripts pass it). The
experiment variants of the node capacities and of the search within a node were measured with
this suite (see the design decisions above).

With `--suite=longlongsorted`, it measures the sorted maps from `long` keys to `long` values
with the same operations: `stencil` (`LongLongTreeMap`), `fastutil-rb` and `fastutil-avl`
(`Long2LongRBTreeMap`, `Long2LongAVLTreeMap`), `speiger` (its `Long2LongRBTreeMap`), and `jdk`
(`TreeMap<Long,Long>`). The lookups return the sum of the values found, and the scans add up
keys and values, so every operation reads the values. The values lie outside the `Long` cache,
so the JDK map holds a boxed value per entry, and its footprint counts the boxed keys and
values.

With `--suite=longsetsorted`, it measures the sorted sets of `long` elements with the same
operations and names: `stencil` (`LongTreeSet`), `fastutil-rb` and `fastutil-avl`
(`LongRBTreeSet`, `LongAVLTreeSet`), `speiger` (its `LongRBTreeSet`), and `jdk`
(`TreeSet<Long>`). `getHit` and `getMiss` call `contains` and return the number of elements
found, `churn` removes and adds elements, and the scans add up the elements. The footprint
counts the boxed elements of the JDK set.

With `--suite=longlinked`, it measures the insertion-ordered maps from `long` keys to object
values: `stencil` (`LongObjectLinkedHashMap`), `fastutil` and `speiger` (their
`Long2ObjectLinkedOpenHashMap`), and `jdk` (`LinkedHashMap<Long,Object>`), by default with the
sizes 1,400 to 1,400,000 and RANDOM keys. Besides `getHit`, `getMiss`, and `build`, the
operations are `iterate` (all entries in insertion order, per entry), `churn` (remove a random
present key, insert a new one), `fifo` (remove the oldest entry, insert a new one), and `touch`
(store a value for a random present key and make its entry the newest: `putLast`,
`putAndMoveToLast`). In Speiger Primitive-Collections 1.0.1, `putAndMoveToLast` and
`moveToLast` leave the links inconsistent after a few calls and then fail with an
`ArrayIndexOutOfBoundsException`, so its adapter removes the key and inserts it again. The
footprint of this suite is the deep size without the shared value objects.

With `--suite=objectintlinked`, it measures the insertion-ordered maps from `String` keys to
`int` values: `stencil` (`ObjectIntLinkedHashMap`), `fastutil` and `speiger` (their
`Object2IntLinkedOpenHashMap`), and `jdk` (`LinkedHashMap<String,Integer>`), by default with the
sizes 1,400 to 1,400,000 and both key distributions. The keys are those of `--suite=objectint`,
the operations those of `--suite=longlinked` plus `count`; `churn` puts the removed keys back
into a ring of absent keys, from which it takes the inserted ones, and `fifo` slides through a
cyclic pool of keys, so that no string is created during a measurement. Speiger's adapter
removes and inserts for `touch` here as well: its `putAndMoveToLast` fails in the same way. The
footprint counts the boxed values of the JDK map, not the `String` keys.

With `--suite=longheap`, it measures the priority queues of `long` values: `stencil`
(`LongHeap`), `fastutil` and `speiger` (their `LongHeapPriorityQueue`), and `jdk`
(`PriorityQueue<Long>`), with `build`, `sort`, and `hold` (see `AbstractLongHeapBench`), by
default with the sizes 1,400 to 1,400,000 and both value distributions. The experiment classes of
the arity (`arity2`, `arity4`, `arity4-bottomup`, `arity8-bottomup`, `arity4-bottomup-loop`,
`arity8`, `arity16-bottomup`, `arity4-loop`) run only when named in `--impls`. With
`--suite=intlongindexedheap`, it measures the indexed priority queues: `stencil`
(`IntLongIndexedHeap`), `fastutil` (`LongHeapIndirectPriorityQueue`), and `jdk`
(`PriorityQueue` of records with lazy deletion), with `dijkstra` and `sort` (see
`AbstractIntLongIndexedHeapBench`). With `--suite=longlongindexedheap`, it measures the indexed
priority queues with arbitrary `long` keys: `stencil` (`LongLongIndexedHeap`), `stencil-dense`
(`LongIntHashMap` from keys to dense ids with `IntLongIndexedHeap`), `fastutil`
(`Long2IntOpenHashMap` with `LongHeapIndirectPriorityQueue`), and `jdk` (`PriorityQueue` of
records and `HashMap<Long, Long>`, lazy deletion), with `dijkstra` and `timers` (see
`AbstractLongLongIndexedHeapBench`), by default with RANDOM only. The footprint of these suites is
the deep size of a full queue, with the maps of the workarounds.

With `--suite=intlist`, it measures the lists of `int` elements: `stencil` (`IntArrayList`),
`fastutil`, `hppc`, `eclipse`, and `speiger` (their `IntArrayList`), and `jdk`
(`ArrayList<Integer>`), with `build`, `get`, `iterate`, and `insertRemove` (see
`AbstractIntListBench`). With `--suite=intdeque`, it measures the double-ended queues:
`stencil` (`IntArrayDeque`), `fastutil` and `speiger` (their `IntArrayFIFOQueue`), `hppc`
(`IntArrayDeque`), and `jdk` (`ArrayDeque<Integer>`), with `fifo`, `stack`, and `window` (see
`AbstractIntDequeBench`); fastutil's queue can neither iterate nor read by index, so these are
not compared. Both suites use the sizes 1,400 to 1,400,000 and random values only by default;
the footprint is the deep size of a full collection, with the boxed `Integer` elements of the JDK
collections.

With `--suite=objectint`, it measures the maps from `String` keys to `int` values: `stencil`
(`ObjectIntHashMap`), `fastutil` (`Object2IntOpenHashMap`), `hppc` and `eclipse` (their
`ObjectIntHashMap`), and `jdk` (`HashMap<String,Integer>`). RANDOM keys are the hex digits of
64-bit random numbers, SEQUENTIAL keys the decimal digits of 0..n-1 (similar hash codes). All
strings and their hash codes are created during setup; lookups use equal copies of the keys, not
the same objects, so every hit compares the characters. Besides `getHit`, `getMiss`, `build`,
and `churn`, the operation `count` increments present keys by one, with ranks drawn from a
Zipf-like distribution (density proportional to `1/rank`), as when counting words or events per
category (`addTo`, `addToValue` for Eclipse Collections, `merge` for the JDK map). The footprint
of this suite is the deep size without the keys, but with the boxed values of the JDK map.

With `--suite=intconc` and `--suite=longconc`, it measures the thread-safe maps from `int` or
`long` keys to object values with several threads: `stencil` (`IntObjectConcurrentHashMap`,
`LongObjectConcurrentHashMap`), `jdk` (`ConcurrentHashMap<Integer,Object>`,
`ConcurrentHashMap<Long,Object>`), `jctools` (JCTools `NonBlockingHashMapLong`, lock-free, also
for `int` keys), and `speiger` (Speiger Primitive-Collections `Int2ObjectConcurrentOpenHashMap`,
`Long2ObjectConcurrentOpenHashMap`, created with as many segments as the stencil map uses).
`--threads=1,6,12` (the default of these suites) adds the number of threads as a dimension of the
cells; every thread count is measured in every round. The operations: `getHit` and `getMiss`
(all threads look up keys), `mixed` (of 16 operations, 14 lookups, one insertion of a new key and
one removal of the thread's oldest key, so the size stays constant), `writeHeavy` (the same with
2 lookups of 4 operations), `computeIfAbsent` (a cache: keys with Zipf-like ranks, and every 16th
operation removes a key with the same distribution), and `build` (that many worker threads fill
a new map from the default capacity). Scores are ns per operation and thread; the report adds
the total throughput of all threads. The defaults of these suites are the sizes 1,400 to
1,400,000 and RANDOM keys. The footprint is the deep size without the shared values, but with
the boxed keys of `ConcurrentHashMap`; it needs `--add-opens` for `java.util.concurrent`,
`java.util.concurrent.locks`, and `java.util.concurrent.atomic` (the scripts pass them).

With `--suite=intintconc` and `--suite=longlongconc`, it measures thread-safe maps with `int`
or `long` keys and values, as counters: `stencil` (`IntIntConcurrentHashMap`,
`LongLongConcurrentHashMap`), `jdk` (`ConcurrentHashMap` with boxed keys and values, counting
with `merge`), `jdk-adder` (`ConcurrentHashMap` with `LongAdder` values, counting with
`computeIfAbsent(key, k -> new LongAdder()).increment()`, the usual idiom under contention),
`jctools` (`NonBlockingHashMapLong` with `AtomicInteger`/`AtomicLong` values), `speiger`
(`Int2IntConcurrentOpenHashMap`, `Long2LongConcurrentOpenHashMap` with `addTo`), and `trivago`
(`fastutil-concurrent-wrapper` in blocking mode with as many buckets as stencil has segments;
it has no `addTo`, so it counts with `computeIfPresent`). The operations are those of the
other concurrent suites, with `count` instead of `computeIfAbsent`: every thread adds one to
present keys with Zipf-like ranks, so all threads keep counting the same few hot keys.

### Interleaved Execution (important)

Do **not** use `java -jar benchmarks.jar`. JMH runs benchmarks one after another in
alphabetical order. On a laptop that heats up and throttles, the implementation measured last
is systematically at a disadvantage.

`net.collections.stencil.bench.InterleavedRunner` uses a *randomized complete block design* instead:
- A **cell** is one (op, dist, size, threads) combination. Each **round** visits all cells in random order.
- Within a cell, each implementation runs exactly one fork, back to back and also in random order.
- `analyze.py` computes the ratios **paired** within a block and summarizes them over the rounds,
  so slow drift cancels out. Drift diagnostics (per round and per position within a block) show
  how much the machine throttled.

Options: `--suite=int|long|intset|longset|intobj|longobj|longsorted|longlongsorted|longsetsorted|longlinked|objectint|objectintlinked|longheap|intlongindexedheap|intlist|intdeque|intconc|longconc|intintconc|longlongconc --rounds=5 --impls=… --ops=… --sizes=… --dists=… --threads=… --wi=2 --w=1000 --i=2 --r=1000 --seed=42`
(`w`/`r` in ms). For diagnosis: `--prof=gc,stack` runs JMH profilers in every fork (numbers to
`<label>-prof.csv`, text to `<label>-prof.txt`; options after a colon, as in
`--prof=perfasm:events=cpu-clock`, which works under WSL, where `perf` has no hardware
counters), and `--jvmArgs=-XX:+AlwaysPreTouch,…` adds JVM
options to every fork. Profiled runs are not meant for timing comparisons. Duration ≈ rounds × cells × implementations × ~5 s. Default:
5 × 40 × 9 × 5 s ≈ 150 min per platform (restrict with `--impls` and `--rounds`). The scripts take
the JDK from `JAVA_HOME` (or `--java-home`).

```powershell
python scripts\run_bench.py                # Windows, then WSL, then analyze.py -> results\<timestamp>\report.md + plots\
python scripts\run_bench.py --name smoke -- --rounds=1 --sizes=1400 --wi=1 --w=200 --i=1 --r=200
python scripts\run_bench.py --platforms=windows --out=results\x -- --rounds=3 --impls=stencil,fastutil
```

Arguments after `--` go to the runner on both platforms; `python scripts\run_bench.py --help`
lists the other options (WSL distribution, Python for `analyze.py`, `--no-analyze`).

```bash
bash scripts/run-bench-wsl.sh /mnt/e/.../results/x [runner arguments]   # WSL only
```

```powershell
python scripts\analyze.py results\<run>     # regenerate report + plots (needs pandas and matplotlib)
```

Progress with remaining time: `results\<run>\windows.log`, or `~/stencil-bench/wsl.log` under WSL.

Measurement notes:
- Run Windows and WSL **one after the other**. WSL2 shares the CPU with Windows.
- Laptop: plug in the power supply, and don't use the machine during a run.
- Optional, to reduce throttling itself: disable turbo boost (Windows power options:
  "Maximum processor state" = 99 %). The clock is then lower but much more stable.
- The JDKs differ: Zulu 25.0.5 (Windows) and Ubuntu OpenJDK 25.0.5 (WSL), both HotSpot.
  To compare only the operating systems, use a Zulu Linux JDK under WSL (`JAVA=/path/bin/java`).
