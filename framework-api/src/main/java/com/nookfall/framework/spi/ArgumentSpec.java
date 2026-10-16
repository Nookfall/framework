package com.nookfall.framework.spi;

import com.nookfall.framework.command.ArgumentResolver;

/** A custom command argument type backed by an injected resolver. */
public record ArgumentSpec<T>(Class<T> valueType, Class<? extends ArgumentResolver<T>> resolverType) {}
