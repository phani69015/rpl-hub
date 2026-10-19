package com.rplhub.model;

import java.util.List;
import java.util.UUID;

public record CompetencyView(UUID id, String code, String title, boolean mandatory, int maxScore, String rubric,
                             List<String> evidenceRequirements) {}
