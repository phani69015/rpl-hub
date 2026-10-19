package com.rplhub.service;

import static com.rplhub.model.CaseStatus.AWAITING_DECISION;
import static com.rplhub.model.CaseStatus.DECIDED;
import static com.rplhub.model.CaseStatus.DRAFT;

import com.rplhub.exception.ConflictException;
import com.rplhub.model.AppUser;
import com.rplhub.model.CaseRow;
import com.rplhub.model.CaseView;
import com.rplhub.model.Declaration;
import com.rplhub.repository.AssessmentRepository;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.CaseRepository;
import com.rplhub.repository.EvidenceRepository;
import com.rplhub.repository.ProfileRepository;
import com.rplhub.repository.QualificationRepository;
import com.rplhub.repository.UserRepository;
import com.rplhub.workflow.WorkflowGateway;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Case access rules, worker declaration (idempotent upsert + submit) and the role-filtered case view. */
@Service
public class CaseService {

    private final CaseRepository cases;
    private final AssessmentRepository assessments;
    private final EvidenceRepository evidence;
    private final QualificationRepository qualifications;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AuditRepository audit;
    private final WorkflowGateway workflows;

    CaseService(CaseRepository cases, AssessmentRepository assessments, EvidenceRepository evidence, QualificationRepository qualifications,
                UserRepository users, ProfileRepository profiles, AuditRepository audit, WorkflowGateway workflows) {
        this.cases = cases;
        this.assessments = assessments;
        this.evidence = evidence;
        this.qualifications = qualifications;
        this.users = users;
        this.profiles = profiles;
        this.audit = audit;
        this.workflows = workflows;
    }

    /** Workers: own cases. Assessors: assigned cases. Admins: any. Anything else looks like "not found". */
    public CaseRow access(AppUser u, UUID id) {
        CaseRow c = cases.find(id).orElseThrow(CaseService::notFound);
        boolean ok = u.isAdmin()
            || (u.isWorker() && c.workerId().equals(u.id()))
            || (u.isAssessor() && u.id().equals(c.assessorId()));
        if (!ok || (!u.isWorker() && c.status().equals(DRAFT))) throw notFound();
        return c;
    }

    public static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found");
    }

    public List<CaseRow> list(AppUser u) {
        if (u.isAdmin()) return cases.all();
        return u.isAssessor() ? cases.forAssessor(u.id()) : cases.forWorker(u.id());
    }

    /**
     * Idempotent: the id is client-generated, so a repeated sync of the same data is a no-op. Differing data on
     * a stale baseVersion is a conflict (409 with the server copy); a submitted case is never modified.
     */
    @Transactional
    public CaseView save(AppUser u, UUID id, Declaration d) {
        if (!u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only workers submit declarations");
        String trade = d.trade().trim(), skills = d.skills().trim(), details = blankToNull(d.details());
        boolean created = cases.insertDraft(id, u.id(), trade, d.experienceYears(), skills, details);
        CaseRow row = cases.lock(id).orElseThrow(CaseService::notFound);
        if (!row.workerId().equals(u.id())) throw notFound();
        if (created) audit.record(u.id(), "CASE_CREATED", "rpl_case", id, Map.of("caseNumber", row.caseNumber()));

        boolean same = row.trade().equals(trade) && row.experienceYears() == d.experienceYears()
            && row.skills().equals(skills) && Objects.equals(row.details(), details);
        if (!row.status().equals(DRAFT)) {
            if (same || d.submit()) return view(u, row); // repeated sync of an already-submitted case
            throw new ConflictException("Case already submitted and can no longer be edited", view(u, row));
        }
        if (!same && !created) {
            if (row.version() != d.baseVersion()) throw new ConflictException("Case was changed elsewhere", view(u, row));
            cases.updateDraft(id, d.baseVersion(), trade, d.experienceYears(), skills, details);
        }
        if (d.submit()) {
            cases.submit(id);
            audit.record(u.id(), "CASE_SUBMITTED", "rpl_case", id, Map.of("caseNumber", row.caseNumber()));
            workflows.pokeAfterCommit(id);
        }
        return view(u, cases.find(id).orElseThrow());
    }

    public CaseView get(AppUser u, UUID id) {
        return view(u, access(u, id));
    }

    /** Unfiltered view for internal use (AI summary). */
    public CaseView full(UUID id) {
        return build(cases.find(id).orElseThrow(CaseService::notFound));
    }

    /** Unfiltered view for the report. Staff see it once the assessor has submitted; the worker only after the decision. */
    public CaseView reportView(AppUser u, UUID id) {
        CaseRow row = access(u, id);
        boolean ready = u.isWorker() ? row.status().equals(DECIDED)
            : List.of(AWAITING_DECISION, DECIDED).contains(row.status());
        if (!ready) throw new ConflictException("Report is not available yet");
        return build(row);
    }

    private CaseView view(AppUser u, CaseRow row) {
        CaseView v = build(row);
        if (u.isWorker()) {
            // Workers see their own declaration, suggestions and status; assessment detail only once decided.
            boolean decided = row.status().equals(DECIDED);
            return new CaseView(v.id(), v.caseNumber(), v.status(), v.version(), v.trade(), v.experienceYears(), v.skills(), v.details(),
                null, v.workerName(), null, null, v.qualification(), v.suggestions(),
                decided ? v.scores() : List.of(), v.evidence(), v.evidenceRequests(),
                decided ? v.submission() : null, v.decision(), v.createdAt(), v.submittedAt());
        }
        if (u.isAssessor()) {
            return new CaseView(v.id(), v.caseNumber(), v.status(), v.version(), v.trade(), v.experienceYears(), v.skills(), v.details(),
                v.workerPhone(), v.workerName(), v.assessorId(), v.assessorPhone(), v.qualification(), List.of(), v.scores(), v.evidence(),
                v.evidenceRequests(), v.submission(), v.decision(), v.createdAt(), v.submittedAt());
        }
        return v;
    }

    private CaseView build(CaseRow c) {
        UUID id = c.id();
        String workerPhone = users.find(c.workerId()).orElseThrow().phone();
        String workerName = profiles.find(c.workerId()).map(p -> p.fullName()).orElse(null);
        String assessorPhone = c.assessorId() == null ? null : users.find(c.assessorId()).orElseThrow().phone();
        var qualification = c.qualificationVersionId() == null ? null : qualifications.detail(c.qualificationVersionId()).orElse(null);
        return new CaseView(id, c.caseNumber(), c.status(), c.version(), c.trade(), c.experienceYears(), c.skills(), c.details(),
            workerPhone, workerName, c.assessorId(), assessorPhone, qualification, cases.suggestions(id), assessments.scores(id),
            evidence.forCase(id), assessments.evidenceRequests(id), assessments.submission(id).orElse(null),
            assessments.decision(id).orElse(null), c.createdAt(), c.submittedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
