package com.b201.bibim.feature.management;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TeacherClassManagementDto {

    private TeacherClassManagementDto() {
    }

    public record EconomyRules(
            @Min(0) long roleIncome,
            @Min(0) long day1CorrectReward,
            @Min(0) @Max(10000) int incomeTaxBps,
            @Min(0) @Max(10000) int depositInterestBps,
            @Min(0) @Max(10000) int financialTaxBps,
            @Min(0) long day2CorrectReward,
            @Min(0) @Max(10000) int inflationBps,
            @Min(1) long depositMinAmount,
            @Min(1) long depositUnit,
            @Min(1) long investmentMinAmount,
            @Min(1) long investmentUnit,
            @Min(0) long initialFundAmount
    ) {
    }

    public record QuizOption(String id, String text) {
    }

    public record QuizQuestion(
            UUID questionId,
            int orderNo,
            @NotBlank @Size(max = 1000) String prompt,
            @NotEmpty @Size(min = 2, max = 6) List<@Valid QuizOption> options,
            @NotBlank @Size(max = 50) String correctAnswer,
            @Size(max = 2000) String explanation
    ) {
    }

    public record QuizSettings(short dayNumber, String status, List<QuizQuestion> questions) {
    }

    public record InvestmentAsset(
            UUID assetId,
            String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String description,
            @NotBlank @Size(max = 10000) String companyInfo,
            @NotBlank String riskLevel,
            int minReturnBps,
            int maxReturnBps,
            @Min(-10000) @Max(100000) int actualReturnBps,
            int displayOrder
    ) {
    }

    public record ShopProduct(
            UUID productId,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 255) String description,
            @Min(0) long price,
            String imageUrl,
            int currentStock,
            int displayOrder
    ) {
    }

    public record StudentRoleCatalogEntry(
            int participantNumber,
            String roleName
    ) {
    }

    public record FundCostOption(
            UUID optionId,
            @NotBlank @Size(max = 100) String name,
            @Min(1) long amount,
            boolean enabled,
            int displayOrder
    ) {
    }

    public record ClassSettingsResponse(
            UUID roomId,
            String roomName,
            String roomStatus,
            boolean editingLiveClass,
            boolean investmentResultsLocked,
            EconomyRules economy,
            List<QuizSettings> quizzes,
            List<InvestmentAsset> investments,
            List<ShopProduct> products,
            List<FundCostOption> fundCostOptions,
            List<StudentRoleCatalogEntry> roleCatalog
    ) {
    }

    public record UpdateClassSettingsRequest(
            @NotBlank @Size(max = 100) String roomName,
            @NotNull @Valid EconomyRules economy,
            @NotNull List<@Valid QuizSettings> quizzes,
            @NotNull List<@Valid InvestmentAsset> investments,
            @NotNull List<@Valid ShopProduct> products,
            @NotNull List<@Valid FundCostOption> fundCostOptions,
            @NotNull List<UUID> discontinuedProductIds
    ) {
    }

    public record AddStudentRequest(
            @NotNull @Min(1) @Max(99) Integer participantNumber,
            @Size(max = 50) String nickname
    ) {
    }

    public record UpdateStudentRequest(
            @NotNull @Min(1) @Max(99) Integer participantNumber,
            @Size(max = 50) String nickname,
            @Size(max = 50) String roleName,
            @NotBlank String status
    ) {
    }

    public record ManagedStudent(
            UUID studentId,
            int participantNumber,
            String participationCode,
            String nickname,
            String roleName,
            String status,
            boolean passwordConfigured,
            boolean online,
            Instant lastSeenAt
    ) {
    }
}
