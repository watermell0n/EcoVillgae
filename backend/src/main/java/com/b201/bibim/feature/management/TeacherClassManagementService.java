package com.b201.bibim.feature.management;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.b201.bibim.domain.investment.InvestmentAssetEntity;
import com.b201.bibim.domain.finance.AccountEntity;
import com.b201.bibim.domain.finance.AccountRepository;
import com.b201.bibim.domain.investment.InvestmentAssetRepository;
import com.b201.bibim.feature.investment.InvestmentService;
import com.b201.bibim.domain.quiz.QuizQuestionEntity;
import com.b201.bibim.domain.quiz.QuizQuestionRepository;
import com.b201.bibim.domain.quiz.QuizRepository;
import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.domain.student.StudentEntity;
import com.b201.bibim.domain.student.StudentRepository;
import com.b201.bibim.feature.management.TeacherClassManagementDto.AddStudentRequest;
import com.b201.bibim.feature.management.TeacherClassManagementDto.ClassSettingsResponse;
import com.b201.bibim.feature.management.TeacherClassManagementDto.EconomyRules;
import com.b201.bibim.feature.management.TeacherClassManagementDto.ManagedStudent;
import com.b201.bibim.feature.management.TeacherClassManagementDto.UpdateClassSettingsRequest;
import com.b201.bibim.feature.management.TeacherClassManagementDto.UpdateStudentRequest;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeacherClassManagementService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> STUDENT_STATUSES = Set.of("INVITED", "ACTIVE", "ABSENT", "DISABLED");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    private final RoomRepository roomRepository;
    private final RoomRulesRepository rulesRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final InvestmentAssetRepository investmentRepository;
    private final ProductRepository productRepository;
    private final StudentRepository studentRepository;
    private final AccountRepository accountRepository;
    private final InvestmentService investmentService;

    public TeacherClassManagementService(
            RoomRepository roomRepository,
            RoomRulesRepository rulesRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository questionRepository,
            InvestmentAssetRepository investmentRepository,
            ProductRepository productRepository,
            StudentRepository studentRepository,
            AccountRepository accountRepository,
            InvestmentService investmentService
    ) {
        this.roomRepository = roomRepository;
        this.rulesRepository = rulesRepository;
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.investmentRepository = investmentRepository;
        this.productRepository = productRepository;
        this.studentRepository = studentRepository;
        this.accountRepository = accountRepository;
        this.investmentService = investmentService;
    }

    @Transactional
    public ClassSettingsResponse settings(UUID teacherId, UUID roomId) {
        RoomEntity room = ownedRoom(teacherId, roomId);
        investmentService.ensureDefaultAssets(roomId);
        RoomRulesEntity rules = rulesRepository.findByRoomId(roomId)
                .orElseThrow(() -> notFound("수업 규칙을 찾을 수 없습니다."));
        return response(room, rules);
    }

    @Transactional
    public ClassSettingsResponse updateSettings(
            UUID teacherId,
            UUID roomId,
            UpdateClassSettingsRequest request
    ) {
        RoomEntity room = ownedRoomForUpdate(teacherId, roomId);
        if ("COMPLETED".equals(room.getStatus()) || "ARCHIVED".equals(room.getStatus())) {
            throw conflict("종료된 수업의 설정은 변경할 수 없습니다.");
        }
        investmentService.ensureDefaultAssets(roomId);

        EconomyRules economy = request.economy();
        room.rename(request.roomName());
        RoomRulesEntity rules = rulesRepository.findByRoomId(roomId)
                .orElseThrow(() -> notFound("수업 규칙을 찾을 수 없습니다."));
        rules.updateEconomyRules(
                economy.roleIncome(), economy.day1CorrectReward(), economy.incomeTaxBps(),
                economy.depositInterestBps(), economy.financialTaxBps(), economy.day2CorrectReward(),
                economy.inflationBps(), economy.depositMinAmount(), economy.depositUnit(),
                economy.investmentMinAmount(), economy.investmentUnit()
        );
        if (!"PREPARING".equals(room.getStatus()) && economy.initialFundAmount() != rules.getInitialFundAmount()) {
            throw conflict("공동기금 초기 금액은 수업 시작 전에만 변경할 수 있습니다.");
        }
        if ("PREPARING".equals(room.getStatus())) {
            AccountEntity fund = accountRepository.findRoomAccountForUpdate(roomId, "CLASS_FUND")
                    .orElseGet(() -> accountRepository.saveAndFlush(AccountEntity.classFund(roomId)));
            long delta = Math.subtractExact(economy.initialFundAmount(), fund.getBalance());
            if (delta != 0) fund.changeBy(delta);
            rules.updateInitialFundAmount(economy.initialFundAmount());
        }

        for (var quizRequest : request.quizzes()) {
            var quiz = quizRepository.findByRoomIdAndDayNumber(roomId, quizRequest.dayNumber())
                    .orElse(null);
            if (quiz == null) continue;
            if (!"READY".equals(quiz.getStatus())) continue;
            for (var questionRequest : quizRequest.questions()) {
                QuizQuestionEntity question = questionRepository
                        .findByIdAndQuizId(questionRequest.questionId(), quiz.getId())
                        .orElseThrow(() -> notFound("퀴즈 문항을 찾을 수 없습니다."));
                var options = questionRequest.options().stream()
                        .map(option -> new QuizQuestionEntity.QuizOption(option.id(), option.text()))
                        .toList();
                if (options.stream().noneMatch(option -> option.id().equals(questionRequest.correctAnswer()))) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER", "정답은 보기 중 하나여야 합니다.");
                }
                question.updateContent(
                        questionRequest.prompt(), options, questionRequest.correctAnswer(),
                        questionRequest.explanation()
                );
            }
        }

        for (var assetRequest : request.investments()) {
            InvestmentAssetEntity asset = investmentRepository.findById(assetRequest.assetId())
                    .filter(item -> item.getRoomId().equals(roomId))
                    .orElseThrow(() -> notFound("투자 상품을 찾을 수 없습니다."));
            if (!RISK_LEVELS.contains(assetRequest.riskLevel())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RISK", "올바른 위험도를 선택해 주세요.");
            }
            if (assetRequest.minReturnBps() > assetRequest.maxReturnBps()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RETURN_RANGE", "최소 수익률은 최대 수익률보다 클 수 없습니다.");
            }
            asset.updateDetails(
                    assetRequest.name(), assetRequest.description(), assetRequest.companyInfo(),
                    assetRequest.riskLevel(), assetRequest.minReturnBps(), assetRequest.maxReturnBps()
            );
        }

        for (var productRequest : request.products()) {
            ProductEntity product = productRepository.findById(productRequest.productId())
                    .filter(item -> item.getRoomId().equals(roomId))
                    .orElseThrow(() -> notFound("상점 상품을 찾을 수 없습니다."));
            product.updateCatalog(
                    productRequest.name(), productRequest.description(), productRequest.price(), productRequest.imageUrl()
            );
        }

        room.markEconomicStateChanged();
        return response(room, rules);
    }

    @Transactional(readOnly = true)
    public List<ManagedStudent> students(UUID teacherId, UUID roomId) {
        ownedRoom(teacherId, roomId);
        return studentRepository.findAllByRoomIdOrderByParticipantNumber(roomId).stream()
                .map(this::managedStudent)
                .toList();
    }

    @Transactional
    public ManagedStudent addStudent(UUID teacherId, UUID roomId, AddStudentRequest request) {
        RoomEntity room = ownedRoomForUpdate(teacherId, roomId);
        if (studentRepository.existsByRoomIdAndParticipantNumber(roomId, request.participantNumber())) {
            throw conflict("이미 사용 중인 학생 번호입니다.");
        }
        StudentEntity student = StudentEntity.invited(
                roomId,
                request.participantNumber(),
                uniqueParticipationCode(request.participantNumber())
        );
        student.updateProfile(request.participantNumber(), request.nickname());
        room.markControlStateChanged();
        return managedStudent(studentRepository.save(student));
    }

    @Transactional
    public ManagedStudent updateStudent(
            UUID teacherId,
            UUID roomId,
            UUID studentId,
            UpdateStudentRequest request
    ) {
        RoomEntity room = ownedRoomForUpdate(teacherId, roomId);
        if (!STUDENT_STATUSES.contains(request.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDENT_STATUS", "올바른 학생 상태를 선택해 주세요.");
        }
        StudentEntity student = managedStudentForUpdate(roomId, studentId);
        if (student.getParticipantNumber() != request.participantNumber()
                && studentRepository.existsByRoomIdAndParticipantNumber(roomId, request.participantNumber())) {
            throw conflict("이미 사용 중인 학생 번호입니다.");
        }
        student.updateProfile(request.participantNumber(), request.nickname());
        student.changeStatus(request.status());
        room.markControlStateChanged();
        return managedStudent(student);
    }

    @Transactional
    public ManagedStudent resetPassword(UUID teacherId, UUID roomId, UUID studentId) {
        ownedRoomForUpdate(teacherId, roomId);
        StudentEntity student = managedStudentForUpdate(roomId, studentId);
        student.resetPassword();
        return managedStudent(student);
    }

    private ClassSettingsResponse response(RoomEntity room, RoomRulesEntity rules) {
        EconomyRules economy = new EconomyRules(
                rules.getRoleIncome(), rules.getDay1CorrectReward(), rules.getIncomeTaxBps(),
                rules.getDepositInterestBps(), rules.getFinancialTaxBps(), rules.getDay2CorrectReward(),
                rules.getInflationBps(), rules.getDepositMinAmount(), rules.getDepositUnit(),
                rules.getInvestmentMinAmount(), rules.getInvestmentUnit(), rules.getInitialFundAmount()
        );
        var quizzes = List.of((short) 1, (short) 2).stream().map(day -> {
            var quiz = quizRepository.findByRoomIdAndDayNumber(room.getId(), day).orElse(null);
            if (quiz == null) return new TeacherClassManagementDto.QuizSettings(day, "MISSING", List.of());
            var questions = questionRepository.findAllByQuizIdOrderByOrderNo(quiz.getId()).stream()
                    .map(question -> new TeacherClassManagementDto.QuizQuestion(
                            question.getId(), question.getOrderNo(), question.getPrompt(),
                            question.getOptions().stream()
                                    .map(option -> new TeacherClassManagementDto.QuizOption(option.id(), option.text()))
                                    .toList(),
                            question.getCorrectAnswer(), question.getExplanation()
                    )).toList();
            return new TeacherClassManagementDto.QuizSettings(day, quiz.getStatus(), questions);
        }).toList();
        var investments = investmentRepository.findAllByRoomIdOrderByDisplayOrder(room.getId()).stream()
                .filter(asset -> InvestmentService.isSupportedAssetCode(asset.getCode()))
                .map(asset -> new TeacherClassManagementDto.InvestmentAsset(
                        asset.getId(), asset.getCode(), asset.getName(), asset.getDescription(),
                        asset.getCompanyInfo(), asset.getRiskLevel(), asset.getMinReturnBps(),
                        asset.getMaxReturnBps(), asset.getDisplayOrder()
                )).toList();
        var products = productRepository.findAllByRoomIdOrderByDisplayOrder(room.getId()).stream()
                .map(product -> new TeacherClassManagementDto.ShopProduct(
                        product.getId(), product.getName(), product.getDescription(),
                        product.getOriginalPrice(), product.getImageUrl(), product.getCurrentStock(), product.getDisplayOrder()
                )).toList();
        boolean editingLiveClass = !"PREPARING".equals(room.getStatus());
        return new ClassSettingsResponse(
                room.getId(), room.getName(), room.getStatus(), editingLiveClass,
                economy, quizzes, investments, products
        );
    }

    private StudentEntity managedStudentForUpdate(UUID roomId, UUID studentId) {
        return studentRepository.findByIdForUpdate(studentId)
                .filter(student -> student.getRoomId().equals(roomId))
                .orElseThrow(() -> notFound("학생을 찾을 수 없습니다."));
    }

    private ManagedStudent managedStudent(StudentEntity student) {
        return new ManagedStudent(
                student.getId(), student.getParticipantNumber(), student.getParticipationCode(),
                student.getNickname(), student.getStatus(), student.isOnline(), student.getLastSeenAt()
        );
    }

    private String uniqueParticipationCode(int participantNumber) {
        String base = "ECO-" + String.format("%02d", participantNumber);
        if (!studentRepository.existsByParticipationCode(base)) return base;
        String candidate;
        do {
            candidate = base + "-" + (100 + RANDOM.nextInt(900));
        } while (studentRepository.existsByParticipationCode(candidate));
        return candidate;
    }

    private RoomEntity ownedRoom(UUID teacherId, UUID roomId) {
        return roomRepository.findById(roomId)
                .filter(room -> room.getTeacherId().equals(teacherId))
                .orElseThrow(() -> notFound("수업을 찾을 수 없습니다."));
    }

    private RoomEntity ownedRoomForUpdate(UUID teacherId, UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .filter(room -> room.getTeacherId().equals(teacherId))
                .orElseThrow(() -> notFound("수업을 찾을 수 없습니다."));
    }

    private ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", message);
    }
}
