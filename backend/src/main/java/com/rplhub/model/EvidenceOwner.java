package com.rplhub.model;

import java.util.UUID;

/** Who uploaded an evidence item and to which case; used to make repeated uploads idempotent. */
public record EvidenceOwner(UUID caseId, UUID uploadedBy) {}
