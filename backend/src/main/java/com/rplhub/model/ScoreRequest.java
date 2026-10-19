package com.rplhub.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ScoreRequest(@Min(0) int score, @Size(max = 2000) String comment) {}
