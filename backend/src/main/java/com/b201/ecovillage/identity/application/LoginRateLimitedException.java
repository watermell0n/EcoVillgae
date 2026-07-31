package com.b201.ecovillage.identity.application;

public class LoginRateLimitedException extends RuntimeException {

    private final long retryAfterSeconds;

    public LoginRateLimitedException(long retryAfterSeconds) {
        super("Student login rate limit exceeded");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
