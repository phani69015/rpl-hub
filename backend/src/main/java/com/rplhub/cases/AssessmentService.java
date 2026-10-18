package com.rplhub.cases;

import com.rplhub.audit.AuditService;
import com.rplhub.cases.ApiExceptions.ConflictException;
import com.rplhub.cases.ApiExceptions.InvalidException;
import com.rplhub.user.AppUser;
import com.rplhub.workflow.WorkflowGateway;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Assessor actions (scores, evidence request, recommendation) and the human final decision. Every change is audited. */
@Service
public class AssessmentService {

    public record ScoreRequest(@Min(0) int score, @Size(max = 2000) String comment) {}
    public record EvidenceRequestBody(@NotBlank @Size(max = 2000) String message) {}
    public record SubmitRequest(@NotBlank @Pattern(regexp = "COMPETENT|NOT_YET_COMPETENT") String recommendation, @Size(max = 4000) String notes) {}
    public record DecisionRequest(@NotBlank @Pattern(regexp = "COMPETENT|NOT_YET_COMPETENT") String outcome, @Size(max = 4000) String rationale) {}

    private final CaseRepository cases;
    private final CaseService caseService;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final WorkflowGateway workflows;

    AssessmentService(CaseRepository cases, CaseService caseService, JdbcClient jdbc, AuditService audit, WorkflowGateway workflows) {
        this.cases = cases;
        this.caseService = caseService;
        this.jdbc = jdbc;
        this.audit = audit;
        this.workflows = workflows;
    }

    @Transactional
    public CaseView score(AppUser u, UUID caseId, UUID competencyId, ScoreRequest r) {
        CaseRow c = lockAssigned(u, caseId, "IN_ASSESSMENT", "EVIDENCE_REQUESTED");
        Integer max = jdbc.sql("select max_score from competency where id = ? and version_id = ?")
            .params(competencyId, c.qualificationVersionId()).query(Integer.class).optional()
            .orElseThrow(() -> new InvalidException("Competency does not belong to this case's qualification"));
        if (r.score() > max) throw new InvalidException("Score must be between 0 and " + max);
        Integer before = jdbc.sql("select score from competency_score where case_id = ? and competency_id = ?")
            .params(caseId, competencyId).query(Integer.class).optional().orElse(null);
        jdbc.sql("""
                insert into competency_score(case_id, competency_id, score, comment, updated_by) values (?,?,?,?,?)
                on conflict (case_id, competency_id) do update set score = excluded.score, comment = excluded.comment,
                  updated_by = excluded.updated_by, updated_at = now()""")
            .params(caseId, competencyId, r.score(), r.comment(), u.id()).update();
        var detail = new HashMap<String, Object>();
        detail.put("competencyId", competencyId);
        detail.put("before", before);
        detail.put("after", r.score());
        detail.put("comment", r.comment());
        audit.record(u.id(), before == null ? "SCORE_RECORDED" : "SCORE_CHANGED", "rpl_case", caseId, detail);
        return caseService.get(u, caseId);
    }

    @Transactional
    public CaseView requestEvidence(AppUser u, UUID caseId, EvidenceRequestBody b) {
        lockAssigned(u, caseId, "IN_ASSESSMENT");
        jdbc.sql("insert into evidence_request(id, case_id, message, requested_by) values (?,?,?,?)")
            .params(UUID.randomUUID(), caseId, b.message().trim(), u.id()).update();
        cases.transition(caseId, List.of("IN_ASSESSMENT"), "EVIDENCE_REQUESTED");
        audit.record(u.id(), "EVIDENCE_REQUESTED", "rpl_case", caseId, Map.of("message", b.message().trim()));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    /** Submits the recommendation. Mandatory competencies must all be scored; optional gaps are reported, not blocking. */
    @Transactional
    public CaseView submit(AppUser u, UUID caseId, SubmitRequest r) {
        CaseRow c = caseService.access(u, caseId);
        if (!u.isAssessor()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned assessor can submit");
        c = cases.lock(caseId).orElseThrow();
        if (!c.status().equals("IN_ASSESSMENT")) {
            if (jdbc.sql("select count(*) from assessment_submission where case_id = ?").param(caseId).query(Integer.class).single() > 0)
                return caseService.get(u, caseId); // repeated request
            throw new ConflictException("Case is " + c.status() + "; it must be IN_ASSESSMENT to submit", null);
        }
        List<String> missing = jdbc.sql("""
                select comp.code from competency comp
                where comp.version_id = ? and comp.mandatory
                  and not exists (select 1 from competency_score s where s.case_id = ? and s.competency_id = comp.id)
                order by comp.position""")
            .params(c.qualificationVersionId(), caseId).query(String.class).list();
        if (!missing.isEmpty()) throw new InvalidException("Mandatory competencies not scored: " + String.join(", ", missing));
        jdbc.sql("insert into assessment_submission(case_id, recommendation, notes, submitted_by) values (?,?,?,?)")
            .params(caseId, r.recommendation(), r.notes(), u.id()).update();
        cases.transition(caseId, List.of("IN_ASSESSMENT"), "REPORT_PENDING");
        audit.record(u.id(), "ASSESSMENT_SUBMITTED", "rpl_case", caseId, Map.of("recommendation", r.recommendation()));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    /** Only a user with decision authority (admin or assigned assessor with can_decide) may record the outcome. */
    @Transactional
    public CaseView decide(AppUser u, UUID caseId, DecisionRequest r) {
        CaseRow c = caseService.access(u, caseId);
        if (!u.canDecide() || u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorised to record final decisions");
        c = cases.lock(caseId).orElseThrow();
        var existing = jdbc.sql("select outcome from final_decision where case_id = ?").param(caseId).query(String.class).optional();
        if (existing.isPresent()) {
            if (existing.get().equals(r.outcome())) return caseService.get(u, caseId); // repeated request
            throw new ConflictException("A different final decision is already recorded", null);
        }
        if (!c.status().equals("AWAITING_DECISION")) throw new ConflictException("Case is " + c.status() + "; the report is not ready for a decision", null);
        jdbc.sql("insert into final_decision(case_id, outcome, rationale, decided_by) values (?,?,?,?)")
            .params(caseId, r.outcome(), r.rationale(), u.id()).update();
        cases.transition(caseId, List.of("AWAITING_DECISION"), "DECIDED");
        audit.record(u.id(), "FINAL_DECISION", "rpl_case", caseId, Map.of("outcome", r.outcome()));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    private CaseRow lockAssigned(AppUser u, UUID caseId, String... statuses) {
        caseService.access(u, caseId);
        if (!u.isAssessor()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned assessor can do this");
        CaseRow c = cases.lock(caseId).orElseThrow();
        if (!List.of(statuses).contains(c.status()))
            throw new ConflictException("Case is " + c.status() + "; expected " + String.join(" or ", statuses), null);
        return c;
    }
}
