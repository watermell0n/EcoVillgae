package com.b201.ecovillage.identity.application;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.UserType;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.identity.security.EcoVillagePrincipal;

@Service
public class AuthBootstrapQueryService {

    private static final String STUDENT_START_SCREEN = "SCR-STU-003";
    private static final String TEACHER_DASHBOARD_START_SCREEN = "SCR-WEB-002";
    private static final String CLASSROOM_CREATION_START_SCREEN = "SCR-WEB-003";

    private final UserAccountRepository userAccountRepository;
    private final ClassroomTeacherRepository classroomTeacherRepository;
    private final ClassroomStudentRepository classroomStudentRepository;
    private final ClassroomRepository classroomRepository;

    public AuthBootstrapQueryService(
        UserAccountRepository userAccountRepository,
        ClassroomTeacherRepository classroomTeacherRepository,
        ClassroomStudentRepository classroomStudentRepository,
        ClassroomRepository classroomRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.classroomTeacherRepository = classroomTeacherRepository;
        this.classroomStudentRepository = classroomStudentRepository;
        this.classroomRepository = classroomRepository;
    }

    @Transactional(readOnly = true)
    public AuthBootstrapView get(EcoVillagePrincipal principal) {
        requirePrincipal(principal);
        return principal.isTeacher()
            ? teacherBootstrap(principal)
            : studentBootstrap(principal);
    }

    private AuthBootstrapView teacherBootstrap(EcoVillagePrincipal principal) {
        UserAccount teacher = userAccountRepository
            .findById(principal.actorId())
            .filter(user -> user.getUserType() == UserType.TEACHER)
            .filter(user -> user.getStatus() == AccountStatus.ACTIVE)
            .orElseThrow(AuthBootstrapQueryService::authRequired);

        Map<UUID, ClassroomTeacherRole> rolesByClassroom = new LinkedHashMap<>();
        classroomTeacherRepository
            .findAllByUserIdAndEndedAtIsNull(teacher.getId())
            .forEach(assignment -> rolesByClassroom.merge(
                assignment.getClassroomId(),
                assignment.getRole(),
                AuthBootstrapQueryService::morePrivileged
            ));

        List<AuthBootstrapView.ClassroomView> classrooms = classroomRepository
            .findAllById(rolesByClassroom.keySet())
            .stream()
            .sorted(Comparator.comparing(Classroom::getClassroomCode))
            .map(AuthBootstrapQueryService::toView)
            .toList();
        List<String> roles = rolesByClassroom
            .values()
            .stream()
            .map(AuthBootstrapQueryService::roleCode)
            .distinct()
            .sorted(Comparator.comparingInt(AuthBootstrapQueryService::roleOrder))
            .toList();

        return new AuthBootstrapView(
            teacher.getId(),
            roles.isEmpty() ? List.of("TCH") : roles,
            classrooms,
            classrooms.isEmpty()
                ? CLASSROOM_CREATION_START_SCREEN
                : TEACHER_DASHBOARD_START_SCREEN
        );
    }

    private AuthBootstrapView studentBootstrap(EcoVillagePrincipal principal) {
        ClassroomStudent student = classroomStudentRepository
            .findById(principal.actorId())
            .filter(candidate -> candidate.getStatus() == ClassroomStudentStatus.ACTIVE)
            .orElseThrow(AuthBootstrapQueryService::authRequired);
        Classroom classroom = classroomRepository
            .findById(student.getClassroomId())
            .orElseThrow(AuthBootstrapQueryService::authRequired);

        return new AuthBootstrapView(
            student.getId(),
            List.of("STU"),
            List.of(toView(classroom)),
            STUDENT_START_SCREEN
        );
    }

    private static AuthBootstrapView.ClassroomView toView(Classroom classroom) {
        return new AuthBootstrapView.ClassroomView(
            classroom.getId(),
            classroom.getDisplayName(),
            classroom.getStatus().name(),
            Math.toIntExact(classroom.getStateVersion()),
            classroom.getStateVersion(),
            classroom.getClassroomCode(),
            classroom.getCurrencyName()
        );
    }

    private static ClassroomTeacherRole morePrivileged(
        ClassroomTeacherRole first,
        ClassroomTeacherRole second
    ) {
        return first == ClassroomTeacherRole.OWNER || second == ClassroomTeacherRole.OWNER
            ? ClassroomTeacherRole.OWNER
            : ClassroomTeacherRole.COT;
    }

    private static String roleCode(ClassroomTeacherRole role) {
        return role == ClassroomTeacherRole.OWNER ? "OWN" : "COT";
    }

    private static int roleOrder(String role) {
        return "OWN".equals(role) ? 0 : 1;
    }

    private static void requirePrincipal(EcoVillagePrincipal principal) {
        if (
            principal == null
                || principal.actorId() == null
                || (!principal.isTeacher() && !principal.isStudent())
        ) {
            throw authRequired();
        }
    }

    private static TeacherAuthenticationException authRequired() {
        return new TeacherAuthenticationException(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REQUIRED",
            "로그인이 필요합니다.",
            false
        );
    }
}
