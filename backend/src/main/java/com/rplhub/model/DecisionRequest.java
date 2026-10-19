package com.rplhub.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DecisionRequest(@NotBlank @Pattern(regexp = Outcome.PATTERN) String outcome, @Size(max = 4000) String rationale) {}
