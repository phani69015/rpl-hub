package com.rplhub.service;

import com.rplhub.exception.InvalidException;
import com.rplhub.model.ImportResult;
import com.rplhub.model.Issue;
import com.rplhub.model.QualificationContent;
import com.rplhub.model.RawRow;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.ImportRepository;
import com.rplhub.repository.QualificationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Parse -> validate -> save drafts. Idempotent: a qualification whose content hash already exists is skipped,
 * so repeating an import (or a retried workflow step) never creates duplicate records.
 */
@Service
public class ImportService {

    private final FileParser parser;
    private final RowValidator validator;
    private final QualificationRepository qualifications;
    private final ImportRepository imports;
    private final AuditRepository audit;

    ImportService(FileParser parser, RowValidator validator, QualificationRepository qualifications, ImportRepository imports, AuditRepository audit) {
        this.parser = parser;
        this.validator = validator;
        this.qualifications = qualifications;
        this.imports = imports;
        this.audit = audit;
    }

    @Transactional
    public ImportResult importFile(UUID actor, String filename, byte[] content) {
        String sourceType = parser.sourceType(filename);
        List<RawRow> rows = parser.parse(filename, content);
        RowValidator.Result validated = validator.validate(rows);

        UUID batchId = imports.insertBatch(sourceType, filename, rows.size(), actor);
        List<Issue> issues = new ArrayList<>(validated.issues());
        int created = 0, duplicates = 0;
        for (RowValidator.Accepted a : validated.accepted()) {
            QualificationContent c = a.content();
            if (c.source().isEmpty()) {
                c = new QualificationContent(c.title(), c.trade(), c.nsqfLevel(), "upload:" + filename, c.competencies());
            }
            UUID qid = qualifications.lockQualification(a.code());
            var existing = qualifications.versionWithHash(qid, c.contentHash());
            if (existing.isPresent()) {
                duplicates++;
                issues.add(Issue.info(a.code(), "Identical to existing version " + existing.get() + "; skipped"));
                continue;
            }
            for (String other : qualifications.codesWithSameTitleAndTrade(a.code(), c.title(), c.trade())) {
                issues.add(Issue.warning(null, a.code(), "title", "Possible duplicate of existing qualification '" + other + "' (same title and trade)"));
            }
            UUID versionId = qualifications.insertDraft(qid, c, batchId);
            created++;
            issues.add(Issue.info(a.code(), "Draft version " + qualifications.versionNumber(versionId) + " created"));
            audit.record(actor, "QUALIFICATION_DRAFT_IMPORTED", "qualification_version", versionId,
                Map.of("batchId", batchId, "code", a.code(), "filename", filename));
        }

        imports.updateTotals(batchId, created, duplicates, validated.rejected());
        imports.insertIssues(batchId, issues);
        audit.record(actor, "QUALIFICATION_IMPORT", "import_batch", batchId,
            Map.of("filename", filename, "created", created, "duplicates", duplicates, "rejected", validated.rejected()));
        return new ImportResult(batchId, filename, sourceType, rows.size(), created, duplicates, validated.rejected(), issues);
    }

    public List<Map<String, Object>> listBatches() {
        return imports.listBatches();
    }

    public ImportResult get(UUID id) {
        return imports.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
    }
}
