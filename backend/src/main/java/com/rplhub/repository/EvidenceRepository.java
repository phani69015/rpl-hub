package com.rplhub.repository;

import com.rplhub.model.CaseView.EvidenceView;
import com.rplhub.model.EvidenceFile;
import com.rplhub.model.EvidenceOwner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EvidenceRepository {

    private final JdbcClient jdbc;

    EvidenceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EvidenceOwner> owner(UUID evidenceId) {
        return jdbc.sql("select case_id, uploaded_by from evidence where id = ?").param(evidenceId).query(EvidenceOwner.class).optional();
    }

    public void insert(UUID id, UUID caseId, UUID competencyId, UUID uploadedBy, String filename, String contentType, long sizeBytes, String note) {
        jdbc.sql("insert into evidence(id, case_id, competency_id, uploaded_by, filename, content_type, size_bytes, note) values (?,?,?,?,?,?,?,?)")
            .params(id, caseId, competencyId, uploadedBy, filename, contentType, sizeBytes, note).update();
    }

    public Optional<EvidenceFile> file(UUID caseId, UUID evidenceId) {
        return jdbc.sql("select filename, content_type from evidence where id = ? and case_id = ?").params(evidenceId, caseId)
            .query(EvidenceFile.class).optional();
    }

    public List<EvidenceView> forCase(UUID caseId) {
        return jdbc.sql("select id, competency_id, filename, content_type, size_bytes, note, created_at from evidence where case_id = ? order by created_at")
            .param(caseId).query(EvidenceView.class).list();
    }

    /** True when something was uploaded after the assessor's latest evidence request. */
    public boolean uploadedSinceLastRequest(UUID caseId) {
        return jdbc.sql("""
                select count(*) from evidence e where e.case_id = ? and e.created_at >
                  (select max(created_at) from evidence_request where case_id = ?)""")
            .params(caseId, caseId).query(Integer.class).single() > 0;
    }
}
