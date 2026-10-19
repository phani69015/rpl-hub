package com.rplhub.service;

import static com.rplhub.model.CaseStatus.DECIDED;
import static com.rplhub.model.CaseStatus.EVIDENCE_REQUESTED;
import static com.rplhub.model.CaseStatus.IN_ASSESSMENT;

import com.rplhub.exception.ConflictException;
import com.rplhub.exception.InvalidException;
import com.rplhub.model.AppUser;
import com.rplhub.model.CaseRow;
import com.rplhub.model.CaseView;
import com.rplhub.model.EvidenceDownload;
import com.rplhub.repository.AssessmentRepository;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.CaseRepository;
import com.rplhub.repository.EvidenceRepository;
import com.rplhub.repository.QualificationRepository;
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
import org.springframework.http.HttpStatus;
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

    private static final Set<String> TYPES = Set.of("application/pdf", "text/plain");
    private static final int MAX_FILENAME = 255;

    private final CaseRepository cases;
    private final EvidenceRepository evidence;
    private final AssessmentRepository assessments;
    private final QualificationRepository qualifications;
    private final CaseService caseService;
    private final AuditRepository audit;
    private final WorkflowGateway workflows;
    private final Path root;

    EvidenceService(CaseRepository cases, EvidenceRepository evidence, AssessmentRepository assessments, QualificationRepository qualifications,
                    CaseService caseService, AuditRepository audit, WorkflowGateway workflows, @Value("${app.evidence.dir}") String dir) {
        this.cases = cases;
        this.evidence = evidence;
        this.assessments = assessments;
        this.qualifications = qualifications;
        this.caseService = caseService;
        this.audit = audit;
        this.workflows = workflows;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    @Transactional
    public CaseView add(AppUser u, UUID caseId, UUID evidenceId, UUID competencyId, String note, MultipartFile file) throws IOException {
        CaseRow c = caseService.access(u, caseId);
        if (!acceptsEvidence(u, c)) throw new ConflictException("Evidence cannot be added while the case is " + c.status());
        if (file.isEmpty()) throw new InvalidException("File is empty");
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!(type.startsWith("image/") || type.startsWith("video/") || TYPES.contains(type)))
            throw new InvalidException("Unsupported file type; use an image, video, PDF or text file");
        if (competencyId != null && (c.qualificationVersionId() == null
                || qualifications.competencyMaxScore(competencyId, c.qualificationVersionId()).isEmpty()))
            throw new InvalidException("Competency does not belong to this case's qualification");

        var existing = evidence.owner(evidenceId);
        if (existing.isPresent()) {
            if (!existing.get().caseId().equals(caseId) || !existing.get().uploadedBy().equals(u.id())) throw new ConflictException("Evidence id already used");
            return caseService.get(u, caseId); // repeated sync
        }
        store(file, path(caseId, evidenceId));
        String name = Path.of(file.getOriginalFilename() == null ? "evidence" : file.getOriginalFilename()).getFileName().toString();
        evidence.insert(evidenceId, caseId, competencyId, u.id(), name.length() > MAX_FILENAME ? name.substring(0, MAX_FILENAME) : name, type,
            file.getSize(), note == null || note.isBlank() ? null : note.trim());
        audit.record(u.id(), "EVIDENCE_ADDED", "rpl_case", caseId, Map.of("evidenceId", evidenceId, "filename", name));
        return caseService.get(u, caseId);
    }

    /** Worker confirms the requested evidence has been uploaded; the assessment resumes with all prior progress. */
    @Transactional
    public CaseView fulfil(AppUser u, UUID caseId) {
        caseService.access(u, caseId);
        if (!u.isWorker()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the worker can submit requested evidence");
        CaseRow c = cases.lock(caseId).orElseThrow();
        if (c.status().equals(IN_ASSESSMENT)) return caseService.get(u, caseId); // repeated request
        if (!c.status().equals(EVIDENCE_REQUESTED)) throw new ConflictException("No evidence has been requested");
        if (!evidence.uploadedSinceLastRequest(caseId)) throw new InvalidException("Upload the requested evidence first");
        assessments.fulfilEvidenceRequests(caseId);
        cases.transition(caseId, List.of(EVIDENCE_REQUESTED), IN_ASSESSMENT);
        audit.record(u.id(), "EVIDENCE_SUBMITTED", "rpl_case", caseId, Map.of());
        workflows.pokeAfterCommit(caseId);
        return caseService.get(u, caseId);
    }

    public EvidenceDownload download(AppUser u, UUID caseId, UUID evidenceId) {
        caseService.access(u, caseId);
        var meta = evidence.file(caseId, evidenceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence not found"));
        var resource = new FileSystemResource(path(caseId, evidenceId));
        if (!resource.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File missing");
        return new EvidenceDownload(resource, meta.filename(), meta.contentType());
    }

    /** Workers may add evidence until the case is decided; assessors only while assessing. */
    private static boolean acceptsEvidence(AppUser u, CaseRow c) {
        if (u.isWorker()) return !c.status().equals(DECIDED);
        return u.isAssessor() && Set.of(IN_ASSESSMENT, EVIDENCE_REQUESTED).contains(c.status());
    }

    private static void store(MultipartFile file, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), "up-", ".tmp");
        try (var in = file.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Path path(UUID caseId, UUID evidenceId) {
        Path p = root.resolve(caseId.toString()).resolve(evidenceId.toString()).normalize();
        if (!p.startsWith(root)) throw new IllegalStateException("Invalid path");
        return p;
    }
}
