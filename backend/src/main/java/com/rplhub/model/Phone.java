package com.rplhub.model;

/** Shared validation pattern for phone numbers (international, optional leading +). */
public final class Phone {

    public static final String PATTERN = "^\\+?[0-9]{10,15}$";
    public static final String INVALID = "invalid phone number";

    private Phone() {}
}
