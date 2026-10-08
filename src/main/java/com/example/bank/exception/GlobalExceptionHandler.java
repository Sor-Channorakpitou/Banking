package com.example.bank.exception;

import com.example.bank.service.BankMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The single place where exceptions become HTTP responses. Every error body has
 * the same JSON shape (see {@link ProblemDetails}), whatever went wrong:
 * <ul>
 *   <li>Our business errors ({@link BankException}): status and code come from {@link ErrorCode}.</li>
 *   <li>Spring MVC errors (bad JSON, missing header, wrong method): handled by the
 *       parent class, then given our "code" and "timestamp".</li>
 *   <li>Database concurrency errors: 409, so the client knows a retry may work.</li>
 *   <li>Anything unexpected: 500 with a generic message. The details go to the log,
 *       never to the client, since stack traces can reveal internals.</li>
 * </ul>
 * Errors raised inside the security filter chain (missing or invalid token) never
 * reach this class. {@code security.ProblemDetailsAuthenticationEntryPoint} and its
 * siblings produce the same shape for those.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final BankMetrics metrics;

    public GlobalExceptionHandler(BankMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(BankException.class)
    ResponseEntity<ProblemDetail> handleBankException(BankException ex) {
        metrics.businessError(ex.getErrorCode());
        if (ex instanceof RateLimitExceededException limited) {
            return ResponseEntity.status(ex.getErrorCode().status())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.getRetryAfterSeconds()))
                    .body(ProblemDetails.of(ex.getErrorCode(), ex.getMessage()));
        }
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    /** From @PreAuthorize. Without this, the catch-all below would turn it into a 500. */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.ACCESS_DENIED, "You don't have permission to perform this action");
    }

    /** Optimistic lock version mismatch or pessimistic lock timeout. */
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ProblemDetail> handleConcurrencyFailure(ConcurrencyFailureException ex) {
        log.warn("Concurrency failure: {}", ex.getMessage());
        return respond(ErrorCode.CONCURRENT_MODIFICATION,
                "The data was changed by another request at the same time. Please retry.");
    }

    /** A database constraint we didn't translate into a business error ourselves. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DATA_CONFLICT, "The request conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return respond(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred");
    }

    /** Bean Validation on a request body (@Valid): report every invalid field at once. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return validationFailed(errors);
    }

    /** Bean Validation on method parameters, e.g. @Max(100) on a "size" query param. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> errors.put(
                result.getMethodParameter().getParameterName(),
                result.getResolvableErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)
                        .collect(Collectors.joining("; "))));
        return validationFailed(errors);
    }

    /** Every Spring MVC error passes through here; give it our extra fields. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            ProblemDetails.decorate(problem, statusCode);
        }
        return response;
    }

    private static ResponseEntity<Object> validationFailed(Map<String, String> errors) {
        ProblemDetail body = ProblemDetails.of(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        body.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    private static ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail) {
        return ResponseEntity.status(code.status()).body(ProblemDetails.of(code, detail));
    }
}
