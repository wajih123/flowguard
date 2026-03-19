package fr.flowguard.decision.service;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class DecisionEngineScheduler {

    private static final Logger LOG = Logger.getLogger(DecisionEngineScheduler.class);

    @Inject WeeklyBriefService briefService;

    /** Every Monday at 08:00 Europe/Paris */
    @Scheduled(cron = "0 0 8 ? * MON", timeZone = "Europe/Paris")
    void weeklyBriefs() {
        LOG.info("[Scheduler] weeklyBriefs triggered");
        briefService.runWeeklyCron();
    }
}