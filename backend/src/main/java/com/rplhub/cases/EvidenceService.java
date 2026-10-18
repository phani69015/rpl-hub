package com.rplhub.cases;

import com.rplhub.audit.AuditService;
import com.rplhub.cases.ApiExceptions.ConflictException;
import com.rplhub.cases.ApiExceptions.InvalidException;
import com.rplhub.user.AppUser;
import com.rplhub.workflow.WorkflowGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Evidence files live on the local filesystem (outside the web root) and are only served through case access checks.
 * The evidence id is client-generated, so a repeated upload (offline sync) is a no-op.
 */
@Service
public class EvidenceService {

    public record Download(Resource file, String filename, String contentType) {}

    private static final Set<String> TYPES = Set.of("application/pdf", "text/plain");

    private final CaseRepository cases;
    private final CaseService caseService;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final WorkflowGateway workflows;
    private final Path root;

    EvidenceService(CaseRepository cases, CaseService caseService, JdbcClient jdbc, AuditService audit, WorkflowGateway workflows,
                    @Value("${app.evidence.dir}") String dir) {
        this.cases = cases;
        this.caseService = caseService;
        this.jdbc = jdbc;
        this.audit = audit;
        this.workflows = workflows;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Transactional
    public CaseView add(AppUser u, UUID caseId, UUID evidenceId, UUID competencyId, String note, MultipartFile file) throws IOException {
        CaseRow c = caseService.access(u, caseId);
        boolean open = u.isWorker() ? !c.status().equals("DECIDED") : (u.isAssessor() && Set.of("IN_ASSESSMENT", "EVIDENCE_REQUESTED").contains(c.status()));
        if (!open) throw new ConflictException("Evidence cannot be added while the case is " + c.status(), null);
        if (file.isEmpty()) throw new InvalidException("File is empty");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!(type.startsWith("image/") || type.startsWith("video/") || TYPES.contains(type)))
            throw new InvalidException("Unsupported file type; use an image, video, PDF or text file");
        if (competencyId != null && (c.qualificationVersionId() == null || jdbc.sql("select count(*) from competency where id = ? and version_id = ?")
                .params(competencyId, c.qualificationVersionId()).query(Integer.class).single() == 0))
            throw new InvalidException("Competency does not belong to this case's qualification");

        var existing = jdbc.sql("select case_id, uploaded_by from evidence where id = ?").param(evidenceId)
            .query((rs, i) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}).optional();
        if (existing.isPresent()) {
            if (!existing.get()[0].equals(caseId) || !existing.get()[1].equals(u.id())) throw new ConflictException("Evidence id already used", null);
            return caseService.get(u, caseId); // repeated sync
        }
        Path target = path(caseId, evidenceId);
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "up-", ".tmp");
        try (var in = file.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
        String name = Path.of(file.getOriginalFilename() == null ? "evidence" : file.getOriginalFilename()).getFileName().toString();
        jdbc.sql("insert into evidence(id, case_id, competency_id, uploaded_by, filename, content_type, size_bytes, note) values (?,?,?,?,?,?,?,?)")
            .params(evidenceId, caseId, competencyId, u.id(), name.length() > 255 ? name.substring(0, 255) : name, type, file.getSize(),
                note == null || note.isBlank() ? null : note.trim()).update();
        audit.record(u.id(), "EVIDENCE_ADDED", "rpl_case", caseId, Map.of("evidenceId", evidenceId, "filename", name));
        return caseService.get(u, caseId);
    }

    /** Worker confirms the requested evidence has been uploaded; the assessment resumes with all prior progress. */
    @Transactional
    public CaseView fulfil(AppUser u, UUID caseId) {
        CaseRow c = caseService.access(u, caseId);
        if (!u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the worker can submit requested evidence");
        c = cases.lock(caseId).orElseThrow();
        if (c.status().equals("IN_ASSESSMENT")) return caseService.get(u, caseId); // repeated request
        if (!c.status().equals("EVIDENCE_REQUESTED")) throw new ConflictException("No evidence has been requested", null);
        int added = jdbc.sql("""
                select count(*) from evidence e where e.case_id = ? and e.created_at >
                  (select max(created_at) from evidence_request where case_id = ?)""").params(caseId, caseId).query(Integer.class).single();
        if (added == 0) throw new InvalidException("Upload the requested evidence first");
        jdbc.sql("update evidence_request set fulfilled_at = now() where case_id = ? and fulfilled_at is null").param(caseId).update();
        cases.transition(caseId, List.of("EVIDENCE_REQUESTED"), "IN_ASSESSMENT");
        audit.record(u.id(), "EVIDENCE_SUBMITTED", "rpl_case", caseId, Map.of());
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    public Download download(AppUser u, UUID caseId, UUID evidenceId) {
        caseService.access(u, caseId);
        var meta = jdbc.sql("select filename, content_type from evidence where id = ? and case_id = ?").params(evidenceId, caseId)
            .query((rs, i) -> new String[] {rs.getString(1), rs.getString(2)}).optional()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
        return new Download(new FileSystemResource(path(caseId, evidenceId)), meta[0], meta[1]);
    }

    private Path path(UUID caseId, UUID evidenceId) {
        Path p = root.resolve(caseId.toString()).resolve(evidenceId.toString()).normalize();
        if (!p.startsWith(root)) throw new IllegalStateException("Invalid path");
        return p;
    }
}
