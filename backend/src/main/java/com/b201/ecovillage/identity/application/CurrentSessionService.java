package com.b201.ecovillage.identity.application;

import java.time.Clock;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomTeacher;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.StudentSession;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.SchoolMembershipRepository;
import com.b201.ecovillage.identity.repository.StudentSessionRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.identity.security.EcoVillagePrincipal;

@Service
public class CurrentSessionService {

    private final TeacherSessionStore teacherSessionStore;
    private final UserAccountRepository userAccountRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final ClassroomTeacherRepository classroomTeacherRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomStudentRepository classroomStudentRepository;
    private final StudentSessionRepository studentSessionRepository;
    private final Clock clock;

    public CurrentSessionService(
        TeacherSessionStore teacherSessionStore,
        UserAccountRepository userAccountRepository,
        SchoolMembershipRepository schoolMembershipRepository,
        ClassroomTeacherRepository classroomTeacherRepository,
        ClassroomRepository classroomRepository,
        ClassroomStudentRepository classroomStudentRepository,
        StudentSessionRepository studentSessionRepository,
        Clock clock
    ) {
        this.teacherSessionStore = teacherSessionStore;
        this.userAccountRepository = userAccountRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.classroomTeacherRepository = classroomTeacherRepository;
        this.classroomRepository = classroomRepository;
        this.classroomStudentRepository = classroomStudentRepository;
        this.studentSessionRepository = studentSessionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CurrentUserView current(EcoVillagePrincipal principal) {
        requirePrincipal(principal);
        return principal.isTeacher()
            ? currentTeacher(principal)
            : currentStudent(principal);
    }

    @Transactional
    public void logout(EcoVillagePrincipal principal) {
        requirePrincipal(principal);
        if (principal.isTeacher()) {
            try {
                if (!teacherSessionStore.revoke(principal.sessionId(), clock.instant())) {
                    throw authRequired();
                }
            } catch (DataAccessException exception) {
                throw new TeacherAuthenticationException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AUTH_SESSION_STORE_UNAVAILABLE",
                    "교사 세션을 종료할 수 없습니다. 잠시 후 다시 시도해주세요.",
                    true
                );
            }
            return;
        }
        StudentSession session = studentSessionRepository
            .findById(principal.sessionId())
            .orElseThrow(CurrentSessionService::authRequired);
        session.revoke(clock.instant());
        studentSessionRepository.saveAndFlush(session);
    }

    private CurrentUserView currentTeacher(EcoVillagePrincipal principal) {
        UserAccount teacher = userAccountRepository
            .findById(principal.actorId())
            .orElseThrow(CurrentSessionService::authRequired);
        List<SchoolAccessView> schools = schoolMembershipRepository
            .findAllByUserIdAndStatus(teacher.getId(), AccountStatus.ACTIVE)
            .stream()
            .map(membership -> new SchoolAccessView(
                membership.getSchoolId(),
                membership.getRole().name()
            ))
            .toList();
        List<ClassroomAccessView> classrooms = classroomTeacherRepository
            .findAllByUserIdAndEndedAtIsNull(teacher.getId())
            .stream()
            .map(this::toClassroomAccess)
            .toList();
        return new CurrentUserView(
            principal.sessionId(),
            teacher.getId(),
            teacher.getDisplayName(),
            "TCH",
            principal.accessTokenExpiresAt(),
            schools,
            classrooms
        );
    }

    private CurrentUserView currentStudent(EcoVillagePrincipal principal) {
        ClassroomStudent student = classroomStudentRepository
            .findById(principal.actorId())
            .orElseThrow(CurrentSessionService::authRequired);
        Classroom classroom = classroomRepository
            .findById(student.getClassroomId())
            .orElseThrow(CurrentSessionService::authRequired);
        return new CurrentUserView(
            principal.sessionId(),
            student.getId(),
            student.getNickname(),
            "STU",
            principal.accessTokenExpiresAt(),
            List.of(),
            List.of(new ClassroomAccessView(
                classroom.getId(),
                classroom.getClassroomCode(),
                "STU"
            ))
        );
    }

    private ClassroomAccessView toClassroomAccess(ClassroomTeacher assignment) {
        Classroom classroom = classroomRepository
            .findById(assignment.getClassroomId())
            .orElseThrow(CurrentSessionService::authRequired);
        String role = assignment.getRole() == ClassroomTeacherRole.OWNER
            ? "OWN"
            : "COT";
        return new ClassroomAccessView(
            classroom.getId(),
            classroom.getClassroomCode(),
            role
        );
    }

    private static void requirePrincipal(EcoVillagePrincipal principal) {
        if (principal == null || (!principal.isTeacher() && !principal.isStudent())) {
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
