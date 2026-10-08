package com.example.bank.service;

import com.example.bank.domain.Role;
import com.example.bank.domain.User;
import com.example.bank.dto.AuthResponse;
import com.example.bank.dto.LoginRequest;
import com.example.bank.dto.RegisterRequest;
import com.example.bank.dto.UserResponse;
import com.example.bank.exception.EmailAlreadyUsedException;
import com.example.bank.exception.InvalidCredentialsException;
import com.example.bank.repository.UserRepository;
import com.example.bank.security.TokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Hash of a throwaway password, checked when the email is unknown. Login then
     * takes about as long for unknown emails as for wrong passwords, so response
     * timing doesn't reveal which emails exist.
     */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        // Quick, friendly check. Two simultaneous sign-ups can both pass it, so the
        // database UNIQUE constraint is the real guarantee (see the catch below).
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }
        User user = new User(request.fullName().trim(), email,
                passwordEncoder.encode(request.password()), Role.CUSTOMER);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyUsedException(email);
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        TokenService.IssuedToken token = tokenService.issueAccessToken(user.get());
        return AuthResponse.bearer(token.value(), token.expiresInSeconds());
    }

    /** Emails are case-insensitive in practice, so store and look them up in lower case. */
    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
