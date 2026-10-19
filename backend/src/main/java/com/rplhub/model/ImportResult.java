package com.rplhub.model;

import java.util.List;
import java.util.UUID;

public record ImportResult(UUID batchId, String filename, String sourceType, int totalRows, int versionsCreated,
                           int duplicatesSkipped, int qualificationsRejected, List<Issue> issues) {}
