package com.b201.ecovillage.trade.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.domain.ClassroomStudent;
import com.b201.ecovillage.identity.domain.ClassroomStudentStatus;
import com.b201.ecovillage.identity.repository.ClassroomStudentRepository;
import com.b201.ecovillage.trade.domain.Trade;
import com.b201.ecovillage.trade.domain.TradeMoneyLeg;
import com.b201.ecovillage.trade.domain.TradeStatus;
import com.b201.ecovillage.trade.repository.TradeItemLegRepository;
import com.b201.ecovillage.trade.repository.TradeMoneyLegRepository;
import com.b201.ecovillage.trade.repository.TradeRepository;

/**
 * 교사 승인을 기다리는 학생 간 거래 목록이다.
 *
 * <p>지금까지는 거래를 만들고 승인하는 명령만 있고 목록을 읽는 길이 없었다. 그래서 학생이
 * 거래를 걸어도 교사 화면에는 아무것도 뜨지 않았다. 승인·정산 화면이 이 목록을 읽는다.
 */
@Service
public class PendingTradeQueryService {

    private final ClassroomAuthorizationService authorizationService;
    private final TradeRepository tradeRepository;
    private final TradeMoneyLegRepository moneyLegRepository;
    private final TradeItemLegRepository itemLegRepository;
    private final ClassroomStudentRepository studentRepository;

    public PendingTradeQueryService(
        ClassroomAuthorizationService authorizationService,
        TradeRepository tradeRepository,
        TradeMoneyLegRepository moneyLegRepository,
        TradeItemLegRepository itemLegRepository,
        ClassroomStudentRepository studentRepository
    ) {
        this.authorizationService = authorizationService;
        this.tradeRepository = tradeRepository;
        this.moneyLegRepository = moneyLegRepository;
        this.itemLegRepository = itemLegRepository;
        this.studentRepository = studentRepository;
    }

    /** 교사가 이미 승인하거나 거절한 거래다. */
    @Transactional(readOnly = true)
    public List<PendingTradeView> findDecided(String teacherSubject, UUID classroomId) {
        return findByStatuses(teacherSubject, classroomId, List.of(
            TradeStatus.COMPLETED, TradeStatus.REJECTED, TradeStatus.SETTLING, TradeStatus.FAILED
        ));
    }

    @Transactional(readOnly = true)
    public List<PendingTradeView> findAwaitingTeacher(String teacherSubject, UUID classroomId) {
        return findByStatuses(teacherSubject, classroomId, List.of(TradeStatus.AWAITING_TEACHER));
    }

    @Transactional(readOnly = true)
    public List<PendingTradeView> findMine(String studentSubject, UUID classroomId) {
        UUID studentId = parseStudentId(studentSubject);
        authorizationService.authorize(
            studentSubject,
            classroomId,
            ClassroomPermission.DECIDE_TRADE,
            studentId
        );
        return toViews(
            classroomId,
            tradeRepository.findAllForStudent(classroomId, studentId)
        );
    }

    @Transactional(readOnly = true)
    public List<TradeClassmateView> findClassmates(String studentSubject, UUID classroomId) {
        UUID studentId = parseStudentId(studentSubject);
        authorizationService.authorize(
            studentSubject,
            classroomId,
            ClassroomPermission.PROPOSE_TRADE,
            studentId
        );
        return studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(
                classroomId,
                ClassroomStudentStatus.ACTIVE
            )
            .stream()
            .filter(student -> !student.getId().equals(studentId))
            .map(student -> new TradeClassmateView(
                student.getId(),
                student.getRosterNumber(),
                student.getNickname()
            ))
            .toList();
    }

    private List<PendingTradeView> findByStatuses(String teacherSubject, UUID classroomId, List<TradeStatus> statuses) {
        authorizationService.authorize(
            teacherSubject,
            classroomId,
            ClassroomPermission.APPROVE_TRADE,
            null
        );
        return toViews(
            classroomId,
            tradeRepository.findAllByClassroomIdAndStatusInOrderByCreatedAtDesc(
                classroomId,
                statuses
            )
        );
    }

    private List<PendingTradeView> toViews(UUID classroomId, List<Trade> trades) {
        if (trades.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> nicknames = new HashMap<>();
        for (ClassroomStudent student : studentRepository
            .findAllByClassroomIdAndStatusOrderByRosterNumber(classroomId, ClassroomStudentStatus.ACTIVE)) {
            nicknames.put(student.getId(), student.getNickname());
        }
        return trades.stream()
            .map(trade -> new PendingTradeView(
                trade.getId(),
                trade.getProposerStudentId(),
                nicknames.getOrDefault(trade.getProposerStudentId(), "학생"),
                trade.getCounterpartStudentId(),
                nicknames.getOrDefault(trade.getCounterpartStudentId(), "학생"),
                trade.getDescription(),
                totalAmount(trade.getId()),
                itemLegRepository.findAllByTradeIdOrderByInventoryItemId(trade.getId()).size(),
                trade.getStatus().name(),
                trade.getStateVersion(),
                trade.getCreatedAt()
            ))
            .toList();
    }

    private static UUID parseStudentId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new ClassroomOperationException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_REQUIRED",
                "A valid student session is required."
            );
        }
    }

    /** 거래로 오가는 돈이다. 방향은 화면에 "제안자 → 상대"로 이미 드러나므로 합계만 준다. */
    private long totalAmount(UUID tradeId) {
        return moneyLegRepository.findAllByTradeIdOrderByFromStudentId(tradeId).stream()
            .mapToLong(TradeMoneyLeg::getAmount)
            .sum();
    }
}
