package com.b201.ecovillage.store.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import com.b201.ecovillage.audit.repository.AuditEventRepository;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
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
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:store-product-command;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StoreProductCommandApiTest {

    private static final String OWNER_SUBJECT = "store-product-owner";
    private static final Instant NOW = Instant.parse("2026-08-03T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomTeacherRepository classroomTeacherRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private ClassroomPolicyVersionRepository policyRepository;

    @Autowired
    private StoreProductVersionRepository versionRepository;

    @Autowired
    private AuditEventRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID classroomId;
    private UUID teacherId;
    private UUID studentId;
    private Principal owner;
    private Principal student;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        owner = () -> OWNER_SUBJECT + classroomId;
        student = () -> studentId.toString();

        userRepository.save(UserAccount.createTeacher(
            teacherId,
            owner.getName(),
            "Store Owner",
            NOW
        ));
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
        classroomTeacherRepository.save(ClassroomTeacher.assign(
            UUID.randomUUID(),
            classroomId,
            teacherId,
            ClassroomTeacherRole.OWNER,
            NOW
        ));
        studentRepository.save(ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "학생",
            NOW
        ));
    }

    @Test
    void executesAllCommandsReplaysSameRequestAndKeepsDraftVisibleToTeacher()
        throws Exception {
        UUID createKey = UUID.randomUUID();
        ObjectNode create = body("CREATE", null, null, "연필", 1, 1_000, 10, 0, 0);
        JsonNode created = execute(createKey, create);
        UUID productId = UUID.fromString(created.path("data").path("product_id").asText());
        assertEquals("DRAFT", created.path("data").path("status").asText());
        assertEquals(1, created.path("data").path("version").asInt());

        JsonNode replayed = execute(createKey, create);
        assertEquals(created.path("data"), replayed.path("data"));
        assertEquals(1, versionsInClassroom());
        assertEquals(1, auditsInClassroom());
        assertEquals(1, idempotencyRecordsInClassroom());

        mockMvc.perform(get("/classrooms/{classroomId}/store/catalog", classroomId)
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products[0].resource_id").value(productId.toString()))
            .andExpect(jsonPath("$.data.products[0].status").value("DRAFT"))
            .andExpect(jsonPath("$.data.products[0].version").value(1))
            .andExpect(jsonPath("$.data.products[0].unit_price").value(1))
            .andExpect(jsonPath("$.data.products[0].vat_rate_bps").value(1_000))
            .andExpect(jsonPath("$.data.products[0].stock_quantity").value(10));

        JsonNode updated = execute(
            UUID.randomUUID(),
            body("UPDATE", productId, 1, "연필 세트", 1, 1_000, 8, 0, 0)
        );
        assertEquals(2, updated.path("data").path("version").asInt());
        assertEquals("DRAFT", updated.path("data").path("status").asText());

        JsonNode published = execute(
            UUID.randomUUID(),
            body("PUBLISH", productId, 2, "연필 세트", 1, 1_000, 8, 0, 0)
        );
        assertEquals(3, published.path("data").path("version").asInt());
        assertEquals("PUBLISHED", published.path("data").path("status").asText());

        JsonNode unpublished = execute(
            UUID.randomUUID(),
            body("UNPUBLISH", productId, 3, "연필 세트", 1, 1_000, 7, 0, 0)
        );
        assertEquals(4, unpublished.path("data").path("version").asInt());
        assertEquals("UNPUBLISHED", unpublished.path("data").path("status").asText());
        assertEquals(4, versionsInClassroom());
        assertEquals(4, auditsInClassroom());

        mockMvc.perform(commandRequest(createKey, body(
                "CREATE",
                null,
                null,
                "다른 상품명",
                1,
                1_000,
                10,
                0,
                0
            )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void exposesOnlyPublishedProductsToStudents() throws Exception {
        JsonNode created = execute(
            UUID.randomUUID(),
            body("CREATE", null, null, "이용권", 250, 0, 30, 0, 0)
        );
        UUID productId = UUID.fromString(created.path("data").path("product_id").asText());

        mockMvc.perform(get("/classrooms/{classroomId}/store/catalog", classroomId)
                .principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products.length()").value(0));

        execute(
            UUID.randomUUID(),
            body("PUBLISH", productId, 1, "이용권", 250, 0, 30, 0, 0)
        );
        mockMvc.perform(get("/classrooms/{classroomId}/store/catalog", classroomId)
                .principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products.length()").value(1))
            .andExpect(jsonPath("$.data.products[0].status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.products[0].version").value(2));

        execute(
            UUID.randomUUID(),
            body("UNPUBLISH", productId, 2, "이용권", 250, 0, 30, 0, 0)
        );
        mockMvc.perform(get("/classrooms/{classroomId}/store/catalog", classroomId)
                .principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products.length()").value(0));
    }

    @Test
    void keepsUploadedProductImageAndExposesItInStudentCatalog() throws Exception {
        String imageUrl = "data:image/png;base64,iVBORw0KGgo=";
        ObjectNode create = body(
            "CREATE",
            null,
            null,
            "Photo product",
            250,
            0,
            10,
            0,
            0
        );
        create.put("image_url", imageUrl);
        JsonNode created = execute(UUID.randomUUID(), create);
        UUID productId = UUID.fromString(created.path("data").path("product_id").asText());

        execute(
            UUID.randomUUID(),
            body("PUBLISH", productId, 1, "Photo product", 250, 0, 10, 0, 0)
        );

        mockMvc.perform(get("/classrooms/{classroomId}/store/catalog", classroomId)
                .principal(student))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products[0].image_url").value(imageUrl));
    }

    @Test
    void rejectsStaleClassroomPolicyAndProductVersionsAndPolicyRanges()
        throws Exception {
        policyRepository.save(ClassroomPolicyVersion.policy(
            UUID.randomUUID(),
            classroomId,
            "STORE",
            1,
            Map.of(
                "maximum_unit_price", 100,
                "maximum_stock_quantity", 50,
                "maximum_vat_rate_bps", 2_000
            ),
            "TEACHER",
            NOW
        ));

        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "CREATE", null, null, "상태 충돌", 50, 1_000, 10, 9, 1
            )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));

        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "CREATE", null, null, "정책 충돌", 50, 1_000, 10, 0, 0
            )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("POLICY_VERSION_CONFLICT"));

        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "CREATE", null, null, "가격 초과", 101, 1_000, 10, 0, 1
            )))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("PRICE_OUT_OF_RANGE"));

        JsonNode created = execute(
            UUID.randomUUID(),
            body("CREATE", null, null, "정상 상품", 100, 2_000, 50, 0, 1)
        );
        UUID productId = UUID.fromString(created.path("data").path("product_id").asText());
        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "UPDATE", productId, 99, "오래된 수정", 100, 2_000, 50, 0, 1
            )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("PRODUCT_VERSION_CONFLICT"));
    }

    @Test
    void rejectsInvalidValuesCharactersAndStateTransitionsForEveryCommand()
        throws Exception {
        ObjectNode createWithId = body(
            "CREATE", UUID.randomUUID(), null, "잘못된 생성", 10, 0, 1, 0, 0
        );
        mockMvc.perform(commandRequest(UUID.randomUUID(), createWithId))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "CREATE", null, null, "세율 오류", 10, 10_001, 1, 0, 0
            )))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_TAX_RATE"));

        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "CREATE", null, null, "재고 오류", 10, 0, -1, 0, 0
            )))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("INVALID_STOCK"));

        ObjectNode character = body(
            "CREATE", null, null, "미승인 캐릭터", 10, 0, 1, 0, 0
        );
        character.put("product_kind", "CHARACTER");
        character.put("character_source_id", UUID.randomUUID().toString());
        mockMvc.perform(commandRequest(UUID.randomUUID(), character))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("CHARACTER_NOT_APPROVED"));

        JsonNode created = execute(
            UUID.randomUUID(),
            body("CREATE", null, null, "상태 상품", 10, 0, 1, 0, 0)
        );
        UUID productId = UUID.fromString(created.path("data").path("product_id").asText());
        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "UNPUBLISH", productId, 1, "상태 상품", 10, 0, 1, 0, 0
            )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));

        execute(
            UUID.randomUUID(),
            body("PUBLISH", productId, 1, "상태 상품", 10, 0, 1, 0, 0)
        );
        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "UPDATE", productId, 2, "게시 중 수정", 20, 0, 1, 0, 0
            )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.version").value(3));
        mockMvc.perform(commandRequest(UUID.randomUUID(), body(
                "PUBLISH", productId, 3, "중복 게시", 20, 0, 1, 0, 0
            )))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void createsAnApprovedCharacterProductAndReturnsItsCatalogCode() throws Exception {
        UUID catalogId = UUID.randomUUID();
        UUID classroomCharacterId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO character_catalog (
                id, character_code, name, status
            ) VALUES (?, 'FOX', '여우', 'ACTIVE')
            """,
            catalogId
        );
        jdbcTemplate.update(
            """
            INSERT INTO classroom_characters (
                id, classroom_id, character_catalog_id, display_name, sort_order, status
            ) VALUES (?, ?, ?, '여우', 0, 'ACTIVE')
            """,
            classroomCharacterId,
            classroomId,
            catalogId
        );

        ObjectNode create = body(
            "CREATE", null, null, "여우 캐릭터", 500, 0, 5, 0, 0
        );
        create.put("product_kind", "CHARACTER");
        create.put("character_source_id", classroomCharacterId.toString());
        JsonNode created = execute(UUID.randomUUID(), create);
        UUID productId = UUID.fromString(created.path("data").path("product_id").asText());

        assertEquals(
            productId,
            jdbcTemplate.queryForObject(
                "SELECT store_product_id FROM classroom_characters WHERE id = ?",
                UUID.class,
                classroomCharacterId
            )
        );
        mockMvc.perform(get("/classrooms/{classroomId}/store/catalog", classroomId)
                .principal(owner))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.products[0].character_code").value("FOX"));
    }

    private JsonNode execute(UUID requestId, ObjectNode body) throws Exception {
        MvcResult result = mockMvc.perform(commandRequest(requestId, body))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
        commandRequest(UUID requestId, ObjectNode body) {
        return post(
            "/classrooms/{classroomId}/store/product-commands",
            classroomId
        )
            .principal(owner)
            .header("Idempotency-Key", requestId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body.toString());
    }

    private ObjectNode body(
        String command,
        UUID productId,
        Integer expectedProductVersion,
        String name,
        long unitPrice,
        int vatRateBps,
        int stockQuantity,
        long expectedStateVersion,
        int expectedPolicyVersion
    ) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("expected_state_version", expectedStateVersion);
        body.put("expected_policy_version", expectedPolicyVersion);
        body.put("command", command);
        if (productId != null) {
            body.put("product_id", productId.toString());
        }
        body.put("name", name);
        body.put("description", "통합 테스트 상품");
        body.put("product_kind", "PHYSICAL");
        body.put("unit_price", unitPrice);
        body.put("vat_rate_bps", vatRateBps);
        body.put("stock_quantity", stockQuantity);
        if (expectedProductVersion != null) {
            body.put("expected_product_version", expectedProductVersion);
        }
        return body;
    }

    private long versionsInClassroom() {
        return versionRepository.findAll().stream()
            .filter(version -> version.getClassroomId().equals(classroomId))
            .count();
    }

    private int auditsInClassroom() {
        return auditRepository
            .findAllByClassroomIdOrderByOccurredAtDescIdDesc(
                classroomId,
                PageRequest.of(0, 100)
            )
            .size();
    }

    private int idempotencyRecordsInClassroom() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM idempotency_records WHERE classroom_id = ?",
            Integer.class,
            classroomId
        );
    }

    private static String randomClassroomCode() {
        String value = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return value.substring(0, 8).replace('I', 'J').replace('O', 'P').replace('L', 'M');
    }
}
