package com.example.bank.security;

import com.example.bank.domain.Role;

/**
 * Who is making the current request, taken from a verified JWT. Controllers get it
 * with {@code @AuthenticationPrincipal AuthenticatedUser user} and pass it to services,
 * which use it for ownership checks ("customers only see their own data").
 */
public record AuthenticatedUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
