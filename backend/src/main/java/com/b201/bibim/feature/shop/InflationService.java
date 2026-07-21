package com.b201.bibim.feature.shop;

import java.util.List;
import java.util.UUID;

import com.b201.bibim.domain.room.RoomEntity;
import com.b201.bibim.domain.room.RoomRepository;
import com.b201.bibim.domain.room.RoomRulesEntity;
import com.b201.bibim.domain.room.RoomRulesRepository;
import com.b201.bibim.domain.shop.ProductEntity;
import com.b201.bibim.domain.shop.ProductRepository;
import com.b201.bibim.feature.shop.InflationDto.ApplyResponse;
import com.b201.bibim.feature.shop.InflationDto.PreviewResponse;
import com.b201.bibim.feature.shop.InflationDto.ProductPriceResponse;
import com.b201.bibim.global.exception.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InflationService {

    private final RoomRepository roomRepository;
    private final RoomRulesRepository rulesRepository;
    private final ProductRepository productRepository;
    private final InflationCalculator calculator;

    public InflationService(
            RoomRepository roomRepository,
            RoomRulesRepository rulesRepository,
            ProductRepository productRepository,
            InflationCalculator calculator
    ) {
        this.roomRepository = roomRepository;
        this.rulesRepository = rulesRepository;
        this.productRepository = productRepository;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public PreviewResponse preview(UUID teacherId, UUID roomId) {
        RoomEntity room = findOwnedRoom(teacherId, roomId);
        ensurePreviewStage(room);
        return buildPreview(room, rules(roomId), activeProducts(roomId));
    }

    @Transactional
    public ApplyResponse apply(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findByIdForUpdate(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        if (!"DAY2_INFLATION_APPLY".equals(room.getCurrentStage())) {
            throw conflict("INFLATION_NOT_OPEN", "물가상승 적용 단계에서만 실행할 수 있습니다.");
        }
        RoomRulesEntity rules = rules(roomId);
        if (room.getInflationAppliedAt() != null) {
            return new ApplyResponse(
                    buildPreview(room, rules, activeProducts(roomId)),
                    true
            );
        }
        List<ProductEntity> products = productRepository.findAllByRoomIdForUpdate(roomId).stream()
                .filter(product -> !"DISCONTINUED".equals(product.getStatus()))
                .toList();
        if (products.isEmpty()) {
            throw conflict("PRODUCT_NOT_FOUND", "물가상승을 적용할 상품이 없습니다.");
        }
        try {
            products.forEach(product -> product.updateCurrentPrice(
                    calculator.calculate(product.getOriginalPrice(), rules.getInflationBps())
            ));
        } catch (ArithmeticException | IllegalArgumentException exception) {
            throw conflict("INFLATION_CALCULATION_FAILED", "상품 가격을 안전하게 계산할 수 없습니다.");
        }
        room.markInflationApplied();
        return new ApplyResponse(buildPreview(room, rules, products), false);
    }

    private PreviewResponse buildPreview(
            RoomEntity room,
            RoomRulesEntity rules,
            List<ProductEntity> products
    ) {
        boolean applied = room.getInflationAppliedAt() != null;
        List<ProductPriceResponse> prices = products.stream()
                .map(product -> {
                    long next = applied
                            ? product.getCurrentPrice()
                            : calculator.calculate(product.getOriginalPrice(), rules.getInflationBps());
                    return new ProductPriceResponse(
                            product.getId(), product.getName(), product.getOriginalPrice(), next,
                            Math.subtractExact(next, product.getOriginalPrice()), product.getCurrentStock()
                    );
                })
                .toList();
        return new PreviewResponse(
                room.getId(), rules.getInflationBps(), applied, room.getInflationAppliedAt(), prices
        );
    }

    private List<ProductEntity> activeProducts(UUID roomId) {
        return productRepository.findAllByRoomIdOrderByDisplayOrder(roomId).stream()
                .filter(product -> !"DISCONTINUED".equals(product.getStatus()))
                .toList();
    }

    private RoomRulesEntity rules(UUID roomId) {
        return rulesRepository.findByRoomId(roomId)
                .orElseThrow(() -> conflict("ROOM_RULES_NOT_FOUND", "수업 규칙을 찾을 수 없습니다."));
    }

    private RoomEntity findOwnedRoom(UUID teacherId, UUID roomId) {
        RoomEntity room = roomRepository.findById(roomId).orElseThrow(this::roomNotFound);
        ensureOwner(room, teacherId);
        return room;
    }

    private void ensureOwner(RoomEntity room, UUID teacherId) {
        if (!room.getTeacherId().equals(teacherId)) throw roomNotFound();
    }

    private void ensurePreviewStage(RoomEntity room) {
        if (!List.of(
                "DAY2_TAX_INFLATION_GUIDED",
                "DAY2_INFLATION_APPLY",
                "DAY2_FINAL_SHOP"
        ).contains(room.getCurrentStage())) {
            throw conflict("INFLATION_NOT_OPEN", "인플레이션 교육 이후 가격을 확인할 수 있습니다.");
        }
    }

    private ApiException roomNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROOM_NOT_FOUND", "수업 방을 찾을 수 없습니다.");
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
