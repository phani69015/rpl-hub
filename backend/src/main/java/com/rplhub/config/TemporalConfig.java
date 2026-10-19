package com.rplhub.config;

import com.rplhub.workflow.CaseActivities;
import com.rplhub.workflow.CaseWorkflowImpl;
import com.rplhub.workflow.WorkflowGateway;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    @Bean(destroyMethod = "shutdown")
    WorkflowServiceStubs workflowServiceStubs(@Value("${app.temporal.target}") String target) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    WorkflowClient workflowClient(WorkflowServiceStubs service, @Value("${app.temporal.namespace}") String namespace) {
        return WorkflowClient.newInstance(service,
            WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }

    /** Runs the workflow worker inside this service. Because state is in Temporal and Postgres, a restart resumes work. */
    @Bean
    @ConditionalOnProperty(name = "app.temporal.worker-enabled", havingValue = "true", matchIfMissing = true)
    SmartLifecycle caseWorker(WorkflowClient client, CaseActivities activities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        var worker = factory.newWorker(WorkflowGateway.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(CaseWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return new SmartLifecycle() {
            private volatile boolean running;
            @Override public void start() { factory.start(); running = true; }
            @Override public void stop() { factory.shutdown(); running = false; }
            @Override public boolean isRunning() { return running; }
        };
    }
}
