package com.rplhub.model;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConfirmQualification(@NotNull UUID versionId) {}
