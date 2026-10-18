package com.rplhub.workflow;

import static org.junit.jupiter.api.Assertions.*;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CaseWorkflowTest {

    /** Fake activities driven by a mutable "database" stage, like the real ones. */
    static class FakeActivities implements CaseActivities {
        final AtomicReference<String> stage = new AtomicReference<>("NEW");
        final List<String> calls = new ArrayList<>();
        int matchFailures;

        @Override public String stage(String id) { return stage.get(); }
        @Override public void match(String id) {
            calls.add("match");
            if (matchFailures-- > 0) throw new IllegalStateException("temporary failure");
            stage.set("AWAITING_QUALIFICATION");
        }
        @Override public void generateReport(String id) { calls.add("report"); stage.set("AWAITING_DECISION"); }
    }

    private TestWorkflowEnvironment env;
    private FakeActivities fake;
    private CaseWorkflow wf;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        var worker = env.newWorker("q");
        worker.registerWorkflowImplementationTypes(CaseWorkflowImpl.class);
        fake = new FakeActivities();
        worker.registerActivitiesImplementations(fake);
        env.start();
        wf = env.getWorkflowClient().newWorkflowStub(CaseWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue("q").setWorkflowId("case-1").build());
    }

    @AfterEach
    void tearDown() { env.close(); }

    @Test
    void retriesTemporaryFailureThenWaitsForHumansAndCompletesOnlyWhenDecided() {
        fake.matchFailures = 2;
        var result = io.temporal.client.WorkflowClient.execute(wf::run, "1");
        env.sleep(Duration.ofMinutes(5));
        assertEquals(List.of("match", "match", "match"), fake.calls, "retried until success, no duplicate steps");
        assertFalse(result.isDone(), "waits while a human acts");

        fake.stage.set("REPORT_PENDING");
        wf.advance();
        env.sleep(Duration.ofSeconds(5));
        assertEquals("AWAITING_DECISION", fake.stage.get());
        assertFalse(result.isDone());

        fake.stage.set("DECIDED");
        wf.advance();
        result.join();
        assertEquals(List.of("match", "match", "match", "report"), fake.calls);
    }

    @Test
    void missedSignalIsHealedByPeriodicWakeUp() {
        fake.stage.set("AWAITING_ASSESSOR");
        var result = io.temporal.client.WorkflowClient.execute(wf::run, "1");
        env.sleep(Duration.ofMinutes(1));
        fake.stage.set("DECIDED"); // state changed but no signal was delivered
        env.sleep(Duration.ofHours(2));
        assertTrue(result.isDone());
    }
}
