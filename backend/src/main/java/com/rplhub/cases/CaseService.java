package com.rplhub.cases;

import com.rplhub.audit.AuditService;
import com.rplhub.cases.ApiExceptions.ConflictException;
import com.rplhub.cases.CaseView.*;
import com.rplhub.qualification.QualificationRepository;
import com.rplhub.user.AppUser;
import com.rplhub.workflow.WorkflowGateway;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Case access rules, worker declaration (idempotent upsert + submit) and the role-filtered case view. */
@Service
public class CaseService {

    public record Declaration(
        @NotBlank @Size(max = 120) String trade,
        @Min(0) @Max(60) int experienceYears,
        @NotBlank @Size(max = 4000) String skills,
        @Size(max = 4000) String details,
        int baseVersion,
        boolean submit) {}

    private final CaseRepository cases;
    private final QualificationRepository qualifications;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final WorkflowGateway workflows;

    CaseService(CaseRepository cases, QualificationRepository qualifications, JdbcClient jdbc, AuditService audit, WorkflowGateway workflows) {
        this.cases = cases;
        this.qualifications = qualifications;
        this.jdbc = jdbc;
        this.audit = audit;
        this.workflows = workflows;
    }

    /** Workers: own cases. Assessors: assigned cases. Admins: any. Anything else looks like "not found". */
    public CaseRow access(AppUser u, UUID id) {
        CaseRow c = cases.find(id).orElseThrow(CaseService::notFound);
        boolean ok = u.isAdmin()
            || (u.isWorker() && c.workerId().equals(u.id()))
            || (u.isAssessor() && u.id().equals(c.assessorId()));
        if (!ok || (!u.isWorker() && c.status().equals("DRAFT"))) throw notFound();
        return c;
    }

    static ResponseStatusException notFound() {
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
        boolean created = cases.insertDraft(id, u.id(), d.trade().trim(), d.experienceYears(), d.skills().trim(), blankToNull(d.details()));
        CaseRow row = cases.lock(id).orElseThrow(CaseService::notFound);
        if (!row.workerId().equals(u.id())) throw notFound();
        if (created) audit.record(u.id(), "CASE_CREATED", "rpl_case", id, Map.of("caseNumber", row.caseNumber()));

        boolean same = row.trade().equals(d.trade().trim()) && row.experienceYears() == d.experienceYears()
            && row.skills().equals(d.skills().trim()) && Objects.equals(row.details(), blankToNull(d.details()));
        if (!row.status().equals("DRAFT")) {
            if (same || d.submit()) return view(u, row); // repeated sync of an already-submitted case
            throw new ConflictException("Case already submitted and can no longer be edited", view(u, row));
        }
        if (!same && !created) {
            if (row.version() != d.baseVersion()) throw new ConflictException("Case was changed elsewhere", view(u, row));
            cases.updateDraft(id, d.baseVersion(), d.trade().trim(), d.experienceYears(), d.skills().trim(), blankToNull(d.details()));
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

    /** Unfiltered view for internal use (report, AI summary). */
    public CaseView full(UUID id) {
        return build(cases.find(id).orElseThrow(CaseService::notFound));
    }

    @Transactional
    public void storeAiSummary(UUID id, String summary) {
        jdbc.sql("update assessment_submission set ai_summary = ?, ai_generated_at = now() where case_id = ?").params(summary, id).update();
    }

    private CaseView view(AppUser u, CaseRow row) {
        CaseView v = build(row);
        boolean decided = row.status().equals("DECIDED");
        if (u.isWorker()) {
            // Workers see their own declaration, suggestions and status; assessment detail only once decided.
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
        var worker = jdbc.sql("select u.phone, p.full_name from app_user u left join worker_profile p on p.user_id = u.id where u.id = ?")
            .param(c.workerId()).query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)}).single();
        String assessorPhone = c.assessorId() == null ? null
            : jdbc.sql("select phone from app_user where id = ?").param(c.assessorId()).query(String.class).single();
        var qualification = c.qualificationVersionId() == null ? null : qualifications.detail(c.qualificationVersionId()).orElse(null);
        var suggestions = jdbc.sql("""
                select s.qualification_version_id as versionId, q.code, v.title, v.version, s.confidence, s.explanation, s.flagged, s.source
                from match_suggestion s join qualification_version v on v.id = s.qualification_version_id
                join qualification q on q.id = v.qualification_id where s.case_id = ? order by s.confidence desc""")
            .param(id).query(SuggestionView.class).list();
        var scores = jdbc.sql("select competency_id, score, comment, updated_at from competency_score where case_id = ?")
            .param(id).query(ScoreView.class).list();
        var evidence = jdbc.sql("select id, competency_id, filename, content_type, size_bytes, note, created_at from evidence where case_id = ? order by created_at")
            .param(id).query(EvidenceView.class).list();
        var requests = jdbc.sql("select id, message, created_at, fulfilled_at from evidence_request where case_id = ? order by created_at")
            .param(id).query(EvidenceRequestView.class).list();
        var submission = jdbc.sql("select recommendation, notes, submitted_at, ai_summary, ai_generated_at from assessment_submission where case_id = ?")
            .param(id).query(SubmissionView.class).optional().orElse(null);
        var decision = jdbc.sql("select d.outcome, d.rationale, u.phone as decidedBy, d.decided_at from final_decision d join app_user u on u.id = d.decided_by where d.case_id = ?")
            .param(id).query(DecisionView.class).optional().orElse(null);
        return new CaseView(id, c.caseNumber(), c.status(), c.version(), c.trade(), c.experienceYears(), c.skills(), c.details(),
            worker[0], worker[1], c.assessorId(), assessorPhone, qualification, suggestions, scores, evidence, requests, submission, decision,
            c.createdAt(), c.submittedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
