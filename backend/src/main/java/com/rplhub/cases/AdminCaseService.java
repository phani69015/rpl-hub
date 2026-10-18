package com.rplhub.cases;

import com.rplhub.audit.AuditService;
import com.rplhub.cases.ApiExceptions.ConflictException;
import com.rplhub.cases.ApiExceptions.InvalidException;
import com.rplhub.qualification.QualificationRepository;
import com.rplhub.user.AppUser;
import com.rplhub.user.UserRepository;
import com.rplhub.user.UserRepository.AssessorView;
import com.rplhub.workflow.WorkflowGateway;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Administrator decisions in the case flow: confirm qualification, confirm assessor, workflow monitoring. */
@Service
public class AdminCaseService {

    private final CaseRepository cases;
    private final CaseService caseService;
    private final QualificationRepository qualifications;
    private final UserRepository users;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final WorkflowGateway workflows;

    AdminCaseService(CaseRepository cases, CaseService caseService, QualificationRepository qualifications, UserRepository users,
                     JdbcClient jdbc, AuditService audit, WorkflowGateway workflows) {
        this.cases = cases;
        this.caseService = caseService;
        this.qualifications = qualifications;
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
        this.workflows = workflows;
    }

    /** Pins the exact published version to the case. Only PUBLISHED versions are accepted. */
    @Transactional
    public CaseView confirmQualification(AppUser admin, UUID caseId, UUID versionId) {
        CaseRow c = cases.lock(caseId).orElseThrow(CaseService::notFound);
        if (versionId.equals(c.qualificationVersionId())) return caseService.get(admin, caseId); // repeated request
        if (!c.status().equals("AWAITING_QUALIFICATION")) throw new ConflictException("Case is " + c.status() + "; qualification cannot be confirmed now", null);
        String status = jdbc.sql("select status from qualification_version where id = ?").param(versionId).query(String.class).optional()
            .orElseThrow(() -> new InvalidException("Qualification version not found"));
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
        if (c.qualificationVersionId() == null) throw new ConflictException("Confirm the qualification first", null);
        var q = qualifications.detail(c.qualificationVersionId()).orElseThrow();
        return users.eligibleAssessors(q.summary().trade());
    }

    @Transactional
    public CaseView assign(AppUser admin, UUID caseId, UUID assessorId) {
        CaseRow c = cases.lock(caseId).orElseThrow(CaseService::notFound);
        if (assessorId.equals(c.assessorId())) return caseService.get(admin, caseId); // repeated request
        if (!c.status().equals("AWAITING_ASSESSOR")) throw new ConflictException("Case is " + c.status() + "; assessor cannot be assigned now", null);
        String trade = qualifications.detail(c.qualificationVersionId()).orElseThrow().summary().trade();
        if (!users.isEligible(assessorId, trade)) throw new InvalidException("Assessor is not authorised for trade '" + trade + "'");
        cases.assign(caseId, assessorId, admin.id());
        audit.record(admin.id(), "ASSESSOR_ASSIGNED", "rpl_case", caseId, Map.of("assessorId", assessorId));
        workflows.pokeAfterCommit(caseId);
        return caseService.get(admin, caseId);
    }

    public WorkflowGateway.Status workflowStatus(UUID caseId) {
        cases.find(caseId).orElseThrow(CaseService::notFound);
        return workflows.status(caseId);
    }

    /** Resume/retry: signal-with-start restarts a failed workflow from the persisted case state. */
    public WorkflowGateway.Status retryWorkflow(AppUser admin, UUID caseId) {
        CaseRow c = cases.find(caseId).orElseThrow(CaseService::notFound);
        if (c.status().equals("DRAFT")) throw new InvalidException("Case has not been submitted");
        try {
            workflows.poke(caseId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Workflow service unavailable: " + e.getMessage());
        }
        audit.record(admin.id(), "WORKFLOW_RETRIED", "rpl_case", caseId, Map.of());
        return workflows.status(caseId);
    }

    public List<Map<String, Object>> auditTrail(UUID caseId) {
        return jdbc.sql("""
                select a.created_at, a.action, u.phone as actor, a.detail::text as detail from audit_event a
                left join app_user u on u.id = a.actor_id where a.entity_id = ? order by a.created_at, a.id""")
            .param(caseId.toString()).query().listOfRows();
    }
}
