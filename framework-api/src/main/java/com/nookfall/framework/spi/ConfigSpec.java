package com.nookfall.framework.spi;

import java.util.List;
import java.util.function.Supplier;

/** A generated configuration declaration. The factory creates an instance holding the defaults. */
public record ConfigSpec<T>(Class<T> type, String file, List<String> header, Supplier<T> factory) {}
