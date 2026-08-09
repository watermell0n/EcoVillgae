package com.b201.ecovillage.vote.api;

import java.security.Principal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.vote.application.VoteBallotCommand;
import com.b201.ecovillage.vote.application.VoteBallotResult;
import com.b201.ecovillage.vote.application.VoteBallotService;
import com.b201.ecovillage.vote.application.VoteCommand;
import com.b201.ecovillage.vote.application.VoteCommandResult;
import com.b201.ecovillage.vote.application.VoteCommandService;
import com.b201.ecovillage.vote.application.VoteOptionCommand;
import com.b201.ecovillage.vote.application.VoteQueryService;
import com.b201.ecovillage.vote.application.VoteView;

@RestController
@RequestMapping("/classrooms/{classroomId}/votes")
public class VoteController {

    private final VoteCommandService commandService;
    private final VoteBallotService ballotService;
    private final VoteQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public VoteController(
        VoteCommandService commandService,
        VoteBallotService ballotService,
        VoteQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.commandService = commandService;
        this.ballotService = ballotService;
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/{voteId}/commands")
    public ApiResponse<VoteCommandResponse> command(
        @PathVariable UUID classroomId,
        @PathVariable UUID voteId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody VoteCommandRequest request,
        Principal principal
    ) {
        UUID correlation = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        List<VoteOptionCommand> options = request.options() == null
            ? null
            : request.options().stream()
                .map(option -> new VoteOptionCommand(
                    option.label(),
                    option.description(),
                    option.budgetAmount()
                ))
                .toList();
        VoteCommandResult result = commandService.execute(
            new VoteCommand(
                subject(principal),
                classroomId,
                voteId,
                requestId,
                correlation,
                request.expectedStateVersion(),
                request.command(),
                request.title(),
                request.description(),
                options,
                request.participantIds(),
                request.publicDisplayEnabled(),
                request.selectedUse()
            )
        );
        return new ApiResponse<>(
            new VoteCommandResponse(
                result.voteId(),
                result.status(),
                result.stateVersion(),
                result.selectedOptionId(),
                result.transactionGroupId()
            ),
            new ApiMeta(requestId, correlation, clock.instant())
        );
    }

    @PostMapping("/{voteId}/ballots")
    public ResponseEntity<ApiResponse<VoteBallotResponse>> cast(
        @PathVariable UUID classroomId,
        @PathVariable UUID voteId,
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody VoteBallotRequest request,
        Principal principal
    ) {
        UUID correlation = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        VoteBallotResult result = ballotService.cast(
            new VoteBallotCommand(
                subject(principal),
                classroomId,
                voteId,
                request.optionId(),
                requestId,
                correlation
            )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(
            new ApiResponse<>(
                new VoteBallotResponse(
                    result.receiptHash(),
                    result.castAt()
                ),
                new ApiMeta(requestId, correlation, clock.instant())
            )
        );
    }

    @GetMapping("/{voteId}")
    public ApiResponse<VoteResponse> get(
        @PathVariable UUID classroomId,
        @PathVariable UUID voteId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        VoteView vote = queryService.get(
            subject(principal),
            classroomId,
            voteId
        );
        return new ApiResponse<>(
            new VoteResponse(
                vote.voteId(),
                vote.title(),
                vote.description(),
                vote.anonymous(),
                vote.status(),
                vote.stateVersion(),
                vote.opensAt(),
                vote.closesAt(),
                vote.publicFundBalance(),
                vote.participantCount(),
                vote.ballotCount(),
                vote.eligible(),
                vote.hasVoted(),
                vote.selectedOptionId(),
                vote.selectedUse(),
                vote.transactionGroupId(),
                vote.options().stream()
                    .map(option -> new VoteOptionResponse(
                        option.optionId(),
                        option.label(),
                        option.description(),
                        option.budgetAmount(),
                        option.voteCount()
                    ))
                    .toList()
            ),
            new ApiMeta(requestId, correlation, clock.instant())
        );
    }

    @GetMapping("/latest")
    public ApiResponse<VoteResponse> latest(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        UUID requestId = uuidGenerator.generate();
        UUID correlation = correlationId == null
            ? uuidGenerator.generate()
            : correlationId;
        VoteView vote = queryService.latest(subject(principal), classroomId);
        return new ApiResponse<>(toResponse(vote), new ApiMeta(requestId, correlation, clock.instant()));
    }

    private static VoteResponse toResponse(VoteView vote) {
        return new VoteResponse(
            vote.voteId(),
            vote.title(),
            vote.description(),
            vote.anonymous(),
            vote.status(),
            vote.stateVersion(),
            vote.opensAt(),
            vote.closesAt(),
            vote.publicFundBalance(),
            vote.participantCount(),
            vote.ballotCount(),
            vote.eligible(),
            vote.hasVoted(),
            vote.selectedOptionId(),
            vote.selectedUse(),
            vote.transactionGroupId(),
            vote.options().stream()
                .map(option -> new VoteOptionResponse(
                    option.optionId(),
                    option.label(),
                    option.description(),
                    option.budgetAmount(),
                    option.voteCount()
                ))
                .toList()
        );
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
