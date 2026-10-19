package com.rplhub.controller;

import com.rplhub.model.AdminCaseRow;
import com.rplhub.model.AppUser;
import com.rplhub.model.AssessorRequest;
import com.rplhub.model.AssessorView;
import com.rplhub.model.AssignRequest;
import com.rplhub.model.CaseView;
import com.rplhub.model.ConfirmQualification;
import com.rplhub.model.WorkflowStatus;
import com.rplhub.service.AdminCaseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrator-only (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
class AdminController {

    private final AdminCaseService admin;

    AdminController(AdminCaseService admin) {
        this.admin = admin;
    }

    @GetMapping("/cases")
    List<AdminCaseRow> cases() {
        return admin.listCases();
    }

    @PostMapping("/cases/{id}/confirm-qualification")
    CaseView confirm(@AuthenticationPrincipal AppUser u, @PathVariable UUID id, @Valid @RequestBody ConfirmQualification b) {
        return admin.confirmQualification(u, id, b.versionId());
    }

    @GetMapping("/cases/{id}/assessor-candidates")
    List<AssessorView> candidates(@PathVariable UUID id) {
        return admin.candidates(id);
    }

    @PostMapping("/cases/{id}/assign")
    CaseView assign(@AuthenticationPrincipal AppUser u, @PathVariable UUID id, @Valid @RequestBody AssignRequest b) {
        return admin.assign(u, id, b.assessorId());
    }

    @GetMapping("/cases/{id}/workflow")
    WorkflowStatus workflow(@PathVariable UUID id) {
        return admin.workflowStatus(id);
    }

    @PostMapping("/cases/{id}/workflow/retry")
    WorkflowStatus retry(@AuthenticationPrincipal AppUser u, @PathVariable UUID id) {
        return admin.retryWorkflow(u, id);
    }

    @GetMapping("/cases/{id}/audit")
    List<Map<String, Object>> caseAudit(@PathVariable UUID id) {
        return admin.caseAuditTrail(id);
    }

    @GetMapping("/audit")
    List<Map<String, Object>> audit() {
        return admin.recentAudit();
    }

    @GetMapping("/assessors")
    List<AssessorView> assessors() {
        return admin.assessors();
    }

    @PostMapping("/assessors")
    AssessorView saveAssessor(@Valid @RequestBody AssessorRequest r) {
        return admin.saveAssessor(r);
    }
}
