package com.b201.bibim.feature.learning;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.ModuleStateEntity;
import com.b201.bibim.domain.room.ModuleStateRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.learning.LearningDto.ChangePageRequest;
import com.b201.bibim.feature.learning.LearningDto.GuidedLearningResponse;
import com.b201.bibim.feature.learning.LearningDto.PageResponse;
import com.b201.bibim.feature.learning.LearningDto.TopicDetailResponse;
import com.b201.bibim.feature.learning.LearningDto.TopicResponse;
import com.b201.bibim.global.exception.ApiException;
import com.b201.bibim.feature.flow.StudentFlowService;
import com.b201.bibim.domain.room.RoomStage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {

    private static final UUID INCOME_TOPIC_ID = id(101);
    private static final UUID SHOP_TOPIC_ID = id(102);
    private static final UUID DEPOSIT_TOPIC_ID = id(103);
    private static final UUID INVESTMENT_TOPIC_ID = id(104);
    private static final UUID DAY2_FINANCE_TOPIC_ID = id(105);
    private static final UUID INFLATION_TOPIC_ID = id(106);

    private static final List<TopicDefinition> TOPICS = List.of(
            new TopicDefinition(
                    INCOME_TOPIC_ID,
                    "INCOME_AND_TAX",
                    "월급명세서 학습",
                    "월급과 실수령액, 세금과 공동기금의 역할을 알아봐요.",
                    1
            ),
            new TopicDefinition(
                    SHOP_TOPIC_ID,
                    "SMART_SPENDING",
                    "합리적인 소비",
                    "가격, 필요, 남은 돈을 비교해 현명하게 소비해요.",
                    1
            ),
            new TopicDefinition(
                    DEPOSIT_TOPIC_ID,
                    "DEPOSIT",
                    "소비·저축과 예금",
                    "소비와 저축을 나누고 예금과 이자를 알아봐요.",
                    1
            ),
            new TopicDefinition(
                    INVESTMENT_TOPIC_ID,
                    "INVESTMENT",
                    "투자와 위험",
                    "기대 수익과 손실 위험, 분산투자의 원리를 알아봐요.",
                    1
            ),
            new TopicDefinition(
                    DAY2_FINANCE_TOPIC_ID,
                    "DAY2_FINANCIAL_KNOWLEDGE",
                    "금융세 알아보기",
                    "이자와 투자 이익에 붙는 금융세를 알아봐요.",
                    2
            ),
            new TopicDefinition(
                    INFLATION_TOPIC_ID,
                    "INFLATION",
                    "인플레이션 알아보기",
                    "물건값이 오르면 같은 돈으로 살 수 있는 것이 어떻게 달라지는지 알아봐요.",
                    1
            )
    );

    private static final List<PageDefinition> PAGES = List.of(
            page(111, "INCOME_AND_TAX", "💵", "월급과 실수령액",
                    "일을 해서 받는 돈을 월급이라고 해요.\n세금을 내고 나면 실제로 받는 금액이 달라져요.\n이걸 실수령액이라고 부른답니다!",
                    "총 월급 150비빔 - 세금 15비빔 => 실수령액 135비빔"),
            page(112, "INCOME_AND_TAX", "🏛️", "세금이란?",
                    "우리가 버는 모든 소득에는 정해진 비율의 세금을 내요.\n세금은 학교와 병원처럼 우리가 생활하고 활동하는 환경을 만들고 운영하는 데 사용돼요.\n이코빌리지에서는 학급 공동기금으로 모여요!",
                    "세금 15비빔 → 공동기금 → 학급 행사에 사용"),
            page(113, "INCOME_AND_TAX", "🏫", "세금의 활용",
                    "세금은 모두의 생활과 활동을 돕는 데 쓰이는 돈이에요.\n학교, 병원, 도로, 공원처럼 함께 사용하는 환경을 만들고 운영하는 데 사용돼요.\n우리 교실에서는 공동기금처럼 사용할 수 있어요!",
                    "모은 세금 → 공용 물품 구입 또는 학급 행사 준비"),
            page(121, "SMART_SPENDING", "🛒", "필요와 원하는 것을 구분해요",
                    "물건을 사기 전에는 지금 꼭 필요한지 생각해요.\n가격이 알맞은지 확인해요.\n사고 난 뒤에도 필요한 돈이 남는지 살펴봐요.",
                    "준비물인 공책을 먼저 사고, 남은 돈을 확인한 뒤 간식을 살지 결정해요."),
            page(122, "SMART_SPENDING", "🏷️", "가격과 재고를 확인해요",
                    "같은 물건이라도 가격과 남은 수량은 달라질 수 있어요.\n구매 버튼을 누르기 전에 가격을 확인해요.\n재고와 수량을 다시 확인하면 실수를 줄일 수 있어요.",
                    "연필 2자루의 총가격과 남은 재고를 확인한 뒤 구매를 확정해요."),
            page(131, "DEPOSIT", "🐷💰", "소비와 저축",
                    "소비는 필요한 물건이나 서비스를 사는 거예요.\n저축은 나중을 위해 돈을 모아 두는 거예요.\n쓰기와 모으기를 잘 나누는 것이 중요해요!",
                    "용돈 100비빔 → 60비빔은 소비, 40비빔은 저축"),
            page(132, "DEPOSIT", "🏦", "예금이란?",
                    "은행에 돈을 맡기면 약속한 이율에 따라 이자를 받을 수 있어요.\n이코빌리지에서는 선생님이 정한 이율을 적용해요.\n확정한 예금은 2일차 정산 전에는 꺼낼 수 없어요.",
                    "100비빔 예금 + 이자 10비빔 => 110비빔"),
            page(133, "DEPOSIT", "🏦➕", "이자란?",
                    "이자는 돈을 맡기거나 빌릴 때 붙는 추가 금액이에요.\n은행에 예금하면 이자를 받을 수 있어요.\n반대로 돈을 빌리면 이자를 내기도 해요!",
                    "100비빔을 예금하면 → 이자 10비빔 → 나중에 110비빔"),
            page(141, "INVESTMENT", "📈", "투자란?",
                    "투자는 회사의 성장 가능성을 살펴보고 돈을 맡기는 경제활동이에요.\n회사의 가치가 오르면 수익을 얻을 수 있지만, 가치가 내려가면 손실이 날 수도 있어요.\n위험도가 높을수록 결과가 크게 달라질 수 있어요.",
                    "거북이 상사: 변화가 비교적 작음\n독수리 테크: 수익과 손실의 가능성이 모두 큼"),
            page(142, "INVESTMENT", "⚖️", "위험과 분산투자",
                    "한 곳에 모든 돈을 넣으면 전부 잃을 수도 있어요.\n여러 곳에 나눠서 투자하면 위험이 줄어들어요.\n이걸 분산투자라고 해요!",
                    "달걀을 한 바구니에 담지 마세요 🥚"),
            page(151, "DAY2_FINANCIAL_KNOWLEDGE", "🧾", "금융세가 뭐예요?",
                    "금융세는 예금 이자나 투자 수익처럼 금융 활동으로 번 돈의 일부를 함께 모으는 세금이에요.\n모인 금융세는 학급 공동기금에 더해져요!",
                    "금융 활동으로 번 돈 - 금융세 → 실제로 받은 돈"),
            page(152, "INFLATION", "📈", "인플레이션이 뭐예요?",
                    "인플레이션은 물건값이 점점 오르는 거예요.\n그래서 같은 돈으로 살 수 있는 것이 줄어들어요.\n쉽게 말해, 돈의 힘이 조금 약해지는 거예요!",
                    "같은 돈으로는 전보다 살 수 있는 게 줄어요")
    );

    private static final Map<String, Set<String>> STAGE_TOPICS = Map.of(
            "DAY1_KNOWLEDGE_GUIDED", Set.of("INCOME_AND_TAX"),
            "DAY1_SHOP_GUIDED", Set.of("SMART_SPENDING"),
            "DAY1_FINANCE_GUIDED", Set.of("DEPOSIT", "INVESTMENT", "INFLATION"),
            "DAY2_TAX_INFLATION_GUIDED", Set.of("DAY2_FINANCIAL_KNOWLEDGE", "INFLATION")
    );

    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final DaySessionRepository sessionRepository;
    private final ModuleStateRepository moduleStateRepository;
    private final StudentFlowService studentFlowService;

    public LearningService(
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            DaySessionRepository sessionRepository,
            ModuleStateRepository moduleStateRepository,
            StudentFlowService studentFlowService
    ) {
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.moduleStateRepository = moduleStateRepository;
        this.studentFlowService = studentFlowService;
    }

    @Transactional(readOnly = true)
    public GuidedLearningResponse teacherCurrent(UUID teacherId, UUID roomId) {
        RoomEntity room = findRoom(roomId);
        ensureOwner(room, teacherId);
        return guidedResponse(room);
    }

    @Transactional
    public GuidedLearningResponse changePage(
            UUID teacherId,
            UUID roomId,
            ChangePageRequest request
    ) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        if (room.getVersion() != request.expectedVersion()) {
            throw stateConflict("다른 화면에서 교육 페이지가 변경되었습니다. 최신 상태를 확인해 주세요.");
        }

        List<PageDefinition> pages = pagesForStage(room.getCurrentStage());
        PageDefinition target = pages.stream()
                .filter(page -> page.id().equals(request.pageId()))
                .findFirst()
                .orElseThrow(() -> stateConflict("현재 교육 단계에서 볼 수 없는 페이지입니다."));
        UUID sessionId = sessionRepository
                .findByRoomIdAndDayNumber(roomId, (short) room.getCurrentDay())
                .orElseThrow(() -> stateConflict("현재 일차 수업을 찾을 수 없습니다."))
                .getId();
        ModuleStateEntity moduleState = moduleStateRepository
                .findBySessionIdAndModuleType(sessionId, "KNOWLEDGE")
                .orElseGet(() -> ModuleStateEntity.guided(
                        sessionId,
                        target.id(),
                        target.order(),
                        teacherId
                ));
        moduleState.changePage(target.id(), target.order(), teacherId);
        moduleStateRepository.save(moduleState);
        room.markControlStateChanged();
        return guidedResponse(room, pages, target.id());
    }

    @Transactional(readOnly = true)
    public GuidedLearningResponse studentCurrent(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        return guidedResponse(
                room,
                studentFlowService.effectiveStage(studentId, room.getCurrentStage())
        );
    }

    @Transactional(readOnly = true)
    public List<TopicResponse> studentTopics(UUID studentId) {
        RoomEntity room = roomForFreeReview(studentId);
        if (room.getCurrentDay() != 1) {
            throw learningNotAvailable();
        }
        return TOPICS.stream()
                .filter(topic -> topic.dayNumber() == 1)
                .map(this::toTopicResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TopicDetailResponse studentTopic(UUID studentId, UUID topicId) {
        roomForFreeReview(studentId);
        TopicDefinition topic = TOPICS.stream()
                .filter(item -> item.dayNumber() == 1 && item.id().equals(topicId))
                .findFirst()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "LEARNING_TOPIC_NOT_FOUND",
                        "금융지식 주제를 찾을 수 없습니다."
                ));
        return new TopicDetailResponse(
                topic.id(),
                topic.code(),
                topic.title(),
                topic.description(),
                PAGES.stream().filter(page -> page.topicCode().equals(topic.code()))
                        .map(this::toPageResponse).toList()
        );
    }

    private GuidedLearningResponse guidedResponse(RoomEntity room) {
        return guidedResponse(room, room.getCurrentStage());
    }

    private GuidedLearningResponse guidedResponse(RoomEntity room, String stage) {
        List<PageDefinition> pages = pagesForStage(stage);
        UUID sessionId = sessionRepository
                .findByRoomIdAndDayNumber(
                        room.getId(),
                        RoomStage.find(stage)
                                .map(value -> (short) value.day())
                                .orElse((short) room.getCurrentDay())
                )
                .orElseThrow(() -> stateConflict("현재 일차 수업을 찾을 수 없습니다."))
                .getId();
        UUID currentPageId = moduleStateRepository
                .findBySessionIdAndModuleType(sessionId, "KNOWLEDGE")
                .map(ModuleStateEntity::getCurrentPageId)
                .filter(id -> pages.stream().anyMatch(page -> page.id().equals(id)))
                .orElse(pages.getFirst().id());
        return guidedResponse(room, stage, pages, currentPageId);
    }

    private GuidedLearningResponse guidedResponse(
            RoomEntity room,
            List<PageDefinition> pages,
            UUID currentPageId
    ) {
        return guidedResponse(room, room.getCurrentStage(), pages, currentPageId);
    }

    private GuidedLearningResponse guidedResponse(
            RoomEntity room,
            String stage,
            List<PageDefinition> pages,
            UUID currentPageId
    ) {
        int currentIndex = 0;
        for (int index = 0; index < pages.size(); index += 1) {
            if (pages.get(index).id().equals(currentPageId)) {
                currentIndex = index;
                break;
            }
        }
        List<PageResponse> responses = pages.stream().map(this::toPageResponse).toList();
        return new GuidedLearningResponse(
                stage,
                RoomStage.find(stage).map(RoomStage::mode).orElse(room.getMode()),
                room.getSyncVersion(),
                currentIndex,
                responses,
                responses.get(currentIndex)
        );
    }

    private List<PageDefinition> pagesForStage(String stage) {
        Set<String> topicCodes = STAGE_TOPICS.get(stage);
        if (topicCodes == null) {
            throw learningNotAvailable();
        }
        return PAGES.stream().filter(page -> topicCodes.contains(page.topicCode())).toList();
    }

    private RoomEntity roomForFreeReview(UUID studentId) {
        StudentEntity student = findActiveStudent(studentId);
        RoomEntity room = findRoom(student.getRoomId());
        boolean catchUpReview = studentFlowService.canUseStage(studentId, "DAY1_FREE_ACTIVITY");
        if ((!"DAY1_FREE_ACTIVITY".equals(room.getCurrentStage())
                || !"FREE".equals(room.getMode())) && !catchUpReview) {
            throw learningNotAvailable();
        }
        return room;
    }

    private StudentEntity findActiveStudent(UUID studentId) {
        return studentRepository.findById(studentId)
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED",
                        "로그인이 필요합니다."
                ));
    }

    private RoomEntity findRoom(UUID roomId) {
        return roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
    }

    private void ensureOwner(RoomEntity room, UUID teacherId) {
        if (!room.getTeacherId().equals(teacherId)) {
            throw roomNotFound();
        }
    }

    private TopicResponse toTopicResponse(TopicDefinition topic) {
        int pageCount = Math.toIntExact(PAGES.stream()
                .filter(page -> page.topicCode().equals(topic.code()))
                .count());
        return new TopicResponse(topic.id(), topic.code(), topic.title(), topic.description(), pageCount);
    }

    private PageResponse toPageResponse(PageDefinition page) {
        TopicDefinition topic = TOPICS.stream()
                .filter(item -> item.code().equals(page.topicCode()))
                .findFirst()
                .orElseThrow();
        return new PageResponse(
                page.id(),
                topic.code(),
                topic.title(),
                page.order(),
                page.title(),
                page.content(),
                page.keyPoints(),
                page.icon(),
                page.example()
        );
    }

    private ApiException learningNotAvailable() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "LEARNING_NOT_AVAILABLE",
                "현재 단계에서는 이 금융지식 페이지를 볼 수 없습니다."
        );
    }

    private ApiException stateConflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", message);
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private static UUID id(int suffix) {
        return UUID.fromString("80000000-0000-0000-0000-%012d".formatted(suffix));
    }

    private static PageDefinition page(
            int suffix,
            String topicCode,
            String icon,
            String title,
            String content,
            String example,
            String... keyPoints
    ) {
        return new PageDefinition(
                id(suffix), topicCode, suffix % 10, title, content, List.of(keyPoints), icon, example
        );
    }

    private record TopicDefinition(
            UUID id,
            String code,
            String title,
            String description,
            int dayNumber
    ) {
    }

    private record PageDefinition(
            UUID id,
            String topicCode,
            int order,
            String title,
            String content,
            List<String> keyPoints,
            String icon,
            String example
    ) {
    }
}
