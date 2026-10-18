package com.rplhub.qualification;

import com.rplhub.audit.AuditService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parse -> validate -> save drafts. Idempotent: a qualification whose content hash already exists is skipped,
 * so repeating an import (or a retried workflow step) never creates duplicate records.
 */
@Service
class ImportService {

    record ImportResult(UUID batchId, String filename, String sourceType, int totalRows, int versionsCreated,
                        int duplicatesSkipped, int qualificationsRejected, List<Issue> issues) {}

    private final FileParser parser;
    private final RowValidator validator;
    private final QualificationRepository repo;
    private final JdbcClient jdbc;
    private final AuditService audit;

    ImportService(FileParser parser, RowValidator validator, QualificationRepository repo, JdbcClient jdbc, AuditService audit) {
        this.parser = parser;
        this.validator = validator;
        this.repo = repo;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    ImportResult importFile(UUID actor, String filename, byte[] content) {
        String sourceType = parser.sourceType(filename);
        List<RawRow> rows = parser.parse(filename, content);
        RowValidator.Result validated = validator.validate(rows);

        UUID batchId = UUID.randomUUID();
        jdbc.sql("""
                insert into import_batch(id, source_type, filename, total_rows, versions_created, duplicates_skipped, qualifications_rejected, created_by)
                values (?,?,?,?,0,0,0,?)""")
            .params(batchId, sourceType, filename, rows.size(), actor).update();

        List<Issue> issues = new ArrayList<>(validated.issues());
        int created = 0, duplicates = 0;
        for (RowValidator.Accepted a : validated.accepted()) {
            QualificationContent c = a.content();
            if (c.source().isEmpty()) {
                c = new QualificationContent(c.title(), c.trade(), c.nsqfLevel(), "upload:" + filename, c.competencies());
            }
            UUID qid = repo.lockQualification(a.code());
            var existing = repo.versionWithHash(qid, c.contentHash());
            if (existing.isPresent()) {
                duplicates++;
                issues.add(Issue.info(a.code(), "Identical to existing version " + existing.get() + "; skipped"));
                continue;
            }
            for (String other : repo.codesWithSameTitleAndTrade(a.code(), c.title(), c.trade())) {
                issues.add(Issue.warning(null, a.code(), "title", "Possible duplicate of existing qualification '" + other + "' (same title and trade)"));
            }
            UUID versionId = repo.insertDraft(qid, c, batchId);
            created++;
            issues.add(Issue.info(a.code(), "Draft version " + repo.versionNumber(versionId) + " created"));
            audit.record(actor, "QUALIFICATION_DRAFT_IMPORTED", "qualification_version", versionId,
                Map.of("batchId", batchId, "code", a.code(), "filename", filename));
        }

        jdbc.sql("update import_batch set versions_created=?, duplicates_skipped=?, qualifications_rejected=? where id=?")
            .params(created, duplicates, validated.rejected(), batchId).update();
        for (Issue i : issues) {
            jdbc.sql("insert into import_issue(id, batch_id, row_number, qualification_code, severity, field, message) values (?,?,?,?,?,?,?)")
                .params(UUID.randomUUID(), batchId, i.rowNumber(), i.qualificationCode(), i.severity(), i.field(), i.message()).update();
        }
        audit.record(actor, "QUALIFICATION_IMPORT", "import_batch", batchId,
            Map.of("filename", filename, "created", created, "duplicates", duplicates, "rejected", validated.rejected()));
        return new ImportResult(batchId, filename, sourceType, rows.size(), created, duplicates, validated.rejected(), issues);
    }

    List<Map<String, Object>> listBatches() {
        return jdbc.sql("""
                select id, source_type, filename, total_rows, versions_created, duplicates_skipped, qualifications_rejected, created_at
                from import_batch order by created_at desc limit 50""").query().listOfRows();
    }

    java.util.Optional<ImportResult> get(UUID id) {
        return jdbc.sql("select id, filename, source_type, total_rows, versions_created, duplicates_skipped, qualifications_rejected from import_batch where id = ?")
            .param(id).query((rs, n) -> new Object[] {rs.getString("filename"), rs.getString("source_type"), rs.getInt("total_rows"),
                rs.getInt("versions_created"), rs.getInt("duplicates_skipped"), rs.getInt("qualifications_rejected")})
            .optional().map(b -> new ImportResult(id, (String) b[0], (String) b[1], (Integer) b[2], (Integer) b[3], (Integer) b[4], (Integer) b[5],
                jdbc.sql("select row_number, qualification_code, severity, field, message from import_issue where batch_id = ? order by row_number nulls last, severity")
                    .param(id).query(Issue.class).list()));
    }
}
