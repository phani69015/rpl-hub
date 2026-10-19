package com.rplhub.workflow;

import io.grpc.StatusRuntimeException;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflowservice.v1.DescribeWorkflowExecutionRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.serviceclient.WorkflowServiceStubs;
import com.rplhub.model.WorkflowStatus;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** The only place that talks to Temporal from request handling. */
@Component
public class WorkflowGateway {

    public static final String TASK_QUEUE = "rpl-case";
    private static final Logger log = LoggerFactory.getLogger(WorkflowGateway.class);

    private final WorkflowClient client;
    private final WorkflowServiceStubs service;

    WorkflowGateway(WorkflowClient client, WorkflowServiceStubs service) {
        this.client = client;
        this.service = service;
    }

    static String workflowId(UUID caseId) {
        return "case-" + caseId;
    }

    /** Wakes the case workflow, starting it if needed, after the current DB transaction commits. Never throws. */
    public void pokeAfterCommit(UUID caseId) {
        Runnable poke = () -> {
            try {
                poke(caseId);
            } catch (RuntimeException e) {
                // DB state is already committed; the admin retry endpoint or the next poke recovers.
                log.warn("Could not signal workflow for case {}: {}", caseId, e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { poke.run(); }
            });
        } else {
            poke.run();
        }
    }

    /** Signal-with-start: one workflow per case, so repeated requests cannot create duplicates. */
    public void poke(UUID caseId) {
        CaseWorkflow wf = client.newWorkflowStub(CaseWorkflow.class, WorkflowOptions.newBuilder()
            .setWorkflowId(workflowId(caseId))
            .setTaskQueue(TASK_QUEUE)
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
            .build());
        try {
            var request = client.newSignalWithStartRequest();
            request.add(wf::run, caseId.toString());
            request.add(wf::advance);
            client.signalWithStart(request);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // closed successfully (case decided): nothing to do
        }
    }

    public WorkflowStatus status(UUID caseId) {
        try {
            var resp = service.blockingStub().describeWorkflowExecution(DescribeWorkflowExecutionRequest.newBuilder()
                .setNamespace(client.getOptions().getNamespace())
                .setExecution(WorkflowExecution.newBuilder().setWorkflowId(workflowId(caseId))).build());
            WorkflowExecutionStatus s = resp.getWorkflowExecutionInfo().getStatus();
            return switch (s) {
                case WORKFLOW_EXECUTION_STATUS_RUNNING -> {
                    var failing = resp.getPendingActivitiesList().stream().filter(a -> a.hasLastFailure()).findFirst();
                    yield failing.map(a -> new WorkflowStatus("RETRYING", a.getActivityType().getName() + ": " + a.getLastFailure().getMessage()))
                        .orElse(new WorkflowStatus("RUNNING", null));
                }
                case WORKFLOW_EXECUTION_STATUS_COMPLETED -> new WorkflowStatus("COMPLETED", null);
                case WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> new WorkflowStatus("RUNNING", null);
                case WORKFLOW_EXECUTION_STATUS_FAILED -> new WorkflowStatus("FAILED", failureMessage(caseId));
                default -> new WorkflowStatus(s.name().replace("WORKFLOW_EXECUTION_STATUS_", ""), null);
            };
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) return new WorkflowStatus("NOT_STARTED", null);
            return new WorkflowStatus("UNAVAILABLE", e.getStatus().getCode().name());
        }
    }

    private String failureMessage(UUID caseId) {
        try {
            client.newUntypedWorkflowStub(workflowId(caseId)).getResult(Void.class);
            return null;
        } catch (WorkflowFailedException e) {
            return e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }
}
