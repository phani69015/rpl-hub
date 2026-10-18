package com.rplhub.workflow;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * One workflow per case (id "case-{caseId}"). The database case status is the source of truth; the workflow
 * drives the automated steps (matching, report generation) and sleeps while a human acts. Signals only wake it,
 * so a lost or duplicate signal can never corrupt state, and a failed workflow can be restarted from the DB state.
 */
@WorkflowInterface
public interface CaseWorkflow {

    @WorkflowMethod
    void run(String caseId);

    @SignalMethod
    void advance();
}
