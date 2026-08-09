package com.b201.ecovillage.trade.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.trade.repository.TradeItemLegRepository;
import com.b201.ecovillage.trade.repository.TradeMoneyLegRepository;
import com.b201.ecovillage.trade.repository.TradeRepository;

class PendingTradeQueryServiceTest {

    @Test
    void returnsAuthRequiredInsteadOfInternalErrorWithoutStudentSession() {
        PendingTradeQueryService service = new PendingTradeQueryService(
            mock(ClassroomAuthorizationService.class),
            mock(TradeRepository.class),
            mock(TradeMoneyLegRepository.class),
            mock(TradeItemLegRepository.class),
            mock(ClassroomStudentRepository.class)
        );

        ClassroomOperationException failure = assertThrows(
            ClassroomOperationException.class,
            () -> service.findClassmates(null, UUID.randomUUID())
        );

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatus());
        assertEquals("AUTH_REQUIRED", failure.getCode());
    }
}
