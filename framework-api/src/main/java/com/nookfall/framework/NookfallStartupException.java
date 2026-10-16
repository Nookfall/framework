package com.nookfall.framework;

/** Thrown when a plugin's Nookfall runtime cannot start. Everything created before the failure is released. */
public class NookfallStartupException extends RuntimeException {

    public NookfallStartupException(String message) {
        super(message);
    }

    public NookfallStartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
