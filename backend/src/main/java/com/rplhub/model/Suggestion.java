package com.rplhub.model;

import java.util.UUID;

public record Suggestion(UUID versionId, double confidence, String explanation, boolean flagged, String source) {}
