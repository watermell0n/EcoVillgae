package com.b201.ecovillage.ai.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.LoanPolicyVersion;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.finance.repository.LoanPolicyVersionRepository;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;

class OperationalBalancingApplyServiceTest {

    private static final UUID CLASSROOM_ID = UUID.fromString("019c0750-0000-7000-8000-000000000001");
    private static final UUID CURRENT_POLICY_ID = UUID.fromString("019c0750-0000-7000-8000-000000000002");
    private static final UUID NEXT_POLICY_ID = UUID.fromString("019c0750-0000-7000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");

    @Test
    void appliesLoanRateBySupersedingTheCurrentPolicyAndCreatingANewVersion() {
        LoanPolicyVersionRepository loanPolicies = mock(LoanPolicyVersionRepository.class);
        UuidV7Generator uuidGenerator = mock(UuidV7Generator.class);
        LoanPolicyVersion current = LoanPolicyVersion.activate(
            CURRENT_POLICY_ID,
            CLASSROOM_ID,
            3,
            100_000L,
            700,
            30,
            3,
            1,
            Map.of("A", -100),
            NOW.minusSeconds(86_400)
        );
        when(loanPolicies.findForUpdate(CLASSROOM_ID, CURRENT_POLICY_ID))
            .thenReturn(java.util.Optional.of(current));
        when(loanPolicies.findTopByClassroomIdAndStatusOrderByVersionDesc(CLASSROOM_ID, "ACTIVE"))
            .thenReturn(java.util.Optional.of(current));
        when(uuidGenerator.generate()).thenReturn(NEXT_POLICY_ID);
        OperationalBalancingApplyService service = new OperationalBalancingApplyService(
            mock(StoreProductRepository.class),
            mock(StoreProductVersionRepository.class),
            mock(StoreStockRepository.class),
            mock(FinancialProductRepository.class),
            mock(FinancialProductVersionRepository.class),
            loanPolicies,
            uuidGenerator,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        String actionId = "loan-rate-" + CURRENT_POLICY_ID;
        Map<String, Object> allowedAction = new LinkedHashMap<>();
        allowedAction.put("action_id", actionId);
        allowedAction.put("target_type", "LOAN_RATE");
        allowedAction.put("target_id", CURRENT_POLICY_ID.toString());
        allowedAction.put("current_value", 700L);
        allowedAction.put("allowed_min_value", 600L);
        allowedAction.put("allowed_max_value", 800L);
        allowedAction.put("unit", "RATE_BPS");
        Map<String, Object> proposedValues = new LinkedHashMap<>();

        service.apply(
            CLASSROOM_ID,
            proposedValues,
            Map.of("items", List.of(allowedAction)),
            List.of(new AiProposalCommandResult.AppliedValue(actionId, 750L, "RATE_BPS"))
        );

        ArgumentCaptor<LoanPolicyVersion> captor = ArgumentCaptor.forClass(LoanPolicyVersion.class);
        verify(loanPolicies, org.mockito.Mockito.times(2)).save(captor.capture());
        LoanPolicyVersion superseded = captor.getAllValues().get(0);
        LoanPolicyVersion created = captor.getAllValues().get(1);
        assertEquals("SUPERSEDED", superseded.getStatus());
        assertEquals(NEXT_POLICY_ID, created.getId());
        assertEquals("ACTIVE", created.getStatus());
        assertEquals(4, created.getVersion());
        assertEquals(750, created.getInterestRateBps());
        assertEquals(current.getMaximumPrincipal(), created.getMaximumPrincipal());
        assertEquals(current.getCreditAdjustments(), created.getCreditAdjustments());
        assertEquals(NOW.toString(), proposedValues.get("applied_at"));
    }
}
