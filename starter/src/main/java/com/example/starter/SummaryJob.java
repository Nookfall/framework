package com.example.starter;

import com.nookfall.framework.task.Execution;
import com.nookfall.framework.task.Scheduled;
import java.util.logging.Logger;

public final class SummaryJob {

    private final VisitRepository visits;
    private final Logger logger;

    public SummaryJob(VisitRepository visits, Logger logger) {
        this.visits = visits;
        this.logger = logger;
    }

    @Scheduled(every = "${summary.interval}", initialDelay = "1m", execution = Execution.ASYNC)
    void logSummary() {
        visits.total().thenAccept(total -> logger.info(total + " different players have visited"));
    }
}
