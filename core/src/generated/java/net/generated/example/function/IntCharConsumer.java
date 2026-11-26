// Generated from template api/KTypeVTypeConsumer.java. Edit the template, not this file.
package net.generated.example.function;

/**
 * Consumer of a pair of a {@code int} and a {@code char}, e.g. a key/value entry of a map.
 * <p>
 * The key and the value are passed as two primitive parameters. No entry object is
 * created, so the call never allocates, whether or not it gets inlined.
 */
@FunctionalInterface
public interface IntCharConsumer {

    /**
     * Performs this operation on a key and its value.
     *
     * @param key the key
     * @param value the value
     */
    void accept(int key, char value);
}
