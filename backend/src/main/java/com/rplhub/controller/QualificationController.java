package com.rplhub.controller;

import com.rplhub.model.AppUser;
import com.rplhub.model.ImportResult;
import com.rplhub.model.QualificationContent;
import com.rplhub.model.VersionDetail;
import com.rplhub.model.VersionSummary;
import com.rplhub.service.ImportService;
import com.rplhub.service.QualificationService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Qualification import and versioning (ROLE_ADMIN enforced in SecurityConfig). */
@RestController
@RequestMapping("/api")
class QualificationController {

    private final ImportService imports;
    private final QualificationService qualifications;

    QualificationController(ImportService imports, QualificationService qualifications) {
        this.imports = imports;
        this.qualifications = qualifications;
    }

    @PostMapping("/imports")
    ImportResult upload(@AuthenticationPrincipal AppUser user, @RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        return imports.importFile(user.id(), name, file.getBytes());
    }

    @GetMapping("/imports")
    List<Map<String, Object>> listImports() {
        return imports.listBatches();
    }

    @GetMapping("/imports/{id}")
    ImportResult getImport(@PathVariable UUID id) {
        return imports.get(id);
    }

    @GetMapping("/qualification-versions")
    List<VersionSummary> list(@RequestParam(required = false) String status) {
        return qualifications.list(status);
    }

    @GetMapping("/qualification-versions/{id}")
    VersionDetail get(@PathVariable UUID id) {
        return qualifications.get(id);
    }

    @PutMapping("/qualification-versions/{id}")
    VersionDetail update(@AuthenticationPrincipal AppUser user, @PathVariable UUID id, @Valid @RequestBody QualificationContent body) {
        return qualifications.update(user.id(), id, body);
    }

    @PostMapping("/qualification-versions/{id}/publish")
    VersionDetail publish(@AuthenticationPrincipal AppUser user, @PathVariable UUID id) {
        return qualifications.publish(user, id);
    }
}
