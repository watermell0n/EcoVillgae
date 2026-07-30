package com.b201.ecovillage.identity.application;

import java.time.Instant;

public interface StudentLoginRateLimiter {

    void check(String requestKey, Instant now);
}
