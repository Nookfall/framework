package com.nookfall.examples.homes;

import com.nookfall.framework.task.Execution;
import com.nookfall.framework.task.Scheduled;
import com.nookfall.framework.task.Tasks;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Bukkit;

/** Removes homes in worlds that no longer exist. The interval comes from config.yml and follows reloads. */
public final class CleanupJob {

    private final HomeRepository repository;
    private final Tasks tasks;
    private final Logger logger;

    public CleanupJob(HomeRepository repository, Tasks tasks, Logger logger) {
        this.repository = repository;
        this.tasks = tasks;
        this.logger = logger;
    }

    @Scheduled(every = "${cleanup.interval}", execution = Execution.ASYNC)
    void cleanup() {
        repository.worlds()
                // World lookups belong on the global thread.
                .thenApplyAsync(worlds -> worlds.stream().filter(w -> Bukkit.getWorld(w) == null).toList(),
                        tasks.global().executor())
                .thenAccept(this::deleteMissing);
    }

    private void deleteMissing(List<String> missing) {
        for (String world : missing) {
            repository.deleteInWorld(world).thenAccept(count -> {
                if (count > 0) {
                    logger.info("Removed " + count + " home(s) in deleted world " + world);
                }
            });
        }
    }
}
