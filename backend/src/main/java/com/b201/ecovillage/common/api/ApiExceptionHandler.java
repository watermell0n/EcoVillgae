package com.b201.ecovillage.common.api;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.application.ClassroomNotActiveException;
import com.b201.ecovillage.identity.application.InvalidCredentialsException;
import com.b201.ecovillage.identity.application.InvalidLoginRequestException;
import com.b201.ecovillage.identity.application.LoginRateLimitedException;
import com.b201.ecovillage.identity.application.TeacherAuthenticationException;
import com.b201.ecovillage.ledger.application.LedgerTransactionValidationException;
import com.b201.ecovillage.ledger.domain.InsufficientLedgerBalanceException;
import com.b201.ecovillage.store.domain.InsufficientStoreStockException;
import com.b201.ecovillage.trade.domain.TradeOperationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final UuidV7Generator uuidGenerator;
    private final Clock clock;

    public ApiExceptionHandler(UuidV7Generator uuidGenerator, Clock clock) {
        this.uuidGenerator = uuidGenerator;
        this.clock = clock;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidCredentials(
        InvalidCredentialsException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "로그인 정보를 확인해 주세요.",
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(TeacherAuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleTeacherAuthentication(
        TeacherAuthenticationException exception,
        HttpServletRequest request
    ) {
        return response(
            exception.getStatus(),
            exception.getCode(),
            exception.getMessage(),
            exception.isRetryable(),
            List.of(),
            request
        );
    }

    @ExceptionHandler(ClassroomOperationException.class)
    ResponseEntity<ApiErrorResponse> handleClassroomOperation(
        ClassroomOperationException exception,
        HttpServletRequest request
    ) {
        return response(
            exception.getStatus(),
            exception.getCode(),
            exception.getMessage(),
            exception.getStatus().is5xxServerError(),
            List.of(),
            request
        );
    }

    @ExceptionHandler(InsufficientLedgerBalanceException.class)
    ResponseEntity<ApiErrorResponse> handleInsufficientBalance(
        InsufficientLedgerBalanceException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "INSUFFICIENT_BALANCE",
            "상품을 구매할 잔액이 부족합니다.",
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(InsufficientStoreStockException.class)
    ResponseEntity<ApiErrorResponse> handleInsufficientStock(
        InsufficientStoreStockException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "OUT_OF_STOCK",
            "상품 재고가 부족합니다.",
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(TradeOperationException.class)
    ResponseEntity<ApiErrorResponse> handleTradeOperation(
        TradeOperationException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            exception.getCode(),
            exception.getMessage(),
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(LedgerTransactionValidationException.class)
    ResponseEntity<ApiErrorResponse> handleLedgerValidation(
        LedgerTransactionValidationException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            exception.getCode(),
            exception.getMessage(),
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(ClassroomNotActiveException.class)
    ResponseEntity<ApiErrorResponse> handleClassroomNotActive(
        ClassroomNotActiveException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.CONFLICT,
            "CLASS_NOT_ACTIVE",
            "현재 학생 로그인을 허용하지 않는 경제교실입니다.",
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ResponseEntity<ApiErrorResponse> handleRateLimited(
        LoginRateLimitedException exception,
        HttpServletRequest request
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()));
        return response(
            HttpStatus.TOO_MANY_REQUESTS,
            "RATE_LIMITED",
            "요청이 많습니다. 잠시 후 다시 시도해 주세요.",
            true,
            List.of(),
            request,
            headers
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> handleMissingHeader(
        MissingRequestHeaderException exception,
        HttpServletRequest request
    ) {
        String code = "Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())
            ? "IDEMPOTENCY_KEY_REQUIRED"
            : "VALIDATION_ERROR";
        return response(
            HttpStatus.BAD_REQUEST,
            code,
            "필수 요청 헤더가 없습니다.",
            false,
            List.of(new ApiErrorDetail(exception.getHeaderName(), "REQUIRED")),
            request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException exception,
        HttpServletRequest request
    ) {
        List<ApiErrorDetail> details = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toDetail)
            .toList();
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "요청 입력값을 확인해 주세요.",
            false,
            details,
            request
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> handleTypeMismatch(
        MethodArgumentTypeMismatchException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "요청 입력값의 형식이 올바르지 않습니다.",
            false,
            List.of(new ApiErrorDetail(exception.getName(), "INVALID_FORMAT")),
            request
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> handleMalformedJson(
        HttpMessageNotReadableException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_JSON",
            "JSON 요청 형식이 올바르지 않습니다.",
            false,
            List.of(),
            request
        );
    }

    @ExceptionHandler(InvalidLoginRequestException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidLoginRequest(
        InvalidLoginRequestException exception,
        HttpServletRequest request
    ) {
        return response(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "요청 입력값을 확인해 주세요.",
            false,
            List.of(),
            request
        );
    }

    private ApiErrorDetail toDetail(FieldError error) {
        String reason = error.getCode() == null
            ? "INVALID"
            : error.getCode().toUpperCase(java.util.Locale.ROOT);
        return new ApiErrorDetail(error.getField(), reason);
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        boolean retryable,
        List<ApiErrorDetail> details,
        HttpServletRequest request
    ) {
        return response(status, code, message, retryable, details, request, new HttpHeaders());
    }

    private ResponseEntity<ApiErrorResponse> response(
        HttpStatus status,
        String code,
        String message,
        boolean retryable,
        List<ApiErrorDetail> details,
        HttpServletRequest request,
        HttpHeaders headers
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
            new ApiError(code, message, retryable, details),
            new ApiMeta(
                headerUuidOrNew(request, "Idempotency-Key"),
                headerUuidOrNew(request, "X-Correlation-Id"),
                clock.instant()
            )
        );
        return new ResponseEntity<>(body, headers, status);
    }

    private UUID headerUuidOrNew(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Invalid untrusted headers are never reflected into the response.
            }
        }
        return uuidGenerator.generate();
    }
}
