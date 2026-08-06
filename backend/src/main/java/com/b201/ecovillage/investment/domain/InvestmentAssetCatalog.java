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

    private static final List<Seed> SEEDS = List.of(
        new Seed("GREEN", "그린모빌리티", "친환경 이동수단을 만드는 가상 기업", new BigDecimal("1.00")),
        new Seed("CLOUD", "클라우드교육", "학교용 디지털 학습 도구를 만드는 가상 기업", new BigDecimal("1.25")),
        new Seed("FOOD", "마을푸드", "지역 농산물 유통을 돕는 가상 기업", new BigDecimal("0.80")),
        new Seed("ENERGY", "새빛에너지", "재생 에너지 설비를 만드는 가상 기업", new BigDecimal("1.50"))
    );

    private InvestmentAssetCatalog() {
    }

    public static List<Seed> seeds() {
        return SEEDS;
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

    public record Seed(String symbol, String name, String profile, BigDecimal priceWeight) {
    }
}
