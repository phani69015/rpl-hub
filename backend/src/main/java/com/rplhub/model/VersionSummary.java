package com.rplhub.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record VersionSummary(UUID id, String code, int version, String title, String trade, Integer nsqfLevel,
                             String source, String status, OffsetDateTime createdAt, OffsetDateTime publishedAt) {}
