package com.b201.bibim.feature.result;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.fund.FundExpenseRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.settlement.FinancialSettlementItemEntity;
import com.b201.bibim.domain.settlement.FinancialSettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementItemEntity;
import com.b201.bibim.domain.settlement.SettlementItemRepository;
import com.b201.bibim.domain.settlement.SettlementRunEntity;
import com.b201.bibim.domain.settlement.SettlementRunRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.shop.PurchaseEntity;
import com.b201.bibim.domain.shop.PurchaseRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.domain.vote.VoteEntity;
import com.b201.bibim.domain.vote.VoteOptionEntity;
import com.b201.bibim.domain.vote.VoteOptionRepository;
import com.b201.bibim.domain.vote.VoteRepository;
import com.b201.bibim.domain.vote.VoteSessionEntity;
import com.b201.bibim.domain.vote.VoteSessionRepository;
import com.b201.bibim.feature.result.FinalResultDto.ProductDistributionItemResponse;
import com.b201.bibim.feature.result.FinalResultDto.ProductDistributionResponse;
import com.b201.bibim.feature.result.FinalResultDto.PurchaseResultResponse;
import com.b201.bibim.feature.result.FinalResultDto.StudentFinalResultResponse;
import com.b201.bibim.feature.result.FinalResultDto.TeacherFinalSummaryResponse;
import com.b201.bibim.feature.result.FinalResultDto.TeacherStudentResultResponse;
import com.b201.bibim.feature.result.FinalResultDto.VoteDecisionResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinalResultService {

    private static final List<String> RESULT_STAGES = List.of(
            "DAY2_FINAL_RESULT", "DAY2_SURVEY", "DAY2_COMPLETED"
    );

    private final RoomRepository roomRepository;
    private final StudentRepository studentRepository;
    private final AccountRepository accountRepository;
    private final SettlementRunRepository runRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final FinancialSettlementItemRepository financialItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final FundExpenseRepository expenseRepository;
    private final VoteSessionRepository voteSessionRepository;
    private final VoteOptionRepository voteOptionRepository;
    private final VoteRepository voteRepository;
    private final String surveyUrl;

    public FinalResultService(
            RoomRepository roomRepository,
            StudentRepository studentRepository,
            AccountRepository accountRepository,
            SettlementRunRepository runRepository,
            SettlementItemRepository settlementItemRepository,
            FinancialSettlementItemRepository financialItemRepository,
            PurchaseRepository purchaseRepository,
            ProductRepository productRepository,
            FundExpenseRepository expenseRepository,
            VoteSessionRepository voteSessionRepository,
            VoteOptionRepository voteOptionRepository,
            VoteRepository voteRepository,
            @Value("${app.survey.url}") String surveyUrl
    ) {
        this.roomRepository = roomRepository;
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.runRepository = runRepository;
        this.settlementItemRepository = settlementItemRepository;
        this.financialItemRepository = financialItemRepository;
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.expenseRepository = expenseRepository;
        this.voteSessionRepository = voteSessionRepository;
        this.voteOptionRepository = voteOptionRepository;
        this.voteRepository = voteRepository;
        this.surveyUrl = surveyUrl;
    }

    @Transactional(readOnly = true)
    public StudentFinalResultResponse studentResult(UUID studentId) {
        StudentEntity student = studentRepository.findById(studentId)
                .filter(item -> "ACTIVE".equals(item.getStatus()))
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."
                ));
        RoomEntity room = roomRepository.findById(student.getRoomId()).orElseThrow(this::roomNotFound);
        ensureResultAvailable(room);
        return buildStudentResult(room, student, products(room.getId()), voteDecision(room.getId()));
    }

    @Transactional(readOnly = true)
    public TeacherFinalSummaryResponse teacherSummary(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensureResultAvailable(room);
        Map<UUID, ProductEntity> products = products(roomId);
        VoteDecisionResponse vote = voteDecision(roomId);
        List<StudentFinalResultResponse> results = studentRepository
                .findAllByRoomIdOrderByParticipantNumber(roomId).stream()
                .filter(student -> "ACTIVE".equals(student.getStatus()))
                .map(student -> buildStudentResult(room, student, products, vote))
                .toList();
        List<TeacherStudentResultResponse> students = results.stream()
                .map(result -> new TeacherStudentResultResponse(
                        result.studentId(), result.participantNumber(), result.nickname(), result.grossIncome(),
                        Math.addExact(result.incomeTax(), result.financialTax()), result.depositInterest(),
                        result.investmentProfit(), result.day2QuizReward(), result.totalPurchaseAmount(),
                        result.finalWalletBalance()
                ))
                .toList();
        return new TeacherFinalSummaryResponse(
                roomId, room.getName(), students.size(),
                sum(results, StudentFinalResultResponse::grossIncome),
                sum(results, StudentFinalResultResponse::incomeTax),
                sum(results, StudentFinalResultResponse::depositInterest),
                sum(results, StudentFinalResultResponse::investmentProfit),
                sum(results, StudentFinalResultResponse::financialTax),
                sum(results, StudentFinalResultResponse::day2QuizReward),
                sum(results, StudentFinalResultResponse::totalPurchaseAmount),
                sum(results, StudentFinalResultResponse::finalWalletBalance),
                roomBalance(roomId, "CLASS_FUND"),
                expenseRepository.findByRoomId(roomId).map(item -> item.getAmount()).orElse(0L),
                vote, surveyUrl, students
        );
    }

    @Transactional(readOnly = true)
    public ProductDistributionResponse productDistribution(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensureResultAvailable(room);
        Map<UUID, ProductEntity> products = products(roomId);
        Map<UUID, StudentEntity> students = studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId)
                .stream().collect(Collectors.toMap(StudentEntity::getId, Function.identity()));
        List<ProductDistributionItemResponse> items = purchaseRepository
                .findAllByRoomIdOrderByCreatedAtDesc(roomId).stream()
                .filter(purchase -> "SUCCESS".equals(purchase.getStatus()))
                .map(purchase -> {
                    ProductEntity product = products.get(purchase.getProductId());
                    StudentEntity student = students.get(purchase.getStudentId());
                    return new ProductDistributionItemResponse(
                            purchase.getProductId(), product == null ? "삭제된 상품" : product.getName(),
                            purchase.getStudentId(), student == null ? 0 : student.getParticipantNumber(),
                            student == null ? "알 수 없음" : student.getNickname(), purchase.getQuantity(),
                            purchase.getTotalPrice()
                    );
                })
                .sorted(Comparator.comparing(ProductDistributionItemResponse::productName)
                        .thenComparingInt(ProductDistributionItemResponse::participantNumber))
                .toList();
        return new ProductDistributionResponse(
                roomId, items.stream().mapToInt(ProductDistributionItemResponse::quantity).sum(), items
        );
    }

    private StudentFinalResultResponse buildStudentResult(
            RoomEntity room,
            StudentEntity student,
            Map<UUID, ProductEntity> products,
            VoteDecisionResponse vote
    ) {
        SettlementItemEntity income = settlementItem(room.getId(), "DAY1_INCOME", student.getId());
        SettlementItemEntity quizReward = settlementItem(room.getId(), "DAY2_QUIZ_REWARD", student.getId());
        FinancialSettlementItemEntity finance = financialItem(room.getId(), student.getId());
        List<PurchaseResultResponse> purchases = purchaseRepository
                .findAllByStudentIdOrderByCreatedAtDesc(student.getId()).stream()
                .filter(purchase -> "SUCCESS".equals(purchase.getStatus()))
                .map(purchase -> toPurchase(purchase, products.get(purchase.getProductId())))
                .toList();
        return new StudentFinalResultResponse(
                room.getId(), student.getId(), student.getParticipantNumber(), student.getNickname(),
                income == null ? 0 : income.getGrossIncome(),
                income == null ? 0 : income.getIncomeTax(),
                income == null ? 0 : income.getNetIncome(),
                finance == null ? 0 : finance.getDepositPrincipal(),
                finance == null ? 0 : finance.getDepositInterest(),
                finance == null ? 0 : finance.getInvestmentPrincipal(),
                finance == null ? 0 : finance.getInvestmentProfit(),
                finance == null ? 0 : finance.getFinancialTax(),
                quizReward == null ? 0 : quizReward.getNetIncome(),
                purchases.stream().mapToLong(PurchaseResultResponse::totalPrice).sum(),
                roomBalance(room.getId(), student.getId(), "STUDENT_WALLET"),
                purchases, vote, surveyUrl
        );
    }

    private PurchaseResultResponse toPurchase(PurchaseEntity purchase, ProductEntity product) {
        return new PurchaseResultResponse(
                purchase.getId(), purchase.getProductId(), product == null ? "삭제된 상품" : product.getName(),
                purchase.getQuantity(), purchase.getUnitPrice(), purchase.getTotalPrice(), purchase.getCreatedAt()
        );
    }

    private VoteDecisionResponse voteDecision(UUID roomId) {
        VoteSessionEntity session = voteSessionRepository.findByRoomId(roomId)
                .filter(item -> "DECIDED".equals(item.getStatus()))
                .orElse(null);
        if (session == null) return null;
        VoteOptionEntity option = voteOptionRepository.findById(session.getDecidedOptionId()).orElse(null);
        if (option == null) return null;
        long count = voteRepository.findAllByVoteSessionId(session.getId()).stream()
                .filter(vote -> vote.getOptionId().equals(option.getId())).count();
        return new VoteDecisionResponse(
                session.getId(), option.getId(), option.getTitle(), option.getDescription(), option.getRequiredAmount(),
                count, voteRepository.countByVoteSessionId(session.getId())
        );
    }

    private SettlementItemEntity settlementItem(UUID roomId, String type, UUID studentId) {
        return completedRun(roomId, type)
                .flatMap(run -> settlementItemRepository.findBySettlementRunIdAndStudentId(run.getId(), studentId))
                .orElse(null);
    }

    private FinancialSettlementItemEntity financialItem(UUID roomId, UUID studentId) {
        return completedRun(roomId, "DAY2_FINANCE")
                .flatMap(run -> financialItemRepository.findBySettlementRunIdAndStudentId(run.getId(), studentId))
                .orElse(null);
    }

    private java.util.Optional<SettlementRunEntity> completedRun(UUID roomId, String type) {
        return runRepository.findByRoomIdAndSettlementType(roomId, type)
                .filter(run -> "COMPLETED".equals(run.getStatus()));
    }

    private Map<UUID, ProductEntity> products(UUID roomId) {
        return productRepository.findAllByRoomIdOrderByDisplayOrder(roomId).stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
    }

    private long roomBalance(UUID roomId, String accountType) {
        return accountRepository.findByRoomIdAndStudentIdIsNullAndAccountType(roomId, accountType)
                .map(AccountEntity::getBalance).orElse(0L);
    }

    private long roomBalance(UUID roomId, UUID studentId, String accountType) {
        return accountRepository.findByRoomIdAndStudentIdAndAccountType(roomId, studentId, accountType)
                .map(AccountEntity::getBalance).orElse(0L);
    }

    private long sum(
            List<StudentFinalResultResponse> results,
            java.util.function.ToLongFunction<StudentFinalResultResponse> getter
    ) {
        return results.stream().mapToLong(getter).sum();
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
        if (!room.getTeacherId().equals(teacherId)) throw roomNotFound();
        return room;
    }

    private void ensureResultAvailable(RoomEntity room) {
        if (!RESULT_STAGES.contains(room.getCurrentStage())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "FINAL_RESULT_NOT_OPEN", "최종 결과 단계부터 결과를 확인할 수 있습니다."
            );
        }
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }
}

