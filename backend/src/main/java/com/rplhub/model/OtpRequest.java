package com.rplhub.model;

import jakarta.validation.constraints.Pattern;

public record OtpRequest(@Pattern(regexp = Phone.PATTERN, message = Phone.INVALID) String phone) {}
