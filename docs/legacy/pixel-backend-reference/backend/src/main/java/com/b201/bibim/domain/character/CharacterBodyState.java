package com.b201.bibim.domain.character;

/**
 * 이스터에그 체형(소득 대비 비율 기반). 노출은 본인만·중립어 권장.
 * 규격: docs/plans/2026-07-29-character-cosmetic-pricing-and-seed.md 4절.
 *
 * <ul>
 *   <li>save_ratio  = balance / (income_base * days)</li>
 *   <li>spend_ratio = spend_total / (income_base * days)</li>
 *   <li>save_ratio >= 0.6 -> FAT / spend_ratio >= 0.6 -> SLIM / 그 외 BASE</li>
 * </ul>
 * 히스테리시스: 한 번 FAT/SLIM 이 되면 경계에서 ±0.1 여유를 둬 잦은 전환을 막는다.
 */
public enum CharacterBodyState {
    FAT,
    BASE,
    SLIM;

    public static final long INCOME_BASE = 100L;
    public static final int DAYS = 2;

    private static final double ENTER_THRESHOLD = 0.6;
    private static final double EXIT_THRESHOLD = 0.5; // ENTER - 0.1 (히스테리시스)

    private static long denominator() {
        return INCOME_BASE * DAYS;
    }

    public static double saveRatio(long walletBalance) {
        return (double) Math.max(walletBalance, 0) / denominator();
    }

    public static double spendRatio(long spendTotal) {
        return (double) Math.max(spendTotal, 0) / denominator();
    }

    /**
     * 저장된 이전 상태가 없을 때(첫 조회)는 진입 임계값으로만 판정한다.
     */
    public static CharacterBodyState evaluate(long walletBalance, long spendTotal) {
        return evaluate(BASE, walletBalance, spendTotal);
    }

    /**
     * 이전 상태를 반영해 히스테리시스를 적용한다. 경계값이 EXIT~ENTER 사이면 이전 상태를 유지한다.
     */
    public static CharacterBodyState evaluate(
            CharacterBodyState previous,
            long walletBalance,
            long spendTotal
    ) {
        CharacterBodyState prior = previous == null ? BASE : previous;
        double save = saveRatio(walletBalance);
        double spend = spendRatio(spendTotal);

        if (prior == FAT) {
            if (save >= EXIT_THRESHOLD) {
                return FAT;
            }
            return spend >= ENTER_THRESHOLD ? SLIM : BASE;
        }
        if (prior == SLIM) {
            if (spend >= EXIT_THRESHOLD) {
                return SLIM;
            }
            return save >= ENTER_THRESHOLD ? FAT : BASE;
        }
        // BASE: 진입 임계값을 넘어야 전환. 저축을 우선한다(둘 다 넘기 어려운 구조).
        if (save >= ENTER_THRESHOLD) {
            return FAT;
        }
        if (spend >= ENTER_THRESHOLD) {
            return SLIM;
        }
        return BASE;
    }
}
