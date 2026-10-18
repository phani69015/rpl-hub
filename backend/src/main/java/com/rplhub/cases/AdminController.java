package com.rplhub.cases;

import com.rplhub.user.AppUser;
import com.rplhub.user.UserRepository;
import com.rplhub.user.UserRepository.AssessorView;
import com.rplhub.workflow.WorkflowGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Administrator-only (enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
class AdminController {

    record ConfirmQualification(@NotNull UUID versionId) {}
    record Assign(@NotNull UUID assessorId) {}
    record AssessorRequest(@Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "invalid phone number") String phone,
                           @NotEmpty List<String> trades, boolean canDecide) {}

    private final AdminCaseService admin;
    private final CaseRepository cases;
    private final WorkflowGateway workflows;
    private final UserRepository users;
    private final JdbcClient jdbc;

    AdminController(AdminCaseService admin, CaseRepository cases, WorkflowGateway workflows, UserRepository users, JdbcClient jdbc) {
        this.admin = admin;
        this.cases = cases;
        this.workflows = workflows;
        this.users = users;
        this.jdbc = jdbc;
    }

    /** Submitted cases with their workflow state (pending / failed / completed). */
    @GetMapping("/cases")
    List<Map<String, Object>> cases() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CaseRow c : cases.all()) {
            var m = new LinkedHashMap<String, Object>();
            m.put("case", c);
            m.put("workflow", workflows.status(c.id()));
            out.add(m);
        }
        return out;
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
    CaseView assign(@AuthenticationPrincipal AppUser u, @PathVariable UUID id, @Valid @RequestBody Assign b) {
        return admin.assign(u, id, b.assessorId());
    }

    @GetMapping("/cases/{id}/workflow")
    WorkflowGateway.Status workflow(@PathVariable UUID id) {
        return admin.workflowStatus(id);
    }

    @PostMapping("/cases/{id}/workflow/retry")
    WorkflowGateway.Status retry(@AuthenticationPrincipal AppUser u, @PathVariable UUID id) {
        return admin.retryWorkflow(u, id);
    }

    @GetMapping("/cases/{id}/audit")
    List<Map<String, Object>> caseAudit(@PathVariable UUID id) {
        return admin.auditTrail(id);
    }

    @GetMapping("/audit")
    List<Map<String, Object>> audit() {
        return jdbc.sql("""
                select a.created_at, a.action, u.phone as actor, a.entity_type, a.entity_id, a.detail::text as detail
                from audit_event a left join app_user u on u.id = a.actor_id order by a.created_at desc limit 200""")
            .query().listOfRows();
    }

    @GetMapping("/assessors")
    List<AssessorView> assessors() {
        return users.assessors();
    }

    @PostMapping("/assessors")
    AssessorView saveAssessor(@AuthenticationPrincipal AppUser u, @Valid @RequestBody AssessorRequest r) {
        try {
            return users.saveAssessor(r.phone(), r.trades().stream().filter(t -> !t.isBlank()).toList(), r.canDecide());
        } catch (IllegalStateException e) {
            throw new ApiExceptions.InvalidException(e.getMessage());
        }
    }
}
