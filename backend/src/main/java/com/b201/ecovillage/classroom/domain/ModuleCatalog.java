package com.b201.ecovillage.classroom.domain;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical catalog for modules that a teacher can select for a classroom.
 *
 * <p>{@code WALLET} is deliberately absent because wallets and the ledger are
 * technical infrastructure created lazily by monetary use cases. Legacy input
 * aliases are accepted only at API boundaries and always resolve to a catalog
 * entry before persistence or output.</p>
 */
public final class ModuleCatalog {

    public static final String WALLET_INFRASTRUCTURE_CODE = "WALLET";

    private static final Pattern CODE_PATTERN =
        Pattern.compile("^[A-Z][A-Z0-9_]{0,39}$");

    private static final List<Definition> DEFINITIONS = List.of(
        module("ROLE_SALARY", "역할·급여", true, List.of(), true, "SALARY", false),
        module("INCOME_TAX", "소득세", true, List.of("ROLE_SALARY"), true,
            "INCOME_TAX_RATE", false),
        module("STORE", "상점·소비", true, List.of(), false, null, false),
        module("BUDGET", "예산 관리", true, List.of(), false, null, false),
        module("PAYSLIP", "급여명세서", true, List.of(), false, null, false),
        module("PUBLIC_FUND", "공동기금", true, List.of(), false, null, false),
        module("PUBLIC_BUDGET", "공공예산·투표", true, List.of(), false, null, false),
        module("DEPOSIT", "정기예금", true, List.of(), true, "DEPOSIT_RATE", false),
        module("SAVINGS", "정기적금", true, List.of(), false, null, false),
        module("CREDIT", "신용 기초", false, List.of(), false, null, false),
        module("LOAN", "대출·상환", true, List.of("DEPOSIT"), true,
            "LOAN_RATE", false),
        module("INVESTMENT", "가상 투자", true, List.of(), false, null, false),
        module("RISK_MANAGEMENT", "투자 위험관리", false, List.of(), false, null, false),
        module("MARKET_PRICE", "가격 변화", true, List.of(), false, null, false),
        module("TRADE", "개인거래", true, List.of(), false, null, false),
        module("PHYSICAL_STORE", "실제 상품", true, List.of(), false, null, false),
        module("CHARACTER", "캐릭터", true, List.of(), false, null, false),
        module("QUIZ_GAME", "게임형 퀴즈", true, List.of(), false, null, true),
        module("QUIZ_TEST", "평가형 퀴즈", false, List.of(), false, null, true),
        module("AI_EVENT", "AI 경제 이벤트", true, List.of(), false, null, false),
        module("CUMULATIVE_REPORT", "누적 리포트", false, List.of(), false, null, false)
    );

    private static final Map<String, Definition> BY_CODE = definitionsByCode();
    private static final Map<String, String> LEGACY_ALIASES = Map.of(
        "INCOME", "ROLE_SALARY",
        "SALARY", "ROLE_SALARY",
        "TAX", "INCOME_TAX"
    );
    private static final Set<String> CODES = Set.copyOf(BY_CODE.keySet());

    static {
        DEFINITIONS.forEach(definition -> definition.prerequisites().forEach(prerequisite -> {
            if (!BY_CODE.containsKey(prerequisite)) {
                throw new IllegalStateException(
                    definition.code() + " has an unknown prerequisite: " + prerequisite
                );
            }
        }));
    }

    private ModuleCatalog() {
    }

    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    public static Set<String> codes() {
        return CODES;
    }

    public static Optional<Definition> find(String inputCode) {
        String normalized = normalize(inputCode);
        if (normalized == null || WALLET_INFRASTRUCTURE_CODE.equals(normalized)) {
            return Optional.empty();
        }
        String canonical = LEGACY_ALIASES.getOrDefault(normalized, normalized);
        return Optional.ofNullable(BY_CODE.get(canonical));
    }

    public static Definition require(String inputCode) {
        return find(inputCode).orElseThrow(() -> new IllegalArgumentException(
            "Unsupported selectable module code: " + inputCode
        ));
    }

    public static boolean isCanonical(String moduleCode) {
        String normalized = normalize(moduleCode);
        return normalized != null && BY_CODE.containsKey(normalized);
    }

    public static boolean isLegacyAlias(String moduleCode) {
        String normalized = normalize(moduleCode);
        return normalized != null && LEGACY_ALIASES.containsKey(normalized);
    }

    public static boolean isWalletInfrastructure(String moduleCode) {
        return WALLET_INFRASTRUCTURE_CODE.equals(normalize(moduleCode));
    }

    private static String normalize(String inputCode) {
        if (inputCode == null) {
            return null;
        }
        String normalized = inputCode.trim().toUpperCase(Locale.ROOT);
        return CODE_PATTERN.matcher(normalized).matches() ? normalized : null;
    }

    private static Definition module(
        String code,
        String displayName,
        boolean usesMoney,
        List<String> prerequisites,
        boolean initialPolicyRequired,
        String policyArea,
        boolean oneSessionAllowed
    ) {
        return new Definition(
            code,
            displayName,
            usesMoney,
            prerequisites,
            initialPolicyRequired,
            policyArea,
            oneSessionAllowed
        );
    }

    private static Map<String, Definition> definitionsByCode() {
        Map<String, Definition> result = new LinkedHashMap<>();
        for (Definition definition : DEFINITIONS) {
            if (result.put(definition.code(), definition) != null) {
                throw new IllegalStateException("Duplicate module code: " + definition.code());
            }
        }
        return Map.copyOf(result);
    }

    public record Definition(
        String code,
        String displayName,
        boolean usesMoney,
        List<String> prerequisites,
        boolean initialPolicyRequired,
        String policyArea,
        boolean oneSessionAllowed
    ) {

        public Definition {
            if (code == null || !CODE_PATTERN.matcher(code).matches()) {
                throw new IllegalArgumentException("A canonical module code is required.");
            }
            if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException("A module display name is required.");
            }
            prerequisites = List.copyOf(new LinkedHashSet<>(prerequisites));
            if (initialPolicyRequired && (policyArea == null || policyArea.isBlank())) {
                throw new IllegalArgumentException(
                    "An initial-policy module requires a policy area."
                );
            }
        }
    }
}
