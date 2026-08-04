package com.b201.ecovillage.realtime.api;

import java.security.Principal;
import java.time.Clock;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.application.TeacherAuthenticationException;
import com.b201.ecovillage.identity.security.EcoVillagePrincipal;
import com.b201.ecovillage.realtime.application.IssuedRealtimeTicket;
import com.b201.ecovillage.realtime.application.RealtimeTicketIssuer;

@RestController
public class RealtimeTicketController {

    private final RealtimeTicketIssuer ticketIssuer;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public RealtimeTicketController(
        RealtimeTicketIssuer ticketIssuer,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.ticketIssuer = ticketIssuer;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @PostMapping("/auth/realtime-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RealtimeTicketResponse> issue(
        @RequestHeader("Idempotency-Key") UUID requestId,
        @RequestHeader(name = "X-Correlation-Id", required = false) UUID correlationId,
        @Valid @RequestBody RealtimeTicketRequest request,
        Principal principal
    ) {
        IssuedRealtimeTicket issued = ticketIssuer.issue(
            authenticated(principal),
            request.channelIds(),
            request.resourceIds()
        );
        return new ApiResponse<>(
            new RealtimeTicketResponse(issued.ticket(), issued.expiresAt()),
            new ApiMeta(
                requestId,
                correlationId == null ? uuidGenerator.generate() : correlationId,
                clock.instant()
            )
        );
    }

    private static EcoVillagePrincipal authenticated(Principal principal) {
        if (principal instanceof EcoVillagePrincipal authenticated) {
            return authenticated;
        }
        throw new TeacherAuthenticationException(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REQUIRED",
            "로그인이 필요합니다.",
            false
        );
    }
}
