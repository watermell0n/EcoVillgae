package com.b201.bibim.global.health;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String requestId = (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        return ApiResponse.success(
                Map.of(
                        "status", "UP",
                        "service", "ecovillage-backend"
                ),
                requestId
        );
    }
}

