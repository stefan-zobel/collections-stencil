// Generated from template api/KTypeVTypePredicate.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Predicate on a pair of a {@code int} and a {@code short}, e.g. a key/value entry of a map.
 * Key and value are passed as two primitive parameters, without an entry object.
 */
@FunctionalInterface
public interface IntShortPredicate {

    /**
     * Evaluates this predicate on a key and its value.
     *
     * @param key the key
     * @param value the value
     * @return {@code true} if the entry matches
     */
    boolean test(int key, short value);
}
