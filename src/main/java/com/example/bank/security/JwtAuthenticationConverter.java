package com.example.bank.security;

import com.example.bank.domain.Role;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs after the token's signature and expiry have been verified. Turns the claims
 * into an {@link AuthenticatedUser} principal and a ROLE_ authority, so that
 * {@code hasRole("ADMIN")} and {@code @AuthenticationPrincipal} work.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Role role = Role.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROLE));
        AuthenticatedUser user = new AuthenticatedUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString(TokenService.CLAIM_EMAIL),
                role);
        return UsernamePasswordAuthenticationToken.authenticated(
                user, jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
