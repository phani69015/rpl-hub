package com.rplhub.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A worker's declaration of prior learning; `submit` turns the draft into a submitted case. */
public record Declaration(
    @NotBlank @Size(max = 120) String trade,
    @Min(0) @Max(60) int experienceYears,
    @NotBlank @Size(max = 4000) String skills,
    @Size(max = 4000) String details,
    int baseVersion,
    boolean submit) {}
