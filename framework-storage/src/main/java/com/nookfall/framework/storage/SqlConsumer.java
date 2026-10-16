package com.nookfall.framework.storage;

import java.sql.SQLException;

/** Work without a result that may throw {@link SQLException}. */
@FunctionalInterface
public interface SqlConsumer<I> {
    void accept(I input) throws SQLException;
}
