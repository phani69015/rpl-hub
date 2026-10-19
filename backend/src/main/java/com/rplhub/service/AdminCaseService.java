package com.rplhub.service;

import static com.rplhub.model.CaseStatus.AWAITING_ASSESSOR;
import static com.rplhub.model.CaseStatus.AWAITING_QUALIFICATION;
import static com.rplhub.model.CaseStatus.DRAFT;

import com.rplhub.exception.ConflictException;
import com.rplhub.exception.InvalidException;
import com.rplhub.model.AdminCaseRow;
import com.rplhub.model.AppUser;
import com.rplhub.model.AssessorRequest;
import com.rplhub.model.AssessorView;
import com.rplhub.model.CaseRow;
import com.rplhub.model.CaseView;
import com.rplhub.model.WorkflowStatus;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.CaseRepository;
import com.rplhub.repository.QualificationRepository;
import com.rplhub.repository.UserRepository;
import com.rplhub.workflow.WorkflowGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Administrator actions: confirm qualification, assign assessor, manage assessors, monitor workflows, read audit trails. */
@Service
public class AdminCaseService {

    private final CaseRepository cases;
    private final CaseService caseService;
    private final QualificationRepository qualifications;
    private final UserRepository users;
    private final AuditRepository audit;
    private final WorkflowGateway workflows;

    AdminCaseService(CaseRepository cases, CaseService caseService, QualificationRepository qualifications, UserRepository users,
                     AuditRepository audit, WorkflowGateway workflows) {
        this.cases = cases;
        this.caseService = caseService;
        this.qualifications = qualifications;
        this.users = users;
        this.audit = audit;
        this.workflows = workflows;
    }

    public List<AdminCaseRow> listCases() {
        return cases.all().stream().map(c -> new AdminCaseRow(c, workflows.status(c.id()))).toList();
    }

    /** Pins the exact published version to the case. Only PUBLISHED versions are accepted. */
    @Transactional
    public CaseView confirmQualification(AppUser admin, UUID caseId, UUID versionId) {
        CaseRow c = cases.lock(caseId).orElseThrow(CaseService::notFound);
        if (versionId.equals(c.qualificationVersionId())) return caseService.get(admin, caseId); // repeated request
        if (!c.status().equals(AWAITING_QUALIFICATION)) throw new ConflictException("Case is " + c.status() + "; qualification cannot be confirmed now");
        String status = qualifications.status(versionId).orElseThrow(() -> new InvalidException("Qualification version not found"));
        if (!status.equals("PUBLISHED")) throw new InvalidException("Only published qualification versions can be used");
        boolean suggested = cases.suggestions(caseId).stream().anyMatch(s -> s.versionId().equals(versionId));
        cases.confirmQualification(caseId, versionId, admin.id());
        audit.record(admin.id(), "QUALIFICATION_CONFIRMED", "rpl_case", caseId, Map.of("versionId", versionId, "wasSuggested", suggested));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(admin, caseId);
    }

    /** Recommended assessors: those authorised for the trade of the confirmed qualification. Empty = case stays pending. */
    public List<AssessorView> candidates(UUID caseId) {
        CaseRow c = cases.find(caseId).orElseThrow(CaseService::notFound);
        if (c.qualificationVersionId() == null) throw new ConflictException("Confirm the qualification first");
        return users.eligibleAssessors(tradeOf(c));
    }

    @Transactional
    public CaseView assign(AppUser admin, UUID caseId, UUID assessorId) {
        CaseRow c = cases.lock(caseId).orElseThrow(CaseService::notFound);
        if (assessorId.equals(c.assessorId())) return caseService.get(admin, caseId); // repeated request
        if (!c.status().equals(AWAITING_ASSESSOR)) throw new ConflictException("Case is " + c.status() + "; assessor cannot be assigned now");
        String trade = tradeOf(c);
        if (!users.isEligible(assessorId, trade)) throw new InvalidException("Assessor is not authorised for trade '" + trade + "'");
        cases.assign(caseId, assessorId, admin.id());
        audit.record(admin.id(), "ASSESSOR_ASSIGNED", "rpl_case", caseId, Map.of("assessorId", assessorId));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(admin, caseId);
    }

    public WorkflowStatus workflowStatus(UUID caseId) {
        cases.find(caseId).orElseThrow(CaseService::notFound);
        return workflows.status(caseId);
    }

    /** Resume/retry: signal-with-start restarts a failed workflow from the persisted case state. */
    public WorkflowStatus retryWorkflow(AppUser admin, UUID caseId) {
        CaseRow c = cases.find(caseId).orElseThrow(CaseService::notFound);
        if (c.status().equals(DRAFT)) throw new InvalidException("Case has not been submitted");
        try {
            workflows.poke(caseId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Workflow service unavailable: " + e.getMessage());
        }
        audit.record(admin.id(), "WORKFLOW_RETRIED", "rpl_case", caseId, Map.of());
        return workflows.status(caseId);
    }

    public List<Map<String, Object>> caseAuditTrail(UUID caseId) {
        return audit.forEntity(caseId);
    }

    public List<Map<String, Object>> recentAudit() {
        return audit.recent();
    }

    public List<AssessorView> assessors() {
        return users.assessors();
    }

    public AssessorView saveAssessor(AssessorRequest r) {
        try {
            return users.saveAssessor(r.phone(), r.trades().stream().filter(t -> !t.isBlank()).toList(), r.canDecide());
        } catch (IllegalStateException e) {
            throw new InvalidException(e.getMessage());
        }
    }

    private String tradeOf(CaseRow c) {
        return qualifications.detail(c.qualificationVersionId()).orElseThrow().summary().trade();
    }
}
