// Generated from template api/KTypeObjectFunction.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Function of a {@code int} key and an object value, e.g. the remapping function of
 * {@code compute}.
 * <p>
 * The key is passed as a primitive parameter, so the call never boxes it.
 *
 * @param <V> the type of the value
 * @param <R> the type of the result
 */
@FunctionalInterface
public interface IntObjectFunction<V, R> {

    /**
     * Applies this function to a key and a value.
     *
     * @param key the key
     * @param value the value
     * @return the result
     */
    R apply(int key, V value);
}
