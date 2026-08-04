from __future__ import annotations

import re
from collections.abc import Mapping
from typing import Any

_UNSUPPORTED_STRICT_KEYS = frozenset(
    {
        "default",
        "examples",
        "exclusiveMaximum",
        "exclusiveMinimum",
        "maxItems",
        "maxLength",
        "maximum",
        "minItems",
        "minLength",
        "minimum",
        "multipleOf",
        "pattern",
        "uniqueItems",
    }
)


def strict_output_schema(schema: Mapping[str, Any]) -> dict[str, Any]:
    """공급자 공통 JSON Schema 부분집합으로 정규화한다.

    길이·범위 등 제거된 제약은 공급자 응답 뒤 Pydantic과 정책 파이프라인이 다시
    검증한다. 모든 object 필드는 required로 만들고 선택값은 null union으로 유지한다.
    """

    def normalize(value: Any) -> Any:
        if isinstance(value, list):
            return [normalize(item) for item in value]
        if not isinstance(value, Mapping):
            return value

        result = {
            key: normalize(item)
            for key, item in value.items()
            if key not in _UNSUPPORTED_STRICT_KEYS
        }
        properties = result.get("properties")
        if result.get("type") == "object" and isinstance(properties, Mapping):
            result["required"] = list(properties)
            result["additionalProperties"] = False
        return result

    normalized = normalize(schema)
    if not isinstance(normalized, dict) or normalized.get("type") != "object":
        raise ValueError("구조화 출력 스키마는 JSON object여야 합니다.")
    return normalized


def schema_name(prompt_key: str) -> str:
    normalized = re.sub(r"[^a-zA-Z0-9_-]", "_", prompt_key).strip("_-")
    return (normalized or "ecovillage_output")[:64]
