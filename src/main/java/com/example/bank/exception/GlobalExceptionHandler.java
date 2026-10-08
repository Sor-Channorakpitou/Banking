package com.example.bank.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON bodies, each with a stable
 * {@code code} property. Extending ResponseEntityExceptionHandler covers Spring
 * MVC's own errors (malformed JSON, wrong HTTP method, missing header, ...).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BankException.class)
    ResponseEntity<ProblemDetail> handleBankException(BankException ex) {
        return respond(ex.getErrorCode(), ex.getMessage());
    }

    /** Bean Validation failures (@Valid): report every invalid field at once. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail body = ProblemDetails.of(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        body.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(body);
    }

    private static ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail) {
        return ResponseEntity.status(code.status()).body(ProblemDetails.of(code, detail));
    }
}
