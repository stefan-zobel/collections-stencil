// Generated from template api/KTypeObjectConsumer.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Consumer of a {@code long} key and an object value, e.g. an entry of a map.
 * <p>
 * The key is passed as a primitive parameter. No entry object is created, so the call never
 * allocates, whether or not it gets inlined.
 *
 * @param <V> the type of the value
 */
@FunctionalInterface
public interface LongObjectConsumer<V> {

    /**
     * Performs this operation on a key and its value.
     *
     * @param key the key
     * @param value the value
     */
    void accept(long key, V value);
}
