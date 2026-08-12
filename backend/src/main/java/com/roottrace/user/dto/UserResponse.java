package com.roottrace.user.dto;

import com.roottrace.user.Role;
import com.roottrace.user.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        Role role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
