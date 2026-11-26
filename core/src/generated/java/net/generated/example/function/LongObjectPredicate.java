// Generated from template api/KTypeObjectPredicate.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Predicate on a {@code long} key and an object value, e.g. an entry of a map. The key is
 * passed as a primitive parameter, without an entry object.
 *
 * @param <V> the type of the value
 */
@FunctionalInterface
public interface LongObjectPredicate<V> {

    /**
     * Evaluates this predicate on a key and its value.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if the entry matches
     */
    boolean test(long key, V value);
}
