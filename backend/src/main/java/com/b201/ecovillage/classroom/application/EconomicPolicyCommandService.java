package com.b201.ecovillage.classroom.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.audit.application.AuditTrailService;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.ledger.domain.PolicySnapshot;
import com.b201.ecovillage.ledger.repository.PolicySnapshotRepository;

@Service
public class EconomicPolicyCommandService {

    static final String POLICY_AREA = "ECONOMIC";
    private static final String ROUTE =
        "/classrooms/{classroom_id}/economic-policy";

    private final ClassroomMutationSupport mutationSupport;
    private final ClassroomRepository classroomRepository;
    private final ClassroomPolicyVersionRepository policyRepository;
    private final PolicySnapshotRepository snapshotRepository;
    private final SetupPrecheckInvalidator precheckInvalidator;
    private final AuditTrailService auditTrailService;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public EconomicPolicyCommandService(
        ClassroomMutationSupport mutationSupport,
        ClassroomRepository classroomRepository,
        ClassroomPolicyVersionRepository policyRepository,
        PolicySnapshotRepository snapshotRepository,
        SetupPrecheckInvalidator precheckInvalidator,
        AuditTrailService auditTrailService,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.mutationSupport = mutationSupport;
        this.classroomRepository = classroomRepository;
        this.policyRepository = policyRepository;
        this.snapshotRepository = snapshotRepository;
        this.precheckInvalidator = precheckInvalidator;
        this.auditTrailService = auditTrailService;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public EconomicPolicyResult update(EconomicPolicyCommand command) {
        Map<String, Object> values = validate(command);
        UserAccount teacher = mutationSupport.owner(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.MANAGE_ECONOMIC_POLICY
        );
        String requestHash = mutationSupport.hash(ClassroomMutationSupport.body(
            "classroom_id", command.classroomId(),
            "expected_state_version", command.expectedStateVersion(),
            "expected_policy_version", command.expectedPolicyVersion(),
            "values", values
        ));
        Map<String, Object> replay = mutationSupport.replay(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash
        );
        if (replay != null) {
            return new EconomicPolicyResult(
                java.util.UUID.fromString(replay.get("policy_version_id").toString()),
                ((Number) replay.get("policy_version")).intValue(),
                replay.get("status").toString(),
                ((Number) replay.get("state_version")).longValue(),
                true
            );
        }

        Classroom classroom = classroomRepository.findForUpdateById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom could not be found."
            ));
        if (classroom.getStatus() != ClassroomStatus.ACTIVE) {
            throw operation(
                HttpStatus.CONFLICT,
                "CLASS_NOT_ACTIVE",
                "Economic policy can change only in an active classroom."
            );
        }
        if (
            command.expectedStateVersion() != null
                && command.expectedStateVersion() != classroom.getStateVersion()
        ) {
            throw operation(
                HttpStatus.CONFLICT,
                "STATE_VERSION_CONFLICT",
                "The classroom state has changed."
            );
        }

        ClassroomPolicyVersion previous = policyRepository
            .findForUpdateByClassroomIdAndPolicyAreaAndSupersededAtIsNull(
                command.classroomId(),
                POLICY_AREA
            )
            .orElse(null);
        int currentVersion = previous == null ? 0 : previous.getVersion();
        if (command.expectedPolicyVersion() != currentVersion) {
            throw policyConflict();
        }

        Instant now = clock.instant();
        int nextVersion = currentVersion + 1;
        Map<String, Object> before = previous == null
            ? Map.of("policy_version", 0)
            : ClassroomMutationSupport.body(
                "policy_version", previous.getVersion(),
                "values", previous.getPolicyValues()
            );
        if (previous != null) {
            previous.supersede(now);
            policyRepository.flush();
        }
        ClassroomPolicyVersion policy = ClassroomPolicyVersion.policy(
            uuidGenerator.generate(),
            command.classroomId(),
            POLICY_AREA,
            nextVersion,
            values,
            "TEACHER",
            now
        );
        policyRepository.save(policy);
        String snapshotHash = mutationSupport.hash(values);
        if (snapshotRepository.findByClassroomIdAndSnapshotHash(
            command.classroomId(),
            snapshotHash
        ).isEmpty()) {
            snapshotRepository.save(PolicySnapshot.capture(
                uuidGenerator.generate(),
                command.classroomId(),
                snapshotHash,
                "1",
                values,
                "INTEGER_UNIT_1",
                now
            ));
        }
        classroom.touch(now);
        try {
            policyRepository.flush();
            classroomRepository.saveAndFlush(classroom);
        } catch (
            DataIntegrityViolationException | ObjectOptimisticLockingFailureException exception
        ) {
            throw policyConflict();
        }

        EconomicPolicyResult result = new EconomicPolicyResult(
            policy.getId(),
            nextVersion,
            policy.getStatus(),
            classroom.getStateVersion(),
            false
        );
        precheckInvalidator.invalidate(command.classroomId());
        auditTrailService.record(
            command.classroomId(),
            teacher.getId(),
            "ECONOMIC_POLICY_CHANGED",
            "CLASSROOM_POLICY_VERSION",
            policy.getId(),
            command.requestId(),
            command.correlationId(),
            before,
            ClassroomMutationSupport.body(
                "policy_version", nextVersion,
                "values", values
            ),
            "POLICY",
            "Economic policy was updated for future transactions."
        );
        mutationSupport.remember(
            command.classroomId(),
            teacher.getId(),
            ROUTE,
            command.requestId(),
            requestHash,
            ClassroomMutationSupport.body(
                "policy_version_id", result.policyVersionId().toString(),
                "policy_version", result.policyVersion(),
                "status", result.status(),
                "state_version", result.stateVersion()
            )
        );
        return result;
    }

    private static Map<String, Object> validate(EconomicPolicyCommand command) {
        if (
            command == null || command.classroomId() == null
                || command.requestId() == null || command.stockPolicy() == null
                || command.rewardPolicy() == null || command.limits() == null
        ) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Check the economic policy values."
            );
        }
        validateRange(command.priceAdjustmentBps(), -10_000, 100_000);
        validateRange(command.inflationRateBps(), -10_000, 100_000);
        validateRange(command.incomeTaxRateBps(), 0, 10_000);
        validateRange(command.vatRateBps(), 0, 10_000);
        validateRange(command.tradeTaxRateBps(), 0, 10_000);
        validateRange(command.depositRateBps(), 0, 10_000);
        validateRange(command.loanRateBps(), 0, 10_000);
        if (
            command.loanRateBps() > 0
                && command.loanRateBps() < command.depositRateBps() + 200
        ) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "Loan rate must be at least 200 bps above the deposit rate."
            );
        }
        validateIntegerTree(command.stockPolicy(), "stock_policy");
        validateIntegerTree(command.rewardPolicy(), "reward_policy");
        validateIntegerTree(command.limits(), "limits");

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("price_adjustment_bps", command.priceAdjustmentBps());
        values.put("inflation_rate_bps", command.inflationRateBps());
        values.put("income_tax_rate_bps", command.incomeTaxRateBps());
        values.put("vat_rate_bps", command.vatRateBps());
        values.put("trade_tax_rate_bps", command.tradeTaxRateBps());
        values.put("deposit_rate_bps", command.depositRateBps());
        values.put("loan_rate_bps", command.loanRateBps());
        values.put("stock_policy", new LinkedHashMap<>(command.stockPolicy()));
        values.put("reward_policy", new LinkedHashMap<>(command.rewardPolicy()));
        values.put("limits", new LinkedHashMap<>(command.limits()));
        values.put("amount_unit", 1);
        values.put("applies_to", "FUTURE_TRANSACTIONS_AND_NEW_FINANCIAL_PRODUCTS");
        return Map.copyOf(values);
    }

    private static void validateRange(int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "POLICY_VIOLATION",
                "A policy rate is outside the supported range."
            );
        }
    }

    private static void validateIntegerTree(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> validateIntegerTree(
                item,
                path + "." + String.valueOf(key)
            ));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            int index = 0;
            for (Object item : iterable) {
                validateIntegerTree(item, path + "[" + index++ + "]");
            }
            return;
        }
        if (value instanceof Number number) {
            BigDecimal decimal = new BigDecimal(number.toString());
            if (decimal.stripTrailingZeros().scale() > 0 || decimal.signum() < 0) {
                throw operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "POLICY_VIOLATION",
                    path + " amounts and counts must be non-negative integer units."
                );
            }
        }
    }

    private static ClassroomOperationException policyConflict() {
        return operation(
            HttpStatus.CONFLICT,
            "POLICY_VERSION_CONFLICT",
            "The economic policy has changed."
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return ClassroomMutationSupport.operation(status, code, message);
    }
}
