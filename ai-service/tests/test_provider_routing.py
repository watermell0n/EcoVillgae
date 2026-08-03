from __future__ import annotations

from fastapi.testclient import TestClient

from ecovillage_ai.config import Settings
from ecovillage_ai.main import create_app
from ecovillage_ai.provider import FakeGenerationProvider
from ecovillage_ai.runtime import GenerationKind
from ecovillage_ai.telemetry import InMemoryRunEventSink
from tests.factories import headers, teacher_chat_request


class IdentifiedFakeProvider(FakeGenerationProvider):
    def provider_name_for(self, kind: GenerationKind) -> str:
        return "OPENAI"

    def model_id_for(self, kind: GenerationKind) -> str:
        return "routed-openai-model"


def test_selected_route_identity_is_used_in_response_and_safe_event(
    settings: Settings,
) -> None:
    sink = InMemoryRunEventSink()
    with TestClient(
        create_app(
            settings=settings,
            provider=IdentifiedFakeProvider(),
            event_sink=sink,
        )
    ) as client:
        response = client.post(
            "/internal/v1/generations/teacher-chat",
            headers=headers(),
            json=teacher_chat_request(),
        )

    assert response.status_code == 200, response.text
    assert response.json()["provider"] == "OPENAI"
    assert response.json()["model_id"] == "routed-openai-model"
    assert sink.events[0].provider == "OPENAI"
    assert sink.events[0].model_id == "routed-openai-model"
