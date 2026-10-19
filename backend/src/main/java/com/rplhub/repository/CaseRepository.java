package com.rplhub.repository;

import com.rplhub.model.CaseRow;
import com.rplhub.model.CaseView.SuggestionView;
import com.rplhub.model.Suggestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {

    private static final String COLUMNS = """
        id, case_number, worker_id, trade, experience_years, skills, details, status, version,
        qualification_version_id, assessor_id, created_at, updated_at, submitted_at""";

    private final JdbcClient jdbc;

    CaseRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<CaseRow> find(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from rpl_case where id = ?").param(id).query(CaseRow.class).optional();
    }

    /** Row-locks the case for the rest of the transaction; used for every state change. */
    public Optional<CaseRow> lock(UUID id) {
        return jdbc.sql("select " + COLUMNS + " from rpl_case where id = ? for update").param(id).query(CaseRow.class).optional();
    }

    public List<CaseRow> forWorker(UUID workerId) {
        return jdbc.sql("select " + COLUMNS + " from rpl_case where worker_id = ? order by created_at desc").param(workerId).query(CaseRow.class).list();
    }

    public List<CaseRow> forAssessor(UUID assessorId) {
        return jdbc.sql("select " + COLUMNS + " from rpl_case where assessor_id = ? order by created_at desc").param(assessorId).query(CaseRow.class).list();
    }

    public List<CaseRow> all() {
        return jdbc.sql("select " + COLUMNS + " from rpl_case where status <> 'DRAFT' order by created_at desc limit 200").query(CaseRow.class).list();
    }

    /** Idempotent create keyed by the client-generated id. Returns false if the id already exists. */
    public boolean insertDraft(UUID id, UUID workerId, String trade, int years, String skills, String details) {
        return jdbc.sql("""
                insert into rpl_case(id, worker_id, trade, experience_years, skills, details, status)
                values (?,?,?,?,?,?, 'DRAFT') on conflict (id) do nothing""")
            .params(id, workerId, trade, years, skills, details).update() == 1;
    }

    /** Optimistic update of a draft: succeeds only if the caller saw the current version. */
    public boolean updateDraft(UUID id, int baseVersion, String trade, int years, String skills, String details) {
        return jdbc.sql("""
                update rpl_case set trade=?, experience_years=?, skills=?, details=?, version=version+1, updated_at=now()
                where id=? and version=? and status='DRAFT'""")
            .params(trade, years, skills, details, id, baseVersion).update() == 1;
    }

    /** Guarded state transition; false means the case was not in an expected state. */
    public boolean transition(UUID id, List<String> from, String to) {
        return jdbc.sql("update rpl_case set status=?, updated_at=now() where id=? and status = any(?::text[])")
            .params(to, id, from.toArray(new String[0])).update() == 1;
    }

    public boolean submit(UUID id) {
        return jdbc.sql("update rpl_case set status='NEW', submitted_at=now(), version=version+1, updated_at=now() where id=? and status='DRAFT'")
            .param(id).update() == 1;
    }

    public void confirmQualification(UUID id, UUID versionId, UUID admin) {
        jdbc.sql("""
                update rpl_case set qualification_version_id=?, qualification_confirmed_by=?, qualification_confirmed_at=now(),
                  status='AWAITING_ASSESSOR', updated_at=now() where id=?""")
            .params(versionId, admin, id).update();
    }

    public void assign(UUID id, UUID assessor, UUID admin) {
        jdbc.sql("update rpl_case set assessor_id=?, assigned_by=?, assigned_at=now(), status='IN_ASSESSMENT', updated_at=now() where id=?")
            .params(assessor, admin, id).update();
    }

    /** Replaces suggestions atomically so a retried matching activity leaves exactly one set. */
    public void replaceSuggestions(UUID caseId, List<Suggestion> suggestions) {
        jdbc.sql("delete from match_suggestion where case_id = ?").param(caseId).update();
        for (Suggestion s : suggestions) {
            jdbc.sql("insert into match_suggestion(id, case_id, qualification_version_id, confidence, explanation, flagged, source) values (?,?,?,?,?,?,?)")
                .params(UUID.randomUUID(), caseId, s.versionId(), s.confidence(), s.explanation(), s.flagged(), s.source()).update();
        }
    }

    public List<SuggestionView> suggestions(UUID caseId) {
        return jdbc.sql("""
                select s.qualification_version_id as versionId, q.code, v.title, v.version, s.confidence, s.explanation, s.flagged, s.source
                from match_suggestion s join qualification_version v on v.id = s.qualification_version_id
                join qualification q on q.id = v.qualification_id where s.case_id = ? order by s.confidence desc""")
            .param(caseId).query(SuggestionView.class).list();
    }
}
