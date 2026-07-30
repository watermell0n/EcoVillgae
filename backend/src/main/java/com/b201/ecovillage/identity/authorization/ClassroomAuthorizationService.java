package com.b201.ecovillage.identity.authorization;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.UserType;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.SchoolMembershipRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@Service
public class ClassroomAuthorizationService {

    private final UserAccountRepository userAccountRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomTeacherRepository classroomTeacherRepository;
    private final ClassroomStudentRepository classroomStudentRepository;
    private final Clock clock;

    public ClassroomAuthorizationService(
        UserAccountRepository userAccountRepository,
        SchoolMembershipRepository schoolMembershipRepository,
        ClassroomRepository classroomRepository,
        ClassroomTeacherRepository classroomTeacherRepository,
        ClassroomStudentRepository classroomStudentRepository,
        Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.classroomRepository = classroomRepository;
        this.classroomTeacherRepository = classroomTeacherRepository;
        this.classroomStudentRepository = classroomStudentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserAccount requireTeacherInSchool(String externalSubject, UUID schoolId) {
        UserAccount teacher = requireActiveTeacher(externalSubject);
        boolean belongsToSchool = !schoolMembershipRepository
            .findAllBySchoolIdAndUserIdAndStatus(
                schoolId,
                teacher.getId(),
                AccountStatus.ACTIVE
            )
            .isEmpty();
        if (!belongsToSchool) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "SCHOOL_SCOPE_VIOLATION",
                "교사가 소속된 학교에만 경제교실을 만들 수 있습니다."
            );
        }
        return teacher;
    }

    @Transactional(readOnly = true)
    public AuthorizationDecision authorize(
        String subject,
        UUID classroomId,
        ClassroomPermission permission,
        UUID targetStudentId
    ) {
        if (subject == null || subject.isBlank()) {
            throw denied(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "인증이 필요합니다."
            );
        }
        Classroom classroom = classroomRepository
            .findById(classroomId)
            .orElseThrow(() -> denied(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "경제교실을 찾을 수 없습니다."
            ));

        return userAccountRepository
            .findByExternalSubject(subject)
            .map(user -> authorizeTeacher(user, classroom, permission))
            .orElseGet(() -> authorizeStudent(
                subject,
                classroom,
                permission,
                targetStudentId
            ));
    }

    private AuthorizationDecision authorizeTeacher(
        UserAccount user,
        Classroom classroom,
        ClassroomPermission permission
    ) {
        if (user.getUserType() != UserType.TEACHER || user.getStatus() != AccountStatus.ACTIVE) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "ROLE_NOT_ALLOWED",
                "활성 교사 계정이 아닙니다."
            );
        }
        List<ClassroomTeacher> assignments = classroomTeacherRepository
            .findAllByClassroomIdAndUserIdAndEndedAtIsNull(
                classroom.getId(),
                user.getId()
            );
        if (assignments.isEmpty()) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "CLASS_NOT_ASSIGNED",
                "담당 경제교실에만 접근할 수 있습니다."
            );
        }
        ClassroomActorRole role = assignments.stream()
            .map(ClassroomTeacher::getRole)
            .map(ClassroomAuthorizationService::toActorRole)
            .min(Comparator.comparingInt(ClassroomAuthorizationService::roleOrder))
            .orElseThrow();
        if (!permission.allows(role)) {
            String reason = permission == ClassroomPermission.MANAGE_ROSTER
                ? "OWNER_REQUIRED"
                : "ROLE_NOT_ALLOWED";
            throw denied(
                HttpStatus.FORBIDDEN,
                reason,
                "현재 역할에는 이 작업 권한이 없습니다."
            );
        }
        AuthorizationScope scope = role == ClassroomActorRole.OWN
            ? AuthorizationScope.OWNED_CLASS
            : AuthorizationScope.ASSIGNED_CLASS;
        return allowed(permission, role, scope, classroom);
    }

    private AuthorizationDecision authorizeStudent(
        String subject,
        Classroom classroom,
        ClassroomPermission permission,
        UUID targetStudentId
    ) {
        UUID studentId;
        try {
            studentId = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw denied(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "유효한 인증 주체를 확인할 수 없습니다."
            );
        }
        ClassroomStudent student = classroomStudentRepository
            .findById(studentId)
            .filter(candidate -> candidate.getStatus() == ClassroomStudentStatus.ACTIVE)
            .orElseThrow(() -> denied(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "유효한 학생 인증 주체를 확인할 수 없습니다."
            ));
        if (!student.getClassroomId().equals(classroom.getId())) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "CLASS_NOT_ASSIGNED",
                "현재 소속 경제교실에만 접근할 수 있습니다."
            );
        }
        if (!permission.allows(ClassroomActorRole.STU)) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "ROLE_NOT_ALLOWED",
                "학생 역할에는 이 작업 권한이 없습니다."
            );
        }
        if (targetStudentId == null || !student.getId().equals(targetStudentId)) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "RESOURCE_NOT_OWNED",
                "본인 데이터에만 접근할 수 있습니다."
            );
        }
        return allowed(
            permission,
            ClassroomActorRole.STU,
            AuthorizationScope.SELF,
            classroom
        );
    }

    private UserAccount requireActiveTeacher(String externalSubject) {
        if (externalSubject == null || externalSubject.isBlank()) {
            throw denied(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "교사 인증이 필요합니다."
            );
        }
        UserAccount teacher = userAccountRepository
            .findByExternalSubject(externalSubject)
            .orElseThrow(() -> denied(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "유효한 교사 계정을 확인할 수 없습니다."
            ));
        if (
            teacher.getUserType() != UserType.TEACHER
                || teacher.getStatus() != AccountStatus.ACTIVE
        ) {
            throw denied(
                HttpStatus.FORBIDDEN,
                "ROLE_NOT_ALLOWED",
                "활성 교사 계정만 실행할 수 있습니다."
            );
        }
        return teacher;
    }

    private AuthorizationDecision allowed(
        ClassroomPermission permission,
        ClassroomActorRole role,
        AuthorizationScope scope,
        Classroom classroom
    ) {
        return new AuthorizationDecision(
            "ALLOW",
            permission.getRuleId(),
            role,
            scope,
            classroom.getId(),
            classroom.getStateVersion(),
            "ALLOWED",
            clock.instant()
        );
    }

    private static ClassroomActorRole toActorRole(ClassroomTeacherRole role) {
        return role == ClassroomTeacherRole.OWNER
            ? ClassroomActorRole.OWN
            : ClassroomActorRole.COT;
    }

    private static int roleOrder(ClassroomActorRole role) {
        return role == ClassroomActorRole.OWN ? 0 : 1;
    }

    private static ClassroomOperationException denied(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
