package com.roottrace.user.dto;

import com.roottrace.user.Role;
import com.roottrace.user.UserStatus;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        Role role,
        UserStatus status
) {
}
