package com.rplhub.service;

import static com.rplhub.model.CaseStatus.AWAITING_DECISION;
import static com.rplhub.model.CaseStatus.DECIDED;
import static com.rplhub.model.CaseStatus.EVIDENCE_REQUESTED;
import static com.rplhub.model.CaseStatus.IN_ASSESSMENT;
import static com.rplhub.model.CaseStatus.REPORT_PENDING;

import com.rplhub.exception.ConflictException;
import com.rplhub.exception.InvalidException;
import com.rplhub.model.AppUser;
import com.rplhub.model.CaseRow;
import com.rplhub.model.CaseView;
import com.rplhub.model.DecisionRequest;
import com.rplhub.model.EvidenceRequestBody;
import com.rplhub.model.ScoreRequest;
import com.rplhub.model.SubmitRequest;
import com.rplhub.repository.AssessmentRepository;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.CaseRepository;
import com.rplhub.repository.QualificationRepository;
import com.rplhub.workflow.WorkflowGateway;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Assessor actions (scores, evidence request, recommendation) and the human final decision. Every change is audited. */
@Service
public class AssessmentService {

    private final CaseRepository cases;
    private final AssessmentRepository assessments;
    private final QualificationRepository qualifications;
    private final CaseService caseService;
    private final AuditRepository audit;
    private final WorkflowGateway workflows;

    AssessmentService(CaseRepository cases, AssessmentRepository assessments, QualificationRepository qualifications,
                      CaseService caseService, AuditRepository audit, WorkflowGateway workflows) {
        this.cases = cases;
        this.assessments = assessments;
        this.qualifications = qualifications;
        this.caseService = caseService;
        this.audit = audit;
        this.workflows = workflows;
    }

    @Transactional
    public CaseView score(AppUser u, UUID caseId, UUID competencyId, ScoreRequest r) {
        CaseRow c = lockAssigned(u, caseId, IN_ASSESSMENT, EVIDENCE_REQUESTED);
        int max = qualifications.competencyMaxScore(competencyId, c.qualificationVersionId())
            .orElseThrow(() -> new InvalidException("Competency does not belong to this case's qualification"));
        if (r.score() > max) throw new InvalidException("Score must be between 0 and " + max);
        Integer before = assessments.score(caseId, competencyId).orElse(null);
        assessments.saveScore(caseId, competencyId, r.score(), r.comment(), u.id());
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
        lockAssigned(u, caseId, IN_ASSESSMENT);
        String message = b.message().trim();
        assessments.insertEvidenceRequest(caseId, message, u.id());
        cases.transition(caseId, List.of(IN_ASSESSMENT), EVIDENCE_REQUESTED);
        audit.record(u.id(), "EVIDENCE_REQUESTED", "rpl_case", caseId, Map.of("message", message));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    /** Submits the recommendation. Mandatory competencies must all be scored; optional gaps are reported, not blocking. */
    @Transactional
    public CaseView submit(AppUser u, UUID caseId, SubmitRequest r) {
        requireAssessor(u, caseId, "Only the assigned assessor can submit");
        CaseRow c = cases.lock(caseId).orElseThrow();
        if (!c.status().equals(IN_ASSESSMENT)) {
            if (assessments.hasSubmission(caseId)) return caseService.get(u, caseId); // repeated request
            throw new ConflictException("Case is " + c.status() + "; it must be IN_ASSESSMENT to submit");
        }
        List<String> missing = assessments.unscoredMandatory(c.qualificationVersionId(), caseId);
        if (!missing.isEmpty()) throw new InvalidException("Mandatory competencies not scored: " + String.join(", ", missing));
        assessments.insertSubmission(caseId, r.recommendation(), r.notes(), u.id());
        cases.transition(caseId, List.of(IN_ASSESSMENT), REPORT_PENDING);
        audit.record(u.id(), "ASSESSMENT_SUBMITTED", "rpl_case", caseId, Map.of("recommendation", r.recommendation()));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    /** Only a user with decision authority (admin or assigned assessor with can_decide) may record the outcome. */
    @Transactional
    public CaseView decide(AppUser u, UUID caseId, DecisionRequest r) {
        caseService.access(u, caseId);
        if (!u.canDecide() || u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorised to record final decisions");
        CaseRow c = cases.lock(caseId).orElseThrow();
        var existing = assessments.decisionOutcome(caseId);
        if (existing.isPresent()) {
            if (existing.get().equals(r.outcome())) return caseService.get(u, caseId); // repeated request
            throw new ConflictException("A different final decision is already recorded");
        }
        if (!c.status().equals(AWAITING_DECISION)) throw new ConflictException("Case is " + c.status() + "; the report is not ready for a decision");
        assessments.insertDecision(caseId, r.outcome(), r.rationale(), u.id());
        cases.transition(caseId, List.of(AWAITING_DECISION), DECIDED);
        audit.record(u.id(), "FINAL_DECISION", "rpl_case", caseId, Map.of("outcome", r.outcome()));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    private void requireAssessor(AppUser u, UUID caseId, String message) {
        caseService.access(u, caseId);
        if (!u.isAssessor()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private CaseRow lockAssigned(AppUser u, UUID caseId, String... statuses) {
        requireAssessor(u, caseId, "Only the assigned assessor can do this");
        CaseRow c = cases.lock(caseId).orElseThrow();
        if (!List.of(statuses).contains(c.status()))
            throw new ConflictException("Case is " + c.status() + "; expected " + String.join(" or ", statuses));
        return c;
    }
}
