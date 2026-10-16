package com.nookfall.framework.internal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Releases resources in reverse registration order, continuing past failures. */
public final class Closer {

    @FunctionalInterface
    public interface Action {
        void close() throws Exception;
    }

    private record Entry(String name, Action action) {}

    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Logger logger;

    public Closer(Logger logger) {
        this.logger = logger;
    }

    public synchronized void add(String name, Action action) {
        entries.push(new Entry(name, action));
    }

    public void closeAll() {
        while (true) {
            Entry entry;
            synchronized (this) {
                entry = entries.poll();
            }
            if (entry == null) {
                return;
            }
            try {
                entry.action().close();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Failed to release " + entry.name(), t);
            }
        }
    }
}
