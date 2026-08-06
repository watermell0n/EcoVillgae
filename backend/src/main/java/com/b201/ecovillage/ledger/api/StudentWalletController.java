package com.b201.ecovillage.ledger.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.ledger.application.StudentWalletQueryService;

@RestController
@RequestMapping("/classrooms/{classroomId}/students/me/wallet")
public class StudentWalletController {

    private final StudentWalletQueryService queryService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public StudentWalletController(
        StudentWalletQueryService queryService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.queryService = queryService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StudentWalletResponse>> findMine(
        @PathVariable UUID classroomId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        Principal principal
    ) {
        var view = queryService.findMine(
            principal == null ? null : principal.getName(),
            classroomId
        );
        var transactions = view.recentTransactions().stream()
            .map(item -> new StudentWalletResponse.RecentTransactionResponse(
                item.resourceId(),
                item.label(),
                item.status(),
                item.version(),
                item.stateVersion(),
                item.amount(),
                item.occurredAt()
            ))
            .toList();
        return ResponseEntity.ok(new ApiResponse<>(
            new StudentWalletResponse(view.balance(), transactions),
            new ApiMeta(
                uuidGenerator.generate(),
                correlationId == null ? uuidGenerator.generate() : correlationId,
                clock.instant()
            )
        ));
    }
}
