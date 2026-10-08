package com.example.bank.service;

import com.example.bank.config.SecurityProperties;
import com.example.bank.domain.User;
import com.example.bank.domain.UserCode;
import com.example.bank.repository.UserCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 6-digit codes sent by email to prove someone controls an address. A code is short
 * enough to type from a phone, which only works because guessing is capped: it
 * expires after {@code codeTtl}, allows {@code codeMaxAttempts} wrong tries, and a new
 * code replaces the old one.
 */
@Service
public class EmailCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserCodeRepository repository;
    private final MailService mailService;
    private final SecurityProperties properties;
    private final Clock clock = Clock.systemUTC();

    public EmailCodeService(UserCodeRepository repository, MailService mailService, SecurityProperties properties) {
        this.repository = repository;
        this.mailService = mailService;
        this.properties = properties;
    }

    @Transactional
    public void send(User user, UserCode.Purpose purpose) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        repository.save(new UserCode(user.getId(), purpose, hash(user.getId(), code),
                clock.instant().plus(properties.codeTtl())));
        long minutes = properties.codeTtl().toMinutes();
        if (purpose == UserCode.Purpose.VERIFY_EMAIL) {
            mailService.send(user.getEmail(), "Your verification code: " + code,
                    "Hello " + user.getFullName() + ",\n\nYour verification code is " + code
                            + ". It expires in " + minutes + " minutes.\n\nIf you didn't create an account, ignore this email.");
        } else {
            mailService.send(user.getEmail(), "Your password reset code: " + code,
                    "Hello " + user.getFullName() + ",\n\nUse code " + code + " to choose a new password. It expires in "
                            + minutes + " minutes.\n\nIf you didn't ask for this, ignore this email; your password stays the same.");
        }
    }

    /** True if the code is right; it is then used up. Wrong guesses count against the code. */
    @Transactional(noRollbackFor = RuntimeException.class)
    public boolean check(User user, UserCode.Purpose purpose, String code) {
        Instant now = clock.instant();
        UserCode latest = repository.findFirstByUserIdAndPurposeOrderByIdDesc(user.getId(), purpose).orElse(null);
        if (latest == null || !latest.isUsable(now, properties.codeMaxAttempts())) {
            return false;
        }
        boolean ok = code != null && MessageDigest.isEqual(
                latest.getCodeHash().getBytes(StandardCharsets.UTF_8),
                hash(user.getId(), code.trim()).getBytes(StandardCharsets.UTF_8));
        if (ok) {
            latest.markUsed(now);
        } else {
            latest.recordFailedAttempt();
        }
        return ok;
    }

    /** The user ID is mixed in so the same code for two users never has the same hash. */
    private static String hash(Long userId, String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
