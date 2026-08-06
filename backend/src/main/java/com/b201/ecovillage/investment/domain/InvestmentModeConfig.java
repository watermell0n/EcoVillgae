package com.b201.ecovillage.investment.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * INVESTMENT 모듈 설정에서 교사가 쓰겠다고 고른 투자 방식을 읽는다.
 *
 * <p>학급은 단기와 장기를 동시에 운영할 수 있으므로 설정은 {@code investment_modes} 목록을
 * 쓴다. 목록이 생기기 전에 활성화된 학급은 {@code investment_mode} 단일 값만 갖고 있어
 * 그 값도 함께 읽어 준다.
 *
 * <p>고르지 않은 방식은 시나리오를 만들 수 없다. 빈 결과는 "이 학급은 그 방식을 쓰지
 * 않는다"는 뜻이므로 호출하는 쪽에서 기본값으로 채우지 않는다.
 */
public final class InvestmentModeConfig {

    public static final String MODES_KEY = "investment_modes";
    public static final String LEGACY_MODE_KEY = "investment_mode";

    private InvestmentModeConfig() {
    }

    public static Set<InvestmentMode> enabledModes(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<InvestmentMode> modes = new LinkedHashSet<>();
        if (config.get(MODES_KEY) instanceof Collection<?> values) {
            values.forEach(value -> parse(value).ifPresent(modes::add));
        }
        if (modes.isEmpty()) {
            parse(config.get(LEGACY_MODE_KEY)).ifPresent(modes::add);
        }
        return Collections.unmodifiableSet(modes);
    }

    public static Optional<InvestmentMode> parse(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(InvestmentMode.valueOf(String.valueOf(raw).trim()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
