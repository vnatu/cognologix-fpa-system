package com.cognologix.fpa.general.dto;

import com.cognologix.fpa.general.AppUser;
import com.cognologix.fpa.general.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        boolean active,
        boolean mustChangePassword,
        Instant createdAt,
        Instant lastLoginAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
