from fastapi.testclient import TestClient

from lmaa_backend.config import Settings
from lmaa_backend.main import create_app


def test_health_contract() -> None:
    client = TestClient(create_app(_settings()))

    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "service": "lmaa-backend",
        "version": "0.1.0",
    }


def test_readiness_is_red_without_provider_keys() -> None:
    client = TestClient(create_app(_settings()))

    response = client.get("/readyz")

    assert response.status_code == 503
    assert response.json() == {
        "status": "not_ready",
        "model": "gpt-5.6-sol",
        "checks": {
            "openai_api_key_configured": False,
            "openai_model_configured": True,
            "provider_access_verified": False,
        },
    }


def test_readiness_never_exposes_keys() -> None:
    client = TestClient(
        create_app(
            Settings(
                environment="test",
                openai_model="gpt-5.6-sol",
                openai_api_key="openai-secret-value",
            )
        )
    )

    response = client.get("/readyz")

    assert response.status_code == 200
    body = response.text
    assert "openai-secret-value" not in body
    assert response.json()["checks"] == {
        "openai_api_key_configured": True,
        "openai_model_configured": True,
        "provider_access_verified": False,
    }


def _settings() -> Settings:
    return Settings(
        environment="test",
        openai_model="gpt-5.6-sol",
        openai_api_key="",
    )
