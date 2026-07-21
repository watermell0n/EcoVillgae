package com.b201.bibim.feature.management;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.management.TeacherClassManagementDto.AddStudentRequest;
import com.b201.bibim.feature.management.TeacherClassManagementDto.ClassSettingsResponse;
import com.b201.bibim.feature.management.TeacherClassManagementDto.ManagedStudent;
import com.b201.bibim.feature.management.TeacherClassManagementDto.ProductImageUploadResponse;
import com.b201.bibim.feature.management.TeacherClassManagementDto.UpdateClassSettingsRequest;
import com.b201.bibim.feature.management.TeacherClassManagementDto.UpdateStudentRequest;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/teacher/rooms/{roomId}/management")
public class TeacherClassManagementController {

    private final TeacherClassManagementService managementService;
    private final ProductImageStorageService imageStorageService;

    public TeacherClassManagementController(
            TeacherClassManagementService managementService,
            ProductImageStorageService imageStorageService
    ) {
        this.managementService = managementService;
        this.imageStorageService = imageStorageService;
    }

    @PostMapping(value = "/product-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ProductImageUploadResponse> uploadProductImage(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                imageStorageService.store(principal.id(), roomId, file),
                requestId(request)
        );
    }

    @GetMapping("/settings")
    ApiResponse<ClassSettingsResponse> settings(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(managementService.settings(principal.id(), roomId), requestId(request));
    }

    @PutMapping("/settings")
    ApiResponse<ClassSettingsResponse> updateSettings(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateClassSettingsRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(managementService.updateSettings(principal.id(), roomId, body), requestId(request));
    }

    @GetMapping("/students")
    ApiResponse<List<ManagedStudent>> students(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(managementService.students(principal.id(), roomId), requestId(request));
    }

    @PostMapping("/students")
    @ResponseStatus(HttpStatus.CREATED)
    ApiResponse<ManagedStudent> addStudent(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @Valid @RequestBody AddStudentRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(managementService.addStudent(principal.id(), roomId, body), requestId(request));
    }

    @PatchMapping("/students/{studentId}")
    ApiResponse<ManagedStudent> updateStudent(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            @Valid @RequestBody UpdateStudentRequest body,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                managementService.updateStudent(principal.id(), roomId, studentId, body),
                requestId(request)
        );
    }

    @PostMapping("/students/{studentId}/reset-password")
    ApiResponse<ManagedStudent> resetPassword(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @PathVariable UUID roomId,
            @PathVariable UUID studentId,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                managementService.resetPassword(principal.id(), roomId, studentId),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
