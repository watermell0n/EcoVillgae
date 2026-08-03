package com.b201.ecovillage.ai.application;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.contract.EconomicEventContract;
import com.b201.ecovillage.ai.domain.AiArtifact;
import com.b201.ecovillage.ai.domain.AiProposal;
import com.b201.ecovillage.ai.domain.AiRun;
import com.b201.ecovillage.ai.domain.AsyncOperation;
import com.b201.ecovillage.ai.domain.EconomicEventDraft;
import com.b201.ecovillage.ai.infrastructure.AiServiceCallException;
import com.b201.ecovillage.ai.infrastructure.AiServiceProperties;
import com.b201.ecovillage.ai.infrastructure.EconomicEventAiClient;
import com.b201.ecovillage.ai.repository.AiArtifactRepository;
import com.b201.ecovillage.ai.repository.AiProposalRepository;
import com.b201.ecovillage.ai.repository.AiRunRepository;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.ai.repository.EconomicEventDraftRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomModule;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.investment.application.InvestmentNewsTargetQuery;
import com.b201.ecovillage.investment.application.InvestmentNewsTargetView;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.CourseSize;
import com.b201.ecovillage.identity.domain.UserAccount;
import com.b201.ecovillage.identity.repository.ClassroomRepository;
import com.b201.ecovillage.identity.repository.UserAccountRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * API-AI-004 경제 이벤트 초안 생성을 담당한다.
 * 템플릿 허용 학년과 효과 범위는 규칙 엔진이 정하고 AI는 이야기 문구만 만든다.
 * 초안은 항상 DRAFT로 저장하며 이 API는 게시하지 않는다.
 */
@Service
public class EconomicEventDraftService {

    static final String RUN_TYPE = "ECONOMIC_EVENT_DRAFT";
    private static final String OPERATION_TYPE = "AI_ECONOMIC_EVENT_DRAFT";
    private static final String CANCELED_STATUS = "CANCELED";

    private final ClassroomAuthorizationService authorizationService;
    private final UserAccountRepository userRepository;
    private final ClassroomRepository classroomRepository;
    private final ClassroomModuleRepository moduleRepository;
    private final EconomicEventTemplatePolicy templatePolicy;
    private final EconomicEventAiClient aiClient;
    private final EconomicEventFallbackFactory fallbackFactory;
    private final ClassroomAiSettings aiSettings;
    private final AsyncOperationRepository operationRepository;
    private final AiRunRepository runRepository;
    private final AiArtifactRepository artifactRepository;
    private final AiProposalRepository proposalRepository;
    private final EconomicEventDraftRepository draftRepository;
    private final InvestmentNewsTargetQuery investmentNewsTargetQuery;
    private final AiServiceProperties properties;
    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public EconomicEventDraftService(
        ClassroomAuthorizationService authorizationService,
        UserAccountRepository userRepository,
        ClassroomRepository classroomRepository,
        ClassroomModuleRepository moduleRepository,
        EconomicEventTemplatePolicy templatePolicy,
        EconomicEventAiClient aiClient,
        EconomicEventFallbackFactory fallbackFactory,
        ClassroomAiSettings aiSettings,
        AsyncOperationRepository operationRepository,
        AiRunRepository runRepository,
        AiArtifactRepository artifactRepository,
        AiProposalRepository proposalRepository,
        EconomicEventDraftRepository draftRepository,
        InvestmentNewsTargetQuery investmentNewsTargetQuery,
        AiServiceProperties properties,
        UuidV7Generator uuidGenerator,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.classroomRepository = classroomRepository;
        this.moduleRepository = moduleRepository;
        this.templatePolicy = templatePolicy;
        this.aiClient = aiClient;
        this.fallbackFactory = fallbackFactory;
        this.aiSettings = aiSettings;
        this.operationRepository = operationRepository;
        this.runRepository = runRepository;
        this.artifactRepository = artifactRepository;
        this.proposalRepository = proposalRepository;
        this.draftRepository = draftRepository;
        this.investmentNewsTargetQuery = investmentNewsTargetQuery;
        this.properties = properties;
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public EconomicEventDraftResult create(EconomicEventDraftCommand command) {
        validate(command);
        authorizationService.authorize(
            command.teacherSubject(),
            command.classroomId(),
            ClassroomPermission.REQUEST_AI_ANALYSIS,
            null
        );
        UserAccount teacher = userRepository.findByExternalSubject(command.teacherSubject())
            .orElseThrow(() -> operation(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A teacher session is required."
            ));
        Classroom classroom = classroomRepository.findById(command.classroomId())
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The classroom was not found."
            ));

        EconomicEventContract.TemplateType template = command.templateType();
        int grade = classroom.getGrade();
        if (!templatePolicy.supportedGrade(template, grade)) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "GRADE_POLICY_VIOLATION",
                "The template is not allowed for this grade."
            );
        }
        if (template == EconomicEventContract.TemplateType.INTEREST_RATE
            && !templatePolicy.supportedInterestRateScope(grade, command.interestRateScope())) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "GRADE_POLICY_VIOLATION",
                "The interest rate scope is not allowed for this grade."
            );
        }
        requireActiveModules(classroom.getId(), template);

        EconomicEventContract.SourceType sourceType = command.sourceProposalId() == null
            ? EconomicEventContract.SourceType.TEACHER_REQUEST
            : EconomicEventContract.SourceType.MONITOR_RECOMMENDATION;
        List<String> evidenceCodes = List.of();
        if (sourceType == EconomicEventContract.SourceType.MONITOR_RECOMMENDATION) {
            AiProposal proposal = proposalRepository
                .findByClassroomIdAndId(classroom.getId(), command.sourceProposalId())
                .orElseThrow(() -> operation(
                    HttpStatus.NOT_FOUND,
                    "RESOURCE_NOT_FOUND",
                    "The source proposal was not found."
                ));
            if (draftRepository.existsByClassroomIdAndSourceProposalId(
                classroom.getId(),
                proposal.getId()
            )) {
                throw operation(
                    HttpStatus.CONFLICT,
                    "DUPLICATE_EVENT_RECOMMENDATION",
                    "A draft already exists for this monitoring proposal."
                );
            }
            evidenceCodes = evidenceCodes(proposal);
            if (evidenceCodes.isEmpty()) {
                throw operation(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_RECOMMENDATION_CONTEXT",
                    "The source proposal has no evidence code to carry over."
                );
            }
        }

        InvestmentNewsTargetView investmentTarget =
            template == EconomicEventContract.TemplateType.INVESTMENT_NEWS
                ? resolveInvestmentTarget(classroom.getId())
                : null;

        Instant now = clock.instant();
        UUID operationId = uuidGenerator.generate();
        UUID aiRunId = uuidGenerator.generate();
        EconomicEventContract.Request request = new EconomicEventContract.Request(
            aiRunId,
            command.requestId(),
            command.correlationId(),
            EconomicEventContract.SCHEMA_VERSION,
            EconomicEventContract.PROMPT_VERSION,
            1,
            now.plus(properties.getTotalTimeout()),
            EconomicEventContract.LOCALE,
            grade,
            new EconomicEventContract.Payload(
                courseScale(classroom.getCourseSize()),
                sourceType,
                command.sourceProposalId(),
                evidenceCodes,
                template,
                command.interestRateScope(),
                templatePolicy.effectSummary(template, command.interestRateScope()),
                templatePolicy.prohibitedEffects(template),
                command.teacherContext(),
                investmentContext(investmentTarget)
            )
        );

        operationRepository.save(AsyncOperation.running(
            operationId,
            classroom.getId(),
            teacher.getId(),
            OPERATION_TYPE,
            now
        ));
        AiRun run = runRepository.save(AiRun.generating(
            aiRunId,
            classroom.getId(),
            operationId,
            teacher.getId(),
            RUN_TYPE,
            sourceType.name(),
            EconomicEventContract.PROMPT_VERSION,
            Map.of(
                "template_type", template.name(),
                "source_type", sourceType.name()
            ),
            now
        ));

        boolean fallbackUsed = false;
        String fallbackCode = null;
        EconomicEventContract.Response response;
        if (!aiSettings.enabled(classroom.getId())) {
            fallbackUsed = true;
            fallbackCode = "AI_DISABLED_FOR_CLASSROOM";
            response = fallbackFactory.create(request, fallbackCode);
        } else {
            try {
                response = aiClient.draft(request);
                verifyOutput(request, response);
            } catch (AiServiceCallException exception) {
                fallbackUsed = true;
                fallbackCode = exception.getCode();
                response = fallbackFactory.create(request, fallbackCode);
            }
        }

        Instant completedAt = clock.instant();
        EconomicEventContract.Output output = response.output();
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("template_type", template.name());
        effect.put("source_type", sourceType.name());
        effect.put("interest_rate_scope",
            command.interestRateScope() == null ? null : command.interestRateScope().name());
        effect.put("effect_summary", request.payload().effectSummary());
        effect.put("prohibited_effects", request.payload().prohibitedEffects());
        effect.put("evidence_codes", evidenceCodes);
        effect.put("choices", output.choices().stream()
            .map(choice -> Map.of(
                "choice_key", choice.choiceKey(),
                "label", choice.label(),
                "consequence_text", choice.consequenceText()
            ))
            .toList());
        effect.put("progress_messages", output.progressMessages());
        effect.put("fallback_used", fallbackUsed);
        effect.put("fallback_code", fallbackCode);
        if (investmentTarget != null) {
            // 승인 경로가 같은 단계에 그대로 연결하도록 초안에 함께 남긴다.
            Map<String, Object> binding = new LinkedHashMap<>();
            binding.put("scenario_version_id", investmentTarget.scenarioVersionId().toString());
            binding.put("asset_id", investmentTarget.assetId().toString());
            binding.put("price_step_id", investmentTarget.priceStepId().toString());
            binding.put("asset_name", investmentTarget.assetName());
            binding.put("asset_symbol", investmentTarget.assetSymbol());
            binding.put("current_step_index", investmentTarget.currentStepIndex());
            effect.put("investment_news_binding", binding);
        }

        EconomicEventDraft draftToSave = EconomicEventDraft.draft(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            sourceType.name(),
            command.sourceProposalId(),
            output.title(),
            output.narrative(),
            effect,
            completedAt
        );
        if (investmentTarget != null) {
            draftToSave.bindInvestmentPriceStep(investmentTarget.priceStepId());
        }
        EconomicEventDraft draft = draftRepository.save(draftToSave);

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("template_type", template.name());
        validation.put("fallback_used", fallbackUsed);
        validation.put("fallback_code", fallbackCode);
        validation.put("review_status", output.reviewStatus());
        validation.put("validation_codes", response.validationCodes());
        run.awaitTeacher(response.provider(), response.modelId(), validation, completedAt);
        artifactRepository.save(AiArtifact.create(
            uuidGenerator.generate(),
            classroom.getId(),
            aiRunId,
            "ECONOMIC_EVENT_DRAFT",
            toMap(response),
            response.schemaVersion(),
            response.safetyStatus(),
            completedAt
        ));

        return new EconomicEventDraftResult(
            operationId,
            "RUNNING",
            "/operations/" + operationId,
            draft.getId(),
            aiRunId,
            fallbackUsed,
            fallbackCode
        );
    }

    /**
     * AI가 템플릿·출처·근거·금리 범위를 바꾸면 출력을 신뢰할 수 없으므로 거절하고 fallback으로 넘긴다.
     */
    private void verifyOutput(
        EconomicEventContract.Request request,
        EconomicEventContract.Response response
    ) {
        EconomicEventContract.Payload payload = request.payload();
        EconomicEventContract.Output output = response.output();
        boolean sameSource = payload.sourceType() == output.sourceType()
            && java.util.Objects.equals(payload.sourceProposalId(), output.sourceProposalId())
            && Set.copyOf(payload.sourceEvidenceCodes())
                .equals(Set.copyOf(output.sourceEvidenceCodes()));
        boolean sameTemplate = payload.templateType() == output.templateType()
            && payload.interestRateScope() == output.interestRateScope();
        if (!sameSource || !sameTemplate || !"REVIEW_REQUIRED".equals(output.reviewStatus())) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The AI event output changed the template, source, or review status."
            );
        }
        if (output.choices().size() < 2) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The AI event output needs at least two choices."
            );
        }
        requireUnchangedInvestmentBinding(payload, output);
    }

    /**
     * 자산 이름과 기호, 시나리오·종목·단계 연결은 입력값을 그대로 유지해야 한다. AI가 하나라도
     * 바꾸면 출력을 버리고 규칙 기반 대체안을 쓴다.
     */
    private static void requireUnchangedInvestmentBinding(
        EconomicEventContract.Payload payload,
        EconomicEventContract.Output output
    ) {
        EconomicEventContract.InvestmentNewsContext context = payload.investmentNewsContext();
        EconomicEventContract.InvestmentNewsBinding binding = output.investmentNewsBinding();
        if (context == null) {
            if (binding != null) {
                throw new AiServiceCallException(
                    "AI_OUTPUT_CONTRACT_VIOLATION",
                    "Only INVESTMENT_NEWS may return an investment binding."
                );
            }
            return;
        }
        if (binding == null) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The INVESTMENT_NEWS output must return its investment binding."
            );
        }
        boolean same = context.scenarioVersionId().equals(binding.scenarioVersionId())
            && context.assetId().equals(binding.assetId())
            && context.priceStepId().equals(binding.priceStepId())
            && context.assetName().equals(binding.assetName())
            && context.assetSymbol().equals(binding.assetSymbol())
            && context.currentStepIndex() == binding.currentStepIndex();
        if (!same) {
            throw new AiServiceCallException(
                "AI_OUTPUT_CONTRACT_VIOLATION",
                "The AI event output changed the investment price step binding."
            );
        }
    }

    /**
     * 뉴스를 붙일 종목을 고른다. 표시 순서가 앞이고 현재 단계에 살아 있는 뉴스가 없는 종목을 쓴다.
     *
     * <p>계약에 교사가 종목을 지정하는 입력이 없어 서버가 고른다. 교사가 직접 고르게 하려면
     * API-AI-004에 investment_news_targets를 추가해야 한다.
     */
    private InvestmentNewsTargetView resolveInvestmentTarget(UUID classroomId) {
        List<InvestmentNewsTargetView> targets =
            investmentNewsTargetQuery.findPublishableTargets(classroomId);
        if (targets.isEmpty()) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "INVALID_INVESTMENT_STEP_BINDING",
                "The classroom has no revealed investment price step to attach news to."
            );
        }
        return targets.stream()
            .filter(target -> !draftRepository
                .existsByClassroomIdAndInvestmentPriceStepIdAndStatusNot(
                    classroomId,
                    target.priceStepId(),
                    CANCELED_STATUS
                ))
            .findFirst()
            .orElseThrow(() -> operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "DUPLICATE_EVENT_RECOMMENDATION",
                "Every revealed price step already has an active investment news draft."
            ));
    }

    private static EconomicEventContract.InvestmentNewsContext investmentContext(
        InvestmentNewsTargetView target
    ) {
        if (target == null) {
            return null;
        }
        return new EconomicEventContract.InvestmentNewsContext(
            target.scenarioVersionId(),
            target.assetId(),
            target.priceStepId(),
            target.assetName(),
            target.assetSymbol(),
            target.currentStepIndex(),
            target.publicPriceFacts().stream()
                .map(fact -> new EconomicEventContract.InvestmentPriceFact(
                    fact.stepIndex(),
                    fact.price(),
                    fact.changeRateBps(),
                    fact.revealedAt()
                ))
                .toList()
        );
    }

    private void requireActiveModules(UUID classroomId, EconomicEventContract.TemplateType template) {
        Set<String> required = templatePolicy.requiredModules(template);
        if (required.isEmpty()) {
            return;
        }
        Set<String> active = moduleRepository
            .findAllByClassroomIdAndStatusOrderByModuleCode(classroomId, "ACTIVE")
            .stream()
            .map(ClassroomModule::getModuleCode)
            .collect(Collectors.toUnmodifiableSet());
        if (!active.containsAll(required)) {
            throw operation(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "MODULE_NOT_ACTIVE",
                "The template requires active modules: " + String.join(", ", required)
            );
        }
    }

    private static List<String> evidenceCodes(AiProposal proposal) {
        Object codes = proposal.getProposedValues() == null
            ? null
            : proposal.getProposedValues().get("recommendation_action_ids");
        if (codes instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(String::valueOf).distinct().toList();
        }
        String metricCode = proposal.getMetricCode();
        return metricCode == null ? List.of() : List.of(metricCode);
    }

    private static EconomicEventContract.CourseScale courseScale(CourseSize size) {
        return switch (size) {
            case ONE_SESSION -> EconomicEventContract.CourseScale.ONE_SHOT;
            case ONE_WEEK -> EconomicEventContract.CourseScale.ONE_WEEK;
            case ONE_MONTH -> EconomicEventContract.CourseScale.ONE_MONTH;
            case SEMESTER -> EconomicEventContract.CourseScale.ONE_SEMESTER;
        };
    }

    private void validate(EconomicEventDraftCommand command) {
        if (command.teacherSubject() == null || command.teacherSubject().isBlank()) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "A teacher session is required.");
        }
        if (command.templateType() == null) {
            throw operation(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "template_type is required.");
        }
        boolean interestRate =
            command.templateType() == EconomicEventContract.TemplateType.INTEREST_RATE;
        if (interestRate && command.interestRateScope() == null) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "interest_rate_scope is required for the INTEREST_RATE template."
            );
        }
        if (!interestRate && command.interestRateScope() != null) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "interest_rate_scope is only allowed for the INTEREST_RATE template."
            );
        }
        if (command.teacherContext() != null && command.teacherContext().length() > 1000) {
            throw operation(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "teacher_context must be 1000 characters or fewer."
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
