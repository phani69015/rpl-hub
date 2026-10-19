package com.rplhub.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmitRequest(@NotBlank @Pattern(regexp = Outcome.PATTERN) String recommendation, @Size(max = 4000) String notes) {}
