package com.rplhub.model;

import java.util.UUID;

public record LockedVersion(UUID id, UUID qualificationId, int version, String status, String contentHash) {}
