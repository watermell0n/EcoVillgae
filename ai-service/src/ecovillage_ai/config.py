from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass, field

ALL_GENERATION_SCOPES = frozenset(
    {
        "ai:generate:balancing",
        "ai:generate:teacher-chat",
        "ai:generate:economic-event",
        "ai:generate:quiz-draft",
        "ai:generate:investment-company",
    }
)

GENERATION_KINDS = (
    "balancing",
    "teacher-chat",
    "economic-event",
    "quiz-draft",
    "investment-company",
)
SUPPORTED_PROVIDERS = frozenset({"FAKE", "BEDROCK", "OPENAI"})
ROUTE_ENV_SUFFIXES = {
    "balancing": "BALANCING",
    "teacher-chat": "TEACHER_CHAT",
    "economic-event": "ECONOMIC_EVENT",
    "quiz-draft": "QUIZ_DRAFT",
    "investment-company": "INVESTMENT_COMPANY",
}


def _read_scopes(value: str | None) -> frozenset[str]:
    if value is None:
        return ALL_GENERATION_SCOPES
    return frozenset(scope.strip() for scope in value.split(",") if scope.strip())


def _read_bool(value: str | None, *, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes"}


@dataclass(frozen=True, slots=True)
class ProviderRoute:
    provider_name: str
    model_id: str

    def __post_init__(self) -> None:
        provider_name = self.provider_name.strip().upper()
        model_id = self.model_id.strip()
        if provider_name not in SUPPORTED_PROVIDERS:
            raise ValueError(f"지원하지 않는 AI 공급자입니다: {provider_name}")
        if not model_id:
            raise ValueError(f"{provider_name} 공급자 모델 ID가 필요합니다.")
        object.__setattr__(self, "provider_name", provider_name)
        object.__setattr__(self, "model_id", model_id)


def _default_model_id(
    provider_name: str,
    environment: Mapping[str, str],
) -> str:
    explicit = environment.get("ECOVILLAGE_AI_MODEL_ID", "").strip()
    if explicit:
        return explicit
    if provider_name == "FAKE":
        return "deterministic-v1"
    return environment.get(f"ECOVILLAGE_AI_{provider_name}_MODEL_ID", "").strip()


def _read_route(
    value: str,
) -> ProviderRoute:
    provider_name, separator, model_id = value.strip().partition(":")
    provider_name = provider_name.upper()
    if not separator:
        raise ValueError(
            "기능별 공급자 경로는 공급자:모델ID 형식이어야 합니다."
        )
    return ProviderRoute(provider_name=provider_name, model_id=model_id)


@dataclass(frozen=True, slots=True)
class Settings:
    app_name: str = "이코빌리지 AI 서비스"
    app_version: str = "0.1.0"
    environment: str = "local"
    internal_service_token: str = field(
        default="local-development-token",
        repr=False,
    )
    internal_service_scopes: frozenset[str] = field(
        default_factory=lambda: ALL_GENERATION_SCOPES
    )
    provider_name: str = "FAKE"
    provider_model_id: str = "deterministic-v1"
    provider_routes: Mapping[str, ProviderRoute] = field(default_factory=dict)
    provider_timeout_seconds: float = 10.0
    provider_max_output_tokens: int = 4096
    minimum_deadline_remaining_seconds: float = 0.05
    aws_region: str | None = None
    openai_enabled: bool = False
    docs_enabled: bool = True

    def __post_init__(self) -> None:
        provider_name = self.provider_name.strip().upper()
        provider_model_id = self.provider_model_id.strip()
        if provider_name not in SUPPORTED_PROVIDERS:
            raise ValueError(f"지원하지 않는 AI 공급자입니다: {provider_name}")
        if not provider_model_id:
            raise ValueError(f"{provider_name} 공급자 모델 ID가 필요합니다.")
        if self.provider_timeout_seconds <= 0:
            raise ValueError("공급자 제한시간은 0보다 커야 합니다.")
        if self.provider_max_output_tokens <= 0:
            raise ValueError("공급자 최대 출력 토큰은 0보다 커야 합니다.")
        if self.minimum_deadline_remaining_seconds <= 0:
            raise ValueError("최소 잔여 제한시간은 0보다 커야 합니다.")
        invalid_kinds = set(self.provider_routes) - set(GENERATION_KINDS)
        if invalid_kinds:
            raise ValueError(
                "지원하지 않는 기능별 공급자 경로입니다: "
                + ", ".join(sorted(invalid_kinds))
            )
        object.__setattr__(self, "provider_name", provider_name)
        object.__setattr__(self, "provider_model_id", provider_model_id)

    def route_for(self, generation_kind: str) -> ProviderRoute:
        if generation_kind not in GENERATION_KINDS:
            raise ValueError(f"지원하지 않는 AI 생성 기능입니다: {generation_kind}")
        return self.provider_routes.get(
            generation_kind,
            ProviderRoute(self.provider_name, self.provider_model_id),
        )

    def validate_provider_environment(
        self,
        environment: Mapping[str, str] | None = None,
    ) -> None:
        values = environment or os.environ
        routes = {self.route_for(kind) for kind in GENERATION_KINDS}
        if any(route.provider_name == "BEDROCK" for route in routes) and not self.aws_region:
            raise RuntimeError(
                "BEDROCK 사용 시 AWS_REGION 또는 AWS_DEFAULT_REGION이 필요합니다."
            )
        if any(route.provider_name == "OPENAI" for route in routes):
            if not self.openai_enabled:
                raise RuntimeError(
                    "OPENAI 사용 시 ECOVILLAGE_AI_OPENAI_ENABLED=true가 필요합니다."
                )
            if not values.get("OPENAI_API_KEY", "").strip():
                raise RuntimeError("OPENAI 사용 시 OPENAI_API_KEY가 필요합니다.")

    @classmethod
    def from_env(cls) -> Settings:
        environment_name = os.getenv("ECOVILLAGE_AI_ENVIRONMENT", "local")
        token = os.getenv(
            "ECOVILLAGE_AI_INTERNAL_SERVICE_TOKEN",
            "local-development-token",
        )
        if (
            environment_name.lower() in {"prod", "production"}
            and token == "local-development-token"
        ):
            raise RuntimeError("운영 환경에서는 내부 서비스 토큰을 명시해야 합니다.")

        default_provider = (
            "BEDROCK"
            if environment_name.lower() in {"prod", "production"}
            else "FAKE"
        )
        provider_name = os.getenv("ECOVILLAGE_AI_PROVIDER", default_provider).upper()
        provider_model_id = _default_model_id(provider_name, os.environ)
        routes: dict[str, ProviderRoute] = {}
        for kind, suffix in ROUTE_ENV_SUFFIXES.items():
            value = os.getenv(f"ECOVILLAGE_AI_ROUTE_{suffix}")
            if value:
                routes[kind] = _read_route(value)

        settings = cls(
            environment=environment_name,
            internal_service_token=token,
            internal_service_scopes=_read_scopes(
                os.getenv("ECOVILLAGE_AI_INTERNAL_SERVICE_SCOPES")
            ),
            provider_name=provider_name,
            provider_model_id=provider_model_id,
            provider_routes=routes,
            provider_timeout_seconds=float(
                os.getenv("ECOVILLAGE_AI_PROVIDER_TIMEOUT_SECONDS", "10.0")
            ),
            provider_max_output_tokens=int(
                os.getenv("ECOVILLAGE_AI_MAX_OUTPUT_TOKENS", "4096")
            ),
            minimum_deadline_remaining_seconds=float(
                os.getenv("ECOVILLAGE_AI_MINIMUM_DEADLINE_REMAINING_SECONDS", "0.05")
            ),
            aws_region=(
                os.getenv("AWS_REGION") or os.getenv("AWS_DEFAULT_REGION") or None
            ),
            openai_enabled=_read_bool(os.getenv("ECOVILLAGE_AI_OPENAI_ENABLED")),
            docs_enabled=_read_bool(
                os.getenv("ECOVILLAGE_AI_DOCS_ENABLED"),
                default=True,
            ),
        )
        settings.validate_provider_environment()
        return settings
