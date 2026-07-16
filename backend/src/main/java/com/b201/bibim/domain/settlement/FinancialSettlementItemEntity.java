package com.b201.bibim.domain.settlement;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_settlement_items")
public class FinancialSettlementItemEntity {

    @Id
    private UUID id;

    @Column(name = "settlement_run_id", nullable = false)
    private UUID settlementRunId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "wallet_before", nullable = false)
    private long walletBefore;

    @Column(name = "deposit_principal", nullable = false)
    private long depositPrincipal;

    @Column(name = "deposit_interest", nullable = false)
    private long depositInterest;

    @Column(name = "investment_principal", nullable = false)
    private long investmentPrincipal;

    @Column(name = "investment_profit", nullable = false)
    private long investmentProfit;

    @Column(name = "investment_value", nullable = false)
    private long investmentValue;

    @Column(name = "taxable_profit", nullable = false)
    private long taxableProfit;

    @Column(name = "financial_tax", nullable = false)
    private long financialTax;

    @Column(name = "wallet_after", nullable = false)
    private long walletAfter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FinancialSettlementItemEntity() {
    }

    public static FinancialSettlementItemEntity create(
            UUID settlementRunId,
            UUID studentId,
            long walletBefore,
            long depositPrincipal,
            long depositInterest,
            long investmentPrincipal,
            long investmentProfit,
            long investmentValue,
            long taxableProfit,
            long financialTax,
            long walletAfter
    ) {
        FinancialSettlementItemEntity item = new FinancialSettlementItemEntity();
        item.id = UUID.randomUUID();
        item.settlementRunId = settlementRunId;
        item.studentId = studentId;
        item.walletBefore = walletBefore;
        item.depositPrincipal = depositPrincipal;
        item.depositInterest = depositInterest;
        item.investmentPrincipal = investmentPrincipal;
        item.investmentProfit = investmentProfit;
        item.investmentValue = investmentValue;
        item.taxableProfit = taxableProfit;
        item.financialTax = financialTax;
        item.walletAfter = walletAfter;
        item.createdAt = Instant.now();
        return item;
    }

    public UUID getId() { return id; }
    public UUID getStudentId() { return studentId; }
    public long getWalletBefore() { return walletBefore; }
    public long getDepositPrincipal() { return depositPrincipal; }
    public long getDepositInterest() { return depositInterest; }
    public long getInvestmentPrincipal() { return investmentPrincipal; }
    public long getInvestmentProfit() { return investmentProfit; }
    public long getInvestmentValue() { return investmentValue; }
    public long getTaxableProfit() { return taxableProfit; }
    public long getFinancialTax() { return financialTax; }
    public long getWalletAfter() { return walletAfter; }
}
