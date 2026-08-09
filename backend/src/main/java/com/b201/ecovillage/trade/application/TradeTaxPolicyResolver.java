package com.b201.ecovillage.trade.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;

@Component
public class TradeTaxPolicyResolver {

    private static final int DEFAULT_TRADE_TAX_RATE_BPS = 500;

    private final ClassroomPolicyVersionRepository policyVersionRepository;

    public TradeTaxPolicyResolver(ClassroomPolicyVersionRepository policyVersionRepository) {
        this.policyVersionRepository = policyVersionRepository;
    }

    public TradeTaxPolicy resolve(UUID classroomId) {
        Optional<ClassroomPolicyVersion> current = policyVersionRepository
            .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                classroomId,
                "ECONOMIC"
            )
            .or(() -> policyVersionRepository
                .findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                    classroomId,
                    "SALARY"
                ));
        if (current.isEmpty()) {
            return new TradeTaxPolicy(
                DEFAULT_TRADE_TAX_RATE_BPS,
                null,
                0,
                "SYSTEM_DEFAULT"
            );
        }
        ClassroomPolicyVersion policy = current.get();
        Object configured = policy.getPolicyValues().get("trade_tax_rate_bps");
        int rate = configured == null
            ? DEFAULT_TRADE_TAX_RATE_BPS
            : rateBps(configured);
        return new TradeTaxPolicy(
            rate,
            policy.getId(),
            policy.getVersion(),
            policy.getPolicyArea()
        );
    }

    private static int rateBps(Object value) {
        long parsed;
        if (value instanceof Number number) {
            parsed = number.longValue();
        } else {
            try {
                parsed = Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException exception) {
                throw policyViolation();
            }
        }
        if (parsed < 0 || parsed > 10_000) {
            throw policyViolation();
        }
        return Math.toIntExact(parsed);
    }

    private static ClassroomOperationException policyViolation() {
        return new ClassroomOperationException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "POLICY_VIOLATION",
            "The personal trade tax rate must be between 0 and 10000 bps."
        );
    }
}
