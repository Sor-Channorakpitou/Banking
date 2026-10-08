package com.example.bank.service;

import com.example.bank.domain.AuditAction;
import com.example.bank.domain.User;
import com.example.bank.domain.UserCode;
import com.example.bank.dto.SecurityRequests;
import com.example.bank.exception.BankException;
import com.example.bank.exception.BusinessRuleException;
import com.example.bank.exception.ErrorCode;
import com.example.bank.exception.InvalidCredentialsException;
import com.example.bank.exception.ResourceNotFoundException;
import com.example.bank.repository.RefreshTokenRepository;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.AuthenticatedUser;
import com.example.bank.security.DataCipher;
import com.example.bank.security.Totp;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;

/** Email verification, password reset/change and two-step login (TOTP). */
@Service
public class AccountSecurityService {

    private static final String ISSUER = "Lime Bank";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailCodeService emailCodeService;
    private final PasswordEncoder passwordEncoder;
    private final DataCipher dataCipher;
    private final AuditService auditService;
    private final Clock clock = Clock.systemUTC();

    public AccountSecurityService(UserRepository userRepository, RefreshTokenRepository refreshTokenRepository,
                                  EmailCodeService emailCodeService, PasswordEncoder passwordEncoder,
                                  DataCipher dataCipher, AuditService auditService) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailCodeService = emailCodeService;
        this.passwordEncoder = passwordEncoder;
        this.dataCipher = dataCipher;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ email verification

    @Transactional(noRollbackFor = BankException.class)
    public void verifyEmail(SecurityRequests.VerifyEmail request) {
        User user = byEmail(request.email()).orElseThrow(this::invalidCode);
        if (user.isEmailVerified()) {
            return;
        }
        if (!emailCodeService.check(user, UserCode.Purpose.VERIFY_EMAIL, request.code())) {
            throw invalidCode();
        }
        user.markEmailVerified(clock.instant());
        auditService.success(AuditAction.EMAIL_VERIFIED, user.getId(), "USER", user.getId(), null);
    }

    @Transactional
    public void resendVerification(AuthenticatedUser caller) {
        User user = load(caller.id());
        if (!user.isEmailVerified()) {
            emailCodeService.send(user, UserCode.Purpose.VERIFY_EMAIL);
        }
    }

    // ------------------------------------------------------------------ password

    /** Always "OK", whether or not the email exists, so this can't be used to find accounts. */
    @Transactional
    public void forgotPassword(SecurityRequests.ForgotPassword request) {
        byEmail(request.email()).ifPresent(user -> emailCodeService.send(user, UserCode.Purpose.RESET_PASSWORD));
    }

    @Transactional(noRollbackFor = BankException.class)
    public void resetPassword(SecurityRequests.ResetPassword request) {
        User user = byEmail(request.email()).orElseThrow(this::invalidCode);
        if (!emailCodeService.check(user, UserCode.Purpose.RESET_PASSWORD, request.code())) {
            throw invalidCode();
        }
        setPassword(user, request.newPassword());
        user.markEmailVerified(clock.instant()); // receiving the code proves the address too
        auditService.success(AuditAction.PASSWORD_RESET, user.getId(), "USER", user.getId(), null);
    }

    @Transactional
    public void changePassword(AuthenticatedUser caller, SecurityRequests.ChangePassword request) {
        User user = load(caller.id());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        setPassword(user, request.newPassword());
        auditService.success(AuditAction.PASSWORD_CHANGED, user.getId(), "USER", user.getId(), null);
    }

    /** A new password signs out every device: a stolen session must not survive it. */
    private void setPassword(User user, String newPassword) {
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokenRepository.revokeAllForUser(user.getId(), clock.instant());
    }

    // ------------------------------------------------------------------ two-step login (TOTP)

    @Transactional
    public SecurityRequests.TotpSetup startTotpSetup(AuthenticatedUser caller) {
        User user = load(caller.id());
        if (user.isTotpEnabled()) {
            throw new BusinessRuleException(ErrorCode.DATA_CONFLICT, "Two-step login is already on");
        }
        String secret = Totp.newSecret();
        user.startTotpSetup(dataCipher.encrypt(secret));
        return new SecurityRequests.TotpSetup(secret, Totp.otpauthUri(ISSUER, user.getEmail(), secret));
    }

    /** Turned on only after a correct code: proof the authenticator app was set up right. */
    @Transactional
    public void enableTotp(AuthenticatedUser caller, String code) {
        User user = load(caller.id());
        if (user.getTotpSecret() == null || user.isTotpEnabled()) {
            throw new BusinessRuleException(ErrorCode.VALIDATION_FAILED, "Start two-step setup first");
        }
        requireValidTotp(user, code);
        user.enableTotp();
        auditService.success(AuditAction.TOTP_ENABLED, user.getId(), "USER", user.getId(), null);
    }

    @Transactional
    public void disableTotp(AuthenticatedUser caller, String code) {
        User user = load(caller.id());
        if (!user.isTotpEnabled()) {
            return;
        }
        requireValidTotp(user, code);
        user.disableTotp();
        auditService.success(AuditAction.TOTP_DISABLED, user.getId(), "USER", user.getId(), null);
    }

    /** Used at login. Accepted codes are remembered so the same code can't be replayed. */
    @Transactional
    public void requireValidTotp(User user, String code) {
        OptionalLong step = Totp.verify(dataCipher.decrypt(user.getTotpSecret()), code, clock.instant());
        if (step.isEmpty() || !user.acceptTotpStep(step.getAsLong())) {
            throw new BankException(ErrorCode.INVALID_TOTP, "The two-step code is wrong or was already used");
        }
        userRepository.save(user);
    }

    // ------------------------------------------------------------------ helpers

    private java.util.Optional<User> byEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
    }

    private User load(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private BankException invalidCode() {
        return new BankException(ErrorCode.INVALID_CODE, "The code is wrong or has expired. Ask for a new one.");
    }
}
