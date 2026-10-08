package com.example.bank.exception;

import org.springframework.http.ProblemDetail;

/** Builds the one error body shape used across the API. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        return problem;
    }
}
