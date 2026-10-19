package com.rplhub.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvidenceRequestBody(@NotBlank @Size(max = 2000) String message) {}
