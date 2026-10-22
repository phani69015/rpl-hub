package com.rplhub.repository;

import com.rplhub.model.CompetencyView;
import com.rplhub.model.LockedVersion;
import com.rplhub.model.QualificationContent;
import com.rplhub.model.QualificationContent.CompetencyContent;
import com.rplhub.model.VersionDetail;
import com.rplhub.model.VersionSummary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class QualificationRepository {

    private static final String SUMMARY_SQL = """
        select v.id, q.code, v.version, v.title, v.trade, v.nsqf_level, v.source, v.status, v.created_at, v.published_at
        from qualification_version v join qualification q on q.id = v.qualification_id""";

    private final JdbcClient jdbc;

    QualificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the qualification if needed; the surrounding write transaction serialises concurrent imports. */
    public UUID lockQualification(String code) {
        jdbc.sql("insert into qualification(id, code) values (?,?) on conflict (code) do nothing")
            .params(UUID.randomUUID(), code).update();
        return jdbc.sql("select id from qualification where code = ?").param(code)
            .query(UUID.class).single();
    }

    public Optional<Integer> versionWithHash(UUID qualificationId, String hash) {
        return jdbc.sql("select version from qualification_version where qualification_id = ? and content_hash = ?")
            .params(qualificationId, hash).query(Integer.class).optional();
    }

    /** Existing qualifications with another code but the same title and trade. */
    public List<String> codesWithSameTitleAndTrade(String code, String title, String trade) {
        return jdbc.sql("""
                select distinct q.code from qualification q join qualification_version v on v.qualification_id = q.id
                where q.code <> ? and lower(v.title) = lower(?) and lower(v.trade) = lower(?)""")
            .params(code, title, trade).query(String.class).list();
    }

    public UUID insertDraft(UUID qualificationId, QualificationContent c, UUID batchId) {
        int next = jdbc.sql("select coalesce(max(version), 0) + 1 from qualification_version where qualification_id = ?")
            .param(qualificationId).query(Integer.class).single();
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into qualification_version(id, qualification_id, version, title, trade, nsqf_level, source, status, content_hash, batch_id)
                values (?,?,?,?,?,?,?, 'DRAFT', ?, ?)""")
            .params(id, qualificationId, next, c.title(), c.trade(), c.nsqfLevel(), c.source(), c.contentHash(), batchId).update();
        insertCompetencies(id, c.competencies());
        return id;
    }

    public int versionNumber(UUID versionId) {
        return jdbc.sql("select version from qualification_version where id = ?").param(versionId).query(Integer.class).single();
    }

    public void replaceCompetencies(UUID versionId, List<CompetencyContent> comps) {
        jdbc.sql("delete from evidence_requirement where competency_id in (select id from competency where version_id = ?)")
            .param(versionId).update();
        jdbc.sql("delete from competency where version_id = ?").param(versionId).update();
        insertCompetencies(versionId, comps);
    }

    private void insertCompetencies(UUID versionId, List<CompetencyContent> comps) {
        int pos = 0;
        for (CompetencyContent c : comps) {
            UUID cid = UUID.randomUUID();
            jdbc.sql("insert into competency(id, version_id, position, code, title, mandatory, max_score, rubric) values (?,?,?,?,?,?,?,?)")
                .params(cid, versionId, pos++, c.code(), c.title(), c.mandatory(), c.maxScore(), c.rubric()).update();
            int epos = 0;
            for (String e : c.evidenceRequirements() == null ? List.<String>of() : c.evidenceRequirements()) {
                jdbc.sql("insert into evidence_requirement(id, competency_id, position, description) values (?,?,?,?)")
                    .params(UUID.randomUUID(), cid, epos++, e).update();
            }
        }
    }

    public List<VersionSummary> list(String status) {
        var sql = SUMMARY_SQL + (status == null ? "" : " where v.status = ?") + " order by q.code, v.version desc";
        var spec = jdbc.sql(sql);
        if (status != null) spec = spec.param(status);
        return spec.query(VersionSummary.class).list();
    }

    public Optional<VersionDetail> detail(UUID id) {
        return jdbc.sql(SUMMARY_SQL + " where v.id = ?").param(id).query(VersionSummary.class).optional()
            .map(summary -> new VersionDetail(summary, competencies(id)));
    }

    private List<CompetencyView> competencies(UUID versionId) {
        return jdbc.sql("select id, code, title, mandatory, max_score, rubric from competency where version_id = ? order by position")
            .param(versionId)
            .query((rs, i) -> new CompetencyView(UUID.fromString(rs.getString("id")), rs.getString("code"), rs.getString("title"),
                rs.getBoolean("mandatory"), rs.getInt("max_score"), rs.getString("rubric"),
                evidenceRequirements(UUID.fromString(rs.getString("id")))))
            .list();
    }

    private List<String> evidenceRequirements(UUID competencyId) {
        return jdbc.sql("select description from evidence_requirement where competency_id = ? order by position")
            .param(competencyId).query(String.class).list();
    }

    public Optional<LockedVersion> lockVersion(UUID id) {
        return jdbc.sql("select id, qualification_id, version, status, content_hash from qualification_version where id = ?")
            .param(id).query(LockedVersion.class).optional();
    }

    public boolean hashTakenByOther(UUID qualificationId, String hash, UUID versionId) {
        return jdbc.sql("select count(*) from qualification_version where qualification_id = ? and content_hash = ? and id <> ?")
            .params(qualificationId, hash, versionId).query(Integer.class).single() > 0;
    }

    public void updateDraftFields(UUID id, QualificationContent c) {
        jdbc.sql("update qualification_version set title=?, trade=?, nsqf_level=?, source=?, content_hash=? where id=?")
            .params(c.title(), c.trade(), c.nsqfLevel(), c.source(), c.contentHash(), id).update();
    }

    public boolean newerVersionPublished(UUID qualificationId, int version) {
        return jdbc.sql("select count(*) from qualification_version where qualification_id = ? and version > ? and status <> 'DRAFT'")
            .params(qualificationId, version).query(Integer.class).single() > 0;
    }

    public void publish(UUID id, UUID qualificationId, UUID actor) {
        jdbc.sql("update qualification_version set status = 'SUPERSEDED' where qualification_id = ? and status = 'PUBLISHED'")
            .param(qualificationId).update();
        jdbc.sql("update qualification_version set status = 'PUBLISHED', published_by = ?, published_at = strftime('%Y-%m-%dT%H:%M:%fZ','now') where id = ?")
            .params(actor, id).update();
    }

    public Optional<String> status(UUID versionId) {
        return jdbc.sql("select status from qualification_version where id = ?").param(versionId).query(String.class).optional();
    }

    /** Max score of a competency if it belongs to the version, empty otherwise. */
    public Optional<Integer> competencyMaxScore(UUID competencyId, UUID versionId) {
        return jdbc.sql("select max_score from competency where id = ? and version_id = ?")
            .params(competencyId, versionId).query(Integer.class).optional();
    }

    /** Published versions only: the sole set usable by matching and assessment. */
    public List<VersionDetail> published() {
        return list("PUBLISHED").stream().map(v -> detail(v.id()).orElseThrow()).toList();
    }
}
