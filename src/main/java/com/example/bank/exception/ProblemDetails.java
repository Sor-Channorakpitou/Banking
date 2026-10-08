package com.example.bank.exception;

import com.example.bank.config.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.time.Instant;

/**
 * Builds the one error body shape used across the API (RFC 9457 problem details):
 * <pre>
 * {
 *   "type": "about:blank",
 *   "title": "Insufficient funds",
 *   "status": 422,
 *   "detail": "Insufficient funds in account 10000000009: balance 70.00, requested 500",
 *   "instance": "/api/accounts/4/withdraw",
 *   "code": "INSUFFICIENT_FUNDS",
 *   "timestamp": "2026-10-08T13:39:35.6Z",
 *   "requestId": "4f7c2a9e-..."
 * }
 * </pre>
 */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setProperty("code", code.name());
        problem.setProperty("timestamp", Instant.now());
        addRequestId(problem);
        return problem;
    }

    /**
     * Adds "code" and "timestamp" to problem bodies built by Spring itself (for
     * example a missing header or malformed JSON), so they look like ours.
     */
    public static void decorate(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String code = status.value() == 400 ? ErrorCode.MALFORMED_REQUEST.name()
                    : resolved != null ? resolved.name() : "HTTP_" + status.value();
            problem.setProperty("code", code);
        }
        if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now());
        }
        addRequestId(problem);
    }

    /** The same ID as the X-Request-Id response header and the log lines of this request. */
    private static void addRequestId(ProblemDetail problem) {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
    }
}
