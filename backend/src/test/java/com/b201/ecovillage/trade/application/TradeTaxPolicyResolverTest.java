package com.b201.ecovillage.trade.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;

class TradeTaxPolicyResolverTest {

    @Test
    void usesFivePercentSystemDefaultWhenNoPolicyExists() {
        UUID classroomId = UUID.randomUUID();
        ClassroomPolicyVersionRepository repository =
            mock(ClassroomPolicyVersionRepository.class);
        when(repository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
            classroomId,
            "ECONOMIC"
        )).thenReturn(Optional.empty());
        when(repository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
            classroomId,
            "SALARY"
        )).thenReturn(Optional.empty());

        TradeTaxPolicy policy = new TradeTaxPolicyResolver(repository).resolve(classroomId);

        assertEquals(500, policy.rateBps());
        assertEquals(0, policy.version());
        assertEquals("SYSTEM_DEFAULT", policy.source());
        assertNull(policy.policyVersionId());
    }

    @Test
    void readsConfiguredTradeTaxFromCurrentEconomicPolicy() {
        UUID classroomId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        ClassroomPolicyVersion policyVersion = ClassroomPolicyVersion.policy(
            policyId,
            classroomId,
            "ECONOMIC",
            3,
            Map.of("trade_tax_rate_bps", 750),
            "TEACHER",
            Instant.parse("2026-08-10T00:00:00Z")
        );
        ClassroomPolicyVersionRepository repository =
            mock(ClassroomPolicyVersionRepository.class);
        when(repository.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
            classroomId,
            "ECONOMIC"
        )).thenReturn(Optional.of(policyVersion));

        TradeTaxPolicy policy = new TradeTaxPolicyResolver(repository).resolve(classroomId);

        assertEquals(750, policy.rateBps());
        assertEquals(policyId, policy.policyVersionId());
        assertEquals(3, policy.version());
        assertEquals("ECONOMIC", policy.source());
    }
}
