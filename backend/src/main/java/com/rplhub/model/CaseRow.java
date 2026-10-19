package com.rplhub.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CaseRow(UUID id, String caseNumber, UUID workerId, String trade, int experienceYears, String skills,
                      String details, String status, int version, UUID qualificationVersionId, UUID assessorId,
                      OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime submittedAt) {}
