package com.example.bank.security;

import com.example.bank.config.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds the caller's user ID to the logging MDC. Runs inside the security filter
 * chain, right after the JWT has been verified. Not a @Component on purpose:
 * Spring Boot registers every Filter bean as a servlet filter too, which would run
 * it a second time outside the security chain. RequestIdFilter removes the value.
 */
public class AuthenticatedUserMdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            MDC.put(RequestIdFilter.MDC_USER_ID, user.id().toString());
        }
        chain.doFilter(request, response);
    }
}
