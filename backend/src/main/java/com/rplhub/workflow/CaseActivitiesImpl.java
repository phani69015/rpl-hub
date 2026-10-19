package com.rplhub.workflow;

import static com.rplhub.model.CaseStatus.AWAITING_DECISION;
import static com.rplhub.model.CaseStatus.AWAITING_QUALIFICATION;
import static com.rplhub.model.CaseStatus.NEW;
import static com.rplhub.model.CaseStatus.REPORT_PENDING;

import com.rplhub.repository.AssessmentRepository;
import com.rplhub.repository.AuditRepository;
import com.rplhub.repository.CaseRepository;
import com.rplhub.repository.QualificationRepository;
import com.rplhub.service.MatchingService;
import com.rplhub.service.SummaryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class CaseActivitiesImpl implements CaseActivities {

    private final CaseRepository cases;
    private final AssessmentRepository assessments;
    private final MatchingService matching;
    private final QualificationRepository qualifications;
    private final SummaryService summaries;
    private final AuditRepository audit;
    private final TransactionTemplate tx;

    CaseActivitiesImpl(CaseRepository cases, AssessmentRepository assessments, MatchingService matching, QualificationRepository qualifications,
                       SummaryService summaries, AuditRepository audit, TransactionTemplate tx) {
        this.cases = cases;
        this.assessments = assessments;
        this.matching = matching;
        this.qualifications = qualifications;
        this.summaries = summaries;
        this.audit = audit;
        this.tx = tx;
    }

    @Override
    public String stage(String caseId) {
        return cases.find(UUID.fromString(caseId)).orElseThrow().status();
    }

    @Override
    public void match(String caseId) {
        UUID id = UUID.fromString(caseId);
        var c = cases.find(id).orElseThrow();
        if (!c.status().equals(NEW)) return;
        // external call outside any transaction/lock
        var suggestions = matching.suggest(c, qualifications.published());
        tx.executeWithoutResult(s -> {
            if (cases.lock(id).filter(r -> r.status().equals(NEW)).isEmpty()) return;
            cases.replaceSuggestions(id, suggestions);
            cases.transition(id, List.of(NEW), AWAITING_QUALIFICATION);
            audit.record(null, "MATCH_SUGGESTED", "rpl_case", id, Map.of("suggestions", suggestions.size(),
                "source", suggestions.isEmpty() ? "none" : suggestions.get(0).source()));
        });
    }

    @Override
    public void generateReport(String caseId) {
        UUID id = UUID.fromString(caseId);
        if (!cases.find(id).orElseThrow().status().equals(REPORT_PENDING)) return;
        String summary = summaries.generate(id).orElse(null); // external call outside any transaction/lock
        tx.executeWithoutResult(s -> {
            if (cases.lock(id).filter(r -> r.status().equals(REPORT_PENDING)).isEmpty()) return;
            assessments.storeAiSummary(id, summary);
            cases.transition(id, List.of(REPORT_PENDING), AWAITING_DECISION);
            audit.record(null, "REPORT_GENERATED", "rpl_case", id, Map.of("aiSummary", summary != null));
        });
    }
}
