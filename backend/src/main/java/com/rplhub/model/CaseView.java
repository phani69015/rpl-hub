package com.rplhub.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CaseView(
    UUID id, String caseNumber, String status, int version, String trade, int experienceYears, String skills, String details,
    String workerPhone, String workerName, UUID assessorId, String assessorPhone,
    VersionDetail qualification, List<SuggestionView> suggestions, List<ScoreView> scores, List<EvidenceView> evidence,
    List<EvidenceRequestView> evidenceRequests, SubmissionView submission, DecisionView decision,
    OffsetDateTime createdAt, OffsetDateTime submittedAt) {

    public record SuggestionView(UUID versionId, String code, String title, int version, double confidence, String explanation,
                                 boolean flagged, String source) {}
    public record ScoreView(UUID competencyId, int score, String comment, OffsetDateTime updatedAt) {}
    public record EvidenceView(UUID id, UUID competencyId, String filename, String contentType, long sizeBytes, String note,
                               OffsetDateTime createdAt) {}
    public record EvidenceRequestView(UUID id, String message, OffsetDateTime createdAt, OffsetDateTime fulfilledAt) {}
    public record SubmissionView(String recommendation, String notes, OffsetDateTime submittedAt, String aiSummary,
                                 OffsetDateTime aiGeneratedAt) {}
    public record DecisionView(String outcome, String rationale, String decidedBy, OffsetDateTime decidedAt) {}
}
