package com.b201.ecovillage.investment.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.application.ClassroomAiSettings;
import com.b201.ecovillage.ai.contract.InvestmentCompanyContract;
import com.b201.ecovillage.ai.infrastructure.AiServiceProperties;
import com.b201.ecovillage.ai.infrastructure.InvestmentCompanyAiClient;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.investment.domain.InvestmentAssetCatalog;
import com.b201.ecovillage.investment.domain.InvestmentMode;
import com.b201.ecovillage.investment.repository.InvestmentAssetRepository;

/**
 * 시나리오에 쓸 가상 기업을 정한다.
 *
 * <p>기업 이름은 시나리오를 만들 때마다 AI가 새로 짓는다. 같은 이름이 되풀이되면 학생이
 * 기업을 보지 않고 지난번 결과만 기억해 고르게 되기 때문이다.
 *
 * <p>자리(슬롯)는 종목 목록이 정한다. 교사가 "운영 관리 &gt; 투자"에서 정한 1주 시작가와
 * 변동 범위가 그 자리의 기호로 묶여 있으므로, AI는 자리마다 보여 줄 이름과 소개만 바꾼다.
 * AI가 실패하면 기본 이름으로 만든다. 이름을 짓지 못한다고 투자 수업을 멈출 이유는 없다.
 */
@Component
public class InvestmentCompanyFactory {

    private static final Logger log = LoggerFactory.getLogger(InvestmentCompanyFactory.class);

    private final InvestmentCompanyAiClient aiClient;
    private final ClassroomAiSettings aiSettings;
    private final InvestmentAssetRepository assetRepository;
    private final AiServiceProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public InvestmentCompanyFactory(
        InvestmentCompanyAiClient aiClient,
        ClassroomAiSettings aiSettings,
        InvestmentAssetRepository assetRepository,
        AiServiceProperties properties,
        UuidV7Generator uuidGenerator,
        Clock clock
    ) {
        this.aiClient = aiClient;
        this.aiSettings = aiSettings;
        this.assetRepository = assetRepository;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    /**
     * 자리마다 보여 줄 기업을 돌려준다. 결과 길이와 순서는 종목 목록과 같다.
     */
    public List<Company> create(Classroom classroom, InvestmentMode mode) {
        List<InvestmentAssetCatalog.Seed> seeds = InvestmentAssetCatalog.seeds(mode);
        if (!aiSettings.enabled(classroom.getId())) {
            return fallback(seeds);
        }
        try {
            InvestmentCompanyContract.Response response = aiClient.create(request(classroom, mode, seeds));
            List<InvestmentCompanyContract.Company> companies = response.output().companies();
            if (companies.size() != seeds.size()) {
                return fallback(seeds);
            }
            List<Company> result = new ArrayList<>();
            for (int index = 0; index < seeds.size(); index++) {
                InvestmentCompanyContract.Company company = companies.get(index);
                result.add(new Company(
                    seeds.get(index), company.symbol(), company.name(), company.profile()
                ));
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            // 이름을 새로 짓지 못한 것뿐이므로 기본 기업으로 시나리오를 이어서 만든다.
            log.warn("투자 기업 이름 생성에 실패해 기본 기업으로 만듭니다. classroomId={}", classroom.getId(), exception);
            return fallback(seeds);
        }
    }

    private InvestmentCompanyContract.Request request(
        Classroom classroom,
        InvestmentMode mode,
        List<InvestmentAssetCatalog.Seed> seeds
    ) {
        UUID aiRunId = uuidGenerator.generate();
        return new InvestmentCompanyContract.Request(
            aiRunId,
            uuidGenerator.generate(),
            uuidGenerator.generate(),
            InvestmentCompanyContract.SCHEMA_VERSION,
            InvestmentCompanyContract.PROMPT_VERSION,
            1,
            clock.instant().plus(properties.getTotalTimeout()),
            "ko-KR",
            classroom.getGrade(),
            new InvestmentCompanyContract.Payload(
                courseScale(classroom.getCourseSize()),
                mode == InvestmentMode.SHORT_TERM
                    ? InvestmentCompanyContract.InvestmentMode.SHORT_TERM
                    : InvestmentCompanyContract.InvestmentMode.LONG_TERM,
                seeds.size(),
                usedNames(classroom.getId()),
                mode == InvestmentMode.SHORT_TERM
                    ? "하루 만에 결과가 나오는 단기 투자에서 학생이 하는 일을 바로 떠올릴 수 있는 기업"
                    : "여러 날에 걸쳐 지켜보는 장기 투자에서 학생이 성장을 상상할 수 있는 기업"
            )
        );
    }

    /**
     * 이 학급이 이미 쓴 기업 이름이다. 다시 쓰지 않도록 AI에 함께 보낸다.
     * 계약이 40개까지 받으므로 최근 것부터 그만큼만 보낸다.
     */
    private List<String> usedNames(UUID classroomId) {
        return assetRepository.findRecentNames(classroomId).stream()
            .distinct()
            .limit(40)
            .toList();
    }

    private static List<Company> fallback(List<InvestmentAssetCatalog.Seed> seeds) {
        return seeds.stream()
            .map(seed -> new Company(seed, seed.symbol(), seed.name(), seed.profile()))
            .toList();
    }

    private static InvestmentCompanyContract.CourseScale courseScale(CourseSize size) {
        return switch (size) {
            case ONE_SESSION -> InvestmentCompanyContract.CourseScale.ONE_SHOT;
            case ONE_WEEK -> InvestmentCompanyContract.CourseScale.ONE_WEEK;
            case ONE_MONTH -> InvestmentCompanyContract.CourseScale.ONE_MONTH;
            case SEMESTER -> InvestmentCompanyContract.CourseScale.ONE_SEMESTER;
        };
    }

    /**
     * 자리 하나에 놓인 기업이다. seed는 교사가 정한 값을 묶어 두는 자리이고, 나머지는 학생과
     * 교사에게 보이는 이름이다.
     */
    public record Company(
        InvestmentAssetCatalog.Seed seed,
        String symbol,
        String name,
        String profile
    ) {
    }
}
