package com.nookfall.framework.storage;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps the current row of a result set. Do not call {@code next()}. */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet row) throws SQLException;
}
