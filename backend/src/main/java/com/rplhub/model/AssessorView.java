package com.rplhub.model;

import java.util.List;
import java.util.UUID;

public record AssessorView(UUID id, String phone, boolean canDecide, List<String> trades) {}
