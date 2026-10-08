package com.example.bank.exception;

/** Becomes 429 Too Many Requests with a Retry-After header. */
public class RateLimitExceededException extends BankException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.TOO_MANY_REQUESTS,
                "Too many failed login attempts. Try again in " + retryAfterSeconds + " seconds.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
