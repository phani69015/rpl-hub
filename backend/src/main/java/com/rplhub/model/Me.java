package com.rplhub.model;

public record Me(String phone, String role, boolean canDecide, boolean canPublish) {
    public static Me of(AppUser u) {
        return new Me(u.phone(), u.role(), u.canDecide(), u.canPublish());
    }
}
