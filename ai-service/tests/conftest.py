from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from ecovillage_ai.config import ALL_GENERATION_SCOPES, Settings
from ecovillage_ai.main import create_app


@pytest.fixture
def settings() -> Settings:
    return Settings(
        environment="test",
        internal_service_token="test-service-token",
        internal_service_scopes=ALL_GENERATION_SCOPES,
        provider_timeout_seconds=0.2,
        minimum_deadline_remaining_seconds=0.001,
        docs_enabled=True,
    )


@pytest.fixture
def client(settings: Settings) -> TestClient:
    with TestClient(create_app(settings=settings)) as test_client:
        yield test_client
