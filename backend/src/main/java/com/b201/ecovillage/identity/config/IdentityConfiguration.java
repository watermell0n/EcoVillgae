package com.b201.ecovillage.identity.config;

import java.security.SecureRandom;
import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.b201.ecovillage.common.id.UuidV7Generator;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthenticationProperties.class)
public class IdentityConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    UuidV7Generator uuidV7Generator(Clock clock, SecureRandom secureRandom) {
        return new UuidV7Generator(clock, secureRandom);
    }

    @Bean
    PasswordEncoder studentPasswordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
