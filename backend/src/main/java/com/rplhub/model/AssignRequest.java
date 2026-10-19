package com.rplhub.model;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignRequest(@NotNull UUID assessorId) {}
