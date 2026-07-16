package com.b201.bibim.feature.shop;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.b201.bibim.feature.shop.ShopDto.ProductListResponse;
import com.b201.bibim.feature.shop.ShopDto.PurchaseHistoryResponse;
import com.b201.bibim.feature.shop.ShopDto.PurchaseRequest;
import com.b201.bibim.feature.shop.ShopDto.PurchaseResponse;
import com.b201.bibim.global.config.RequestIdFilter;
import com.b201.bibim.global.response.ApiResponse;
import com.b201.bibim.global.security.AuthenticatedPrincipal;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/shop")
public class StudentShopController {

    private final ShopService shopService;

    public StudentShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping("/products")
    ApiResponse<ProductListResponse> products(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                shopService.products(principal.id()),
                requestId(request)
        );
    }

    @PostMapping("/purchases")
    ApiResponse<PurchaseResponse> purchase(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PurchaseRequest purchaseRequest,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                shopService.purchase(principal.id(), idempotencyKey, purchaseRequest),
                requestId(request)
        );
    }

    @GetMapping("/purchases")
    ApiResponse<PurchaseHistoryResponse> purchaseHistory(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletRequest request
    ) {
        return ApiResponse.success(
                shopService.purchaseHistory(principal.id()),
                requestId(request)
        );
    }

    private String requestId(HttpServletRequest request) {
        return (String) request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
    }
}
