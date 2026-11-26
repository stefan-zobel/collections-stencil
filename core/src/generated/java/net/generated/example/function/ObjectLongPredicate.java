// Generated from template api/ObjectVTypePredicate.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Predicate on an object key and a {@code long} value, e.g. an entry of a map. The value is
 * passed as a primitive parameter, without an entry object.
 *
 * @param <K> the type of the key
 */
@FunctionalInterface
public interface ObjectLongPredicate<K> {

    /**
     * Evaluates this predicate on a key and its value.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if the entry matches
     */
    boolean test(K key, long value);
}
