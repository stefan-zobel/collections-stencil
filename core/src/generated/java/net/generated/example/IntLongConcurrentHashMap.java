// Generated from template concurrent-wide/KTypeVTypeConcurrentHashMap.java. Edit the template, not this file.
package net.generated.example;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.LongBinaryOperator;
import java.util.function.IntToLongFunction;

import net.generated.example.function.IntLongConsumer;
import net.generated.example.function.IntLongPredicate;

/**
 * Thread-safe open-addressing hash map from {@code int} keys to {@code long} values, for
 * example counters or sums per key that many threads update.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Segments:</b> as in {@link IntObjectConcurrentHashMap}: a fixed, power-of-two
 *       number of segments (the {@code concurrencyLevel} rounded up; by default four per
 *       available processor, at most 256), selected by the high bits of a key's hash. Each
 *       segment is a linear-probing table with backward-shift deletion and grows on its own.</li>
 *   <li><b>Interleaved layout:</b> one {@code long[]} per segment holds the key of slot
 *       {@code s} at index {@code 2s} and the value bits at {@code 2s + 1}, as in
 *       {@link IntLongHashMap}: key and value share a cache line, so a lookup reads one
 *       line, and the memory per mapping is the same as in the single-threaded map.</li>
 *   <li><b>Keys</b> are stored sign-extended to 64 bits.</li>
 *   <li><b>Lookups do not lock.</b> Each segment has a version counter (a seqlock): an update
 *       makes it odd while it writes to the table and even again afterwards. A lookup reads the
 *       version, probes, and accepts the result only if the version is still the same and
 *       even; otherwise it retries, and after a few failed attempts it takes the lock.</li>
 *   <li><b>Updates</b> take the lock of their segment, a mutex whose state lives in the
 *       segment object; every segment ends with 64 bytes of padding, so that neighboring
 *       segments never share a cache line. A segment builds a larger table aside, so lookups
 *       never wait for a rehash.</li>
 *   <li><b>Counting without lock:</b> {@link #addTo} on a present key adds to the value
 *       word with one atomic instruction, without the lock and without changing the version,
 *       so many threads can count the same keys. Before, it announces itself in a counter of
 *       the segment and then checks that the lock is free; an update takes the lock first and
 *       then waits until no such adder is active (a Dekker handshake: either the update sees
 *       the adder, or the adder sees the lock and takes it as well). While an adder is active,
 *       no update moves or copies the table, and lookups read a value word atomically. A new
 *       key and key {@code 0} are added under the lock.</li>
 *   <li><b>Atomic updates:</b> {@link #merge}, {@link #computeIfAbsent} and the other
 *       updates hold the segment's lock, so a function is called at most once. A function that
 *       updates the same segment of this map gets an {@link IllegalStateException}
 *       ("recursive update").</li>
 *   <li><b>Aggregate operations:</b> {@link #forEach}, {@link #removeIf}, the conversions,
 *       {@code equals}, {@code hashCode} and {@code toString} copy one segment at a time and
 *       work on the copy, so the action may update the map.</li>
 *   <li><b>Empty marker:</b> a slot whose key is {@code 0} is empty; key {@code 0} is stored
 *       outside the table, in its segment.</li>
 *   <li><b>Small maps:</b> a segment allocates its table on the first insertion, but every
 *       segment has an object and a lock, so an empty map takes several kilobytes with the
 *       default concurrency level. For many small maps, pass a lower
 *       {@code concurrencyLevel}.</li>
 * </ul>
 * See {@link IntLongConcurrentMap} for the contract. Iteration order is unspecified and
 * differs between instances.
 */
public final class IntLongConcurrentHashMap implements IntLongConcurrentMap {

    /** Default maximum ratio of occupied slots to capacity, per segment. */
    public static final float DEFAULT_LOAD_FACTOR = 0.75f;

    /** Default number of entries the map can hold without resizing. */
    public static final int DEFAULT_EXPECTED_SIZE = 16;

    /** Maximum number of segments. */
    public static final int MAX_CONCURRENCY_LEVEL = 1 << 16;

    /** Maximum number of segments chosen by default. */
    private static final int MAX_DEFAULT_SEGMENTS = 256;

    /** 2^64 / golden ratio, odd. */
    private static final long PHI = 0x9E37_79B9_7F4A_7C15L;
    private static final int MIN_CAPACITY = 4;
    /** In slots; a table has twice as many words. */
    private static final int MAX_CAPACITY = 1 << 29;

    /** Optimistic attempts of a lookup before it takes the lock. */
    private static final int OPTIMISTIC_ATTEMPTS = 16;

    /** Shared table of segments that have not inserted a key yet; never written. */
    private static final long[] EMPTY_TABLE = new long[2 * MIN_CAPACITY];

    private static final VarHandle VERSION;
    private static final VarHandle SIZE;
    private static final VarHandle ADDERS;
    /** Atomic access to the words of a table. */
    private static final VarHandle WORD = MethodHandles.arrayElementVarHandle(long[].class);

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            VERSION = lookup.findVarHandle(Segment.class, "version", int.class);
            SIZE = lookup.findVarHandle(Segment.class, "size", int.class);
            ADDERS = lookup.findVarHandle(Segment.class, "adders", int.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final float loadFactor;
    private final long seed;
    /** The segments; the length is a power of two. */
    private final Segment[] segments;
    /** {@code 64 - log2(segments.length)}; the segment is {@code (h >>> segmentShift) & segmentMask}. */
    private final int segmentShift;
    private final int segmentMask;

    /**
     * An empty map with room for {@link #DEFAULT_EXPECTED_SIZE} mappings, the default load
     * factor {@link #DEFAULT_LOAD_FACTOR} and the default concurrency level.
     */
    public IntLongConcurrentHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR, defaultConcurrencyLevel());
    }

    /**
     * An empty map with room for {@code expectedSize} mappings, the default load factor
     * {@link #DEFAULT_LOAD_FACTOR} and the default concurrency level.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public IntLongConcurrentHashMap(int expectedSize) {
        this(expectedSize, DEFAULT_LOAD_FACTOR, defaultConcurrencyLevel());
    }

    /**
     * An empty map with room for {@code expectedSize} mappings at the given load factor, with
     * {@code concurrencyLevel} segments (rounded up to a power of two).
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @param loadFactor the maximum fill of each segment's table, in {@code (0, 1)}
     * @param concurrencyLevel the number of segments, in
     *                         {@code [1, }{@value #MAX_CONCURRENCY_LEVEL}{@code ]}: about the
     *                         number of threads that can update the map at the same time
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large,
     *         {@code loadFactor} is not in {@code (0, 1)}, or {@code concurrencyLevel} is not in
     *         {@code [1, }{@link #MAX_CONCURRENCY_LEVEL}{@code ]}
     */
    public IntLongConcurrentHashMap(int expectedSize, float loadFactor, int concurrencyLevel) {
        if (expectedSize < 0) {
            throw new IllegalArgumentException("expectedSize must be >= 0: " + expectedSize);
        }
        if (!(loadFactor > 0f && loadFactor < 1f)) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1): " + loadFactor);
        }
        if (concurrencyLevel < 1 || concurrencyLevel > MAX_CONCURRENCY_LEVEL) {
            throw new IllegalArgumentException("concurrencyLevel must be in [1, " + MAX_CONCURRENCY_LEVEL + "]: "
                    + concurrencyLevel);
        }
        int count = concurrencyLevel == 1 ? 1 : Integer.highestOneBit(concurrencyLevel - 1) << 1;
        int segmentBits = Integer.numberOfTrailingZeros(count);
        this.loadFactor = loadFactor;
        this.seed = ThreadLocalRandom.current().nextLong();
        this.segmentShift = 64 - segmentBits;
        this.segmentMask = count - 1;
        this.segments = new Segment[count];
        int capacity = segmentCapacity(expectedSize, count, loadFactor);
        for (int i = 0; i < count; i++) {
            segments[i] = new PaddedSegment(seed, segmentBits, loadFactor, capacity);
        }
    }

    /**
     * A map with the mappings of {@code other} (any implementation), the default load factor
     * and the default concurrency level.
     *
     * @param other the map to copy (any implementation)
     */
    public IntLongConcurrentHashMap(IntLongMap other) {
        this(other.size());
        putAll(other);
    }

    /**
     * A map with the mappings of {@code other} (any implementation), the default load factor
     * and the default concurrency level.
     *
     * @param other the map to copy (any implementation)
     */
    public IntLongConcurrentHashMap(IntLongConcurrentMap other) {
        this(other.size());
        putAll(other);
    }

    /** Four segments per available processor, rounded up to a power of two, at most 256. */
    private static int defaultConcurrencyLevel() {
        int wanted = 4 * Runtime.getRuntime().availableProcessors();
        return Math.min(MAX_DEFAULT_SEGMENTS, Integer.highestOneBit(wanted - 1) << 1);
    }

    /**
     * Capacity of each of {@code count} segments for {@code expectedSize} mappings in total:
     * the mean share plus three standard deviations, because the keys do not spread evenly.
     */
    private static int segmentCapacity(int expectedSize, int count, float loadFactor) {
        double share = expectedSize / (double) count;
        double withSlack = Math.ceil(share + 3 * Math.sqrt(share));
        return capacityFor((int) Math.min(withSlack, MAX_CAPACITY), loadFactor);
    }

    // ------------------------------------------------------------------
    // Value encoding / hashing (the type-specific core)
    // ------------------------------------------------------------------

    /** The value as stored in the value word. */
    private static long bits(long value) {
        return value;
    }

    /** The value stored in a value word. */
    private static long valueOf(long bits) {
        return bits;
    }

    /** The key stored in a key word. */
    private static int keyOf(long word) {
        return (int) word;
    }

    /** Whether two values are equal in the sense of {@link Long#equals}. */
    private static boolean same(long a, long b) {
        return a == b;
    }

    /** The 64-bit hash of {@code key}; its high bits select the segment, the next ones the slot. */
    private static long hash(long key, long seed) {
        return (key ^ seed) * PHI;
    }

    private Segment segmentFor(long h) {
        return segments[(int) (h >>> segmentShift) & segmentMask];
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Override
    public int size() {
        long sum = 0;
        for (Segment s : segments) {
            sum += (int) SIZE.getOpaque(s);
        }
        return (int) Math.min(sum, Integer.MAX_VALUE);
    }

    @Override
    public boolean isEmpty() {
        for (Segment s : segments) {
            if ((int) SIZE.getOpaque(s) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean containsKey(int key) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
            int v = (int) VERSION.getAcquire(s);
            if ((v & 1) == 0) {
                boolean found = key == 0 ? s.hasZeroKey : find(s.table, key, h, s.segmentBits, s.shift) >= 0;
                VarHandle.loadLoadFence();
                if ((int) VERSION.getOpaque(s) == v) {
                    return found;
                }
            }
            Thread.onSpinWait();
        }
        s.lock();
        try {
            return s.locate(key, h) >= 0;
        } finally {
            s.unlock();
        }
    }

    @Override
    public long get(int key) {
        return getOrDefault(key, 0L);
    }

    @Override
    public long getOrDefault(int key, long defaultValue) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
            int v = (int) VERSION.getAcquire(s);
            if ((v & 1) == 0) {
                long result;
                if (key == 0) {
                    result = s.hasZeroKey ? s.zeroValue : defaultValue;
                } else {
                    final long[] tab = s.table;
                    int i = find(tab, key, h, s.segmentBits, s.shift);
                    // opaque: an adder may change the value word at any time, atomically
                    result = i >= 0 ? valueOf((long) WORD.getOpaque(tab, i + 1)) : defaultValue;
                }
                VarHandle.loadLoadFence();
                if ((int) VERSION.getOpaque(s) == v) {
                    return result;
                }
            }
            Thread.onSpinWait();
        }
        s.lock();
        try {
            int i = s.locate(key, h);
            return i >= 0 ? s.valueAt(i) : defaultValue;
        } finally {
            s.unlock();
        }
    }

    /**
     * Probes {@code tab} for {@code key != 0} without lock: the index of its key word, or
     * {@code -1}. The fields may change at any time, so the result is only valid if the version
     * did not change, but this method must not fail or loop forever on any intermediate state.
     */
    private static int find(long[] tab, long key, long h, int segmentBits, int shift) {
        final int mask = tab.length - 2;
        final int start = (int) ((h << segmentBits) >>> shift) & mask;
        int i = start;
        for (;;) {
            long k = tab[i];
            if (k == key) {
                return i;
            }
            if (k == 0) {
                return -1;
            }
            i = (i + 2) & mask;
            if (i == start) { // a full round without an empty slot: only while the table changes
                return -1;
            }
        }
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    public long put(int key, long value) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i >= 0) {
                long old = s.valueAt(i);
                s.replaceAt(i, value);
                return old;
            }
            s.insertAt(~i, key, h, value);
            return 0L;
        } finally {
            s.unlock();
        }
    }

    @Override
    public boolean putIfAbsent(int key, long value) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i >= 0) {
                return false;
            }
            s.insertAt(~i, key, h, value);
            return true;
        } finally {
            s.unlock();
        }
    }

    @Override
    public boolean replace(int key, long value) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0) {
                return false;
            }
            s.replaceAt(i, value);
            return true;
        } finally {
            s.unlock();
        }
    }

    @Override
    public boolean replace(int key, long expectedValue, long newValue) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0 || !same(s.valueAt(i), expectedValue)) {
                return false;
            }
            s.replaceAt(i, newValue);
            return true;
        } finally {
            s.unlock();
        }
    }

    @Override
    public long remove(int key) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            return i < 0 ? 0L : s.removeAt(i);
        } finally {
            s.unlock();
        }
    }

    @Override
    public boolean remove(int key, long value) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0 || !same(s.valueAt(i), value)) {
                return false;
            }
            s.removeAt(i);
            return true;
        } finally {
            s.unlock();
        }
    }

    @Override
    public long addTo(int key, long increment) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        if (key != 0) {
            // Without lock for a present key: announce the adder, then check the lock (see the
            // class documentation); lockForUpdate waits for the adders that saw it free.
            ADDERS.getAndAdd(s, 1);
            try {
                if (!s.isLocked()) {
                    final long[] tab = s.table;
                    int i = find(tab, key, h, s.segmentBits, s.shift);
                    if (i >= 0) {
                        return addAt(tab, i + 1, increment);
                    }
                }
            } finally {
                ADDERS.getAndAdd(s, -1);
            }
        }
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0) {
                s.insertAt(~i, key, h, increment);
                return increment;
            }
            long v = s.valueAt(i) + increment;
            s.replaceAt(i, v);
            return v;
        } finally {
            s.unlock();
        }
    }

    /** Atomically adds {@code increment} to the value word {@code tab[j]}; returns the new value. */
    private static long addAt(long[] tab, int j, long increment) {
        return (long) WORD.getAndAdd(tab, j, increment) + increment;
    }

    @Override
    public long merge(int key, long value, LongBinaryOperator remappingFunction) {
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0) {
                s.insertAt(~i, key, h, value);
                return value;
            }
            // The function cannot update this segment (recursive update), so i stays valid.
            long v = remappingFunction.applyAsLong(s.valueAt(i), value);
            s.replaceAt(i, v);
            return v;
        } finally {
            s.unlock();
        }
    }

    @Override
    public long computeIfAbsent(int key, IntToLongFunction mappingFunction) {
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i >= 0) {
                return s.valueAt(i);
            }
            long v = mappingFunction.applyAsLong(key);
            s.insertAt(~i, key, h, v);
            return v;
        } finally {
            s.unlock();
        }
    }

    @Override
    public void clear() {
        for (Segment s : segments) {
            s.lockForUpdate();
            try {
                s.clear();
            } finally {
                s.unlock();
            }
        }
    }

    @Override
    public void ensureCapacity(int expectedSize) {
        int capacity = segmentCapacity(expectedSize, segments.length, loadFactor);
        for (Segment s : segments) {
            s.lockForUpdate();
            try {
                if (2 * capacity > s.table.length) {
                    s.rehash(capacity);
                }
            } finally {
                s.unlock();
            }
        }
    }

    // ------------------------------------------------------------------
    // Iteration and bulk removal
    // ------------------------------------------------------------------

    @Override
    public void forEach(IntLongConsumer action) {
        Objects.requireNonNull(action, "action");
        for (Segment s : segments) {
            Snapshot snap = s.snapshot();
            if (snap == null) {
                continue;
            }
            if (snap.hasZeroKey()) {
                action.accept(0, snap.zeroValue());
            }
            final long[] tab = snap.table();
            for (int i = 0; i < tab.length; i += 2) {
                int k = keyOf(tab[i]);
                if (k != 0) {
                    action.accept(k, valueOf(tab[i + 1]));
                }
            }
        }
    }

    @Override
    public boolean removeIf(IntLongPredicate predicate) {
        Objects.requireNonNull(predicate, "predicate");
        boolean removed = false;
        for (Segment s : segments) {
            Snapshot snap = s.snapshot();
            if (snap == null) {
                continue;
            }
            // Test outside the lock, then remove the matches whose value is still the same.
            int[] matchKeys = new int[8];
            long[] matchValues = new long[8];
            int n = 0;
            if (snap.hasZeroKey() && predicate.test(0, snap.zeroValue())) {
                matchKeys[n] = 0;
                matchValues[n++] = snap.zeroValue();
            }
            final long[] tab = snap.table();
            for (int i = 0; i < tab.length; i += 2) {
                int k = keyOf(tab[i]);
                if (k != 0 && predicate.test(k, valueOf(tab[i + 1]))) {
                    if (n == matchKeys.length) {
                        matchKeys = Arrays.copyOf(matchKeys, n * 2);
                        matchValues = Arrays.copyOf(matchValues, n * 2);
                    }
                    matchKeys[n] = k;
                    matchValues[n++] = valueOf(tab[i + 1]);
                }
            }
            if (n == 0) {
                continue;
            }
            s.lockForUpdate();
            try {
                for (int j = 0; j < n; j++) {
                    int k = matchKeys[j];
                    int i = s.locate(k, hash(k, seed));
                    if (i >= 0 && same(s.valueAt(i), matchValues[j])) {
                        s.removeAt(i);
                        removed = true;
                    }
                }
            } finally {
                s.unlock();
            }
        }
        return removed;
    }

    /**
     * Equal to any {@link IntLongConcurrentMap} with the same mappings. Weakly consistent
     * under concurrent updates.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntLongConcurrentMap other) || other.size() != size()) {
            return false;
        }
        for (Segment s : segments) {
            Snapshot snap = s.snapshot();
            if (snap == null) {
                continue;
            }
            if (snap.hasZeroKey() && !containsEntry(other, 0, snap.zeroValue())) {
                return false;
            }
            final long[] tab = snap.table();
            for (int i = 0; i < tab.length; i += 2) {
                int k = keyOf(tab[i]);
                if (k != 0 && !containsEntry(other, k, valueOf(tab[i + 1]))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether {@code map} maps {@code key} to a value equal to {@code value}. */
    private static boolean containsEntry(IntLongConcurrentMap map, int key, long value) {
        return map.containsKey(key) && same(map.get(key), value);
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int[] h = {0};
        forEach((k, v) -> h[0] += Integer.hashCode(k) ^ Long.hashCode(v));
        return h[0];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size(), 1 << 16) * 16 + 2).append('{');
        forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v);
        });
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static int maxFill(int capacity, float loadFactor) {
        // Always keep at least one empty slot so that probing terminates.
        return Math.min(capacity - 1, Math.max(1, (int) (capacity * (double) loadFactor)));
    }

    /** Capacity in slots (a power of two) for {@code expectedSize} entries. */
    static int capacityFor(int expectedSize, float loadFactor) {
        long needed = (long) Math.ceil(expectedSize / (double) loadFactor) + 1;
        if (needed > MAX_CAPACITY) {
            throw new IllegalArgumentException("expectedSize too large: " + expectedSize);
        }
        int cap = needed <= MIN_CAPACITY ? MIN_CAPACITY : Integer.highestOneBit((int) needed - 1) << 1;
        // make sure maxFill(cap) >= expectedSize despite rounding
        while (maxFill(cap, loadFactor) < expectedSize && cap < MAX_CAPACITY) {
            cap <<= 1;
        }
        return cap;
    }

    /** Copy of one segment: its table and key {@code 0}. */
    private record Snapshot(long[] table, boolean hasZeroKey, long zeroValue) {
    }

    /**
     * One segment: a linear-probing table with its lock and its version counter.
     * <p>
     * The lock is a non-reentrant mutex on the state of the synchronizer, so it lives in the
     * segment object itself; {@link PaddedSegment} adds the padding behind the fields.
     * <p>
     * Updates run under the lock and change the table only between {@link #beginWrite} and
     * {@link #endWrite}. An index is the table index of a slot's key word: {@code i >= 0} for a
     * present key, {@code ~i} for the empty slot of an absent key, and {@code table.length} for
     * the virtual slot of key {@code 0}. It stays valid as long as the lock is held and the
     * segment is not changed in between.
     */
    @SuppressWarnings("serial") // never serialized
    static class Segment extends AbstractQueuedSynchronizer {

        private final long seed;
        final int segmentBits;
        private final float loadFactor;
        /** Capacity of the first table, allocated on the first insertion. */
        private final int initialCapacity;

        /** Odd while an update changes the table; accessed through {@link #VERSION}. */
        int version;
        /** Number of active adders without lock; accessed through {@link #ADDERS}. */
        int adders;
        /** Number of mappings, including key {@code 0}; read without lock through {@link #SIZE}. */
        int size;

        /** Interleaved keys and value bits; {@link #EMPTY_TABLE} until the first insertion. */
        long[] table = EMPTY_TABLE;
        /** {@code 64 - log2(table.length)}; the key word of the home slot is {@code (h << segmentBits) >>> shift}, rounded down to even. */
        int shift = 64 - Integer.numberOfTrailingZeros(EMPTY_TABLE.length);
        boolean hasZeroKey;
        long zeroValue;

        /** Number of occupied slots in {@link #table}; only accessed under the lock. */
        private int assigned;
        /** Maximum of {@link #assigned} before the table grows; {@code 0} for the shared empty table. */
        private int maxFill;

        Segment(long seed, int segmentBits, float loadFactor, int initialCapacity) {
            this.seed = seed;
            this.segmentBits = segmentBits;
            this.loadFactor = loadFactor;
            this.initialCapacity = initialCapacity;
        }

        // ------------------------------------------------------------------
        // Lock
        // ------------------------------------------------------------------

        @Override
        protected final boolean tryAcquire(int ignored) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        @Override
        protected final boolean tryRelease(int ignored) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        @Override
        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final void lock() {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
            } else {
                acquire(1);
            }
        }

        final void unlock() {
            release(1);
        }

        /**
         * Takes the lock for an update and waits until no adder without lock is active; a thread
         * that already holds the lock is inside a function of an atomic operation.
         */
        void lockForUpdate() {
            if (isHeldExclusively()) {
                throw new IllegalStateException("recursive update");
            }
            lock();
            // the adders that saw the lock free finish; new ones see it taken
            while ((int) ADDERS.getVolatile(this) != 0) {
                Thread.onSpinWait();
            }
        }

        /** Whether some thread holds the lock. */
        final boolean isLocked() {
            return getState() != 0;
        }

        // ------------------------------------------------------------------
        // Copies without lock
        // ------------------------------------------------------------------

        /** A copy of the mappings, or {@code null} if the segment is empty. */
        Snapshot snapshot() {
            if ((int) SIZE.getOpaque(this) == 0) {
                return null;
            }
            for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
                int v = (int) VERSION.getAcquire(this);
                if ((v & 1) == 0) {
                    long[] tab = table.clone();
                    boolean z = hasZeroKey;
                    long zv = zeroValue;
                    VarHandle.loadLoadFence();
                    if ((int) VERSION.getOpaque(this) == v) {
                        return new Snapshot(tab, z, zv);
                    }
                }
                Thread.onSpinWait();
            }
            lock();
            try {
                return new Snapshot(table.clone(), hasZeroKey, zeroValue);
            } finally {
                unlock();
            }
        }

        // ------------------------------------------------------------------
        // Updates (lock held)
        // ------------------------------------------------------------------

        private void beginWrite() {
            VERSION.setOpaque(this, version + 1);
            VarHandle.storeStoreFence();
        }

        private void endWrite() {
            VERSION.setRelease(this, version + 1);
        }

        /** The even table index of the home slot of a key with hash {@code h}. */
        private int home(long h, int sh) {
            return (int) ((h << segmentBits) >>> sh) & ~1;
        }

        /** For {@code key != 0}: index of {@code key} if present, otherwise {@code ~firstEmptyIndex}. */
        private int probe(long key, long h) {
            final long[] tab = table;
            final int mask = tab.length - 2;
            int i = home(h, shift);
            for (;;) {
                long k = tab[i];
                if (k == key) {
                    return i;
                }
                if (k == 0) {
                    return ~i;
                }
                i = (i + 2) & mask;
            }
        }

        /** Index of {@code key}: {@code >= 0} if present, otherwise the complement of its insertion index. */
        int locate(int key, long h) {
            if (key == 0) {
                int z = table.length;
                return hasZeroKey ? z : ~z;
            }
            return probe(key, h);
        }

        long valueAt(int i) {
            return i == table.length ? zeroValue : valueOf(table[i + 1]);
        }

        void replaceAt(int i, long value) {
            beginWrite();
            if (i == table.length) {
                zeroValue = value;
            } else {
                table[i + 1] = bits(value);
            }
            endWrite();
        }

        /** Inserts an absent key at {@code slot}, the complement of {@link #locate}'s result. */
        void insertAt(int slot, int key, long h, long value) {
            if (slot == table.length) {
                beginWrite();
                hasZeroKey = true;
                zeroValue = value;
                endWrite();
            } else {
                if (assigned >= maxFill) {
                    rehash(table == EMPTY_TABLE ? initialCapacity : nextCapacity());
                    slot = ~probe(key, h);
                }
                beginWrite();
                table[slot] = key;
                table[slot + 1] = bits(value);
                endWrite();
                assigned++;
            }
            size++;
        }

        /** Removes the mapping at index {@code i} and returns its value. */
        long removeAt(int i) {
            long old;
            beginWrite();
            if (i == table.length) {
                old = zeroValue;
                hasZeroKey = false;
                zeroValue = 0L;
            } else {
                old = valueOf(table[i + 1]);
                deleteAt(i);
            }
            endWrite();
            size--;
            return old;
        }

        /** Backward-shift deletion: moves later entries of the probe chain into the gap. */
        private void deleteAt(int gap) {
            final long[] tab = table;
            final int mask = tab.length - 2;
            final int sh = shift;
            int i = gap;
            for (;;) {
                i = (i + 2) & mask;
                long k = tab[i];
                if (k == 0) {
                    break;
                }
                int ideal = home(hash(k, seed), sh);
                // The entry can move into gap iff gap lies on its probe path ideal -> i (cyclically).
                if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                    tab[gap] = k;
                    tab[gap + 1] = tab[i + 1];
                    gap = i;
                }
            }
            tab[gap] = 0L;
            tab[gap + 1] = 0L;
            assigned--;
        }

        void clear() {
            if (size == 0) {
                return;
            }
            beginWrite();
            if (table != EMPTY_TABLE) { // only key 0 present: the shared empty table stays untouched
                Arrays.fill(table, 0L);
            }
            hasZeroKey = false;
            zeroValue = 0L;
            endWrite();
            assigned = 0;
            size = 0;
        }

        private int nextCapacity() {
            if (table.length >= 2 * MAX_CAPACITY) {
                throw new IllegalStateException("Maximum capacity of a segment exceeded: " + MAX_CAPACITY);
            }
            return table.length; // twice the current capacity in slots
        }

        /** Builds the new table aside; lookups use the old one until the array is switched. */
        void rehash(int newCapacity) {
            final long[] old = table;
            final long[] tab = new long[newCapacity << 1];
            final int mask = tab.length - 2;
            final int sh = 64 - Integer.numberOfTrailingZeros(tab.length);
            for (int j = 0; j < old.length; j += 2) {
                long k = old[j];
                if (k != 0) {
                    int i = home(hash(k, seed), sh);
                    while (tab[i] != 0) {
                        i = (i + 2) & mask;
                    }
                    tab[i] = k;
                    tab[i + 1] = old[j + 1];
                }
            }
            beginWrite();
            table = tab;
            shift = sh;
            endWrite();
            maxFill = maxFill(newCapacity, loadFactor);
        }
    }

    /**
     * A segment followed by 64 bytes of padding: the segments are allocated one after the other,
     * and without padding the fields of one segment (version, table) would share a cache line
     * with the lock state of the next one (false sharing).
     */
    @SuppressWarnings({"serial", "unused"}) // never serialized; the fields only take space
    static final class PaddedSegment extends Segment {
        private long p0;
        private long p1;
        private long p2;
        private long p3;
        private long p4;
        private long p5;
        private long p6;
        private long p7;

        PaddedSegment(long seed, int segmentBits, float loadFactor, int initialCapacity) {
            super(seed, segmentBits, loadFactor, initialCapacity);
        }
    }
}
