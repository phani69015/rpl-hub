package com.rplhub.qualification;

import com.rplhub.qualification.ImportService.ImportResult;
import com.rplhub.qualification.QualificationRepository.VersionDetail;
import com.rplhub.qualification.QualificationRepository.VersionSummary;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.rplhub.user.AppUser;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Admin endpoints (ROLE_ADMIN enforced in SecurityConfig). */
@RestController
class QualificationController {

    private final ImportService imports;
    private final QualificationService qualifications;

    QualificationController(ImportService imports, QualificationService qualifications) {
        this.imports = imports;
        this.qualifications = qualifications;
    }

    @PostMapping("/api/imports")
    ImportResult upload(@AuthenticationPrincipal AppUser user, @RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        return imports.importFile(user.id(), name, file.getBytes());
    }

    @GetMapping("/api/imports")
    List<Map<String, Object>> listImports() {
        return imports.listBatches();
    }

    @GetMapping("/api/imports/{id}")
    ImportResult getImport(@PathVariable UUID id) {
        return imports.get(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import not found"));
    }

    @GetMapping("/api/qualification-versions")
    List<VersionSummary> list(@RequestParam(required = false) String status) {
        return qualifications.list(status);
    }

    @GetMapping("/api/qualification-versions/{id}")
    VersionDetail get(@PathVariable UUID id) {
        return qualifications.get(id);
    }

    @PutMapping("/api/qualification-versions/{id}")
    VersionDetail update(@AuthenticationPrincipal AppUser user, @PathVariable UUID id, @Valid @RequestBody QualificationContent body) {
        return qualifications.update(user.id(), id, body);
    }

    @PostMapping("/api/qualification-versions/{id}/publish")
    VersionDetail publish(@AuthenticationPrincipal AppUser user, @PathVariable UUID id) {
        return qualifications.publish(user.id(), id);
    }

    @ExceptionHandler(FileParser.ImportFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badFile(FileParser.ImportFormatException e) {
        return Map.of("error", e.getMessage());
    }
}
