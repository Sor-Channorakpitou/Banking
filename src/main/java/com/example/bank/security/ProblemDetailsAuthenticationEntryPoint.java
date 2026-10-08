package com.example.bank.security;

import com.example.bank.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 responses: no token, or a token that is invalid or expired. The standard
 * bearer entry point still sets the WWW-Authenticate header (part of the OAuth2
 * spec); this class adds a JSON body in our error format.
 */
@Component
public class ProblemDetailsAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
    private final ProblemDetailsWriter writer;

    public ProblemDetailsAuthenticationEntryPoint(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        delegate.commence(request, response, authException);
        String detail = authException instanceof InvalidBearerTokenException
                ? "The access token is invalid or has expired"
                : "A valid Bearer access token is required";
        writer.write(request, response, ErrorCode.UNAUTHENTICATED, detail);
    }
}
