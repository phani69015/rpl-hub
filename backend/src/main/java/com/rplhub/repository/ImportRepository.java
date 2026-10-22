package com.rplhub.repository;

import com.rplhub.model.ImportResult;
import com.rplhub.model.Issue;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ImportRepository {

    private final JdbcClient jdbc;

    ImportRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insertBatch(String sourceType, String filename, int totalRows, UUID actor) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                insert into import_batch(id, source_type, filename, total_rows, versions_created, duplicates_skipped, qualifications_rejected, created_by)
                values (?,?,?,?,0,0,0,?)""")
            .params(id, sourceType, filename, totalRows, actor).update();
        return id;
    }

    public void updateTotals(UUID batchId, int created, int duplicates, int rejected) {
        jdbc.sql("update import_batch set versions_created=?, duplicates_skipped=?, qualifications_rejected=? where id=?")
            .params(created, duplicates, rejected, batchId).update();
    }

    public void insertIssues(UUID batchId, List<Issue> issues) {
        for (Issue i : issues) {
            jdbc.sql("insert into import_issue(id, batch_id, row_number, qualification_code, severity, field, message) values (?,?,?,?,?,?,?)")
                .params(UUID.randomUUID(), batchId, i.rowNumber(), i.qualificationCode(), i.severity(), i.field(), i.message()).update();
        }
    }

    public List<Map<String, Object>> listBatches() {
        return jdbc.sql("""
                select id, source_type, filename, total_rows, versions_created, duplicates_skipped, qualifications_rejected, created_at
                from import_batch order by created_at desc limit 50""").query().listOfRows();
    }

    public Optional<ImportResult> find(UUID id) {
        return jdbc.sql("""
                select filename, source_type, total_rows, versions_created, duplicates_skipped, qualifications_rejected
                from import_batch where id = ?""")
            .param(id).query((rs, n) -> new ImportResult(id, rs.getString("filename"), rs.getString("source_type"), rs.getInt("total_rows"),
                rs.getInt("versions_created"), rs.getInt("duplicates_skipped"), rs.getInt("qualifications_rejected"), issues(id)))
            .optional();
    }

    private List<Issue> issues(UUID batchId) {
        return jdbc.sql("select row_number, qualification_code, severity, field, message from import_issue where batch_id = ? order by row_number is null, row_number, severity")
            .param(batchId).query(Issue.class).list();
    }
}
