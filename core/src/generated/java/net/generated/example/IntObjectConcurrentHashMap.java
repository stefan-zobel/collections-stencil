// Generated from template concurrent-object/KTypeObjectConcurrentHashMap.java. Edit the template, not this file.
package net.generated.example;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.function.BiFunction;
import java.util.function.IntFunction;

import net.generated.example.function.IntObjectConsumer;
import net.generated.example.function.IntObjectFunction;
import net.generated.example.function.IntObjectPredicate;

/**
 * Thread-safe open-addressing hash map from {@code int} keys to object values.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li><b>Segments:</b> the map consists of a fixed, power-of-two number of segments (the
 *       {@code concurrencyLevel} rounded up; by default four per available processor, at most
 *       256). The high bits of a key's hash select the segment, the following bits the slot
 *       within it. Each segment is a linear-probing table as in {@link IntObjectHashMap}: a
 *       {@code int[]} of keys and an {@code Object[]} of values, key {@code 0} stored outside
 *       the table, backward-shift deletion without tombstones. A segment grows on its own, so
 *       there is no global resize, and the memory per mapping is the same as in
 *       {@link IntObjectHashMap}.</li>
 *   <li><b>Updates</b> take the lock of their segment. Updates of different segments run in
 *       parallel, updates of the same segment one after the other: a few keys that are updated
 *       all the time by many threads do not scale beyond one segment. The lock lives in the
 *       segment object, and every segment ends with 64 bytes of padding, so that the lock and
 *       the version counter of neighboring segments never share a cache line.</li>
 *   <li><b>Lookups do not lock.</b> Each segment has a version counter (a seqlock): an update
 *       makes it odd while it changes the table, and even again afterwards. A lookup reads the
 *       version, probes, and accepts the result only if the version is still the same and
 *       even; otherwise it retries, and after a few failed attempts it takes the lock. A lookup
 *       only waits while an update of the same segment changes the table, which takes a few
 *       nanoseconds for a single key.</li>
 *   <li><b>Growth:</b> a segment builds its larger table aside while lookups continue on the
 *       old one; only the switch to the new arrays changes the version. Lookups never wait for
 *       a rehash.</li>
 *   <li><b>Atomic operations:</b> {@link #computeIfAbsent}, {@link #computeIfPresent},
 *       {@link #compute} and {@link #merge} hold the segment's lock while their function runs,
 *       so it is called at most once. Lookups are not blocked meanwhile, other updates of the
 *       segment wait. A function that updates the same segment of this map gets an
 *       {@link IllegalStateException} ("recursive update") instead of corrupting the table.
 *       {@link #computeIfAbsent} and {@link #putIfAbsent} look up the key without locking
 *       first.</li>
 *   <li><b>Aggregate operations:</b> {@link #forEach}, {@link #removeIf}, the conversions,
 *       {@code equals}, {@code hashCode} and {@code toString} copy one segment at a time and
 *       work on the copy: nothing is locked while the action runs, and the action may update
 *       the map. {@link #size} adds up the sizes of the segments.</li>
 *   <li><b>Hashing:</b> Fibonacci hashing of {@code key ^ seed} with a per-instance seed, as in
 *       {@link IntObjectHashMap}.</li>
 *   <li><b>Small maps:</b> a segment allocates its table on the first insertion, but every
 *       segment has an object and a lock, so an empty map takes a few kilobytes with the
 *       default concurrency level. For many small maps, pass a lower
 *       {@code concurrencyLevel}.</li>
 * </ul>
 * See {@link IntObjectConcurrentMap} for the contract. Values must not be {@code null}.
 * Iteration order is unspecified and differs between instances.
 *
 * @param <V> the type of the values
 */
public final class IntObjectConcurrentHashMap<V> implements IntObjectConcurrentMap<V> {

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
    private static final int MAX_CAPACITY = 1 << 30;

    /** Optimistic attempts of a lookup before it takes the lock. */
    private static final int OPTIMISTIC_ATTEMPTS = 16;

    /** Shared table of segments that have not inserted a key yet; never written. */
    private static final int[] EMPTY_KEYS = new int[MIN_CAPACITY];
    private static final Object[] EMPTY_VALUES = new Object[MIN_CAPACITY];

    private static final VarHandle VERSION;
    private static final VarHandle SIZE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            VERSION = lookup.findVarHandle(Segment.class, "version", int.class);
            SIZE = lookup.findVarHandle(Segment.class, "size", int.class);
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
    public IntObjectConcurrentHashMap() {
        this(DEFAULT_EXPECTED_SIZE, DEFAULT_LOAD_FACTOR, defaultConcurrencyLevel());
    }

    /**
     * An empty map with room for {@code expectedSize} mappings, the default load factor
     * {@link #DEFAULT_LOAD_FACTOR} and the default concurrency level.
     *
     * @param expectedSize the number of mappings the map can hold without resizing
     * @throws IllegalArgumentException if {@code expectedSize} is negative or too large
     */
    public IntObjectConcurrentHashMap(int expectedSize) {
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
    public IntObjectConcurrentHashMap(int expectedSize, float loadFactor, int concurrencyLevel) {
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
     * @param other the map to copy (any implementation); its values must not be {@code null}
     */
    public IntObjectConcurrentHashMap(IntObjectMap<? extends V> other) {
        this(other.size());
        putAll(other);
    }

    /**
     * A map with the mappings of {@code other} (any implementation), the default load factor
     * and the default concurrency level.
     *
     * @param other the map to copy (any implementation)
     */
    public IntObjectConcurrentHashMap(IntObjectConcurrentMap<? extends V> other) {
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

    /** The 64-bit hash of {@code key}; its high bits select the segment, the next ones the slot. */
    private static long hash(int key, long seed) {
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
        return segmentFor(h).get(key, h) != null;
    }

    @Override
    public V get(int key) {
        long h = hash(key, seed);
        @SuppressWarnings("unchecked")
        V v = (V) segmentFor(h).get(key, h);
        return v;
    }

    @Override
    public V getOrDefault(int key, V defaultValue) {
        V v = get(key);
        return v != null ? v : defaultValue;
    }

    // ------------------------------------------------------------------
    // Updates
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public V put(int key, V value) {
        Objects.requireNonNull(value, "value");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i >= 0) {
                Object old = s.valueAt(i);
                s.replaceAt(i, value);
                return (V) old;
            }
            s.insertAt(~i, key, h, value);
            return null;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V putIfAbsent(int key, V value) {
        Objects.requireNonNull(value, "value");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        Object current = s.get(key, h);
        if (current != null) {
            return (V) current;
        }
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i >= 0) {
                return (V) s.valueAt(i);
            }
            s.insertAt(~i, key, h, value);
            return null;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V replace(int key, V value) {
        Objects.requireNonNull(value, "value");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0) {
                return null;
            }
            Object old = s.valueAt(i);
            s.replaceAt(i, value);
            return (V) old;
        } finally {
            s.unlock();
        }
    }

    @Override
    public boolean replace(int key, V oldValue, V newValue) {
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "newValue");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0 || !oldValue.equals(s.valueAt(i))) {
                return false;
            }
            s.replaceAt(i, newValue);
            return true;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(int key) {
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            return i < 0 ? null : (V) s.removeAt(i);
        } finally {
            s.unlock();
        }
    }

    @Override
    public boolean remove(int key, Object value) {
        if (value == null) {
            return false;
        }
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0 || !value.equals(s.valueAt(i))) {
                return false;
            }
            s.removeAt(i);
            return true;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V computeIfAbsent(int key, IntFunction<? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction, "mappingFunction");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        Object current = s.get(key, h);
        if (current != null) {
            return (V) current;
        }
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i >= 0) {
                return (V) s.valueAt(i);
            }
            // The function cannot update this segment (recursive update), so i stays valid.
            V v = mappingFunction.apply(key);
            if (v != null) {
                s.insertAt(~i, key, h, v);
            }
            return v;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V computeIfPresent(int key, IntObjectFunction<? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            if (i < 0) {
                return null;
            }
            V v = remappingFunction.apply(key, (V) s.valueAt(i));
            if (v == null) {
                s.removeAt(i);
            } else {
                s.replaceAt(i, v);
            }
            return v;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V compute(int key, IntObjectFunction<? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(remappingFunction, "remappingFunction");
        long h = hash(key, seed);
        Segment s = segmentFor(h);
        s.lockForUpdate();
        try {
            int i = s.locate(key, h);
            V v = remappingFunction.apply(key, i >= 0 ? (V) s.valueAt(i) : null);
            if (v == null) {
                if (i >= 0) {
                    s.removeAt(i);
                }
            } else if (i >= 0) {
                s.replaceAt(i, v);
            } else {
                s.insertAt(~i, key, h, v);
            }
            return v;
        } finally {
            s.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public V merge(int key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
        Objects.requireNonNull(value, "value");
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
            V v = remappingFunction.apply((V) s.valueAt(i), value);
            if (v == null) {
                s.removeAt(i);
            } else {
                s.replaceAt(i, v);
            }
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
                if (capacity > s.keys.length) {
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
    @SuppressWarnings("unchecked")
    public void forEach(IntObjectConsumer<? super V> action) {
        Objects.requireNonNull(action, "action");
        for (Segment s : segments) {
            Snapshot snap = s.snapshot();
            if (snap == null) {
                continue;
            }
            if (snap.zeroValue() != null) {
                action.accept(0, (V) snap.zeroValue());
            }
            final int[] ks = snap.keys();
            final Object[] vs = snap.values();
            for (int i = 0; i < ks.length; i++) {
                int k = ks[i];
                if (k != 0) {
                    action.accept(k, (V) vs[i]);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean removeIf(IntObjectPredicate<? super V> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        boolean removed = false;
        for (Segment s : segments) {
            Snapshot snap = s.snapshot();
            if (snap == null) {
                continue;
            }
            // Test outside the lock, then remove the matches whose value is still the same object.
            int[] matchKeys = new int[8];
            Object[] matchValues = new Object[8];
            int n = 0;
            if (snap.zeroValue() != null && predicate.test(0, (V) snap.zeroValue())) {
                matchKeys[n] = 0;
                matchValues[n++] = snap.zeroValue();
            }
            final int[] ks = snap.keys();
            final Object[] vs = snap.values();
            for (int i = 0; i < ks.length; i++) {
                int k = ks[i];
                if (k != 0 && predicate.test(k, (V) vs[i])) {
                    if (n == matchKeys.length) {
                        matchKeys = Arrays.copyOf(matchKeys, n * 2);
                        matchValues = Arrays.copyOf(matchValues, n * 2);
                    }
                    matchKeys[n] = k;
                    matchValues[n++] = vs[i];
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
                    if (i >= 0 && s.valueAt(i) == matchValues[j]) {
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
     * Equal to any {@link IntObjectConcurrentMap} with the same mappings. Weakly consistent
     * under concurrent updates.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IntObjectConcurrentMap<?> other) || other.size() != size()) {
            return false;
        }
        for (Segment s : segments) {
            Snapshot snap = s.snapshot();
            if (snap == null) {
                continue;
            }
            if (snap.zeroValue() != null && !snap.zeroValue().equals(other.get(0))) {
                return false;
            }
            final int[] ks = snap.keys();
            final Object[] vs = snap.values();
            for (int i = 0; i < ks.length; i++) {
                int k = ks[i];
                if (k != 0 && !vs[i].equals(other.get(k))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Same formula as {@link java.util.Map#hashCode()} on boxed entries. */
    @Override
    public int hashCode() {
        int[] h = {0};
        forEach((k, v) -> h[0] += Integer.hashCode(k) ^ v.hashCode());
        return h[0];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(Math.min(size(), 1 << 16) * 16 + 2).append('{');
        forEach((k, v) -> {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(k).append('=').append(v == this ? "(this Map)" : v);
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

    /** Copy of one segment: arrays of the table and the value of key {@code 0} ({@code null} if absent). */
    private record Snapshot(int[] keys, Object[] values, Object zeroValue) {
    }

    /**
     * One segment: a linear-probing table with its lock and its version counter.
     * <p>
     * The lock is a non-reentrant mutex on the state of the synchronizer, so it lives in the
     * segment object itself; {@link PaddedSegment} adds the padding behind the fields.
     * <p>
     * Updates run under the lock and change the table only between {@link #beginWrite} and
     * {@link #endWrite}. Index arguments follow the index handles of {@link IntObjectMap}:
     * {@code i >= 0} is the slot of a present key, {@code ~i} the empty slot for an absent key,
     * and {@code keys.length} the virtual slot of key {@code 0}. An index stays valid as long as
     * the lock is held and the segment is not changed in between.
     */
    @SuppressWarnings("serial") // never serialized
    static class Segment extends AbstractQueuedSynchronizer {

        private final long seed;
        private final int segmentBits;
        private final float loadFactor;
        /** Capacity of the first table, allocated on the first insertion. */
        private final int initialCapacity;

        /** Odd while an update changes the table; accessed through {@link #VERSION}. */
        int version;
        /** Number of mappings, including key {@code 0}; read without lock through {@link #SIZE}. */
        int size;

        /** Keys; the length is the capacity, a power of two. {@link #EMPTY_KEYS} until the first insertion. */
        int[] keys = EMPTY_KEYS;
        /** Values, parallel to {@link #keys}; {@code null} in empty slots. */
        Object[] values = EMPTY_VALUES;
        /** {@code 64 - log2(keys.length)}; the slot is {@code (h << segmentBits) >>> shift}. */
        int shift = 64 - Integer.numberOfTrailingZeros(MIN_CAPACITY);
        /** Value of key {@code 0}, {@code null} if absent. */
        Object zeroValue;

        /** Number of occupied slots in {@link #keys}; only accessed under the lock. */
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

        // ------------------------------------------------------------------
        // Lookups without lock
        // ------------------------------------------------------------------

        /** The value of {@code key}, or {@code null}; optimistic, falls back to the lock. */
        Object get(int key, long h) {
            for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
                int v = (int) VERSION.getAcquire(this);
                if ((v & 1) == 0) {
                    Object result = lookup(key, h);
                    VarHandle.loadLoadFence();
                    if ((int) VERSION.getOpaque(this) == v) {
                        return result;
                    }
                }
                Thread.onSpinWait();
            }
            lock();
            try {
                return lookup(key, h);
            } finally {
                unlock();
            }
        }

        /**
         * Probes for {@code key}. Without the lock the fields may change at any time: the result
         * is only valid if the version did not change, but this method must not fail or loop
         * forever on any intermediate state.
         */
        private Object lookup(int key, long h) {
            if (key == 0) {
                return zeroValue;
            }
            final int[] ks = keys;
            final Object[] vs = values;
            final int mask = ks.length - 1;
            // A rehash may switch the arrays between the two reads above: the value index is
            // masked with the length of its own array, and the version check discards the result.
            final int start = (int) ((h << segmentBits) >>> shift) & mask;
            int i = start;
            for (;;) {
                int k = ks[i];
                if (k == key) {
                    return vs[i & (vs.length - 1)];
                }
                if (k == 0) {
                    return null;
                }
                i = (i + 1) & mask;
                if (i == start) { // a full round without an empty slot: only while the table changes
                    return null;
                }
            }
        }

        /** A copy of the mappings, or {@code null} if the segment is empty. */
        Snapshot snapshot() {
            if ((int) SIZE.getOpaque(this) == 0) {
                return null;
            }
            for (int attempt = 0; attempt < OPTIMISTIC_ATTEMPTS; attempt++) {
                int v = (int) VERSION.getAcquire(this);
                if ((v & 1) == 0) {
                    int[] ks = keys.clone();
                    Object[] vs = values.clone();
                    Object z = zeroValue;
                    VarHandle.loadLoadFence();
                    if ((int) VERSION.getOpaque(this) == v) {
                        return new Snapshot(ks, vs, z);
                    }
                }
                Thread.onSpinWait();
            }
            lock();
            try {
                return new Snapshot(keys.clone(), values.clone(), zeroValue);
            } finally {
                unlock();
            }
        }

        // ------------------------------------------------------------------
        // Updates (lock held)
        // ------------------------------------------------------------------

        /** Takes the lock; a thread that already holds it is inside a function of an atomic operation. */
        void lockForUpdate() {
            if (isHeldExclusively()) {
                throw new IllegalStateException("recursive update");
            }
            lock();
        }

        private void beginWrite() {
            VERSION.setOpaque(this, version + 1);
            VarHandle.storeStoreFence();
        }

        private void endWrite() {
            VERSION.setRelease(this, version + 1);
        }

        private long hash(int key) {
            return (key ^ seed) * PHI;
        }

        /** For {@code key != 0}: slot of {@code key} if present, otherwise {@code ~firstEmptySlot}. */
        private int probe(int key, long h) {
            final int[] ks = keys;
            final int mask = ks.length - 1;
            int i = (int) ((h << segmentBits) >>> shift);
            for (;;) {
                int k = ks[i];
                if (k == key) {
                    return i;
                }
                if (k == 0) {
                    return ~i;
                }
                i = (i + 1) & mask;
            }
        }

        /** Index of {@code key}: {@code >= 0} if present, otherwise the complement of its insertion slot. */
        int locate(int key, long h) {
            if (key == 0) {
                int z = keys.length;
                return zeroValue != null ? z : ~z;
            }
            return probe(key, h);
        }

        Object valueAt(int i) {
            return i == keys.length ? zeroValue : values[i];
        }

        void replaceAt(int i, Object value) {
            beginWrite();
            if (i == keys.length) {
                zeroValue = value;
            } else {
                values[i] = value;
            }
            endWrite();
        }

        /** Inserts an absent key at {@code slot}, the complement of {@link #locate}'s result. */
        void insertAt(int slot, int key, long h, Object value) {
            if (slot == keys.length) {
                beginWrite();
                zeroValue = value;
                endWrite();
            } else {
                if (assigned >= maxFill) {
                    rehash(keys == EMPTY_KEYS ? initialCapacity : nextCapacity());
                    slot = ~probe(key, h);
                }
                beginWrite();
                keys[slot] = key;
                values[slot] = value;
                endWrite();
                assigned++;
            }
            size++;
        }

        /** Removes the mapping at index {@code i} and returns its value. */
        Object removeAt(int i) {
            Object old;
            beginWrite();
            if (i == keys.length) {
                old = zeroValue;
                zeroValue = null;
            } else {
                old = values[i];
                deleteAt(i);
            }
            endWrite();
            size--;
            return old;
        }

        /** Backward-shift deletion: moves later entries of the probe chain into the gap. */
        private void deleteAt(int gap) {
            final int[] ks = keys;
            final Object[] vs = values;
            final int mask = ks.length - 1;
            final int sh = shift;
            int i = gap;
            for (;;) {
                i = (i + 1) & mask;
                int k = ks[i];
                if (k == 0) {
                    break;
                }
                int ideal = (int) ((hash(k) << segmentBits) >>> sh);
                // The entry can move into gap iff gap lies on its probe path ideal -> i (cyclically).
                if (((i - ideal) & mask) >= ((i - gap) & mask)) {
                    ks[gap] = k;
                    vs[gap] = vs[i];
                    gap = i;
                }
            }
            ks[gap] = 0;
            vs[gap] = null;
            assigned--;
        }

        void clear() {
            if (size == 0) {
                return;
            }
            beginWrite();
            if (keys != EMPTY_KEYS) { // only key 0 present: the shared empty table stays untouched
                Arrays.fill(keys, 0);
                Arrays.fill(values, null);
            }
            zeroValue = null;
            endWrite();
            assigned = 0;
            size = 0;
        }

        private int nextCapacity() {
            if (keys.length >= MAX_CAPACITY) {
                throw new IllegalStateException("Maximum capacity of a segment exceeded: " + MAX_CAPACITY);
            }
            return keys.length << 1;
        }

        /** Builds the new table aside; lookups use the old one until the arrays are switched. */
        void rehash(int newCapacity) {
            final int[] oldKeys = keys;
            final Object[] oldValues = values;
            final int[] ks = new int[newCapacity];
            final Object[] vs = new Object[newCapacity];
            final int mask = newCapacity - 1;
            final int sh = 64 - Integer.numberOfTrailingZeros(newCapacity);
            for (int j = 0; j < oldKeys.length; j++) {
                int k = oldKeys[j];
                if (k != 0) {
                    int i = (int) ((hash(k) << segmentBits) >>> sh);
                    while (ks[i] != 0) {
                        i = (i + 1) & mask;
                    }
                    ks[i] = k;
                    vs[i] = oldValues[j];
                }
            }
            beginWrite();
            keys = ks;
            values = vs;
            shift = sh;
            endWrite();
            maxFill = maxFill(newCapacity, loadFactor);
        }
    }

    /**
     * A segment followed by 64 bytes of padding: the segments are allocated one after the other,
     * and without padding the fields of one segment (version, table) would share a cache line
     * with the lock state of the next one, so that updates of one segment would slow down the
     * lookups and updates of its neighbors (false sharing).
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
