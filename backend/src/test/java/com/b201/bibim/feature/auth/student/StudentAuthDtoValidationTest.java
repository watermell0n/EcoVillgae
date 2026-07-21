package com.b201.bibim.feature.auth.student;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.b201.bibim.feature.auth.student.StudentAuthDto.LoginRequest;
import com.b201.bibim.feature.auth.student.StudentAuthDto.SetupRequest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentAuthDtoValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void rejectsWhitespaceInsideSetupPassword() {
        var request = new SetupRequest("ECO-01", "비빔이", "   a");

        var violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(item -> item.getMessage().equals("비밀번호에는 공백을 사용할 수 없습니다.")));
    }

    @Test
    void rejectsWhitespaceInsideLoginPassword() {
        var request = new LoginRequest("ECO-01", "12 34");

        var violations = validator.validate(request);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(item -> item.getMessage().equals("비밀번호에는 공백을 사용할 수 없습니다.")));
    }

    @Test
    void acceptsPasswordWithoutWhitespace() {
        assertTrue(validator.validate(new SetupRequest("ECO-01", "비빔이", "1234")).isEmpty());
        assertTrue(validator.validate(new LoginRequest("ECO-01", "1234")).isEmpty());
    }

    @Test
    void acceptsAOneCharacterStudentName() {
        assertTrue(validator.validate(new SetupRequest("ECO-01", "봄", "1234")).isEmpty());
    }
}
