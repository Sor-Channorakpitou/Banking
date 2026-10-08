package com.example.bank.security;

import com.example.bank.config.JwtProperties;
import com.example.bank.domain.AuditAction;
import com.example.bank.domain.AuditOutcome;
import com.example.bank.domain.RefreshToken;
import com.example.bank.domain.User;
import com.example.bank.exception.BankException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.repository.RefreshTokenRepository;
import com.example.bank.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens let a client get a new short-lived access token without asking
 * for the password again.
 * <ul>
 *   <li><b>Opaque, not JWT:</b> 256 random bits. They are only ever checked by this
 *       server against the database, so they can be revoked at any time.</li>
 *   <li><b>Rotation:</b> each refresh consumes the token and returns a new one.</li>
 *   <li><b>Reuse detection:</b> if a consumed token is presented again, either the
 *       client or an attacker holds a stolen copy. We can't tell which, so the
 *       whole session (token family) is revoked and the user must log in again.</li>
 * </ul>
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final AuditService auditService;
    private final Clock clock = Clock.systemUTC();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties,
                               AuditService auditService) {
        this.repository = repository;
        this.properties = properties;
        this.auditService = auditService;
    }

    /** Starts a new session (token family), e.g. at login. */
    @Transactional
    public IssuedRefreshToken issue(User user) {
        return issue(user, UUID.randomUUID().toString());
    }

    /**
     * Consumes a refresh token and issues its successor. {@code noRollbackFor}: when
     * reuse is detected we revoke the family and then throw. Without it, the throw
     * would roll back the revocation too.
     */
    @Transactional(noRollbackFor = BankException.class)
    public Rotation rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken token = repository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(RefreshTokenService::invalid);
        if (token.isRevoked()) {
            int revoked = repository.revokeFamily(token.getFamilyId(), now);
            auditService.record(AuditAction.REFRESH_TOKEN_REUSE_DETECTED, AuditOutcome.FAILURE,
                    token.getUser().getId(), "USER", token.getUser().getId(),
                    "family=" + token.getFamilyId() + " revoked=" + revoked);
            throw invalid();
        }
        if (token.isExpired(now)) {
            throw invalid();
        }
        token.revoke(now);
        User user = token.getUser();
        return new Rotation(user, issue(user, token.getFamilyId()));
    }

    /** Logout: ends the session this token belongs to. Unknown tokens are ignored. */
    @Transactional
    public void revokeSession(String rawToken) {
        repository.findByTokenHashForUpdate(hash(rawToken))
                .ifPresent(token -> {
                    repository.revokeFamily(token.getFamilyId(), clock.instant());
                    auditService.success(AuditAction.LOGOUT, token.getUser().getId(), "USER",
                            token.getUser().getId(), null);
                });
    }

    private IssuedRefreshToken issue(User user, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
        repository.save(new RefreshToken(user, hash(raw), familyId, expiresAt));
        return new IssuedRefreshToken(raw, properties.refreshTokenTtl().toSeconds());
    }

    /**
     * Plain SHA-256 is enough here (unlike passwords, which need slow BCrypt): the
     * token is 256 random bits, so guessing it by brute force is hopeless.
     */
    private static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BankException invalid() {
        return new BankException(ErrorCode.INVALID_REFRESH_TOKEN, "Refresh token is invalid, expired or revoked");
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds) {
    }

    public record Rotation(User user, IssuedRefreshToken refreshToken) {
    }
}
