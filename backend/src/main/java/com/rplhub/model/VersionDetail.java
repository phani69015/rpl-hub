package com.rplhub.model;

import java.util.List;

public record VersionDetail(VersionSummary summary, List<CompetencyView> competencies) {}
