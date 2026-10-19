package com.rplhub.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Profile(@NotBlank @Size(max = 120) String fullName, @Size(max = 120) String primaryTrade,
                      @Min(0) @Max(60) Integer yearsExperience, @Size(max = 4000) String skills) {}
