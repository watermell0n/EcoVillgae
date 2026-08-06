package com.b201.ecovillage.investment.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/**
 * 학급이 투자에 쓰는 가상 기업 목록이다.
 *
 * <p>1주 시작가는 교사가 정한다. 여기 있는 가격 비중은 "어느 기업이 상대적으로 비싼가"만
 * 정하며, 규칙 엔진이 학급 지표로 뽑은 기준가에 이 비중을 곱해 종목별 시작가를 추천한다.
 * 교사가 아무 값도 확정하지 않으면 기본 기준가({@value #DEFAULT_BASE_PRICE})를 쓴다.
 *
 * <p>시나리오 생성과 규칙 엔진이 같은 종목 목록을 봐야 하므로 한곳에 둔다.
 */
public final class InvestmentAssetCatalog {

    /** 교사가 시작가를 정하지 않았을 때 쓰는 기준가다. */
    public static final long DEFAULT_BASE_PRICE = 1_000L;

    /** 단기 투자에 쓰는 종목 수다. 하루 만에 판단해야 하므로 적게 둔다. */
    private static final int SHORT_TERM_COUNT = 3;
    /** 장기 투자에 쓰는 종목 수다. 여러 날 비교하며 고르므로 넉넉히 둔다. */
    private static final int LONG_TERM_COUNT = 6;

    private static final List<Seed> SEEDS = List.of(
        new Seed("GREEN", "그린모빌리티", "친환경 이동수단을 만드는 가상 기업",
            new BigDecimal("1.00"), new BigDecimal("1.00")),
        new Seed("CLOUD", "클라우드교육", "학교용 디지털 학습 도구를 만드는 가상 기업",
            new BigDecimal("1.25"), new BigDecimal("1.40")),
        new Seed("FOOD", "마을푸드", "지역 농산물 유통을 돕는 가상 기업",
            new BigDecimal("0.80"), new BigDecimal("0.60")),
        new Seed("ENERGY", "새빛에너지", "재생 에너지 설비를 만드는 가상 기업",
            new BigDecimal("1.50"), new BigDecimal("1.20")),
        new Seed("HEALTH", "튼튼케어", "학생 건강을 돌보는 용품을 만드는 가상 기업",
            new BigDecimal("1.10"), new BigDecimal("0.80")),
        new Seed("BOOK", "책마을출판", "어린이 책을 만들고 파는 가상 기업",
            new BigDecimal("0.90"), new BigDecimal("0.70"))
    );

    private InvestmentAssetCatalog() {
    }

    /**
     * 투자 방식에 쓰는 종목 목록이다. 자리와 순서는 방식마다 고정이므로, 교사가 정한 1주
     * 시작가와 변동 범위를 묶어 두는 종목 기호도 다시 만들 때마다 같다.
     */
    public static List<Seed> seeds(InvestmentMode mode) {
        return SEEDS.subList(0, mode == InvestmentMode.SHORT_TERM ? SHORT_TERM_COUNT : LONG_TERM_COUNT);
    }

    /** 기준가에 종목 비중을 곱한 1주 가격이다. 10 단위로 끊어 학생이 읽기 쉽게 한다. */
    public static long priceAt(long basePrice, BigDecimal priceWeight) {
        long price = BigDecimal.valueOf(basePrice)
            .multiply(priceWeight)
            .divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.TEN)
            .longValue();
        return Math.max(1L, price);
    }

    /** action_id에 쓸 수 있게 종목 기호를 소문자로 바꾼다. */
    public static String actionKey(String symbol) {
        return symbol.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 하루 변동 범위(±bps)다. 기준 변동폭에 종목의 위험 비중을 곱해 낸다. 50 단위로 끊어
     * 학생 화면에 0.50% 같은 값으로 보이게 한다.
     */
    public static long returnBandAt(long baseVolatilityBps, BigDecimal riskWeight) {
        long band = BigDecimal.valueOf(baseVolatilityBps)
            .multiply(riskWeight)
            .divide(BigDecimal.valueOf(50L), 0, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(50L))
            .longValue();
        return Math.max(50L, band);
    }

    /**
     * 가상 기업 하나다.
     *
     * <p>priceWeight는 종목 사이의 가격 차이만, riskWeight는 종목 사이의 위험 차이만 정한다.
     * 실제 금액과 변동 범위는 학급 지표로 계산해 교사가 확정한다.
     */
    public record Seed(
        String symbol,
        String name,
        String profile,
        BigDecimal priceWeight,
        BigDecimal riskWeight
    ) {
    }
}
