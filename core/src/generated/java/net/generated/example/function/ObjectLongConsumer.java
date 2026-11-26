// Generated from template api/ObjectVTypeConsumer.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Consumer of an object key and a {@code long} value, e.g. an entry of a map.
 * <p>
 * The value is passed as a primitive parameter. No entry object is created, so the call never
 * allocates, whether or not it gets inlined.
 *
 * @param <K> the type of the key
 */
@FunctionalInterface
public interface ObjectLongConsumer<K> {

    /**
     * Performs this operation on a key and its value.
     *
     * @param key the key
     * @param value the value
     */
    void accept(K key, long value);
}
