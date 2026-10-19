package com.rplhub.model;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record AssessorRequest(@Pattern(regexp = Phone.PATTERN, message = Phone.INVALID) String phone,
                              @NotEmpty List<String> trades, boolean canDecide) {}
