package com.example.bank.dto;

import com.example.bank.domain.Role;
import com.example.bank.domain.User;

import java.time.Instant;

/**
 * What the API exposes about a user. Entities are never returned directly, so
 * fields like passwordHash can't leak by accident.
 */
public record UserResponse(Long id, String fullName, String email, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole(), user.getCreatedAt());
    }
}
