package com.rplhub.service;

import com.rplhub.model.AppUser;
import com.rplhub.repository.UserRepository;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** On first start imports and publishes the bundled default trades, and adds demo assessors when none exist. */
@Component
class DefaultQualificationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultQualificationSeeder.class);
    private static final String RESOURCE = "seed/qualifications.json";
    /** Demo assessors (phone, can decide, trades) covering every bundled trade. */
    private static final List<DemoAssessor> DEMO_ASSESSORS = List.of(
        new DemoAssessor("+919000000001", true, List.of("Electrical", "Electronics", "Solar", "HVAC", "Mechanical", "Machining", "Welding")),
        new DemoAssessor("+919000000002", true, List.of("Plumbing", "Carpentry", "Construction", "Painting", "Tiling", "Housekeeping", "Security")),
        new DemoAssessor("+919000000003", false, List.of("Automotive", "Driving", "Agriculture", "Tailoring", "Beauty", "Cooking", "Healthcare", "IT")));

    private record DemoAssessor(String phone, boolean canDecide, List<String> trades) {}

    private final boolean enabled;
    private final String adminPhone;
    private final UserRepository users;
    private final ImportService imports;
    private final QualificationService qualifications;

    DefaultQualificationSeeder(@Value("${app.seed.enabled:true}") boolean enabled,
                               @Value("${app.bootstrap-admin-phone:}") String adminPhone, UserRepository users,
                               ImportService imports, QualificationService qualifications) {
        this.enabled = enabled;
        this.adminPhone = adminPhone;
        this.users = users;
        this.imports = imports;
        this.qualifications = qualifications;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled || adminPhone.isBlank()) return;
        seedAssessors();
        if (!qualifications.list(null).isEmpty()) return;
        users.saveAdmin(adminPhone);
        AppUser admin = users.findOrCreateWorker(adminPhone);
        byte[] content = new ClassPathResource(RESOURCE).getContentAsByteArray();
        var result = imports.importFile(admin.id(), "default-qualifications.json", content);
        qualifications.list("DRAFT").forEach(v -> qualifications.publish(admin, v.id()));
        log.info("Seeded {} default qualifications ({} rejected)", result.versionsCreated(), result.qualificationsRejected());
    }

    private void seedAssessors() {
        if (!users.assessors().isEmpty()) return;
        DEMO_ASSESSORS.forEach(a -> users.saveAssessor(a.phone(), a.trades(), a.canDecide()));
        log.info("Seeded {} demo assessors", DEMO_ASSESSORS.size());
    }
}
