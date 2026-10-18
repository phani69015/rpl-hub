package com.rplhub.user;

import java.util.UUID;

public record AppUser(UUID id, String phone, String role, boolean canPublish, boolean canDecide) {
    public boolean isAdmin() { return role.equals("ADMIN"); }
    public boolean isAssessor() { return role.equals("ASSESSOR"); }
    public boolean isWorker() { return role.equals("WORKER"); }
}
