# AGENTS.md – Arbeitsanweisung und Fortschritt für LMAA

Diese Datei gilt für das gesamte Repository. Sie ist die operative Ergänzung zu `README.md`; Produktumfang, Architektur, Milestones, Risiken und Abnahmekriterien stehen dort und sind verbindlich zu berücksichtigen.

## Ziel

> **Das Ziel sei:** So schnell wie möglich einen auf einem Samsung Galaxy Tab S7+ 5G mit Android 13 / One UI 5.1.1 funktionierenden MVP bereitzustellen, der YouTube-Links sowohl über das Android-Teilen-Menü als auch per Direkteingabe annimmt, daraus mit `youtube-transcript-api` und `gpt-5.6-sol` ein konfigurierbares Briefing erzeugt, die Ergebnisse lokal historisiert und Markdown per Teilen oder Zwischenablage exportiert.

„ASAP“ bedeutet: zuerst einen vollständigen vertikalen Pfad liefern, danach Robustheit und Komfort. Keine vorgezogenen Ausbaustufen zulasten der MVP-Definition-of-Done.

## Verbindliche technische Leitplanken

1. Dokumentation und Nutzertexte sind Deutsch; Codebezeichner, API-Felder und Commit-Nachrichten dürfen technisches Englisch verwenden.
2. Android: Kotlin, Jetpack Compose/Material 3, Room als lokale Source of Truth, minSdk 26; Android 13 ist zwingendes Testziel.
3. Backend: Python/FastAPI. Der Zugriff auf `youtube-transcript-api`, YouTube Data API und OpenAI erfolgt serverseitig.
4. Das OpenAI-Standardmodell heißt exakt `gpt-5.6-sol`. Modellkonfiguration bleibt serverseitig. Nie einen stillen Fallback auf ein anderes Modell implementieren.
5. Kein Secret in Git, APK, Ressourcen, `BuildConfig`, Logs, Screenshots oder Fixtures. `.env.example` enthält ausschließlich Platzhalter.
6. Alte Briefings sind unveränderliche historische Ergebnisse: Bei Neuerstellung, insbesondere mit anderem Stil, immer einen neuen Eintrag erzeugen. Stilname, Stiltext und Modell als Snapshot speichern.
7. Eingehende URLs, API-Daten, Transkripte und Modell-Markdown sind nicht vertrauenswürdig. Hosts/IDs validieren, HTML deaktivieren oder sanitizen, Link-Schemes begrenzen und keine Modell-Toolaufrufe zulassen.
8. Keine vollständigen fremden Transkripte, realen API-Antworten mit personenbezogenen Daten oder Zugangsdaten committen. Tests verwenden synthetische Fixtures.
9. Jede Schemaänderung braucht eine Room-Migration und einen Migrationstest. Jede API-Änderung braucht aktualisierte Schemas/Contract-Tests und Dokumentation.
10. Neue Dependencies begründen, Version pinnen bzw. kontrolliert katalogisieren sowie Lizenz, Wartungsstand und Android-13-/Python-Kompatibilität prüfen.
11. Jedes Briefing muss einen sichtbaren Link/Button zum zugehörigen YouTube-Video anbieten. Dafür ausschließlich eine aus der validierten Video-ID konstruierte kanonische HTTPS-URL verwenden.
12. RapidAPI ist ausschließlich ein standardmäßig deaktivierter Fallback nach geeigneten technischen Fehlern von `youtube-transcript-api`. Ein Aufruf ist nur bei aktivem Opt-in und vorhandenem Nutzer-Key zulässig. Den Key Keystore-gestützt verschlüsseln, aus Backups ausschließen, nur als sensitiven HTTPS-Header übertragen, backendseitig nie persistieren und in Logs/Fehlern vollständig redigieren.
13. Niemals den im Auftrag oder in Tickets/Chats genannten RapidAPI-Key übernehmen, testen oder committen. Da er offengelegt wurde, den Nutzer auf Widerruf/Rotation hinweisen; Beispiele verwenden ausschließlich erkennbare Platzhalter.

## Arbeitsablauf pro Änderung

1. Vor Arbeit `README.md`, diese Datei und weitere tiefer liegende `AGENTS.md` lesen.
2. Den kleinsten offenen Milestone bzw. vertikalen Teil davon wählen und Akzeptanzkriterium notieren.
3. Tests möglichst zuerst oder gemeinsam mit Produktionscode ergänzen.
4. Relevante Checks ausführen; mindestens Formatter/Linter, Unit-Tests und der betroffene Build. Android-UI-Änderungen wenn möglich auf Emulator/Zielgerät prüfen und visuell dokumentieren.
5. Sicherheits- und Datenschutzfolgen prüfen, insbesondere Logs, Netzwerkrequests und Persistenz.
6. In der Fortschrittstabelle und im Änderungsprotokoll dieser Datei nur tatsächlich abgeschlossene Arbeit dokumentieren. Datum im Format `YYYY-MM-DD`, konkrete Testbefehle und verbleibende Blocker nennen.
7. README aktualisieren, falls Architektur, Schnittstelle, Scope, Setup, Risiken oder Bedienung geändert wurden.

## Definitionen für Status

- `offen`: nicht begonnen.
- `in Arbeit`: Code/Research begonnen, Abnahmekriterium noch nicht erfüllt.
- `blockiert`: konkrete externe Voraussetzung verhindert die Abnahme; Ursache und nächster Schritt dokumentieren.
- `erledigt`: Abnahmekriterium erfüllt und relevante Checks bestanden.

Teilfortschritt macht einen Milestone nicht `erledigt`. Keine geschätzten Prozentwerte verwenden.

## Fortschrittsübersicht

| Milestone | Status | Letzte Änderung | Nachweis / nächster Schritt |
|---|---|---:|---|
| Planung und Research | erledigt | 2026-08-29 | Architektur, MVP-Scope, Datenmodell, API-Skizze, Risiken, Tests und Quellen in `README.md` dokumentiert. |
| M0 – Projektgerüst und Spikes | offen | 2026-08-29 | Als Nächstes Android-/Backend-Gerüste anlegen; Hosting-Transkript, Markdown-Renderer und Modellzugriff prüfen. |
| M1 – Vertikaler Happy Path | offen | 2026-08-29 | Direkteingabe bis persistierter Markdown-Detailansicht implementieren. |
| M2 – Android-Integration und Export | offen | 2026-08-29 | Share-Intent, Copy/Share und Zielgerätetest. |
| M3 – Stilverwaltung und Neuerstellung | offen | 2026-08-29 | CRUD, aktiver Stil und unveränderliche Snapshots. |
| M4 – Härtung und APK | offen | 2026-08-29 | Fehlermatrix, Tests, Signing und Gerätesmoke. |

## Bekannte Blocker / früh zu validierende Annahmen

- Der Auftrag verlangt `gpt-5.6-sol`; Zugriff und exakte API-Modell-ID im vorgesehenen OpenAI-Projekt sind noch nicht praktisch verifiziert. M0 muss dies vor weiterem Ausbau prüfen.
- Hosting-Plattform und Domain sind nicht gewählt. `youtube-transcript-api` kann von Rechenzentrums-IPs durch YouTube blockiert werden; der Hosting-Spike ist deshalb ein M0-Abnahmepunkt.
- YouTube Data API Key, OpenAI API Key, Android Application ID und Release-Keystore sind noch nicht eingerichtet.
- Der Markdown-Renderer ist bewusst erst nach einem M0-Lizenz-/Funktionsspike festzulegen.

## Entscheidungsprotokoll

| Datum | Entscheidung | Begründung |
|---|---|---|
| 2026-08-29 | Python-Backend statt eingebettetem Python auf Android | `youtube-transcript-api` ist Python-basiert; Backend beschleunigt MVP und schützt Provider-Schlüssel. |
| 2026-08-29 | YouTube Data API v3 für Metadaten | Transcript-Bibliothek deckt Untertitel ab, nicht verlässlich die geforderten Video-/Kanalinformationen. |
| 2026-08-29 | Room lokal, Backend zunächst zustandsarm | Offline-Historie auf einem primären Gerät bei geringer Backend-Komplexität. |
| 2026-08-29 | Briefings und Stilkonfigurationen per Snapshot historisieren | Neuerstellung darf alte Ergebnisse nicht überschreiben oder nachträglich verändern. |
| 2026-08-29 | Modell-ID konfigurierbar, aber ohne automatischen Fallback | Vorgabe `gpt-5.6-sol` einhalten und Fehlkonfiguration transparent machen. |
| 2026-08-29 | RapidAPI `youtube-transcripts` als Opt-in-Fallback | Kann technische Sperren des Primärproviders abfangen; explizite Aktivierung, eigener Key, Quotenhinweis und strikte Secret-Behandlung begrenzen Kosten- und Sicherheitsrisiken. |

## Änderungsprotokoll

### 2026-08-29 – Initiale Planung und Research

- Produktziel und MVP-Grenzen definiert.
- Architektur Android → FastAPI → Transcript/YouTube/OpenAI festgelegt.
- Milestones M0–M4 mit Abnahmekriterien, Teststrategie, Sicherheitsanforderungen, Risiken und Ausbaustufen dokumentiert.
- Research anhand der in `README.md` verlinkten Primärquellen durchgeführt.
- Dokumentationscheck: Zielphrase, Modell, Share/Direkteingabe, Markdown, Historie, Stil-CRUD, aktiver Stil und Neuerstellung als neuer Eintrag explizit aufgenommen.

### 2026-08-29 – Video-Link pro Briefing ergänzt

- Milestone/Scope: M1 – Vertikaler Happy Path.
- Umgesetzt: Verbindliche MVP-Anforderung für einen sichtbaren YouTube-Button in jeder Briefing-Detailansicht ergänzt; Resolver-Fallback, kanonische URL, Sicherheitsregel, Abnahmekriterium und UI-Testfall dokumentiert.
- Verifiziert mit: `git diff --check` und `rg -n "Video auf YouTube öffnen|Video-Button|Jedes Briefing" README.md AGENTS.md`.
- Manuell geprüft auf: entfällt, da in diesem Schritt ausschließlich Planung und Arbeitsanweisungen geändert wurden.
- Offen/Blocker: Implementierung und Test auf Android 13 bleiben Teil von M1.
- Relevante Entscheidung: Externe Video-Intents dürfen nur eine aus der validierten Video-ID rekonstruierte kanonische HTTPS-URL erhalten.

### 2026-08-29 – Opt-in-RapidAPI-Fallback geplant

- Milestone/Scope: M0-Adapter-Spike und M3-Integrationseinstellungen.
- Umgesetzt: RapidAPI `youtube-transcripts` als nachgelagerten, standardmäßig ausgeschalteten Fallback dokumentiert; zulässige Fehler, Einstellungs-UX, Free-Limit-Warnung, API-Header, Keystore-Speicherung, Backup-Ausschluss, Key-Redaktion und Testspezifikation ergänzt.
- Verifiziert mit: `git diff --check` und `rg -n "RapidAPI|X-LMAA-RapidAPI|100 Requests" README.md AGENTS.md`.
- Manuell geprüft auf: RapidAPI-Produktseite ohne Verwendung des offengelegten Keys abgerufen; realer Provider-Smoke-Test bewusst nicht durchgeführt.
- Offen/Blocker: Endpoint-Schema und aktueller Basic/Free-Tarif müssen beim Adapter-Spike im RapidAPI-Dashboard mit einem neu ausgestellten, nur lokal gespeicherten Key verifiziert werden.
- Relevante Entscheidung: Das Backend erhält den Nutzer-Key nur auftragsbezogen über einen sensitiven Header, hält ihn ausschließlich im Arbeitsspeicher und darf den Fallback ohne Opt-in plus Key nicht aufrufen.

## Vorlage für weitere Fortschrittseinträge

```markdown
### YYYY-MM-DD – Kurztitel

- Milestone/Scope:
- Umgesetzt:
- Verifiziert mit: `exakter Befehl` (Ergebnis)
- Manuell geprüft auf:
- Offen/Blocker:
- Relevante Entscheidung:
```
