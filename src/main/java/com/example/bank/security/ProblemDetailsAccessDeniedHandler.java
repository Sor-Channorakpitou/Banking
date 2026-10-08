package com.example.bank.security;

import com.example.bank.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 403 from URL rules (e.g. a customer calling /api/admin/**), in our JSON format. */
@Component
public class ProblemDetailsAccessDeniedHandler implements AccessDeniedHandler {

    private final BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
    private final ProblemDetailsWriter writer;

    public ProblemDetailsAccessDeniedHandler(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        delegate.handle(request, response, accessDeniedException);
        writer.write(request, response, ErrorCode.ACCESS_DENIED, "You don't have permission to perform this action");
    }
}
