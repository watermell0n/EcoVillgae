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

    private static final List<TopicDefinition> TOPICS = List.of(
            new TopicDefinition(
                    INCOME_TOPIC_ID,
                    "INCOME_AND_TAX",
                    "소득과 세금",
                    "일해서 얻는 소득과 함께 쓰는 세금의 역할을 알아봐요."
            ),
            new TopicDefinition(
                    SHOP_TOPIC_ID,
                    "SMART_SPENDING",
                    "합리적인 소비",
                    "가격, 필요, 남은 돈을 비교해 현명하게 소비해요."
            ),
            new TopicDefinition(
                    DEPOSIT_TOPIC_ID,
                    "DEPOSIT",
                    "예금과 이자",
                    "돈을 안전하게 맡기고 이자를 받는 방법을 알아봐요."
            ),
            new TopicDefinition(
                    INVESTMENT_TOPIC_ID,
                    "INVESTMENT",
                    "투자와 위험",
                    "기대 수익과 손실 위험, 분산투자의 원리를 알아봐요."
            )
    );

    private static final List<PageDefinition> PAGES = List.of(
            page(111, "INCOME_AND_TAX", "소득은 어디에서 올까요?",
                    "맡은 역할의 일을 하고 받는 돈을 소득이라고 해요.",
                    "일의 대가로 받는 돈이에요.", "총소득은 여러 소득을 모두 더한 값이에요."),
            page(112, "INCOME_AND_TAX", "세금은 함께 쓰는 돈이에요",
                    "소득의 일부를 세금으로 모아 학급 모두에게 필요한 곳에 사용해요.",
                    "세금은 공동체를 위해 사용돼요.", "비빔에서는 소득세가 공동기금에 쌓여요."),
            page(121, "SMART_SPENDING", "필요와 원하는 것을 구분해요",
                    "구매 전 꼭 필요한지, 가격은 알맞은지, 사고 난 뒤 돈이 얼마나 남는지 확인해요.",
                    "필요한 것부터 선택해요.", "가격과 내 지갑 잔액을 함께 비교해요."),
            page(122, "SMART_SPENDING", "가격과 재고를 확인해요",
                    "상품의 가격과 남은 수량은 한정되어 있으므로 구매 전에 정보를 살펴봐요.",
                    "재고가 없으면 살 수 없어요.", "한 번의 선택이 다음 소비 기회에 영향을 줘요."),
            page(131, "DEPOSIT", "예금은 안전하게 보관하는 방법이에요",
                    "일정 기간 돈을 맡기면 약속한 이자를 받을 수 있어요.",
                    "원금과 받을 이자를 확인해요.", "기간 동안 바로 쓰기 어려울 수 있어요."),
            page(132, "DEPOSIT", "이자는 기다린 대가예요",
                    "맡긴 금액과 기간, 이자율에 따라 받는 이자가 달라져요.",
                    "이자율이 같으면 맡긴 돈이 클수록 이자가 커져요.", "상품 조건을 비교해야 해요."),
            page(141, "INVESTMENT", "투자는 수익과 손실이 함께 있어요",
                    "더 큰 수익을 기대할 수 있지만 원금보다 돈이 줄어들 수도 있어요.",
                    "기대 수익만 보고 선택하지 않아요.", "감당할 수 있는 손실인지 생각해요."),
            page(142, "INVESTMENT", "나누어 투자하면 위험을 줄일 수 있어요",
                    "투자금을 여러 대상에 나누면 한 곳의 손실이 전체에 주는 영향을 줄일 수 있어요.",
                    "한 곳에 모두 투자하지 않아요.", "예금과 투자도 목적에 맞게 나눌 수 있어요.")
    );

    private static final Map<String, Set<String>> STAGE_TOPICS = Map.of(
            "DAY1_KNOWLEDGE_GUIDED", Set.of("INCOME_AND_TAX", "DEPOSIT", "INVESTMENT"),
            "DAY1_SHOP_GUIDED", Set.of("SMART_SPENDING"),
            "DAY1_FINANCE_GUIDED", Set.of("DEPOSIT", "INVESTMENT")
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
        return TOPICS.stream().map(this::toTopicResponse).toList();
    }

    @Transactional(readOnly = true)
    public TopicDetailResponse studentTopic(UUID studentId, UUID topicId) {
        roomForFreeReview(studentId);
        TopicDefinition topic = TOPICS.stream()
                .filter(item -> item.id().equals(topicId))
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
                page.keyPoints()
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
            String title,
            String content,
            String... keyPoints
    ) {
        return new PageDefinition(id(suffix), topicCode, suffix % 10, title, content, List.of(keyPoints));
    }

    private record TopicDefinition(
            UUID id,
            String code,
            String title,
            String description
    ) {
    }

    private record PageDefinition(
            UUID id,
            String topicCode,
            int order,
            String title,
            String content,
            List<String> keyPoints
    ) {
    }
}
