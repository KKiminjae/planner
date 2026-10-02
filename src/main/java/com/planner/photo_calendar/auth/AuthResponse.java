package com.planner.photo_calendar.auth;

public record AuthResponse(Long ownerId, String username) {
    public static AuthResponse from(OwnerPrincipal principal) {
        return new AuthResponse(principal.ownerId(), principal.username());
    }
}
