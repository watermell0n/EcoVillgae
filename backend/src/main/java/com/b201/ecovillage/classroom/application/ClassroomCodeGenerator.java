package com.b201.ecovillage.classroom.application;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class ClassroomCodeGenerator {

    private static final char[] ALPHABET =
        "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private final SecureRandom secureRandom;

    public ClassroomCodeGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        char[] code = new char[8];
        for (int index = 0; index < code.length; index++) {
            code[index] = ALPHABET[secureRandom.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
