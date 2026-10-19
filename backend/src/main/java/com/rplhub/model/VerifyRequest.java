package com.rplhub.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyRequest(@Pattern(regexp = Phone.PATTERN, message = Phone.INVALID) String phone, @NotBlank String otp) {}
