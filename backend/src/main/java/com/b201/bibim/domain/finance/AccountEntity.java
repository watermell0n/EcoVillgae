package com.b201.bibim.domain.finance;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    private UUID id;

    @Column(name = "room_id", nullable = false)
    private UUID roomId;

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "account_type", nullable = false, length = 40)
    private String accountType;

    @Column(nullable = false)
    private long balance;

    @Column(name = "allow_negative", nullable = false)
    private boolean allowNegative;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() {
    }

    private AccountEntity(
            UUID roomId,
            UUID studentId,
            String accountType,
            boolean allowNegative
    ) {
        this.id = UUID.randomUUID();
        this.roomId = roomId;
        this.studentId = studentId;
        this.accountType = accountType;
        this.balance = 0;
        this.allowNegative = allowNegative;
        this.version = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static AccountEntity studentWallet(UUID roomId, UUID studentId) {
        return new AccountEntity(roomId, studentId, "STUDENT_WALLET", false);
    }

    public static AccountEntity classFund(UUID roomId) {
        return new AccountEntity(roomId, null, "CLASS_FUND", false);
    }

    public static AccountEntity systemTreasury(UUID roomId) {
        return new AccountEntity(roomId, null, "SYSTEM_TREASURY", true);
    }

    public static AccountEntity shopRevenue(UUID roomId) {
        return new AccountEntity(roomId, null, "SHOP_REVENUE", false);
    }

    public void changeBy(long amount) {
        long nextBalance = Math.addExact(balance, amount);
        if (!allowNegative && nextBalance < 0) {
            throw new IllegalStateException("음수를 허용하지 않는 계정의 잔액이 부족합니다.");
        }
        balance = nextBalance;
        version += 1;
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRoomId() {
        return roomId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public String getAccountType() {
        return accountType;
    }

    public long getBalance() {
        return balance;
    }
}
