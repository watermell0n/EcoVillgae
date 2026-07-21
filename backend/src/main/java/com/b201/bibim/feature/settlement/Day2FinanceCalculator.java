package com.b201.bibim.feature.settlement;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

@Component
public class Day2FinanceCalculator {

    private static final long BASIS_POINTS_DIVISOR = 10_000L;
    private static final long HALF_BASIS_POINT_DIVISOR = 5_000L;

    public Calculation calculate(
            long walletBefore,
            long depositPrincipal,
            int depositInterestBps,
            List<AllocationInput> investments,
            int financialTaxBps
    ) {
        if (walletBefore < 0 || depositPrincipal < 0
                || depositInterestBps < 0 || financialTaxBps < 0) {
            throw new IllegalArgumentException("금융 정산 값이 올바르지 않습니다.");
        }

        return calculateWithDepositInterest(
                walletBefore,
                depositPrincipal,
                roundedRate(depositPrincipal, depositInterestBps),
                investments,
                financialTaxBps
        );
    }

    public Calculation calculateWithDepositInterest(
            long walletBefore,
            long depositPrincipal,
            long depositInterest,
            List<AllocationInput> investments,
            int financialTaxBps
    ) {
        return calculateWithDepositInterest(
                walletBefore, depositPrincipal, depositInterest,
                investments, financialTaxBps, 10_000
        );
    }

    public Calculation calculateWithDepositInterest(
            long walletBefore,
            long depositPrincipal,
            long depositInterest,
            List<AllocationInput> investments,
            int financialTaxBps,
            int positiveProfitRewardRateBps
    ) {
        if (walletBefore < 0 || depositPrincipal < 0 || depositInterest < 0
                || financialTaxBps < 0 || positiveProfitRewardRateBps < 0
                || positiveProfitRewardRateBps > 10_000) {
            throw new IllegalArgumentException("금융 정산 값이 올바르지 않습니다.");
        }
        long investmentPrincipal = 0;
        long investmentProfit = 0;
        List<AllocationResult> allocationResults = investments.stream()
                .map(input -> {
                    if (input.principal() <= 0) {
                        throw new IllegalArgumentException("투자 원금은 0보다 커야 합니다.");
                    }
                    long rawProfit = roundedRate(input.principal(), input.returnBps());
                    long profit = rawProfit > 0
                            ? Math.floorDiv(
                                    Math.multiplyExact(rawProfit, positiveProfitRewardRateBps),
                                    BASIS_POINTS_DIVISOR
                            )
                            : rawProfit;
                    long finalValue = Math.addExact(input.principal(), profit);
                    if (finalValue < 0) {
                        throw new IllegalArgumentException("투자 평가액은 음수가 될 수 없습니다.");
                    }
                    return new AllocationResult(
                            input.assetId(), input.principal(), input.returnBps(),
                            profit, finalValue
                    );
                })
                .toList();

        for (AllocationResult result : allocationResults) {
            investmentPrincipal = Math.addExact(investmentPrincipal, result.principal());
            investmentProfit = Math.addExact(investmentProfit, result.profit());
        }
        long investmentValue = Math.addExact(investmentPrincipal, investmentProfit);
        long investmentTaxableProfit = Math.max(investmentProfit, 0);
        long taxableProfit = Math.addExact(depositInterest, investmentTaxableProfit);
        long depositFinancialTax = Math.floorDiv(
                Math.multiplyExact(depositInterest, financialTaxBps),
                BASIS_POINTS_DIVISOR
        );
        long investmentFinancialTax = Math.floorDiv(
                Math.multiplyExact(investmentTaxableProfit, financialTaxBps),
                BASIS_POINTS_DIVISOR
        );
        long financialTax = Math.addExact(depositFinancialTax, investmentFinancialTax);
        long walletAfter = Math.addExact(
                walletBefore,
                Math.subtractExact(
                        Math.addExact(
                                Math.addExact(depositPrincipal, depositInterest),
                                investmentValue
                        ),
                        financialTax
                )
        );

        return new Calculation(
                walletBefore,
                depositPrincipal,
                depositInterest,
                investmentPrincipal,
                investmentProfit,
                investmentValue,
                taxableProfit,
                depositFinancialTax,
                investmentFinancialTax,
                financialTax,
                walletAfter,
                allocationResults
        );
    }

    private long roundedRate(long principal, int rateBps) {
        long multiplied = Math.multiplyExact(principal, rateBps);
        if (multiplied == 0) return 0;
        long absolute = multiplied < 0 ? Math.negateExact(multiplied) : multiplied;
        long rounded = Math.addExact(absolute, HALF_BASIS_POINT_DIVISOR)
                / BASIS_POINTS_DIVISOR;
        return multiplied < 0 ? -rounded : rounded;
    }

    public record AllocationInput(UUID assetId, long principal, int returnBps) {
    }

    public record AllocationResult(
            UUID assetId,
            long principal,
            int returnBps,
            long profit,
            long finalValue
    ) {
    }

    public record Calculation(
            long walletBefore,
            long depositPrincipal,
            long depositInterest,
            long investmentPrincipal,
            long investmentProfit,
            long investmentValue,
            long taxableProfit,
            long depositFinancialTax,
            long investmentFinancialTax,
            long financialTax,
            long walletAfter,
            List<AllocationResult> allocations
    ) {
    }
}
