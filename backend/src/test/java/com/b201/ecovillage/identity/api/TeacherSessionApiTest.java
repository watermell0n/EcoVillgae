package com.b201.ecovillage.identity.api;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import com.b201.ecovillage.identity.application.TeacherAuthenticationException;
import com.b201.ecovillage.identity.application.TeacherIdentityVerifier;
import com.b201.ecovillage.identity.application.TeacherSessionRecord;
import com.b201.ecovillage.identity.application.TeacherSessionStatus;
import com.b201.ecovillage.identity.application.TeacherSessionStore;
import com.b201.ecovillage.identity.application.VerifiedTeacherIdentity;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.School;
import com.b201.ecovillage.identity.repository.SchoolRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:teacher-login;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(TeacherSessionApiTest.IdentityTestConfiguration.class)
@Transactional
class TeacherSessionApiTest {

    private static final String EXTERNAL_SUBJECT = "oidc-teacher-subject";
    private static final String DISPLAY_NAME = "김에코 선생님";
    private static final Instant CREATED_AT = Instant.parse("2026-07-31T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private InMemoryTeacherSessionStore sessionStore;

    private UUID teacherId;

    @BeforeEach
    void setUp() {
        sessionStore.clear();
        teacherId = UUID.randomUUID();
        userAccountRepository.save(
            UserAccount.createTeacher(
                teacherId,
                EXTERNAL_SUBJECT,
                DISPLAY_NAME,
                CREATED_AT
            )
        );
    }

    @Test
    void createsTeacherSessionAndAuthenticatesMeEndpoint() throws Exception {
        UUID requestId = UUID.randomUUID();

        MvcResult login = mockMvc.perform(
            teacherLogin("valid-id-token")
                .header("Idempotency-Key", requestId)
        )
            .andExpect(status().isCreated())
            .andExpect(cookie().httpOnly("ecovillage_teacher_refresh", true))
            .andExpect(cookie().path("ecovillage_teacher_refresh", "/"))
            .andExpect(jsonPath("$.data.access_token").isString())
            .andExpect(jsonPath("$.data.refresh_token").doesNotExist())
            .andExpect(jsonPath("$.data.session_id").isString())
            .andExpect(jsonPath("$.data.teacher_id").value(teacherId.toString()))
            .andExpect(jsonPath("$.data.display_name").value(DISPLAY_NAME))
            .andExpect(jsonPath("$.data.role").value("TCH"))
            .andExpect(jsonPath("$.meta.request_id").value(requestId.toString()))
            .andReturn();

        String accessToken = read(login, "$.data.access_token");
        mockMvc.perform(
            get("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.user_id").value(teacherId.toString()))
            .andExpect(jsonPath("$.data.display_name").value(DISPLAY_NAME))
            .andExpect(jsonPath("$.data.role").value("TCH"))
            .andExpect(jsonPath("$.data.schools").isArray())
            .andExpect(jsonPath("$.data.classrooms").isArray());
    }

    @Test
    void rotatesRefreshTokenAndRejectsReplay() throws Exception {
        MvcResult login = login();
        Cookie firstRefreshCookie = login.getResponse().getCookie(
            "ecovillage_teacher_refresh"
        );
        assertNotNull(firstRefreshCookie);

        MvcResult refresh = mockMvc.perform(
            post("/auth/token-refreshes")
                .header("Idempotency-Key", UUID.randomUUID())
                .cookie(firstRefreshCookie)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.access_token").isString())
            .andExpect(jsonPath("$.data.refresh_token").doesNotExist())
            .andExpect(jsonPath("$.data.role").value("TCH"))
            .andExpect(cookie().httpOnly("ecovillage_teacher_refresh", true))
            .andExpect(cookie().path("ecovillage_teacher_refresh", "/"))
            .andReturn();

        Cookie secondRefreshCookie = refresh.getResponse().getCookie(
            "ecovillage_teacher_refresh"
        );
        assertNotNull(secondRefreshCookie);
        assertNotEquals(firstRefreshCookie.getValue(), secondRefreshCookie.getValue());

        mockMvc.perform(
            post("/auth/token-refreshes")
                .header("Idempotency-Key", UUID.randomUUID())
                .cookie(firstRefreshCookie)
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void rejectsTeacherRefreshTokenInStudentHeaderAndAmbiguousTransport() throws Exception {
        MvcResult login = login();
        Cookie teacherCookie = login.getResponse().getCookie("ecovillage_teacher_refresh");
        assertNotNull(teacherCookie);

        mockMvc.perform(
            post("/auth/token-refreshes")
                .header("Idempotency-Key", UUID.randomUUID())
                .header("X-Refresh-Token", teacherCookie.getValue())
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_REFRESH_TOKEN"));

        mockMvc.perform(
            post("/auth/token-refreshes")
                .header("Idempotency-Key", UUID.randomUUID())
                .header("X-Refresh-Token", "stu_unrelated-token")
                .cookie(teacherCookie)
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("AMBIGUOUS_REFRESH_TOKEN"));
    }

    @Test
    void allowsOnlyConfiguredCredentialedWebOrigin() throws Exception {
        mockMvc.perform(
            options("/auth/token-refreshes")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "Idempotency-Key,Content-Type"
                )
        )
            .andExpect(status().isOk())
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                "http://localhost:3000"
            ))
            .andExpect(header().string(
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                "true"
            ));

        mockMvc.perform(
            options("/auth/token-refreshes")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        ).andExpect(status().isForbidden());
    }

    @Test
    void logoutRevokesOnlyCurrentTeacherSession() throws Exception {
        MvcResult firstLogin = login();
        MvcResult secondLogin = login();
        String firstAccessToken = read(firstLogin, "$.data.access_token");
        String secondAccessToken = read(secondLogin, "$.data.access_token");

        mockMvc.perform(
            delete("/auth/sessions/current")
                .header("Idempotency-Key", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken))
        ).andExpect(status().isNoContent());

        mockMvc.perform(
            get("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken))
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_ACCESS_TOKEN"));

        mockMvc.perform(
            get("/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(secondAccessToken))
        ).andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidOrUnregisteredOidcIdentity() throws Exception {
        mockMvc.perform(
            teacherLogin("invalid-id-token")
                .header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("INVALID_ID_TOKEN"));

        mockMvc.perform(
            teacherLogin("unregistered-id-token")
                .header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("TEACHER_NOT_REGISTERED"));
    }

    @Test
    void rejectsUnknownTeacherLoginField() throws Exception {
        mockMvc.perform(
            post("/auth/teacher-sessions")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "id_token": "valid-id-token",
                      "password": "must-not-be-accepted"
                    }
                    """)
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));
    }

    @Test
    void registersTeacherAndCreatesPasswordSession() throws Exception {
        schoolRepository.save(
            School.create(UUID.randomUUID(), "ECO-TEST", "에코 초등학교", CREATED_AT)
        );

        MvcResult registration = mockMvc.perform(
            post("/auth/teacher-registrations")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "school_code": "eco-test",
                      "display_name": "로컬 교사",
                      "email": "teacher@example.com",
                      "password": "1234"
                    }
                    """)
        )
            .andExpect(status().isCreated())
            .andExpect(cookie().httpOnly("ecovillage_teacher_refresh", true))
            .andExpect(jsonPath("$.data.access_token").isString())
            .andExpect(jsonPath("$.data.display_name").value("로컬 교사"))
            .andReturn();

        mockMvc.perform(
            post("/auth/teacher-password-sessions")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "TEACHER@example.com",
                      "password": "1234"
                    }
                    """)
        )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.teacher_id").value(
                read(registration, "$.data.teacher_id")
            ));
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(
            teacherLogin("valid-id-token")
                .header("Idempotency-Key", UUID.randomUUID())
        )
            .andExpect(status().isCreated())
            .andReturn();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        teacherLogin(String idToken) {
        return post("/auth/teacher-sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "id_token": "%s"
                }
                """.formatted(idToken));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IdentityTestConfiguration {

        @Bean
        @Primary
        TeacherIdentityVerifier testTeacherIdentityVerifier() {
            return idToken -> switch (idToken) {
                case "valid-id-token" ->
                    new VerifiedTeacherIdentity(EXTERNAL_SUBJECT, "OIDC 표시 이름");
                case "unregistered-id-token" ->
                    new VerifiedTeacherIdentity("unknown-subject", "등록되지 않은 교사");
                default -> throw new TeacherAuthenticationException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_ID_TOKEN",
                    "교사 인증 정보가 유효하지 않거나 만료되었습니다.",
                    false
                );
            };
        }

        @Bean
        @Primary
        InMemoryTeacherSessionStore testTeacherSessionStore() {
            return new InMemoryTeacherSessionStore();
        }
    }

    static final class InMemoryTeacherSessionStore implements TeacherSessionStore {

        private final Map<UUID, TeacherSessionRecord> sessions = new ConcurrentHashMap<>();
        private final Map<String, UUID> refreshIndex = new ConcurrentHashMap<>();

        @Override
        public synchronized void save(TeacherSessionRecord session) {
            sessions.put(session.sessionId(), session);
            refreshIndex.put(session.refreshTokenHash(), session.sessionId());
        }

        @Override
        public Optional<TeacherSessionRecord> findBySessionId(UUID sessionId) {
            return Optional.ofNullable(sessions.get(sessionId));
        }

        @Override
        public Optional<TeacherSessionRecord> findByRefreshTokenHash(String refreshTokenHash) {
            UUID sessionId = refreshIndex.get(refreshTokenHash);
            return sessionId == null
                ? Optional.empty()
                : findBySessionId(sessionId)
                    .filter(session -> session.refreshTokenHash().equals(refreshTokenHash));
        }

        @Override
        public synchronized boolean rotate(
            UUID sessionId,
            String expectedRefreshTokenHash,
            String newRefreshTokenHash,
            Instant newExpiresAt,
            Instant rotatedAt
        ) {
            TeacherSessionRecord current = sessions.get(sessionId);
            if (
                current == null
                    || current.status() != TeacherSessionStatus.ACTIVE
                    || !current.refreshTokenHash().equals(expectedRefreshTokenHash)
            ) {
                return false;
            }
            refreshIndex.remove(expectedRefreshTokenHash);
            TeacherSessionRecord rotated = new TeacherSessionRecord(
                current.sessionId(),
                current.userId(),
                current.externalSubject(),
                newRefreshTokenHash,
                current.status(),
                current.issuedAt(),
                newExpiresAt,
                null
            );
            sessions.put(sessionId, rotated);
            refreshIndex.put(newRefreshTokenHash, sessionId);
            return true;
        }

        @Override
        public synchronized boolean revoke(UUID sessionId, Instant revokedAt) {
            TeacherSessionRecord current = sessions.get(sessionId);
            if (current == null) {
                return false;
            }
            refreshIndex.remove(current.refreshTokenHash());
            sessions.put(
                sessionId,
                new TeacherSessionRecord(
                    current.sessionId(),
                    current.userId(),
                    current.externalSubject(),
                    current.refreshTokenHash(),
                    TeacherSessionStatus.REVOKED,
                    current.issuedAt(),
                    current.expiresAt(),
                    revokedAt
                )
            );
            return true;
        }

        void clear() {
            sessions.clear();
            refreshIndex.clear();
        }
    }
}
