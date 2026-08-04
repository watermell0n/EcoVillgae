package com.b201.ecovillage.vote.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.classroom.application.EffectiveAccessModeService;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;
import com.b201.ecovillage.ledger.repository.TransactionBusinessRefRepository;
import com.b201.ecovillage.quiz.repository.ActivityParticipantRepository;
import com.b201.ecovillage.quiz.repository.ActivitySessionRepository;
import com.b201.ecovillage.realtime.application.DisplaySessionService;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.vote.repository.VoteBallotRepository;
import com.b201.ecovillage.vote.repository.VoteEligibilityRepository;
import com.b201.ecovillage.vote.repository.VoteOptionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

class VoteCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");

    @Test
    void createsBudgetOptionsAndAnonymousEligibilityWithinTheCurrentFund() {
        UUID classroomId = UUID.randomUUID();
        UUID voteId = UUID.randomUUID();
        UUID teacherId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        ClassroomAuthorizationService authorizationService =
            mock(ClassroomAuthorizationService.class);
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        ClassroomRepository classroomRepository = mock(ClassroomRepository.class);
        ClassroomStudentRepository studentRepository =
            mock(ClassroomStudentRepository.class);
        ActivitySessionRepository activityRepository =
            mock(ActivitySessionRepository.class);
        ActivityParticipantRepository participantRepository =
            mock(ActivityParticipantRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        VoteOptionRepository optionRepository = mock(VoteOptionRepository.class);
        VoteEligibilityRepository eligibilityRepository =
            mock(VoteEligibilityRepository.class);
        VoteBallotRepository ballotRepository =
            mock(VoteBallotRepository.class);
        LedgerAccountRepository accountRepository =
            mock(LedgerAccountRepository.class);
        TransactionBusinessRefRepository businessRefRepository =
            mock(TransactionBusinessRefRepository.class);
        IdempotencyRecordRepository idempotencyRepository =
            mock(IdempotencyRecordRepository.class);
        AtomicLedgerTransactionService ledgerTransactionService =
            mock(AtomicLedgerTransactionService.class);
        EffectiveAccessModeService accessModeService = mock(EffectiveAccessModeService.class);
        RealtimeEventPublisher eventPublisher =
            mock(RealtimeEventPublisher.class);
        DisplaySessionService displaySessionService =
            mock(DisplaySessionService.class);
        AuditTrailService auditTrailService = mock(AuditTrailService.class);
        UuidV7Generator uuidGenerator = mock(UuidV7Generator.class);
        VoteCommandService service = new VoteCommandService(
            authorizationService,
            userRepository,
            classroomRepository,
            studentRepository,
            activityRepository,
            participantRepository,
            voteRepository,
            optionRepository,
            eligibilityRepository,
            ballotRepository,
            accountRepository,
            businessRefRepository,
            idempotencyRepository,
            ledgerTransactionService,
            accessModeService,
            eventPublisher,
            displaySessionService,
            auditTrailService,
            uuidGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UserAccount teacher = UserAccount.createTeacher(
            teacherId,
            "teacher-subject",
            "교사",
            NOW.minusSeconds(300)
        );
        ClassroomStudent student = ClassroomStudent.enroll(
            studentId,
            classroomId,
            1,
            "학생",
            NOW.minusSeconds(300)
        );
        LedgerAccount fund = LedgerAccount.systemAccount(
            UUID.randomUUID(),
            classroomId,
            "CLASSROOM_FUND",
            LedgerAccountType.CLASSROOM_FUND,
            NOW.minusSeconds(300)
        );
        fund.post(500, NOW.minusSeconds(200));
        when(userRepository.findByExternalSubject("teacher-subject"))
            .thenReturn(Optional.of(teacher));
        when(idempotencyRepository.findForReplay(
            any(),
            any(),
            anyString(),
            anyString()
        )).thenReturn(Optional.empty());
        when(classroomRepository.findById(classroomId))
            .thenReturn(Optional.of(Classroom.active(
                classroomId,
                "CLASS-01",
                NOW.minusSeconds(300)
            )));
        when(studentRepository.findAllForUpdate(
            classroomId,
            List.of(studentId),
            ClassroomStudentStatus.ACTIVE
        )).thenReturn(List.of(student));
        when(accountRepository
            .findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(
                classroomId,
                "CLASSROOM_FUND",
                LedgerAccountType.CLASSROOM_FUND,
                LedgerAccountStatus.ACTIVE
            )).thenReturn(Optional.of(fund));
        when(uuidGenerator.generate()).thenAnswer(invocation -> UUID.randomUUID());
        when(activityRepository.saveAndFlush(any())).thenAnswer(
            invocation -> invocation.getArgument(0)
        );
        when(voteRepository.saveAndFlush(any())).thenAnswer(
            invocation -> invocation.getArgument(0)
        );
        VoteCommand command = new VoteCommand(
            "teacher-subject",
            classroomId,
            voteId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            VoteCommandType.CREATE,
            "공동기금 사용처",
            "우리 반이 사용할 예산을 고릅니다.",
            List.of(
                new VoteOptionCommand("학급 도서", null, 200L),
                new VoteOptionCommand("체육 용품", null, 300L)
            ),
            List.of(studentId),
            true,
            null
        );

        VoteCommandResult result = service.execute(command);

        assertEquals(voteId, result.voteId());
        assertEquals("READY", result.status());
        verify(optionRepository).saveAll(any());
        verify(eligibilityRepository).saveAll(any());
        verify(idempotencyRepository).save(any());
    }
}
