import importlib.util
import json
from pathlib import Path

import pytest

SCRIPT_PATH = Path(__file__).resolve().parents[1] / "scripts" / "provider_smoke.py"
SPEC = importlib.util.spec_from_file_location("provider_smoke", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
provider_smoke = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(provider_smoke)


def test_read_secret_accepts_raw_value_without_exposing_it(tmp_path: Path) -> None:
    path = tmp_path / "key.txt"
    path.write_text("synthetic-secret\n", encoding="utf-8")

    assert provider_smoke._read_secret(path) == "synthetic-secret"


@pytest.mark.parametrize("value", ["", "first\nsecond\n"])
def test_read_secret_rejects_empty_or_multiline_values(tmp_path: Path, value: str) -> None:
    path = tmp_path / "key.txt"
    path.write_text(value, encoding="utf-8")

    with pytest.raises(ValueError):
        provider_smoke._read_secret(path)


def test_rapidapi_usage_counts_attempts_conservatively(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    usage_path = tmp_path / "usage.json"
    usage_path.write_text(
        json.dumps(
            {
                "events": {"rapidapi": []},
                "rapidapi": {"monthly_limit": 100},
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(provider_smoke, "USAGE_FILE", usage_path)

    result = provider_smoke._record_usage(
        provider="rapidapi",
        attempted_at="2026-08-29T21:00:00+00:00",
        success=False,
        details={"http_status": 429},
    )

    assert result["attempted_requests_this_month"] == 1
    assert result["successful_requests_this_month"] == 0
    assert result["estimated_remaining_requests"] == 99
