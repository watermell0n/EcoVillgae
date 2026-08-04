from __future__ import annotations

import logging
from dataclasses import asdict, dataclass
from typing import Any, Protocol


@dataclass(frozen=True, slots=True)
class SafeRunEvent:
    ai_run_id: str
    request_id: str
    correlation_id: str
    kind: str
    schema_version: str
    prompt_version: str
    prompt_key: str | None
    provider: str
    model_id: str
    attempt: int
    grade: int
    duration_ms: int
    input_tokens: int
    output_tokens: int
    outcome: str
    error_code: str | None
    validation_stage: str | None
    validation_codes: tuple[str, ...]

    def as_dict(self) -> dict[str, Any]:
        return asdict(self)


class RunEventSink(Protocol):
    def record(self, event: SafeRunEvent) -> None: ...


class LoggingRunEventSink:
    def __init__(self, logger: logging.Logger | None = None) -> None:
        self._logger = logger or logging.getLogger("ecovillage_ai.run")

    def record(self, event: SafeRunEvent) -> None:
        self._logger.info("ai_generation_run", extra={"ai_run": event.as_dict()})


class InMemoryRunEventSink:
    def __init__(self) -> None:
        self.events: list[SafeRunEvent] = []

    def record(self, event: SafeRunEvent) -> None:
        self.events.append(event)
