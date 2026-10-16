package com.nookfall.framework.storage;

import java.sql.SQLException;

/** Work that uses a JDBC object and may throw {@link SQLException}. */
@FunctionalInterface
public interface SqlFunction<I, T> {
    T apply(I input) throws SQLException;
}
