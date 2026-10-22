package com.rplhub.repository;

import com.rplhub.model.CaseView.DecisionView;
import com.rplhub.model.CaseView.EvidenceRequestView;
import com.rplhub.model.CaseView.ScoreView;
import com.rplhub.model.CaseView.SubmissionView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Scores, evidence requests, the assessor's submission and the final decision of a case. */
@Repository
public class AssessmentRepository {

    private final JdbcClient jdbc;

    AssessmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Integer> score(UUID caseId, UUID competencyId) {
        return jdbc.sql("select score from competency_score where case_id = ? and competency_id = ?")
            .params(caseId, competencyId).query(Integer.class).optional();
    }

    public void saveScore(UUID caseId, UUID competencyId, int score, String comment, UUID actor) {
        jdbc.sql("""
                insert into competency_score(case_id, competency_id, score, comment, updated_by) values (?,?,?,?,?)
                on conflict (case_id, competency_id) do update set score = excluded.score, comment = excluded.comment,
                  updated_by = excluded.updated_by, updated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now')""")
            .params(caseId, competencyId, score, comment, actor).update();
    }

    public List<ScoreView> scores(UUID caseId) {
        return jdbc.sql("select competency_id, score, comment, updated_at from competency_score where case_id = ?")
            .param(caseId).query(ScoreView.class).list();
    }

    /** Codes of mandatory competencies of the version that have no score yet. */
    public List<String> unscoredMandatory(UUID versionId, UUID caseId) {
        return jdbc.sql("""
                select comp.code from competency comp
                where comp.version_id = ? and comp.mandatory
                  and not exists (select 1 from competency_score s where s.case_id = ? and s.competency_id = comp.id)
                order by comp.position""")
            .params(versionId, caseId).query(String.class).list();
    }

    public void insertEvidenceRequest(UUID caseId, String message, UUID actor) {
        jdbc.sql("insert into evidence_request(id, case_id, message, requested_by) values (?,?,?,?)")
            .params(UUID.randomUUID(), caseId, message, actor).update();
    }

    public void fulfilEvidenceRequests(UUID caseId) {
        jdbc.sql("update evidence_request set fulfilled_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where case_id = ? and fulfilled_at is null").param(caseId).update();
    }

    public List<EvidenceRequestView> evidenceRequests(UUID caseId) {
        return jdbc.sql("select id, message, created_at, fulfilled_at from evidence_request where case_id = ? order by created_at")
            .param(caseId).query(EvidenceRequestView.class).list();
    }

    public boolean hasSubmission(UUID caseId) {
        return jdbc.sql("select count(*) from assessment_submission where case_id = ?").param(caseId).query(Integer.class).single() > 0;
    }

    public void insertSubmission(UUID caseId, String recommendation, String notes, UUID actor) {
        jdbc.sql("insert into assessment_submission(case_id, recommendation, notes, submitted_by) values (?,?,?,?)")
            .params(caseId, recommendation, notes, actor).update();
    }

    public Optional<SubmissionView> submission(UUID caseId) {
        return jdbc.sql("select recommendation, notes, submitted_at, ai_summary, ai_generated_at from assessment_submission where case_id = ?")
            .param(caseId).query(SubmissionView.class).optional();
    }

    public void storeAiSummary(UUID caseId, String summary) {
        jdbc.sql("update assessment_submission set ai_summary = ?, ai_generated_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where case_id = ?").params(summary, caseId).update();
    }

    public Optional<String> decisionOutcome(UUID caseId) {
        return jdbc.sql("select outcome from final_decision where case_id = ?").param(caseId).query(String.class).optional();
    }

    public void insertDecision(UUID caseId, String outcome, String rationale, UUID actor) {
        jdbc.sql("insert into final_decision(case_id, outcome, rationale, decided_by) values (?,?,?,?)")
            .params(caseId, outcome, rationale, actor).update();
    }

    public Optional<DecisionView> decision(UUID caseId) {
        return jdbc.sql("select d.outcome, d.rationale, u.phone as decidedBy, d.decided_at from final_decision d join app_user u on u.id = d.decided_by where d.case_id = ?")
            .param(caseId).query(DecisionView.class).optional();
    }
}
