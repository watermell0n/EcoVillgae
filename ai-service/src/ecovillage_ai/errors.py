from __future__ import annotations

from collections.abc import Sequence

from ecovillage_ai.models import ErrorCode


class ServiceError(Exception):
    def __init__(
        self,
        *,
        status_code: int,
        code: ErrorCode,
        message: str,
        retryable: bool = False,
        validation_codes: Sequence[str] = (),
        validation_stage: str | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.safe_message = message
        self.retryable = retryable
        self.validation_codes = list(validation_codes)[:50]
        self.validation_stage = (
            str(validation_stage) if validation_stage is not None else None
        )


class ProviderError(Exception):
    """Base provider failure. Raw provider details must not leave this boundary."""


class ProviderRateLimitedError(ProviderError):
    pass


class ProviderUnavailableError(ProviderError):
    pass
