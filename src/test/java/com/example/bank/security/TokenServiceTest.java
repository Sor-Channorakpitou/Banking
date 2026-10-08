package com.example.bank.security;

import com.example.bank.config.JwtProperties;
import com.example.bank.domain.Role;
import com.example.bank.domain.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final SecretKey KEY =
            new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(), "HmacSHA256");
    private static final JwtProperties PROPS =
            new JwtProperties("unused-here", "core-banking", Duration.ofMinutes(15));

    private final NimbusJwtDecoder decoder = decoder();

    @Test
    void tokenCarriesUserIdRoleAndExpiry() {
        TokenService service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), PROPS);

        TokenService.IssuedToken token = service.issueAccessToken(user(42L, Role.ADMIN));
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("user42@example.com");
        assertThat(token.expiresInSeconds()).isEqualTo(900);
    }

    @Test
    void expiredTokenIsRejected() {
        Clock anHourAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC);
        TokenService service = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(KEY)), PROPS, anHourAgo);

        String token = service.issueAccessToken(user(1L, Role.CUSTOMER)).value();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtValidationException.class);
    }

    private static NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("core-banking"));
        return decoder;
    }

    private static User user(Long id, Role role) {
        User user = new User("User " + id, "user" + id + "@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id); // normally assigned by the database
        return user;
    }
}
