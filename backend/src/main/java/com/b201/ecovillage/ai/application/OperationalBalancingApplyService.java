package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.FinancialProduct;
import com.b201.ecovillage.finance.domain.FinancialProductVersion;
import com.b201.ecovillage.finance.domain.LoanPolicyVersion;
import com.b201.ecovillage.finance.repository.FinancialProductRepository;
import com.b201.ecovillage.finance.repository.FinancialProductVersionRepository;
import com.b201.ecovillage.finance.repository.LoanPolicyVersionRepository;
import com.b201.ecovillage.store.domain.StoreProduct;
import com.b201.ecovillage.store.domain.StoreProductVersion;
import com.b201.ecovillage.store.domain.StoreStock;
import com.b201.ecovillage.store.repository.StoreProductRepository;
import com.b201.ecovillage.store.repository.StoreProductVersionRepository;
import com.b201.ecovillage.store.repository.StoreStockRepository;

@Service
public class OperationalBalancingApplyService {

    private final StoreProductRepository storeProductRepository;
    private final StoreProductVersionRepository storeVersionRepository;
    private final StoreStockRepository storeStockRepository;
    private final FinancialProductRepository financialProductRepository;
    private final FinancialProductVersionRepository financialVersionRepository;
    private final LoanPolicyVersionRepository loanPolicyRepository;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public OperationalBalancingApplyService(
        StoreProductRepository storeProductRepository,
        StoreProductVersionRepository storeVersionRepository,
        StoreStockRepository storeStockRepository,
        FinancialProductRepository financialProductRepository,
        FinancialProductVersionRepository financialVersionRepository,
        LoanPolicyVersionRepository loanPolicyRepository,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.storeProductRepository = storeProductRepository;
        this.storeVersionRepository = storeVersionRepository;
        this.storeStockRepository = storeStockRepository;
        this.financialProductRepository = financialProductRepository;
        this.financialVersionRepository = financialVersionRepository;
        this.loanPolicyRepository = loanPolicyRepository;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @Transactional
    public void apply(
        UUID classroomId,
        Map<String, Object> proposedValues,
        Map<String, Object> allowedRanges,
        List<AiProposalCommandResult.AppliedValue> appliedValues
    ) {
        Map<String, Map<String, Object>> allowedByAction = indexAllowed(allowedRanges);
        Map<UUID, ProductChange> productChanges = new LinkedHashMap<>();
        List<RateChange> rateChanges = new ArrayList<>();
        List<RateChange> loanRateChanges = new ArrayList<>();

        for (AiProposalCommandResult.AppliedValue applied : appliedValues) {
            Map<String, Object> allowed = allowedByAction.get(applied.actionId());
            if (allowed == null) {
                throw validation("The balancing action is not in the trusted allowed range.");
            }
            UUID targetId = uuid(allowed.get("target_id"));
            String targetType = text(allowed.get("target_type"));
            long capturedValue = number(allowed.get("current_value"));
            switch (targetType) {
                case "PRODUCT_PRICE" -> productChanges
                    .computeIfAbsent(targetId, ignored -> new ProductChange())
                    .setPrice(capturedValue, applied.proposedValue());
                case "PRODUCT_STOCK" -> productChanges
                    .computeIfAbsent(targetId, ignored -> new ProductChange())
                    .setStock(capturedValue, applied.proposedValue());
                case "DEPOSIT_RATE", "SAVINGS_RATE" -> rateChanges.add(
                    new RateChange(targetId, targetType, capturedValue, applied.proposedValue())
                );
                case "LOAN_RATE" -> loanRateChanges.add(
                    new RateChange(targetId, targetType, capturedValue, applied.proposedValue())
                );
                default -> throw validation("The balancing action cannot be applied by this service.");
            }
        }

        Instant now = clock.instant();
        productChanges.forEach((targetId, change) -> applyProduct(classroomId, targetId, change, now));
        rateChanges.forEach(change -> applyFinancialRate(classroomId, change, now));
        loanRateChanges.forEach(change -> applyLoanRate(classroomId, change, now));
        proposedValues.put("applied_at", now.toString());
    }

    private void applyProduct(
        UUID classroomId,
        UUID productId,
        ProductChange change,
        Instant now
    ) {
        StoreProduct product = storeProductRepository.findByClassroomIdAndId(classroomId, productId)
            .orElseThrow(() -> stale("The store product used by the proposal no longer exists."));
        if (!product.isPublished()) {
            throw stale("The store product used by the proposal is no longer published.");
        }
        StoreProductVersion current = storeVersionRepository
            .findTopByClassroomIdAndStoreProductIdAndRetiredAtIsNullOrderByVersionDesc(
                classroomId,
                productId
            )
            .orElseThrow(() -> stale("The store product version used by the proposal is missing."));
        StoreStock stock = storeStockRepository.findForUpdate(classroomId, productId)
            .orElseThrow(() -> stale("The store stock used by the proposal is missing."));

        if (change.capturedPrice != null && current.getUnitPrice() != change.capturedPrice) {
            throw stale("The store product price changed after the proposal was generated.");
        }
        if (change.capturedStock != null && stock.getAvailableQuantity() != change.capturedStock) {
            throw stale("The store stock changed after the proposal was generated.");
        }

        long nextPrice = change.nextPrice == null ? current.getUnitPrice() : change.nextPrice;
        int nextStock = change.nextStock == null
            ? stock.getAvailableQuantity()
            : Math.toIntExact(change.nextStock);
        current.retire(now);
        storeVersionRepository.save(current);
        storeVersionRepository.save(StoreProductVersion.publish(
            uuidGenerator.generate(),
            classroomId,
            productId,
            current.getVersion() + 1,
            current.getProductKind(),
            nextPrice,
            current.getVatRateBps(),
            true,
            current.getMetadata(),
            now
        ));
        stock.replaceAvailableQuantity(nextStock, now);
        storeStockRepository.save(stock);
    }

    private void applyFinancialRate(UUID classroomId, RateChange change, Instant now) {
        FinancialProduct product = financialProductRepository
            .findByClassroomIdAndId(classroomId, change.targetId())
            .orElseThrow(() -> stale("The financial product used by the proposal no longer exists."));
        if (!product.isPublished() || !product.getProductType().name().equals(rateProductType(change.targetType()))) {
            throw stale("The financial product type or status changed after the proposal was generated.");
        }
        FinancialProductVersion current = financialVersionRepository
            .findTopByClassroomIdAndFinancialProductIdAndStatusOrderByVersionDesc(
                classroomId,
                change.targetId(),
                "PUBLISHED"
            )
            .orElseThrow(() -> stale("The financial product version used by the proposal is missing."));
        if (current.getInterestRateBps() != change.capturedValue()) {
            throw stale("The financial product rate changed after the proposal was generated.");
        }
        current.supersede(now);
        financialVersionRepository.save(current);
        financialVersionRepository.save(FinancialProductVersion.issue(
            uuidGenerator.generate(),
            classroomId,
            change.targetId(),
            current.getVersion() + 1,
            current.getProductType(),
            current.getTermUnits(),
            Math.toIntExact(change.nextValue()),
            current.getEarlyTerminationRateBps(),
            current.getInstallmentCount(),
            current.getInstallmentAmount(),
            current.getMinimumAmount(),
            current.getMaximumAmount(),
            true,
            now
        ));
    }

    private void applyLoanRate(UUID classroomId, RateChange change, Instant now) {
        LoanPolicyVersion current = loanPolicyRepository
            .findForUpdate(classroomId, change.targetId())
            .orElseThrow(() -> stale("The loan policy used by the proposal no longer exists."));
        LoanPolicyVersion latest = loanPolicyRepository
            .findTopByClassroomIdAndStatusOrderByVersionDesc(classroomId, "ACTIVE")
            .orElseThrow(() -> stale("The active loan policy used by the proposal is missing."));
        if (!latest.getId().equals(current.getId()) || current.getInterestRateBps() != change.capturedValue()) {
            throw stale("The loan policy rate changed after the proposal was generated.");
        }
        current.supersede(now);
        loanPolicyRepository.save(current);
        loanPolicyRepository.save(LoanPolicyVersion.activate(
            uuidGenerator.generate(),
            classroomId,
            current.getVersion() + 1,
            current.getMaximumPrincipal(),
            Math.toIntExact(change.nextValue()),
            current.getTermUnits(),
            current.getInstallmentCount(),
            current.getGraceUnits(),
            current.getCreditAdjustments(),
            now
        ));
    }

    private static Map<String, Map<String, Object>> indexAllowed(Map<String, Object> allowedRanges) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        Object rawItems = allowedRanges.get("items");
        if (!(rawItems instanceof List<?> items)) {
            return result;
        }
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> mapped = new LinkedHashMap<>();
            raw.forEach((key, value) -> mapped.put(String.valueOf(key), value));
            result.put(text(mapped.get("action_id")), mapped);
        }
        return result;
    }

    private static String rateProductType(String targetType) {
        return "SAVINGS_RATE".equals(targetType) ? "SAVINGS" : "DEPOSIT";
    }

    private static UUID uuid(Object value) {
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw validation("The balancing action target is invalid.");
        }
    }

    private static long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (RuntimeException exception) {
            throw validation("The balancing action value is invalid.");
        }
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static ClassroomOperationException stale(String message) {
        return new ClassroomOperationException(HttpStatus.CONFLICT, "STALE_PROPOSAL", message);
    }

    private static ClassroomOperationException validation(String message) {
        return new ClassroomOperationException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "INVALID_RECOMMENDATION_CONTEXT",
            message
        );
    }

    private static final class ProductChange {

        private Long capturedPrice;
        private Long nextPrice;
        private Long capturedStock;
        private Long nextStock;

        private ProductChange setPrice(long captured, long next) {
            capturedPrice = captured;
            nextPrice = next;
            return this;
        }

        private ProductChange setStock(long captured, long next) {
            capturedStock = captured;
            nextStock = next;
            return this;
        }
    }

    private record RateChange(UUID targetId, String targetType, long capturedValue, long nextValue) {
    }
}
