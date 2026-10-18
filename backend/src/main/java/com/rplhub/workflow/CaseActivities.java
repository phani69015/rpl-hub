package com.rplhub.workflow;

import io.temporal.activity.ActivityInterface;

/** Every activity is idempotent: it checks the case status inside a transaction before changing anything. */
@ActivityInterface
public interface CaseActivities {

    String stage(String caseId);

    /** NEW -> AWAITING_QUALIFICATION, storing AI/keyword suggestions. */
    void match(String caseId);

    /** REPORT_PENDING -> AWAITING_DECISION, storing the advisory AI summary. */
    void generateReport(String caseId);
}
