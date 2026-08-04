package com.b201.ecovillage.ai.application;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.ai.domain.AsyncOperation;
import com.b201.ecovillage.ai.repository.AsyncOperationRepository;
import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class AsyncOperationQueryService {

    private final AsyncOperationRepository operationRepository;
    private final ClassroomAuthorizationService authorizationService;

    public AsyncOperationQueryService(
        AsyncOperationRepository operationRepository,
        ClassroomAuthorizationService authorizationService
    ) {
        this.operationRepository = operationRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public AsyncOperationView find(String teacherSubject, UUID operationId) {
        if (teacherSubject == null || teacherSubject.isBlank()) {
            throw operation(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Authentication is required.");
        }
        AsyncOperation operation = operationRepository.findById(operationId)
            .filter(candidate -> candidate.getOperationType().startsWith("AI_"))
            .orElseThrow(() -> operation(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "The operation could not be found."
            ));
        authorizationService.authorize(
            teacherSubject,
            operation.getClassroomId(),
            ClassroomPermission.READ_AI_HISTORY,
            null
        );
        return new AsyncOperationView(
            operation.getId(),
            operation.getOperationType(),
            operation.getStatus(),
            operation.getProgressPercent(),
            operation.getResultLocation(),
            operation.getErrorCode(),
            operation.getStartedAt(),
            operation.getCompletedAt(),
            operation.getExpiresAt(),
            operation.getStateVersion()
        );
    }

    private static ClassroomOperationException operation(
        HttpStatus status,
        String code,
        String message
    ) {
        return new ClassroomOperationException(status, code, message);
    }
}
