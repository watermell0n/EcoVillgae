package com.b201.ecovillage.character.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.character.domain.CharacterEquipment;
import com.b201.ecovillage.character.domain.StudentCharacterProfile;
import com.b201.ecovillage.character.repository.CharacterEquipmentRepository;
import com.b201.ecovillage.character.repository.StudentCharacterProfileRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;

@Service
public class StudentCharacterProfileService {

    private final ClassroomAuthorizationService authorizationService;
    private final ClassroomStudentRepository studentRepository;
    private final StudentCharacterProfileRepository profileRepository;
    private final CharacterEquipmentRepository equipmentRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentCharacterProfileService(
        ClassroomAuthorizationService authorizationService,
        ClassroomStudentRepository studentRepository,
        StudentCharacterProfileRepository profileRepository,
        CharacterEquipmentRepository equipmentRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.studentRepository = studentRepository;
        this.profileRepository = profileRepository;
        this.equipmentRepository = equipmentRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public StudentCharacterProfileView findMine(String subject, UUID classroomId) {
        UUID studentId = studentId(subject);
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_OWN_STUDENT_DATA,
            studentId
        );
        return view(findOrCreate(classroomId, studentId));
    }

    @Transactional
    public StudentCharacterProfile findOrCreate(
        UUID classroomId,
        UUID classroomStudentId
    ) {
        ClassroomStudent student = activeStudent(classroomId, classroomStudentId);
        return profileRepository
            .findByClassroomIdAndClassroomStudentId(classroomId, classroomStudentId)
            .orElseGet(() -> createDefault(student));
    }

    private ClassroomStudent activeStudent(
        UUID classroomId,
        UUID classroomStudentId
    ) {
        return studentRepository.findActiveForUpdate(
            classroomId,
            classroomStudentId,
            ClassroomStudentStatus.ACTIVE
        )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "캐릭터 프로필의 활성 학생을 찾을 수 없습니다."
            ));
    }

    private StudentCharacterProfile createDefault(ClassroomStudent student) {
        Instant now = clock.instant();
        return profileRepository.saveAndFlush(
            StudentCharacterProfile.createDefault(
                uuidGenerator.generate(),
                student.getClassroomId(),
                student.getId(),
                now
            )
        );
    }

    private StudentCharacterProfileView view(StudentCharacterProfile profile) {
        Map<String, UUID> equipped = new LinkedHashMap<>();
        for (CharacterEquipment item : equipmentRepository
            .findAllByClassroomIdAndCharacterProfileIdOrderBySlot(
                profile.getClassroomId(),
                profile.getId()
            )) {
            equipped.put(item.getSlot().name(), item.getInventoryItemId());
        }
        return new StudentCharacterProfileView(
            profile.getId(),
            profile.getClassroomStudentId(),
            profile.getStateVersion(),
            profile.getEquipmentChangedAt(),
            profile.getCreatedAt(),
            profile.getUpdatedAt(),
            Collections.unmodifiableMap(equipped)
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "학생 인증 정보를 확인할 수 없습니다."
            );
        }
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
