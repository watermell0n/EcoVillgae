package com.b201.ecovillage.classroom.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.b201.ecovillage.identity.domain.AccountStatus;
import com.b201.ecovillage.identity.domain.ClassroomTeacherRole;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.domain.UserType;
import com.b201.ecovillage.identity.repository.ClassroomTeacherRepository;
import com.b201.ecovillage.identity.repository.SchoolMembershipRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

@Service
public class TeacherAccessService {

    private final UserAccountRepository userAccountRepository;
    private final SchoolMembershipRepository schoolMembershipRepository;
    private final ClassroomTeacherRepository classroomTeacherRepository;

    public TeacherAccessService(
        UserAccountRepository userAccountRepository,
        SchoolMembershipRepository schoolMembershipRepository,
        ClassroomTeacherRepository classroomTeacherRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.schoolMembershipRepository = schoolMembershipRepository;
        this.classroomTeacherRepository = classroomTeacherRepository;
    }

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
            throw new ClassroomOperationException(
                HttpStatus.FORBIDDEN,
                "SCHOOL_SCOPE_VIOLATION",
                "교사가 소속된 학교에만 경제교실을 만들 수 있습니다."
            );
        }
        return teacher;
    }

    public UserAccount requireOwner(String externalSubject, UUID classroomId) {
        UserAccount teacher = requireActiveTeacher(externalSubject);
        boolean owner = classroomTeacherRepository
            .existsByClassroomIdAndUserIdAndRoleAndEndedAtIsNull(
                classroomId,
                teacher.getId(),
                ClassroomTeacherRole.OWNER
            );
        if (!owner) {
            throw new ClassroomOperationException(
                HttpStatus.FORBIDDEN,
                "OWNER_REQUIRED",
                "학급 소유 교사만 실행할 수 있습니다."
            );
        }
        return teacher;
    }

    public UserAccount requireAssignedTeacher(String externalSubject, UUID classroomId) {
        UserAccount teacher = requireActiveTeacher(externalSubject);
        boolean assigned = classroomTeacherRepository
            .existsByClassroomIdAndUserIdAndEndedAtIsNull(classroomId, teacher.getId());
        if (!assigned) {
            throw new ClassroomOperationException(
                HttpStatus.FORBIDDEN,
                "CLASS_NOT_ASSIGNED",
                "담당 경제교실에만 접근할 수 있습니다."
            );
        }
        return teacher;
    }

    private UserAccount requireActiveTeacher(String externalSubject) {
        if (externalSubject == null || externalSubject.isBlank()) {
            throw new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "교사 인증이 필요합니다."
            );
        }
        UserAccount teacher = userAccountRepository
            .findByExternalSubject(externalSubject)
            .orElseThrow(() -> new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "유효한 교사 계정을 확인할 수 없습니다."
            ));
        if (
            teacher.getUserType() != UserType.TEACHER
                || teacher.getStatus() != AccountStatus.ACTIVE
        ) {
            throw new ClassroomOperationException(
                HttpStatus.FORBIDDEN,
                "TEACHER_ACCOUNT_REQUIRED",
                "활성 교사 계정만 실행할 수 있습니다."
            );
        }
        return teacher;
    }
}
