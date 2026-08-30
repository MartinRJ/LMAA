from __future__ import annotations

import pytest

from lmaa_backend.server import runtime_address


def test_runtime_address_defaults_to_loopback(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("LMAA_BIND_HOST", raising=False)
    monkeypatch.delenv("PORT", raising=False)

    assert runtime_address() == ("127.0.0.1", 8000)


def test_runtime_address_accepts_container_binding(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("LMAA_BIND_HOST", "0.0.0.0")
    monkeypatch.setenv("PORT", "8080")

    assert runtime_address() == ("0.0.0.0", 8080)


@pytest.mark.parametrize(
    ("host", "port"),
    [("localhost", "8000"), ("127.0.0.1", "invalid"), ("127.0.0.1", "70000")],
)
def test_runtime_address_rejects_invalid_values(
    monkeypatch: pytest.MonkeyPatch,
    host: str,
    port: str,
) -> None:
    monkeypatch.setenv("LMAA_BIND_HOST", host)
    monkeypatch.setenv("PORT", port)

    with pytest.raises(ValueError):
        runtime_address()
