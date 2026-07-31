package com.b201.ecovillage.identity.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.StudentCredential;
import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.StudentSessionStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.DeviceLinkRepository;
import com.b201.ecovillage.identity.repository.StudentCredentialRepository;
import com.b201.ecovillage.identity.repository.StudentSessionRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:student-login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ecovillage.auth.login-attempt-limit=3"
})
@AutoConfigureMockMvc
@Transactional
class StudentSessionApiTest {

    private static final String CLASSROOM_CODE = "ABCD2345";
    private static final String NICKNAME = "경제왕";
    private static final String PASSWORD = "economy123!";
    private static final Instant CREATED_AT = Instant.parse("2026-07-30T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomStudentRepository classroomStudentRepository;

    @Autowired
    private StudentCredentialRepository studentCredentialRepository;

    @Autowired
    private DeviceLinkRepository deviceLinkRepository;

    @Autowired
    private StudentSessionRepository studentSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private UUID classroomId;
    private UUID studentId;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();

        classroomRepository.save(
            Classroom.active(classroomId, CLASSROOM_CODE, CREATED_AT)
        );
        classroomStudentRepository.save(
            ClassroomStudent.enroll(studentId, classroomId, 7, NICKNAME, CREATED_AT)
        );
        studentCredentialRepository.save(
            StudentCredential.issue(
                studentId,
                classroomId,
                passwordEncoder.encode(PASSWORD),
                CREATED_AT
            )
        );
    }

    @Test
    void createsStudentSessionForNormalizedValidCredentials() throws Exception {
        UUID requestId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(
            validLoginRequest("  abcd2345  ", NICKNAME, PASSWORD, deviceId)
                .header("Idempotency-Key", requestId)
                .header("X-Correlation-Id", correlationId)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.access_token").isString())
            .andExpect(jsonPath("$.data.refresh_token").isString())
            .andExpect(jsonPath("$.data.expires_at").isString())
            .andExpect(jsonPath("$.data.classroom_id").value(classroomId.toString()))
            .andExpect(jsonPath("$.data.student_id").value(studentId.toString()))
            .andExpect(jsonPath("$.data.role").value("STU"))
            .andExpect(jsonPath("$.meta.request_id").value(requestId.toString()))
            .andExpect(jsonPath("$.meta.correlation_id").value(correlationId.toString()))
            .andReturn();

        List<StudentSession> sessions = studentSessionRepository
            .findAllByClassroomStudentIdAndStatus(studentId, StudentSessionStatus.ACTIVE);
        String refreshToken = JsonPath.read(
            result.getResponse().getContentAsString(),
            "$.data.refresh_token"
        );
        assertEquals(1, sessions.size());
        assertFalse(sessions.getFirst().getTokenHash().isBlank());
        assertNotEquals(refreshToken, sessions.getFirst().getTokenHash());
        assertEquals(1, deviceLinkRepository.count());
        assertNotEquals(
            deviceId.toString(),
            deviceLinkRepository.findAll().getFirst().getDeviceFingerprintHash()
        );
    }

    @Test
    void revokesPreviousSessionWhenStudentLogsInFromAnotherDevice() throws Exception {
        mockMvc.perform(
            validLoginRequest(CLASSROOM_CODE, NICKNAME, PASSWORD, UUID.randomUUID())
                .header("Idempotency-Key", UUID.randomUUID())
        ).andExpect(status().isCreated());

        mockMvc.perform(
            validLoginRequest(CLASSROOM_CODE, NICKNAME, PASSWORD, UUID.randomUUID())
                .header("Idempotency-Key", UUID.randomUUID())
        ).andExpect(status().isCreated());

        List<StudentSession> sessions = studentSessionRepository.findAll();
        assertEquals(2, sessions.size());
        assertEquals(
            1,
            sessions.stream()
                .filter(session -> session.getStatus() == StudentSessionStatus.ACTIVE)
                .count()
        );
        assertEquals(
            1,
            sessions.stream()
                .filter(session -> session.getStatus() == StudentSessionStatus.REVOKED)
                .count()
        );
        assertTrue(
            sessions.stream()
                .filter(session -> session.getStatus() == StudentSessionStatus.REVOKED)
                .allMatch(session -> session.getRevokedAt() != null)
        );
    }

    @Test
    void rejectsWrongPasswordWithoutCreatingAccountLockOrSession() throws Exception {
        mockMvc.perform(
            validLoginRequest(
                CLASSROOM_CODE,
                NICKNAME,
                "wrong-password",
                UUID.randomUUID()
            ).header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.error.retryable").value(false));

        assertTrue(studentSessionRepository.findAll().isEmpty());
        assertEquals(
            1,
            studentCredentialRepository.findById(studentId).orElseThrow().getCredentialVersion()
        );
    }

    @Test
    void rejectsWrongNicknameWithSameCredentialError() throws Exception {
        mockMvc.perform(
            validLoginRequest(
                CLASSROOM_CODE,
                "다른닉네임",
                PASSWORD,
                UUID.randomUUID()
            ).header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));

        assertTrue(studentSessionRepository.findAll().isEmpty());
    }

    @Test
    void rejectsArchivedClassroomWithDefinedConflict() throws Exception {
        Classroom classroom = classroomRepository.findById(classroomId).orElseThrow();
        classroom.changeStatus(ClassroomStatus.ARCHIVED, CREATED_AT);

        mockMvc.perform(
            validLoginRequest(CLASSROOM_CODE, NICKNAME, PASSWORD, UUID.randomUUID())
                .header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ACTIVE"));
    }

    @Test
    void rejectsUnknownRequestFieldBecauseContractIsStrict() throws Exception {
        String body = """
            {
              "classroom_code": "ABCD2345",
              "attendance_number": 7,
              "nickname": "경제왕",
              "password": "economy123!",
              "device_id": "%s",
              "unexpected": true
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(
            post("/auth/student-sessions")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));
    }

    @Test
    void requiresIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(
            validLoginRequest(CLASSROOM_CODE, NICKNAME, PASSWORD, UUID.randomUUID())
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void rateLimitResponseIncludesRetryAfterHeader() throws Exception {
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(
                validLoginRequest(
                    CLASSROOM_CODE,
                    NICKNAME,
                    "wrong-password",
                    UUID.fromString("22222222-2222-4222-8222-222222222222")
                ).header("Idempotency-Key", UUID.randomUUID())
            ).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(
            validLoginRequest(
                CLASSROOM_CODE,
                NICKNAME,
                "wrong-password",
                UUID.fromString("22222222-2222-4222-8222-222222222222")
            ).header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.error.retryable").value(true));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        validLoginRequest(
            String classroomCode,
            String nickname,
            String password,
            UUID deviceId
        ) {
        String body = """
            {
              "classroom_code": "%s",
              "attendance_number": 7,
              "nickname": "%s",
              "password": "%s",
              "device_id": "%s"
            }
            """.formatted(classroomCode, nickname, password, deviceId);

        return post("/auth/student-sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }
}
