package com.b201.ecovillage.character.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.repository.StudentCharacterProfileRepository;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:character-profile-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Transactional
class StudentCharacterProfileApiTest {

    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClassroomRepository classroomRepository;

    @Autowired
    private ClassroomStudentRepository studentRepository;

    @Autowired
    private StudentCharacterProfileRepository profileRepository;

    private UUID classroomId;
    private UUID anotherClassroomId;
    private UUID studentId;
    private Principal studentPrincipal;

    @BeforeEach
    void setUp() {
        classroomId = UUID.randomUUID();
        anotherClassroomId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        studentPrincipal = () -> studentId.toString();

        classroomRepository.save(Classroom.active(classroomId, "ABCD2345", NOW));
        classroomRepository.save(Classroom.active(anotherClassroomId, "EFGH5678", NOW));
        studentRepository.save(
            ClassroomStudent.enroll(studentId, classroomId, 1, "프로필학생", NOW)
        );
    }

    @Test
    void createsAndReturnsTheAuthenticatedStudentsProfile() throws Exception {
        UUID correlationId = UUID.randomUUID();

        mockMvc.perform(get(
            "/classrooms/{classroomId}/students/me/character-profile",
            classroomId
        )
            .principal(studentPrincipal)
            .header("X-Correlation-Id", correlationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.profile_id").isString())
            .andExpect(jsonPath("$.data.classroom_student_id").value(studentId.toString()))
            .andExpect(jsonPath("$.data.state_version").value(0))
            .andExpect(jsonPath("$.data.body_state").doesNotExist())
            .andExpect(jsonPath("$.data.body_state_changed_at").doesNotExist())
            .andExpect(jsonPath("$.data.body_calculation").doesNotExist())
            .andExpect(jsonPath("$.data.equipment_changed_at").isString())
            .andExpect(jsonPath("$.data.equipped_inventory_items").isMap())
            .andExpect(jsonPath("$.meta.correlation_id").value(correlationId.toString()));

        assertTrue(
            profileRepository.findByClassroomIdAndClassroomStudentId(
                classroomId,
                studentId
            ).isPresent()
        );
    }

    @Test
    void returnsTheSameProfileOnEveryReadWithoutBumpingTheAggregateVersion() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(get(
                "/classrooms/{classroomId}/students/me/character-profile",
                classroomId
            ).principal(studentPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state_version").value(0));
        }
    }

    @Test
    void delegatesClassroomBoundaryAuthorization() throws Exception {
        mockMvc.perform(get(
            "/classrooms/{classroomId}/students/me/character-profile",
            anotherClassroomId
        ).principal(studentPrincipal))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code").value("CLASS_NOT_ASSIGNED"));
    }

    @Test
    void savesAnEmptyEquipmentMapAndKeepsTheProfileReadable() throws Exception {
        mockMvc.perform(put(
            "/classrooms/{classroomId}/students/me/character-profile/equipment",
            classroomId
        )
            .principal(studentPrincipal)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expected_state_version\":0,\"equipment\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.classroom_student_id").value(studentId.toString()))
            .andExpect(jsonPath("$.data.equipped_inventory_items").isMap());
    }

    @Test
    void rejectsAnEquipmentSaveWithAStaleProfileVersion() throws Exception {
        mockMvc.perform(put(
            "/classrooms/{classroomId}/students/me/character-profile/equipment",
            classroomId
        )
            .principal(studentPrincipal)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expected_state_version\":9,\"equipment\":{}}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("STATE_VERSION_CONFLICT"));
    }

    @Test
    void equipmentSaveRequiresTheAggregateStateVersion() throws Exception {
        mockMvc.perform(put(
            "/classrooms/{classroomId}/students/me/character-profile/equipment",
            classroomId
        )
            .principal(studentPrincipal)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"equipment\":{}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void equipmentCommandRequiresTheAggregateStateVersion() throws Exception {
        mockMvc.perform(post(
            "/classrooms/{classroomId}/inventory-items/{inventoryItemId}/commands",
            classroomId,
            UUID.randomUUID()
        )
            .principal(studentPrincipal)
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"command\":\"EQUIP\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }
}
