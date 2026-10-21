package com.nexo.model;

public record UserProfile(int id, String username, String displayName, String role, boolean active) {
    public UserProfile(int id, String username, String displayName, String role) {
        this(id, username, displayName, role, true);
    }

    public boolean isOwner() {
        return "DUEÑA".equals(role);
    }
}
