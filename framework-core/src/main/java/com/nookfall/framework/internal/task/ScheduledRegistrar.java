package com.nookfall.framework.internal.task;

import com.nookfall.framework.config.ConfigRef;
import com.nookfall.framework.internal.config.ConfigServiceImpl;
import com.nookfall.framework.spi.ScheduledSpec;
import com.nookfall.framework.task.Execution;
import com.nookfall.framework.task.TaskHandle;
import com.nookfall.framework.task.TaskScope;
import com.nookfall.framework.util.time.Durations;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Schedules {@code @Scheduled} methods. Intervals written as {@code ${path}} are read from configuration and the task
 * is rescheduled after that configuration reloads.
 */
public final class ScheduledRegistrar {

    private final TasksImpl tasks;
    private final ConfigServiceImpl configs;
    private final Logger logger;
    private final List<Job<?>> jobs = new ArrayList<>();

    public ScheduledRegistrar(TasksImpl tasks, ConfigServiceImpl configs, Logger logger) {
        this.tasks = tasks;
        this.configs = configs;
        this.logger = logger;
    }

    /**
     * @throws IllegalArgumentException if a duration or configuration reference is invalid
     */
    public <T> void schedule(ScheduledSpec<T> spec, T instance) {
        Job<T> job = new Job<>(spec, instance);
        job.start();
        jobs.add(job);
        for (ConfigRef<?> ref : job.sources()) {
            ref.onReload(value -> job.restart());
        }
    }

    public int count() {
        return jobs.size();
    }

    /** Current period of a job, for diagnostics and tests. */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Job<?> job : jobs) {
            out.add(job.spec.type().getSimpleName() + "." + job.spec.method() + " every "
                    + Durations.toCompact(job.period));
        }
        return out;
    }

    private final class Job<T> {
        final ScheduledSpec<T> spec;
        final T instance;
        volatile TaskHandle handle;
        volatile Duration period;

        Job(ScheduledSpec<T> spec, T instance) {
            this.spec = spec;
            this.instance = instance;
        }

        List<ConfigRef<?>> sources() {
            List<ConfigRef<?>> out = new ArrayList<>();
            for (String expression : List.of(spec.every(), spec.initialDelay())) {
                if (isReference(expression)) {
                    ConfigRef<?> ref = configs.resolve(inner(expression)).ref();
                    if (!out.contains(ref)) {
                        out.add(ref);
                    }
                }
            }
            return out;
        }

        void start() {
            Duration every = resolve(spec.every(), "every");
            Duration initial = spec.initialDelay().isEmpty() ? every : resolve(spec.initialDelay(), "initialDelay");
            if (every.isZero() || every.isNegative()) {
                throw new IllegalArgumentException(name() + ": every must be positive");
            }
            TaskScope scope = spec.execution() == Execution.ASYNC ? tasks.async() : tasks.global();
            period = every;
            handle = scope.timer(initial, every, () -> {
                try {
                    spec.invoker().run(instance);
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Scheduled method " + name() + " failed", e);
                }
            });
        }

        void restart() {
            Duration before = period;
            TaskHandle previous = handle;
            try {
                Duration next = resolve(spec.every(), "every");
                if (next.equals(before)) {
                    return;
                }
                if (previous != null) {
                    previous.cancel();
                }
                start();
                logger.info("Rescheduled " + name() + " to run every " + Durations.toHuman(period));
            } catch (IllegalArgumentException e) {
                logger.severe(
                        "Could not reschedule " + name() + ": " + e.getMessage() + " (keeping the previous schedule)");
            }
        }

        private Duration resolve(String expression, String attribute) {
            if (isReference(expression)) {
                Object value = configs.resolve(inner(expression)).value();
                if (value instanceof Number) {
                    throw new IllegalArgumentException(name() + ": " + attribute + " " + expression
                            + " must be a duration such as 5m, not a plain number");
                }
                try {
                    return Durations.parse(String.valueOf(value));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                            name() + ": " + attribute + " " + expression + ": " + e.getMessage());
                }
            }
            return Durations.parse(expression);
        }

        private String name() {
            return spec.type().getSimpleName() + "." + spec.method() + "()";
        }
    }

    static boolean isReference(String expression) {
        return expression.startsWith("${") && expression.endsWith("}");
    }

    static String inner(String expression) {
        return expression.substring(2, expression.length() - 1).strip();
    }
}
