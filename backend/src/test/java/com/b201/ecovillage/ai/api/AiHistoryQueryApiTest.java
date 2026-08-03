package com.b201.ecovillage.ai.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiDecision;
import com.b201.ecovillage.ai.domain.AiProposal;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.domain.AsyncOperation;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiDecisionRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:ai-history-query;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class AiHistoryQueryApiTest {

    private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository teacherRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private AiRunRepository runRepository;

    @Autowired
    private AiProposalRepository proposalRepository;

    @Autowired
    private AiArtifactRepository artifactRepository;

    @Autowired
    private AiDecisionRepository decisionRepository;

    @Autowired
    private AsyncOperationRepository operationRepository;

    private UUID classroomId;
    private UUID ownerId;
    private Principal owner;
    private Principal coTeacher;
    private Principal outsider;
    private Principal student;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        UUID coTeacherId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        owner = () -> "history-owner-" + classroomId;
        coTeacher = () -> "history-cot-" + classroomId;
        outsider = () -> "history-outsider-" + classroomId;
        student = studentId::toString;

        classroomRepository.save(Classroom.create(
            classroomId,
            UUID.randomUUID(),
            randomClassroomCode(),
            "ECO",
            5,
            CourseSize.ONE_MONTH,
            24,
            LocalDate.parse("2026-08-01"),
            LocalDate.parse("2026-08-31"),
            "Asia/Seoul",
            NOW
        ));
        userRepository.save(UserAccount.createTeacher(ownerId, owner.getName(), "Owner", NOW));
        userRepository.save(UserAccount.createTeacher(
            coTeacherId,
            coTeacher.getName(),
            "Co-teacher",
            NOW
        ));
        userRepository.save(UserAccount.createTeacher(
            outsiderId,
            outsider.getName(),
            "Outsider",
            NOW
        ));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            ownerId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        teacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            coTeacherId,
            ClassroomTeacherRole.COT,
            NOW
        ));
        studentRepository.save(ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "Student",
            NOW
        ));
    }

    @Test
    void restoresNewestHistoryWithStableCursorAndNoSensitivePayloads() throws Exception {
        UUID oldest = createCompletedRun(NOW.plusSeconds(1), "ON_DEMAND", false, false);
        UUID middle = createCompletedRun(NOW.plusSeconds(2), "AUTO_MONITOR", true, false);
        UUID newest = createCompletedRun(NOW.plusSeconds(3), "SETUP_PRECHECK", true, true);

        MvcResult first = mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            )
                .principal(owner)
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].ai_run_id").value(newest.toString()))
            .andExpect(jsonPath("$.data.items[0].run_type").value("SETUP_PRECHECK"))
            .andExpect(jsonPath("$.data.items[0].fallback_used").value(true))
            .andExpect(jsonPath("$.data.items[0].validation_codes[0]").value("SCHEMA_VALID"))
            .andExpect(jsonPath("$.data.items[0].evidence_codes[0]").value("POLICY-001"))
            .andExpect(jsonPath("$.data.items[0].proposals[0].context_type")
                .value("PRODUCT_DRAFT"))
            .andExpect(jsonPath("$.data.items[0].proposals[0].target_type")
                .value("STORE_PRODUCT"))
            .andExpect(jsonPath("$.data.items[0].proposals[0].target_version").value(4))
            .andExpect(jsonPath("$.data.items[0].proposals[0].allowed_ranges[0].allowed_min_value")
                .value(100))
            .andExpect(jsonPath("$.data.items[0].proposals[0].decision_status")
                .value("ACKNOWLEDGE"))
            .andExpect(jsonPath("$.data.items[1].ai_run_id").value(middle.toString()))
            .andExpect(jsonPath("$.meta.page.has_more").value(true))
            .andReturn();

        String firstBody = first.getResponse().getContentAsString();
        assertFalse(firstBody.contains("student-private-raw-text"));
        assertFalse(firstBody.contains("provider-secret-key"));
        JsonNode firstJson = objectMapper.readTree(firstBody);
        String cursor = firstJson.path("meta").path("page").path("next_cursor").asText();

        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            )
                .principal(owner)
                .param("limit", "2")
                .param("cursor", cursor))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items.length()").value(1))
            .andExpect(jsonPath("$.data.items[0].ai_run_id").value(oldest.toString()))
            .andExpect(jsonPath("$.meta.page.has_more").value(false))
            .andExpect(jsonPath("$.meta.page.next_cursor").doesNotExist());
    }

    @Test
    void allowsAssignedTeachersAndRejectsStudentsOutsidersAndInvalidCursors() throws Exception {
        createCompletedRun(NOW.plusSeconds(1), "ON_DEMAND", false, false);

        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            ).principal(coTeacher))
            .andExpect(status().isOk());
        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            ).principal(student))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("ROLE_NOT_ALLOWED"));
        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            ).principal(outsider))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ASSIGNED"));
        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            ))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            )
                .principal(owner)
                .param("cursor", "not-a-cursor"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
    }

    @Test
    void exposesRunningCompletedAndFailedAiOperationStates() throws Exception {
        AsyncOperation running = AsyncOperation.running(
            UUID.randomUUID(),
            classroomId,
            ownerId,
            "AI_BALANCING_ANALYSIS",
            NOW
        );
        running.updateProgress(55, NOW.plusSeconds(1));
        operationRepository.save(running);
        AsyncOperation completed = AsyncOperation.running(
            UUID.randomUUID(),
            classroomId,
            ownerId,
            "AI_QUIZ_DRAFT",
            NOW
        );
        completed.complete("/classrooms/" + classroomId + "/quiz-questions", NOW.plusSeconds(2));
        operationRepository.save(completed);
        AsyncOperation failed = AsyncOperation.running(
            UUID.randomUUID(),
            classroomId,
            ownerId,
            "AI_ECONOMIC_EVENT_DRAFT",
            NOW
        );
        failed.fail("AI_TIMEOUT", "provider-secret-key", NOW.plusSeconds(3));
        operationRepository.save(failed);

        mockMvc.perform(get("/operations/{operationId}", running.getId()).principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.progress_percent").value(55));
        mockMvc.perform(get("/operations/{operationId}", completed.getId()).principal(coTeacher))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.progress_percent").value(100))
            .andExpect(jsonPath("$.data.result_url")
                .value("/classrooms/" + classroomId + "/quiz-questions"));
        MvcResult failure = mockMvc.perform(get(
                "/operations/{operationId}",
                failed.getId()
            ).principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.error_code").value("AI_TIMEOUT"))
            .andReturn();
        assertFalse(failure.getResponse().getContentAsString().contains("provider-secret-key"));

        mockMvc.perform(get("/operations/{operationId}", running.getId()).principal(outsider))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/operations/{operationId}", UUID.randomUUID()).principal(owner))
            .andExpect(status().isNotFound());
    }

    @Test
    void reportsAwaitingProposalAsExpiredAfterItsDeadline() throws Exception {
        createCompletedRun(Instant.EPOCH, "SETUP_PRECHECK", false, true, false);

        mockMvc.perform(get(
                "/classrooms/{classroomId}/ai/runs-and-proposals",
                classroomId
            ).principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].proposals[0].status").value("EXPIRED"));
    }

    private UUID createCompletedRun(
        Instant requestedAt,
        String runType,
        boolean fallbackUsed,
        boolean withProposal
    ) {
        return createCompletedRun(requestedAt, runType, fallbackUsed, withProposal, true);
    }

    private UUID createCompletedRun(
        Instant requestedAt,
        String runType,
        boolean fallbackUsed,
        boolean withProposal,
        boolean acknowledged
    ) {
        UUID operationId = UUID.randomUUID();
        AsyncOperation operation = AsyncOperation.running(
            operationId,
            classroomId,
            ownerId,
            "AI_BALANCING_ANALYSIS",
            requestedAt
        );
        operation.complete("/operations/" + operationId, requestedAt.plusSeconds(1));
        operationRepository.save(operation);

        UUID runId = UUID.randomUUID();
        AiRun run = AiRun.generating(
            runId,
            classroomId,
            operationId,
            ownerId,
            runType,
            "TEACHER_REQUEST",
            "rules-2026-08",
            Map.of("student_raw_text", "student-private-raw-text"),
            requestedAt
        );
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("fallback_used", fallbackUsed);
        validation.put("fallback_code", fallbackUsed ? "AI_TIMEOUT" : null);
        validation.put("overall_severity", "WARN");
        validation.put("rule_version", "rules-2026-08");
        validation.put("validation_codes", List.of("SCHEMA_VALID"));
        run.awaitTeacher(
            "RULE_FALLBACK",
            "deterministic-v1",
            validation,
            requestedAt.plusSeconds(1)
        );
        runRepository.save(run);
        artifactRepository.save(AiArtifact.create(
            UUID.randomUUID(),
            classroomId,
            runId,
            "SETUP_PRECHECK_EXPLANATION",
            Map.of(
                "provider_api_key", "provider-secret-key",
                "output", Map.of(
                    "summary", "Review the proposed product price.",
                    "checks", List.of(Map.of(
                        "evidence_reason_codes", List.of("POLICY-001")
                    ))
                )
            ),
            "1.0",
            "SAFE",
            requestedAt.plusSeconds(1)
        ));
        if (withProposal) {
            UUID proposalId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            AiProposal proposal = AiProposal.setup(
                proposalId,
                classroomId,
                runId,
                "WARN",
                Map.of(
                    "context_type", "PRODUCT_DRAFT",
                    "target_type", "STORE_PRODUCT",
                    "target_id", targetId.toString(),
                    "target_version", 4,
                    "rule_version", "rules-2026-08",
                    "items", List.of(Map.of(
                        "action_id", "product-price",
                        "target_type", "STORE_PRODUCT",
                        "target_id", targetId.toString(),
                        "proposed_value", 250,
                        "unit", "CURRENCY",
                        "label", "Product price"
                    ))
                ),
                Map.of("items", List.of(Map.of(
                    "action_id", "product-price",
                    "allowed_min_value", 100,
                    "allowed_max_value", 500
                ))),
                requestedAt
            );
            if (acknowledged) {
                proposal.acknowledge(requestedAt.plusSeconds(2));
            }
            proposalRepository.save(proposal);
            if (acknowledged) {
                decisionRepository.save(AiDecision.create(
                    UUID.randomUUID(),
                    classroomId,
                    proposalId,
                    ownerId,
                    "ACKNOWLEDGE",
                    "Reviewed",
                    requestedAt.plusSeconds(2)
                ));
            }
        }
        return runId;
    }

    private static String randomClassroomCode() {
        return "H" + UUID.randomUUID().toString().replace("-", "")
            .substring(0, 7).toUpperCase();
    }
}
