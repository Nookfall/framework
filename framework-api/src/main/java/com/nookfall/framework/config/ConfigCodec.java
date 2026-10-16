package com.nookfall.framework.config;

/**
 * Converts between a YAML value (String, Number, Boolean, List or Map) and a Java value.
 *
 * @param <T> Java value type
 */
public interface ConfigCodec<T> {

    /** Decodes a raw YAML value. Throw {@link IllegalArgumentException} with a readable message for invalid input. */
    T decode(Object raw);

    /** Encodes a value into a YAML-compatible representation. */
    Object encode(T value);
}
