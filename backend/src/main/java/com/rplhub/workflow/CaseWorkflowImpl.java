package com.rplhub.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class CaseWorkflowImpl implements CaseWorkflow {

    private static final int STEPS_PER_RUN = 100;

    private final CaseActivities activities = Workflow.newActivityStub(CaseActivities.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(2))
            .setRetryOptions(RetryOptions.newBuilder()
                .setInitialInterval(Duration.ofSeconds(2))
                .setBackoffCoefficient(2)
                .setMaximumInterval(Duration.ofMinutes(1))
                .setMaximumAttempts(8)
                .build())
            .build());

    private boolean advanced;

    @Override
    public void run(String caseId) {
        for (int i = 0; i < STEPS_PER_RUN; i++) {
            switch (activities.stage(caseId)) {
                case "NEW" -> activities.match(caseId);
                case "REPORT_PENDING" -> activities.generateReport(caseId);
                case "DECIDED" -> { return; }
                default -> {
                    // waiting on a human; the periodic wake-up also heals a missed signal
                    Workflow.await(Duration.ofHours(1), () -> advanced);
                    advanced = false;
                }
            }
        }
        Workflow.continueAsNew(caseId);
    }

    @Override
    public void advance() {
        advanced = true;
    }
}
