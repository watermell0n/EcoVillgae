package com.b201.ecovillage.character.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 캐릭터 착용 상태 일괄 저장 요청.
 *
 * 상점에서 여러 슬롯을 바꾼 뒤 한 번에 저장한다. 슬롯별 단건 요청을 반복하면 중간에
 * 실패했을 때 일부 슬롯만 저장된 상태가 남는다.
 *
 * `equipment`는 전체 상태다(PUT 의미론). 키가 없거나 값이 null인 슬롯은 해제한다.
 */
public record CharacterEquipmentSaveRequest(
    @NotNull @Min(0) Long expectedStateVersion,
    @NotNull Map<String, UUID> equipment,
    String screenId,
    String uiActionId,
    Instant clientOccurredAt
) {
}
