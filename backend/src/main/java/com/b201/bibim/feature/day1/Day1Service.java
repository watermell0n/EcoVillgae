package com.b201.bibim.feature.day1;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.deposit.DepositEntity;
import com.b201.bibim.domain.deposit.DepositRepository;
import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.finance.FinancialTransactionRepository;
import com.b201.bibim.domain.investment.InvestmentAllocationRepository;
import com.b201.bibim.domain.investment.InvestmentAssetEntity;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.domain.investment.InvestmentOrderEntity;
import com.b201.bibim.domain.investment.InvestmentOrderRepository;
import com.b201.bibim.domain.room.DaySessionEntity;
import com.b201.bibim.domain.room.DaySessionRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.PurchaseEntity;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.day1.Day1Dto.ClosePreviewResponse;
import com.b201.bibim.feature.day1.Day1Dto.InvestmentAllocationSummaryResponse;
import com.b201.bibim.feature.day1.Day1Dto.PurchaseSummaryResponse;
import com.b201.bibim.feature.day1.Day1Dto.StudentDay1SummaryResponse;
import com.b201.bibim.feature.day1.Day1Dto.TeacherDay1SummaryResponse;
import com.b201.bibim.feature.day1.Day1Dto.TeacherStudentDay1Response;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Day1Service {

    private static final Set<String> SUMMARY_STAGES = Set.of("DAY1_SUMMARY", "DAY1_CLOSED");
    private static final Set<String> TEACHER_SUMMARY_STAGES = Set.of(
            "DAY1_FREE_ACTIVITY", "DAY1_SUMMARY", "DAY1_CLOSED"
    );

    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final DaySessionRepository sessionRepository;
    private final SettlementRunRepository settlementRunRepository;
    private final AccountRepository accountRepository;
    private final DepositRepository depositRepository;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAllocationRepository allocationRepository;
    private final InvestmentAssetRepository assetRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final FinancialTransactionRepository transactionRepository;

    public Day1Service(
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            DaySessionRepository sessionRepository,
            SettlementRunRepository settlementRunRepository,
            AccountRepository accountRepository,
            DepositRepository depositRepository,
            InvestmentOrderRepository orderRepository,
            InvestmentAllocationRepository allocationRepository,
            InvestmentAssetRepository assetRepository,
            PurchaseRepository purchaseRepository,
            ProductRepository productRepository,
            FinancialTransactionRepository transactionRepository
    ) {
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.sessionRepository = sessionRepository;
        this.settlementRunRepository = settlementRunRepository;
        this.accountRepository = accountRepository;
        this.depositRepository = depositRepository;
        this.orderRepository = orderRepository;
        this.allocationRepository = allocationRepository;
        this.assetRepository = assetRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public StudentDay1SummaryResponse studentSummary(UUID studentId) {
        StudentEntity student = studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(this::authenticationRequired);
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureSummaryAvailable(room);
        return buildStudentSummary(room, student);
    }

    @Transactional(readOnly = true)
    public TeacherDay1SummaryResponse teacherSummary(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensureTeacherSummaryAvailable(room);
        List<TeacherStudentDay1Response> students = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(roomId)
                .stream()
                .map(student -> toTeacherStudent(room, student, buildStudentSummary(room, student)))
                .toList();
        return new TeacherDay1SummaryResponse(
                roomId,
                room.getCurrentStage(),
                students.size(),
                sum(students, TeacherStudentDay1Response::walletBalance),
                sum(students, TeacherStudentDay1Response::depositPrincipal),
                sum(students, TeacherStudentDay1Response::investmentPrincipal),
                sum(students, TeacherStudentDay1Response::purchaseTotal),
                students.stream().filter(TeacherStudentDay1Response::depositConfirmed).count(),
                students.stream().filter(TeacherStudentDay1Response::investmentConfirmed).count(),
                students.stream().filter(TeacherStudentDay1Response::purchased).count(),
                students.stream().filter(TeacherStudentDay1Response::inactive).count(),
                students
        );
    }

    @Transactional(readOnly = true)
    public ClosePreviewResponse closePreview(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        if (!"DAY1_SUMMARY".equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY1_CLOSE_NOT_OPEN",
                    "1일차 요약 단계에서 마감할 수 있습니다."
            );
        }
        return buildClosePreview(room);
    }

    @Transactional
    public void closeDayOne(UUID teacherId, RoomEntity room) {
        if (!room.getTeacherId().equals(teacherId)) {
            throw roomNotFound();
        }
        if (!"DAY1_SUMMARY".equals(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY1_CLOSE_NOT_OPEN",
                    "1일차 요약 단계에서만 마감할 수 있습니다."
            );
        }
        ClosePreviewResponse preview = buildClosePreview(room);
        if (!preview.canClose()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY1_CLOSE_VALIDATION_FAILED",
                    "필수 정산이나 처리 중인 거래를 먼저 확인해 주세요."
            );
        }
        depositRepository.findAllByRoomId(room.getId()).stream()
                .filter(deposit -> "DRAFT".equals(deposit.getStatus()))
                .forEach(DepositEntity::cancelDraft);
        orderRepository.findAllByRoomId(room.getId()).stream()
                .filter(order -> "DRAFT".equals(order.getStatus()))
                .forEach(InvestmentOrderEntity::cancelDraft);
        DaySessionEntity session = dayOneSession(room.getId());
        session.closeDayOne();
        room.closeDayOne();
    }

    private ClosePreviewResponse buildClosePreview(RoomEntity room) {
        boolean incomeCompleted = settlementRunRepository
                .existsByRoomIdAndSettlementTypeAndStatus(
                        room.getId(), "DAY1_INCOME", "COMPLETED"
                );
        long depositDraftCount = depositRepository.countByRoomIdAndStatus(room.getId(), "DRAFT");
        long investmentDraftCount = orderRepository.countByRoomIdAndStatus(room.getId(), "DRAFT");
        boolean pendingTransaction = transactionRepository.existsByRoomIdAndStatus(
                room.getId(), "PENDING"
        );
        List<String> warnings = new ArrayList<>();
        if (!incomeCompleted) warnings.add("1일차 소득 정산이 완료되지 않았습니다.");
        if (pendingTransaction) warnings.add("처리 중인 금융 거래가 있습니다.");
        if (depositDraftCount > 0) warnings.add("미확정 예금 초안은 마감 시 자동 취소됩니다.");
        if (investmentDraftCount > 0) warnings.add("미확정 투자 초안은 마감 시 자동 취소됩니다.");
        return new ClosePreviewResponse(
                room.getId(),
                incomeCompleted && !pendingTransaction,
                incomeCompleted,
                depositDraftCount,
                investmentDraftCount,
                pendingTransaction,
                warnings
        );
    }

    private StudentDay1SummaryResponse buildStudentSummary(RoomEntity room, StudentEntity student) {
        DaySessionEntity session = dayOneSession(room.getId());
        List<DepositEntity> deposits = depositRepository
                .findAllBySessionIdAndStudentIdAndStatus(
                        session.getId(), student.getId(), "CONFIRMED"
                );
        List<InvestmentOrderEntity> orders = orderRepository
                .findAllBySessionIdAndStudentIdAndStatus(
                        session.getId(), student.getId(), "CONFIRMED"
                );
        Map<UUID, InvestmentAssetEntity> assetsById = assetRepository
                .findAllByRoomIdOrderByDisplayOrder(room.getId())
                .stream()
                .collect(Collectors.toMap(InvestmentAssetEntity::getId, Function.identity()));
        Map<UUID, Long> allocationTotals = orders.stream()
                .flatMap(order -> allocationRepository.findAllByOrderId(order.getId()).stream())
                .collect(Collectors.groupingBy(
                        allocation -> allocation.getAssetId(),
                        Collectors.summingLong(allocation -> allocation.getAmount())
                ));
        List<InvestmentAllocationSummaryResponse> investments = allocationTotals.entrySet().stream()
                .map(entry -> {
                    InvestmentAssetEntity asset = assetsById.get(entry.getKey());
                    return new InvestmentAllocationSummaryResponse(
                            entry.getKey(),
                            asset == null ? "알 수 없는 투자처" : asset.getName(),
                            asset == null ? "UNKNOWN" : asset.getRiskLevel(),
                            entry.getValue()
                    );
                })
                .toList();
        Map<UUID, ProductEntity> productsById = productRepository
                .findAllByRoomIdOrderByDisplayOrder(room.getId())
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        List<PurchaseSummaryResponse> purchases = purchaseRepository
                .findAllByStudentIdOrderByCreatedAtDesc(student.getId())
                .stream()
                .map(purchase -> new PurchaseSummaryResponse(
                        purchase.getId(),
                        productName(productsById, purchase),
                        purchase.getQuantity(),
                        purchase.getTotalPrice()
                ))
                .toList();
        long wallet = balance(room.getId(), student.getId(), "STUDENT_WALLET");
        long depositPrincipal = deposits.stream().mapToLong(DepositEntity::getPrincipal).sum();
        long interest = deposits.stream().mapToLong(DepositEntity::getExpectedInterest).sum();
        long investmentPrincipal = orders.stream()
                .mapToLong(InvestmentOrderEntity::getTotalAmount)
                .sum();
        long purchaseTotal = purchases.stream().mapToLong(PurchaseSummaryResponse::totalPrice).sum();
        return new StudentDay1SummaryResponse(
                student.getId(),
                student.getParticipantNumber(),
                student.getNickname(),
                room.getCurrentStage(),
                wallet,
                depositPrincipal,
                interest,
                investmentPrincipal,
                purchaseTotal,
                Math.addExact(wallet, Math.addExact(depositPrincipal, investmentPrincipal)),
                investments,
                purchases
        );
    }

    private TeacherStudentDay1Response toTeacherStudent(
            RoomEntity room,
            StudentEntity student,
            StudentDay1SummaryResponse summary
    ) {
        DaySessionEntity session = dayOneSession(room.getId());
        DepositEntity depositDraft = depositRepository
                .findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
                        session.getId(), student.getId(), "DRAFT"
                )
                .orElse(null);
        InvestmentOrderEntity investmentDraft = orderRepository
                .findFirstBySessionIdAndStudentIdAndStatusOrderByUpdatedAtDesc(
                        session.getId(), student.getId(), "DRAFT"
                )
                .orElse(null);
        boolean depositConfirmed = summary.depositPrincipal() > 0;
        boolean investmentConfirmed = summary.investmentPrincipal() > 0;
        boolean purchased = !summary.purchases().isEmpty();
        List<InvestmentAllocationSummaryResponse> visibleInvestments = investmentDraft == null
                ? summary.investments()
                : allocationRepository.findAllByOrderId(investmentDraft.getId()).stream()
                        .map(allocation -> {
                            InvestmentAssetEntity asset = assetRepository.findById(allocation.getAssetId()).orElse(null);
                            return new InvestmentAllocationSummaryResponse(
                                    allocation.getAssetId(),
                                    asset == null ? "알 수 없는 투자처" : asset.getName(),
                                    asset == null ? "UNKNOWN" : asset.getRiskLevel(),
                                    allocation.getAmount()
                            );
                        })
                        .toList();
        String depositStatus = depositDraft != null && depositDraft.getPrincipal() > 0
                ? "DRAFT" : depositConfirmed ? "CONFIRMED" : "NONE";
        String investmentStatus = investmentDraft != null && investmentDraft.getTotalAmount() > 0
                ? "DRAFT" : investmentConfirmed ? "CONFIRMED" : "NONE";
        return new TeacherStudentDay1Response(
                summary.studentId(), summary.participantNumber(), summary.nickname(),
                summary.walletBalance(), summary.depositPrincipal(), summary.investmentPrincipal(),
                summary.purchaseTotal(), depositConfirmed, investmentConfirmed, purchased,
                "NONE".equals(depositStatus) && "NONE".equals(investmentStatus) && !purchased,
                depositStatus, depositDraft == null ? 0 : depositDraft.getPrincipal(),
                investmentStatus, investmentDraft == null ? 0 : investmentDraft.getTotalAmount(),
                visibleInvestments
        );
    }

    private String productName(Map<UUID, ProductEntity> productsById, PurchaseEntity purchase) {
        ProductEntity product = productsById.get(purchase.getProductId());
        return product == null ? "삭제된 상품" : product.getName();
    }

    private long balance(UUID roomId, UUID studentId, String accountType) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(
                roomId, studentId, accountType
        ).map(AccountEntity::getBalance).orElse(0L);
    }

    private DaySessionEntity dayOneSession(UUID roomId) {
        return sessionRepository.findByRoomIdAndDayNumber(roomId, (short) 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "SESSION_NOT_FOUND", "1일차 수업을 찾을 수 없습니다."
                ));
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
        if (!room.getTeacherId().equals(teacherId)) throw roomNotFound();
        return room;
    }

    private void ensureSummaryAvailable(RoomEntity room) {
        if (!SUMMARY_STAGES.contains(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY1_SUMMARY_NOT_OPEN",
                    "1일차 요약 또는 마감 단계에서 결과를 확인할 수 있습니다."
            );
        }
    }

    private void ensureTeacherSummaryAvailable(RoomEntity room) {
        if (!TEACHER_SUMMARY_STAGES.contains(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "DAY1_SUMMARY_NOT_OPEN",
                    "자유 경제활동 이후 학생 현황을 확인할 수 있습니다."
            );
        }
    }

    private long sum(
            List<TeacherStudentDay1Response> students,
            java.util.function.ToLongFunction<TeacherStudentDay1Response> getter
    ) {
        return students.stream().mapToLong(getter).sum();
    }

    private ApiException authenticationRequired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }
}
