"""Produktionsnaher Uvicorn-Start ohne Secret-Ausgabe."""

from __future__ import annotations

import ipaddress
import os

import uvicorn


def runtime_address() -> tuple[str, int]:
    """Liest und validiert die Deployment-Adresse aus der Umgebung."""
    host = os.getenv("LMAA_BIND_HOST", "127.0.0.1").strip()
    try:
        ipaddress.ip_address(host)
    except ValueError as exc:
        raise ValueError("LMAA_BIND_HOST muss eine IP-Adresse sein") from exc

    raw_port = os.getenv("PORT", "8000").strip()
    try:
        port = int(raw_port)
    except ValueError as exc:
        raise ValueError("PORT muss eine ganze Zahl sein") from exc
    if not 1 <= port <= 65535:
        raise ValueError("PORT muss zwischen 1 und 65535 liegen")

    return host, port


def main() -> None:
    host, port = runtime_address()
    uvicorn.run(
        "lmaa_backend.main:app",
        host=host,
        port=port,
        access_log=False,
    )


if __name__ == "__main__":
    main()
