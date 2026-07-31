package com.b201.ecovillage.vote.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.ledger.application.IdempotencyKeyReusedException;
import com.b201.ecovillage.ledger.domain.IdempotencyRecord;
import com.b201.ecovillage.ledger.repository.IdempotencyRecordRepository;
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;
import com.b201.ecovillage.vote.domain.Vote;
import com.b201.ecovillage.vote.domain.VoteBallot;
import com.b201.ecovillage.vote.domain.VoteEligibility;
import com.b201.ecovillage.vote.domain.VoteOption;
import com.b201.ecovillage.vote.domain.VoteStatus;
import com.b201.ecovillage.vote.repository.VoteBallotRepository;
import com.b201.ecovillage.vote.repository.VoteEligibilityRepository;
import com.b201.ecovillage.vote.repository.VoteOptionRepository;
import com.b201.ecovillage.vote.repository.VoteRepository;

@Service
public class VoteBallotService {

    private static final String ROUTE =
        "/classrooms/{classroom_id}/votes/{resource_id}/ballots";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(365);

    private final ClassroomAuthorizationService authorizationService;
    private final VoteRepository voteRepository;
    private final VoteOptionRepository optionRepository;
    private final VoteEligibilityRepository eligibilityRepository;
    private final VoteBallotRepository ballotRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RealtimeEventPublisher eventPublisher;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public VoteBallotService(
        ClassroomAuthorizationService authorizationService,
        VoteRepository voteRepository,
        VoteOptionRepository optionRepository,
        VoteEligibilityRepository eligibilityRepository,
        VoteBallotRepository ballotRepository,
        IdempotencyRecordRepository idempotencyRepository,
        RealtimeEventPublisher eventPublisher,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.voteRepository = voteRepository;
        this.optionRepository = optionRepository;
        this.eligibilityRepository = eligibilityRepository;
        this.ballotRepository = ballotRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.eventPublisher = eventPublisher;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public VoteBallotResult cast(VoteBallotCommand command) {
        validate(command);
        UUID studentId = studentId(command.studentSubject());
        authorizationService.authorize(
            command.studentSubject(),
            command.classroomId(),
            ClassroomPermission.CAST_PUBLIC_FUND_VOTE,
            studentId
        );
        String requestHash = sha256(
            command.classroomId() + "|" + command.voteId() + "|"
                + command.optionId()
        );
        VoteBallotResult replay = replay(command, studentId, requestHash);
        if (replay != null) {
            return replay;
        }
        Vote vote = voteRepository
            .findForUpdateByClassroomIdAndId(
                command.classroomId(),
                command.voteId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The vote could not be found."
            ));
        if (vote.getStatus() != VoteStatus.OPEN) {
            throw operation(
                HttpStatus.CONFLICT,
                "VOTE_NOT_LIVE",
                "The vote is not accepting ballots."
            );
        }
        VoteOption option = optionRepository
            .findByClassroomIdAndVoteIdAndId(
                command.classroomId(),
                command.voteId(),
                command.optionId()
            )
            .orElseThrow(() -> operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "VOTE_OPTION_INVALID",
                "The selected option does not belong to this vote."
            ));
        VoteEligibility eligibility = eligibilityRepository
            .findForUpdateByClassroomIdAndVoteIdAndClassroomStudentId(
                command.classroomId(),
                command.voteId(),
                studentId
            )
            .orElseThrow(() -> operation(
                HttpStatus.FORBIDDEN,
                "NOT_PARTICIPANT",
                "The student is not eligible for this vote."
            ));
        if (eligibility.getUsedAt() != null) {
            throw operation(
                HttpStatus.CONFLICT,
                "ALREADY_VOTED",
                "The student has already voted."
            );
        }
        Instant now = clock.instant();
        String receiptHash = sha256(
            command.voteId() + "|" + eligibility.getEligibilityTokenHash()
                + "|" + option.getId() + "|" + now + "|"
                + uuidGenerator.generate()
        );
        eligibility.use(now);
        eligibilityRepository.save(eligibility);
        ballotRepository.save(
            VoteBallot.cast(
                uuidGenerator.generate(),
                command.classroomId(),
                command.voteId(),
                option.getId(),
                eligibility.getEligibilityTokenHash(),
                receiptHash,
                now
            )
        );
        VoteBallotResult result = new VoteBallotResult(receiptHash, now);
        remember(command, studentId, requestHash, result, now);
        publishAggregate(command, vote);
        return result;
    }

    private void publishAggregate(VoteBallotCommand command, Vote vote) {
        List<VoteBallot> ballots = ballotRepository.findAllByClassroomIdAndVoteId(
            command.classroomId(),
            command.voteId()
        );
        Map<String, Long> counts = new LinkedHashMap<>();
        ballots.forEach(ballot -> counts.merge(
            ballot.getVoteOptionId().toString(),
            1L,
            Long::sum
        ));
        eventPublisher.publish(
            command.classroomId(),
            "VOTE",
            command.voteId(),
            "vote.ballot.aggregate_changed",
            "VOTE",
            command.voteId(),
            vote.getStateVersion(),
            Map.of(
                "submitted_count", ballots.size(),
                "total_count", eligibilityRepository.countByClassroomIdAndVoteId(
                    command.classroomId(),
                    command.voteId()
                ),
                "option_counts", counts
            ),
            true
        );
    }

    private VoteBallotResult replay(
        VoteBallotCommand command,
        UUID studentId,
        String requestHash
    ) {
        IdempotencyRecord record = idempotencyRepository
            .findForReplay(
                command.classroomId(),
                studentId,
                ROUTE,
                command.requestId().toString()
            )
            .orElse(null);
        if (record == null) {
            return null;
        }
        if (!record.hasRequestHash(requestHash)) {
            throw new IdempotencyKeyReusedException();
        }
        Map<String, Object> body = record.getResponseBody();
        try {
            return new VoteBallotResult(
                body.get("receipt_hash").toString(),
                Instant.parse(body.get("cast_at").toString())
            );
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_RESULT_MISSING",
                "The previous ballot receipt could not be found."
            );
        }
    }

    private void remember(
        VoteBallotCommand command,
        UUID studentId,
        String requestHash,
        VoteBallotResult result,
        Instant now
    ) {
        idempotencyRepository.save(
            IdempotencyRecord.completed(
                uuidGenerator.generate(),
                command.classroomId(),
                studentId,
                ROUTE,
                command.requestId().toString(),
                requestHash,
                HttpStatus.CREATED.value(),
                Map.of(
                    "receipt_hash", result.receiptHash(),
                    "cast_at", result.castAt().toString()
                ),
                null,
                now.plus(IDEMPOTENCY_TTL),
                now
            )
        );
    }

    private static UUID studentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }

    private static void validate(VoteBallotCommand command) {
        if (
            command == null
                || command.classroomId() == null
                || command.voteId() == null
                || command.optionId() == null
                || command.requestId() == null
                || command.correlationId() == null
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the ballot values."
            );
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    value.getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash the ballot.", exception);
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
