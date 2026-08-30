# LMAA-Provider-Referenzprototyp

> **Nicht Teil der Produktlaufzeit.** Die Android-App darf diesen FastAPI-Dienst
> weder voraussetzen noch im täglichen Betrieb kontaktieren. Es gibt kein
> Hosting- oder Deployment-Ziel für diesen Ordner.

Der Prototyp entstand aus der inzwischen aufgehobenen Backend-Architektur. Er
bleibt vorläufig als ausführbare Referenz für folgende bereits getestete Logik
erhalten:

- YouTube-URL-Validierung und kanonische IDs.
- `youtube-transcript-api`-Sprachwahl und Fehlerklassifikation.
- schlüssellose oEmbed-Metadaten mit nullable ID/Datum/Dauer.
- RapidAPI-Fallback-Wahrheitstabelle und lokale Usage-Erfassung.
- Promptaufbau, Chunking/Map-Reduce und `gpt-5.6-sol`.
- synthetische Provider- und HTTP-Contract-Tests.

Die Android-Implementierung darf Code und Fixtures fachlich übernehmen, muss
aber durch Android-/Chaquopy-Tests eigenständig verifiziert werden. Ein grüner
Test in diesem Ordner ist kein Nachweis für den lokalen Tablet-Pfad.

## Referenztests

```powershell
cd backend
python -m venv .venv
.venv\Scripts\python -m pip install -e ".[dev]"
.venv\Scripts\python -m pytest
.venv\Scripts\python -m ruff check --no-cache .
.venv\Scripts\python -m ruff format --check --no-cache .
```

## Explizite Entwicklungs-Smokes

Die Skripte lesen ausschließlich die ignorierten Dateien
`OpenAI API KEY.txt` und `youtube-transcripts Key.txt` aus dem
Repository-Wurzelverzeichnis. Sie geben weder Secrets noch Transkript- oder
Briefinginhalt aus.

```powershell
.venv\Scripts\python scripts\provider_smoke.py openai
.venv\Scripts\python scripts\provider_smoke.py youtube-metadata --video-id VIDEO_ID
.venv\Scripts\python scripts\provider_smoke.py primary-transcript --video-id VIDEO_ID
.venv\Scripts\python scripts\briefing_pipeline_smoke.py --video-id VIDEO_ID
```

RapidAPI-Smokes sind nicht Teil der normalen Regression. Jeder reale Aufruf
verbraucht das knappe Monatskontingent und benötigt einen ausdrücklichen
diagnostischen Grund:

```powershell
.venv\Scripts\python scripts\provider_smoke.py rapidapi --video-id VIDEO_ID
```

Nur RapidAPI-Smokes werden in der ignorierten Datei
`provider-smoke-usage.json` gezählt. Stand 2026-08-30: 3 Versuche, 3 Erfolge,
konservativ 97 von nominal 100 Monatsrequests verbleibend. Das RapidAPI-
Dashboard ist maßgeblich. OpenAI-Verbrauch wird auf Nutzerwunsch nicht lokal
protokolliert.

## Bereits belegte Referenzergebnisse

- Primärtranskripte: manuelles Englisch, automatisch erzeugtes Deutsch und
  Englisch, langes Video und Short.
- OpenAI: vollständige deutsche/englische Briefings, Short sowie vierteiliger
  Map-Reduce-Lauf mit exakt `gpt-5.6-sol`.
- Referenz-Gesamtvertrag: oEmbed, 1.660 Transkriptsegmente und 12.388 Zeichen
  Markdown mit allen Pflichtüberschriften.
- 50 Python-Tests, Ruff und Formatprüfung bestanden.

Diese Resultate reduzieren das fachliche Portierungsrisiko. Sie erfüllen nicht
die Anforderungen LOC-001 und TRN-001 aus `docs/anforderungs-vv.md`.

## Nicht mehr gültige Deployment-Artefakte

`Dockerfile`, `.dockerignore`, `server.py` und `run_local.ps1` stammen aus dem
aufgehobenen Backend-Deploymentpfad. Sie werden nicht für den MVP verwendet und
sollen in einem separaten Code-Cleanup entfernt oder in einen eindeutig
bezeichneten Spike-Bereich verschoben werden. Bis dahin dürfen sie nicht als
Betriebsanleitung interpretiert werden.

## Abhängigkeiten

Alle direkten Python-Abhängigkeiten sind für reproduzierbare Referenztests
gepinnt. Lizenzen: FastAPI, Pydantic, `youtube-transcript-api`, pytest und Ruff
(MIT), Uvicorn/HTTPX (BSD-3-Clause), OpenAI Python (Apache-2.0).
