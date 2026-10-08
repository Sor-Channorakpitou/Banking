package com.example.bank.dto;

/** Field names follow the OAuth2 token response convention. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

    public static AuthResponse bearer(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn);
    }
}
