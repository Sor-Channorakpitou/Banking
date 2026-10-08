package com.example.bank.dto;

/**
 * Field names follow the OAuth2 token response convention. The access token is a
 * short-lived JWT sent on every request; the refresh token is long-lived and only
 * ever sent to /api/auth/refresh. Keep it out of logs and URLs.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn,
                           String refreshToken, long refreshExpiresIn) {

    public static AuthResponse bearer(String accessToken, long expiresIn, String refreshToken, long refreshExpiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, refreshToken, refreshExpiresIn);
    }
}
