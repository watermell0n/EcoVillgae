from __future__ import annotations

from collections.abc import Mapping
from typing import Any

from ecovillage_ai.errors import ProviderConfigurationError
from ecovillage_ai.runtime import GenerationCommand, GenerationKind, ProviderResult


class RoutedGenerationProvider:
    provider_name = "ROUTED"

    def __init__(
        self,
        routes: Mapping[GenerationKind, Any],
    ) -> None:
        missing = set(GenerationKind) - set(routes)
        if missing:
            raise ValueError(
                "모든 AI 기능에 공급자 경로가 필요합니다: "
                + ", ".join(sorted(kind.value for kind in missing))
            )
        self._routes = dict(routes)

    async def generate(self, command: GenerationCommand) -> ProviderResult:
        provider = self._routes.get(command.kind)
        if provider is None:
            raise ProviderConfigurationError("AI 기능 공급자 경로가 없습니다.")
        return await provider.generate(command)

    def provider_name_for(self, kind: GenerationKind) -> str:
        provider = self._routes[kind]
        method = getattr(provider, "provider_name_for", None)
        if callable(method):
            return str(method(kind))
        return str(getattr(provider, "provider_name", "UNKNOWN"))

    def model_id_for(self, kind: GenerationKind) -> str:
        provider = self._routes[kind]
        method = getattr(provider, "model_id_for", None)
        if callable(method):
            return str(method(kind))
        return str(getattr(provider, "model_id", "unknown"))
