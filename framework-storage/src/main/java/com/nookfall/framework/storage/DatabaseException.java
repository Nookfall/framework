package com.nookfall.framework.storage;

/** A database operation failed. The cause is usually a {@link java.sql.SQLException}. */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }

    public DatabaseException(String message) {
        super(message);
    }
}
