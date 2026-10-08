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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: no Spring context and no database. The repository and token
 * service are Mockito mocks; the password encoder is real BCrypt at the lowest cost
 * (4) so hashing is actually exercised but stays fast.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, tokenService, auditService);
    }

    @Test
    void registerCreatesCustomerWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = authService.register(
                new RegisterRequest("Alice", "  Alice@Example.COM ", "password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getValue().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getValue().getPasswordHash())
                .isNotEqualTo("password123")
                .satisfies(hash -> assertThat(passwordEncoder.matches("password123", hash)).isTrue());
        assertThat(response.email()).isEqualTo("alice@example.com");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Alice", "alice@example.com", "password123")))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerTranslatesUniqueConstraintRaceIntoDuplicateEmail() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uk_users_email"));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("Alice", "alice@example.com", "password123")))
                .isInstanceOf(EmailAlreadyUsedException.class);
    }

    @Test
    void loginReturnsTokenForCorrectPassword() {
        User user = new User("Alice", "alice@example.com", passwordEncoder.encode("password123"), Role.CUSTOMER);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));
        when(tokenService.issueAccessToken(user)).thenReturn(new TokenService.IssuedToken("jwt-value", 900));

        AuthResponse response = authService.login(new LoginRequest("ALICE@example.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-value");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("Alice", "alice@example.com", passwordEncoder.encode("password123"), Role.CUSTOMER);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenService, never()).issueAccessToken(any());
    }

    @Test
    void loginRejectsUnknownEmailWithSameError() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "password123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
    }
}
