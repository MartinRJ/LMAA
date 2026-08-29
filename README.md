# Local Media Analysis Assistant (LMAA)

Android-Anwendung zum Entgegennehmen von YouTube-Links, Ermitteln von Transkript und Metadaten und Erstellen dauerhaft gespeicherter, als Markdown dargestellter Video-Briefings mit der OpenAI API.

> **Das Ziel sei:** So schnell wie möglich einen auf einem Samsung Galaxy Tab S7+ 5G mit Android 13 / One UI 5.1.1 funktionierenden MVP bereitzustellen, der YouTube-Links sowohl über das Android-Teilen-Menü als auch per Direkteingabe annimmt, daraus mit `youtube-transcript-api` und `gpt-5.6-sol` ein konfigurierbares Briefing erzeugt, die Ergebnisse lokal historisiert und Markdown per Teilen oder Zwischenablage exportiert.

## 1. MVP-Umfang

### Muss-Funktionen

1. **Link erfassen**
   - Empfang von `Intent.ACTION_SEND` mit `text/plain` aus der YouTube-App.
   - Direkteingabe bzw. Einfügen eines Links in der App.
   - Normalisierung von `youtube.com/watch?v=…`, `youtu.be/…`, `/shorts/…` und `/live/…`; Whitelist der YouTube-Hosts und strikte Video-ID-Validierung.
   - Vor dem Start Vorschau der erkannten URL und Möglichkeit zum Abbrechen.
2. **Daten beschaffen**
   - Transkript über [`youtube-transcript-api`](https://github.com/jdepoix/youtube-transcript-api), bevorzugt in Deutsch, danach Originalsprache bzw. automatisch erzeugte Untertitel.
   - Optionaler Transkript-Fallback über die RapidAPI-API [`youtube-transcripts`](https://rapidapi.com/8v2FWW4H6AmKw89/api/youtube-transcripts). Er darf nur ausgeführt werden, wenn der Nutzer ihn in den App-Einstellungen ausdrücklich aktiviert und dort ein nichtleerer RapidAPI-Key hinterlegt ist. Primär bleibt immer `youtube-transcript-api`.
   - Der Fallback greift nur nach einem als technisch wiederholbar klassifizierten Fehler des Primärproviders (beispielsweise `RequestBlocked`, `IpBlocked` oder temporärer Abruffehler), nicht bei ungültigen URLs, privaten/gelöschten Videos oder bewusst abgebrochenen Requests. Jede Nutzung wird vor dem Request auf der Oberfläche kenntlich gemacht.
   - Video- und Kanalmetadaten separat über YouTube Data API v3 (`videos.list` mit `snippet,contentDetails,statistics`; optional `channels.list`).
   - Mindestens speichern: Video-ID, Titel, Kanal-ID/-name, Veröffentlichungsdatum, Dauer, Thumbnail-URL, Quell-URL, Transkriptsprache, Untertitelart und Abrufzeitpunkt. Statistiken sind optional und als zeitabhängiger Snapshot zu kennzeichnen.
   - Verständliche Fehlerzustände für kein Transkript, privates/gelöschtes/altersbeschränktes Video, ungültige URL, Rate Limit, Netzwerk- und Providerfehler; erneuter Versuch möglich.
3. **Briefing erzeugen**
   - Serverseitiger Aufruf der OpenAI Responses API mit dem Modellnamen **`gpt-5.6-sol`**.
   - Ergebnis ist Markdown. Der Prompt verlangt belegtreue Zusammenfassung ohne erfundene Aussagen und eine klare Kennzeichnung fehlender Informationen.
   - Lange Transkripte werden deterministisch nach Zeichen-/Tokenbudget segmentiert, segmentweise verdichtet und abschließend zusammengeführt (Map-Reduce); Segmentreihenfolge und Zeitbezüge bleiben erhalten.
4. **Briefing-Stile verwalten**
   - Stile anlegen, bearbeiten, löschen und genau einen aktiven Stil wählen.
   - Ein Stil enthält Name, Prompt/Anweisungen, optionale Ausgabesprache und Änderungszeitpunkt.
   - Ein mitgelieferter, nicht versehentlich löschbarer Standardstil stellt sicher, dass stets ein aktiver Stil existiert.
   - „Mit anderem Stil neu erstellen“ erzeugt **einen neuen Briefing-Datensatz** und überschreibt weder das ursprüngliche Briefing noch dessen Stil-Snapshot.
5. **Lesen und verwalten**
   - Startansicht mit chronologisch absteigender Liste bisheriger Briefings, Status, Videotitel, Kanal, Stil und Erstellzeit.
   - Detailansicht rendert übliches ChatGPT-Markdown (Überschriften, Listen, Hervorhebung, Links, Zitate, Inline-Code und Codeblöcke).
   - Jedes Briefing zeigt in der Detailansicht einen eindeutig beschrifteten Button bzw. Link „Video auf YouTube öffnen“. Er öffnet die kanonische Quell-URL des zugehörigen Videos über einen Android-Intent bevorzugt in der YouTube-App und ansonsten im Browser.
   - Volltext des Markdown-Ergebnisses über Android Sharesheet als `text/plain` teilen und über einen expliziten Button in die Zwischenablage kopieren.
   - Lade-, Leer- und Fehlerzustände sowie Wiederholen ohne Datenverlust.

### Bewusst nicht im ersten MVP

- Login, Synchronisation zwischen Geräten, kollaborative Bibliothek, Audio-Transkription für Videos ohne Captions, Playlist-/Kanal-Batchverarbeitung und Play-Store-Veröffentlichung.
- Offline-Erzeugung. Bereits gespeicherte Briefings müssen jedoch offline lesbar, kopierbar und teilbar sein.

## 2. Technische Entscheidung

### Architektur

```text
YouTube-App / Direkteingabe
           │ ACTION_SEND oder URL
           ▼
Android-App (Kotlin, Jetpack Compose, minSdk 26, targetSdk aktuell)
  UI → ViewModel → Repository → Room (lokale Source of Truth)
                         │ HTTPS + installierte App-Client-ID
                         ▼
Backend (Python, FastAPI)
  URL/Video-ID validieren
  ├─ youtube-transcript-api → Captions
  ├─ YouTube Data API v3   → Video-/Kanalmetadaten
  └─ OpenAI Responses API  → Markdown-Briefing
```

`youtube-transcript-api` ist eine Python-Bibliothek und keine Android/Kotlin-Bibliothek. Sie in die APK einzubetten würde Python-Runtime, Wartung und Netzwerkverhalten unnötig verkomplizieren. Ein kleines Python-Backend liefert den schnellsten robusten MVP und hält OpenAI- und YouTube-Schlüssel aus der APK. **Produktionsschlüssel dürfen niemals in App, Ressourcen, BuildConfig oder Repository liegen.**

Für einen persönlichen MVP kann das Backend zunächst auf einem kleinen HTTPS-Dienst laufen. Direkter Internetzugriff des Backends auf YouTube kann blockiert werden; die Bibliothek dokumentiert insbesondere IP-Sperren bei Cloud-Anbietern und Proxy-Unterstützung. Das Deployment muss daher mit realen Zielvideos getestet werden. Proxies sind nur eine Betriebsoption, kein Bestandteil des ersten Happy Paths.

### Android

- Kotlin, Jetpack Compose + Material 3, Navigation Compose.
- Room für `videos`, `briefings`, `styles` und optional `jobs`; DataStore nur für kleine App-Einstellungen.
- Retrofit/OkHttp oder Ktor Client; WorkManager für fortsetzbare Erzeugungsjobs. Ein eindeutiger Idempotency-Key verhindert doppelte Jobs bei Retry.
- Markdown-Renderer als austauschbarer Adapter. Auswahl im Spike anhand Android-13-Kompatibilität, CommonMark-Abdeckung, Linkbehandlung, Copy-Verhalten, Wartungsstand und Lizenz; kein unkontrolliertes WebView-HTML.
- Adaptive Zwei-Spalten-Darstellung auf dem Tablet ist wünschenswert, aber der MVP darf zunächst eine robuste Single-Pane-Navigation verwenden.
- `ACTION_SEND`-Intent-Filter ausschließlich für `text/plain`. Eingehenden Text nie ungeprüft als Netzwerkziel verwenden.
- Der Video-Button verwendet ausschließlich die aus der validierten Video-ID neu konstruierte kanonische HTTPS-URL; keine ungeprüfte Eingabe oder Backend-URL an einen externen Intent weiterreichen. Ist keine passende App installiert, übernimmt der Browser über den normalen Android-Resolver.

### Backend und externe Dienste

- Python-Version und Abhängigkeiten pinnen; FastAPI-Endpunkte mit Pydantic-Schemata.
- `youtube-transcript-api` liefert Untertitel, aber keine vollständigen Video-/Kanaldaten. Dafür wird die offizielle YouTube Data API v3 genutzt. `videos.list` kostet laut API-Dokumentation typischerweise eine Quota-Einheit; Abrufe cachen und nur benötigte `part`s abfragen.
- OpenAI-Aufruf ausschließlich im Backend. Standardmodell ist exakt `gpt-5.6-sol`; Modellname kommt serverseitig aus Konfiguration, damit ein Provider-Rollout ohne APK-Update korrigiert werden kann. Beim Startup/Healthcheck ist die Modellverfügbarkeit zu prüfen und ein nicht unterstütztes Modell klar zu melden, **nicht** stillschweigend auf ein anderes Modell zu wechseln.
- Rohtranskripte werden für Reproduzierbarkeit zunächst lokal in Room gespeichert. Der Server arbeitet für den MVP zustandsarm und löscht Request-Inhalte nach Abschluss aus temporären Speichern; keine Inhalts-Logs.
- Der optionale RapidAPI-Fallback ruft `GET https://youtube-transcripts.p.rapidapi.com/youtube/transcript` serverseitig mit `x-rapidapi-host: youtube-transcripts.p.rapidapi.com` und dem vom Client nur für diesen Auftrag gelieferten `x-rapidapi-key` auf. URL, validierte Video-ID, Sprache und begrenzte Chunk-Größe werden kontrolliert aufgebaut; Providerantworten werden in das interne Transkriptmodell normalisiert.

### Einstellungen für den RapidAPI-Fallback

- Schalter „RapidAPI-Fallback verwenden“, standardmäßig **aus**.
- Passwortfeld „RapidAPI-Key“ mit maskierter Darstellung sowie Aktionen zum Speichern, Ersetzen und Löschen. Ein Key wird weder vorbefüllt noch in Beispielen oder Diagnoseausgaben angezeigt.
- Aktivieren ist nur mit nichtleerem Key möglich. Löschen des Keys deaktiviert den Fallback atomar. Ein optionaler Verbindungstest zählt als API-Request und muss entsprechend beschriftet sein.
- Ein Key gilt erst nach einer erfolgreichen Providerantwort als praktisch validiert. Antwortet RapidAPI mit `401` oder `403`, markiert die App die Konfiguration als ungültig, zeigt eine Handlungsanweisung und verwendet den Fallback bis zum Ersetzen bzw. erneuten Prüfen des Keys nicht mehr.
- Der Key wird auf Android mit einem im Android Keystore geschützten Schlüssel verschlüsselt gespeichert und von Cloud-/ADB-Backups ausgeschlossen. Er gehört nicht in Room, DataStore-Klartext, `BuildConfig`, Ressourcen oder Telemetrie.
- Für einen Analyseauftrag wird der Key ausschließlich über HTTPS in einem sensitiven Request-Header an das eigene Backend gesendet. Das Backend hält ihn nur im Arbeitsspeicher, redigiert den Header in sämtlichen Logs/Traces, persistiert oder cached ihn nicht und leitet ihn ausschließlich an den fest konfigurierten RapidAPI-Host weiter.
- Die App zeigt einen lokalen Monatszähler erfolgreicher Fallback-Aufrufe und warnt vor dem dokumentierten Basic/Free-Limit von **100 Requests pro Monat**. Dieser Zähler ist nur eine Nutzungshilfe; maßgeblich sind Tarif und Abrechnung im RapidAPI-Dashboard. Bei `429` oder erschöpfter Quote erfolgt kein Retry-Sturm, sondern ein klarer Fehler mit Link zu den Einstellungen.
- Ein vom Nutzer eingegebener Key ist als Secret zu behandeln. Der im Projekt bereitgestellte Beispielkey wird ausdrücklich **nicht** in Repository, Dokumentation, Tests oder App-Voreinstellungen übernommen und sollte wegen seiner Offenlegung widerrufen bzw. rotiert werden.

## 3. Vorgeschlagene Datenmodelle

| Entität | Wesentliche Felder |
|---|---|
| `Video` | `videoId` (PK), `canonicalUrl`, `title`, `channelId`, `channelTitle`, `publishedAt`, `durationIso8601`, `thumbnailUrl`, `metadataJson`, `fetchedAt` |
| `Transcript` | `videoId` (FK), `provider` (`primary`/`rapidapi`), `languageCode`, `isGenerated`, `segmentsJson` (`text,start,duration`), `plainText`, `fetchedAt` |
| `BriefingStyle` | `id` (UUID), `name`, `instructions`, `outputLanguage`, `isActive`, `isBuiltIn`, `createdAt`, `updatedAt` |
| `Briefing` | `id` (UUID), `videoId` (FK), `styleId` (nullable FK), `styleNameSnapshot`, `styleInstructionsSnapshot`, `modelSnapshot`, `markdown`, `status`, `errorCode`, `createdAt`, `completedAt` |

Indizes: `Briefing(createdAt)`, `Briefing(videoId)`, `Video(channelTitle)`. Room-Migrationen sind ab Schema-Version 1 durch Tests abzusichern. Löschen eines benutzerdefinierten Stils ist verboten, solange er aktiv ist; historische Briefings bleiben durch Snapshots lesbar.

## 4. API-Skizze für den MVP

### `POST /v1/briefings`

Request:

```json
{
  "url": "https://youtu.be/VIDEO_ID",
  "style": {
    "name": "Standard",
    "instructions": "Erstelle ein sachliches Briefing …",
    "output_language": "de"
  },
  "client_request_id": "UUID"
}
```

Wenn der Fallback lokal aktiviert ist, sendet die App zusätzlich `X-LMAA-RapidAPI-Fallback: enabled` und den Key in `X-LMAA-RapidAPI-Key`. Beide Header sind sensitiv; der Key erscheint niemals im JSON-Körper, in Responses oder Logs. Ohne beide gültigen Header darf das Backend RapidAPI nicht aufrufen. Für die Implementierung ist zu prüfen, ob ein kurzlebiges, verschlüsseltes Secret-Envelope statt des direkten Headers den späteren Mehrnutzerbetrieb besser absichert.

Response (synchron nur wenn schnell genug, sonst `202` + Polling):

```json
{
  "job_id": "UUID",
  "status": "completed",
  "video": {},
  "transcript": {"language_code": "de", "is_generated": false, "segments": []},
  "briefing": {"model": "gpt-5.6-sol", "markdown": "# …"}
}
```

Ergänzend: `GET /v1/jobs/{id}`, `GET /healthz` und optional `DELETE /v1/jobs/{id}`. Stabiler Fehlerkörper: `code`, deutsche `message`, `retryable`, `details`. Keine internen Stacktraces an Clients. Request-Größenlimit, Timeouts, eingeschränkte CORS-Konfiguration, Authentisierung des persönlichen Clients und Rate Limiting sind vor öffentlichem Betrieb Pflicht.

## 5. Standard-Briefingstil

Der initiale Systemstil soll mindestens folgende Struktur anfordern:

```markdown
# Kernaussage
## Kurzfassung
## Wichtigste Punkte
## Argumentation und Belege
## Genannte Personen, Organisationen und Quellen
## Offene Fragen / Unsicherheiten
## Kapitel mit Zeitmarken
```

Anweisung: ausschließlich auf Transkript und Metadaten stützen; Meinungen des Videos als solche attribuieren; keine nicht belegten Fakten ergänzen; Unverständliches und fehlende Quellen explizit markieren; konkrete Zeitmarken verlinkbar ausgeben. Der individuelle Stiltext wird als Anweisung behandelt, nicht als vertrauenswürdiger Inhalt. Transkript und Metadaten sind untrusted data und werden im Prompt eindeutig abgegrenzt, um Prompt Injection zu reduzieren.

## 6. Milestones zum MVP

### M0 – Projektgerüst und technische Spikes (0,5–1 Tag)

- Android-Projekt mit Compose, minSdk 26, CI-Build und Debug-APK anlegen.
- FastAPI-Projekt, gepinnte Dependencies, `.env.example`, Secret-Ausschlüsse und Healthcheck anlegen.
- Auf dem tatsächlichen Hosting je ein manuelles, automatisch erzeugtes, deutsches und englisches Transkript testen.
- RapidAPI-Adapter ausschließlich mit Mock-Server/Fixtures implementieren; einen realen, vom Nutzer neu ausgestellten Key nur lokal für einen expliziten Smoke-Test verwenden.
- Verfügbarkeit und Zugriff auf `gpt-5.6-sol` mit dem vorgesehenen OpenAI-Projekt prüfen.
- Markdown-Renderer durch kleines Fixture mit allen geforderten Elementen festlegen.

**Abnahme:** App startet auf Android 13; Backend-Healthcheck grün; ein festes Testvideo liefert Transkript, Metadaten und Modellantwort. Unverfügbare Modell-ID ist als Blocker dokumentiert.

### M1 – Vertikaler Happy Path (1–2 Tage)

- Direkte URL-Eingabe → API → Transkript/Metadaten → OpenAI → Markdown-Detailansicht.
- Video-Button in der Detailansicht, der die kanonische YouTube-URL öffnet.
- Room-Speicherung und Briefingliste.
- Lade- und einfacher Fehlerzustand.

**Abnahme:** Nach App-Neustart bleibt ein erzeugtes Briefing offline lesbar; sein Video-Button öffnet bei bestehender Verbindung das richtige YouTube-Video.

### M2 – Android-Integration und Export (1 Tag)

- `ACTION_SEND` aus YouTube, kanonische URL-Erkennung und Deduplizierungsdialog.
- Markdown teilen und in Zwischenablage kopieren.
- Tablet-Layout, Zurücknavigation und rotierende/neu erzeugte Activity testen.

**Abnahme:** Ein Video wird auf dem Galaxy Tab aus YouTube an LMAA geteilt und das fertige Markdown anschließend an Telegram (oder einen Test-Share-Empfänger) übergeben.

### M3 – Stilverwaltung und Neuerstellung (1–2 Tage)

- CRUD-Oberfläche, aktiven Stil wählen, Default-Schutz und Validierung.
- Neuerstellung mit anderem Stil; neuer historischer Eintrag mit vollständigem Snapshot.
- Keine erneute Transkript-/Metadatenabfrage, sofern Cache frisch und vollständig ist.

**Abnahme:** Zwei Stile erzeugen zwei getrennte Listeneinträge zum selben Video; Löschen/Ändern des Stils verändert alte Briefings nicht.

Zusätzlich in M3: Integrationseinstellungen für den RapidAPI-Fallback (Opt-in, maskierter Key, verschlüsselte Ablage, Löschen, lokaler Monatszähler) umsetzen. **Abnahme:** Bei deaktiviertem Fallback oder fehlendem Key verlässt kein RapidAPI-Request das Backend; bei aktivierter gültiger Konfiguration wird RapidAPI erst nach einem zulässigen Primärfehler genau einmal aufgerufen.

### M4 – MVP-Härtung und auslieferbare APK (1–2 Tage)

- Fehler-Matrix, Retry/Idempotenz, Timeouts, Abbruch und lange Transkripte.
- Unit-, Room-, Backend- und Compose-UI-Tests; reale Smoke-Tests.
- Release-Signing lokal sicher einrichten, Datenschutz-/Betriebshinweise schreiben, APK erzeugen und auf Zielgerät testen.

**MVP-Definition-of-Done:** Alle Muss-Funktionen funktionieren auf Android 13, keine Secrets sind in APK/Git, Kernpfade besitzen automatisierte Tests, fünf repräsentative Videos sind erfolgreich verarbeitet, bekannte Einschränkungen sind dokumentiert und eine installierbare signierte APK liegt vor.

## 7. Teststrategie

- **Parser-Unit-Tests:** alle unterstützten URL-Formen, zusätzliche Share-Texte, ungültige Hosts, Lookalike-Domains, fehlende/ungültige IDs.
- **Backend-Unit-Tests:** Sprachfallback, Segmentierung, Promptzusammenbau, Fehler-Mapping, Idempotenz; externe APIs mit Fixtures mocken.
- **Fallback-Tests:** Opt-in/Key-Wahrheitstabelle, erlaubte und nicht erlaubte Primärfehler, Provider-Normalisierung, `401`/`403`/`429`, Timeout, genau ein Fallback-Aufruf sowie vollständige Key-Redaktion in Logs und Fehlern.
- **Contract-Tests:** JSON-Schemata App ↔ Backend einschließlich aller Fehlerkörper.
- **Room-Tests:** CRUD, aktiver Stil als Invariante, Snapshots, Migrationen, sortierte Historie.
- **Compose-/Instrumented-Tests:** Share-Intent, Eingabe, Liste, Detail, Video-Button mit verifizierter kanonischer URL, Copy/Share, Stil-CRUD, Prozessneustart.
- **Manueller Gerätesmoke:** Tab S7+ unter Android 13/One UI 5.1.1; Hoch-/Querformat, Dark Mode, große Schrift, Netzunterbrechung und Samsung Sharesheet.
- **Reale Medienmatrix:** manuelle/automatische Captions, DE/EN, sehr langes Video, Shorts-Link, keine Captions, privates Video.

Testvideos in automatisierten Tests nur über kontrollierte IDs/Fixtures referenzieren; keine fremden Transkripte ins Repository committen.

## 8. Datenschutz, Sicherheit und Betrieb

- Vor der ersten Analyse transparent anzeigen, dass URL, Metadaten und Transkript an das eigene Backend und Inhalte zur Verarbeitung an OpenAI übertragen werden.
- Datensparsame Logs: Request-ID, Dauer, Status und Fehlercode; keine API-Schlüssel, vollständigen URLs mit sensitiven Parametern, Transkripte oder Briefings.
- OpenAI-, YouTube- und nutzereigene RapidAPI-Keys gelten gleichermaßen als Secrets. Ein offengelegter Key ist sofort zu widerrufen und zu rotieren; Git-Historie und Artefakte sind zusätzlich auf den Wert zu prüfen.
- Android Network Security Config: nur HTTPS, kein Cleartext in Release. Backend-Schlüssel ausschließlich über Secret Store/Umgebungsvariablen.
- Markdown-Links nur nach Nutzeraktion öffnen; erlaubte Schemes (`https`, optional `http`) prüfen. HTML standardmäßig deaktivieren/sanitizen.
- YouTube-Nutzungsbedingungen, API-Richtlinien, Rechte am Transkript, OpenAI-Datenverarbeitung und notwendige Datenschutzerklärung sind vor Verteilung über den rein persönlichen Gebrauch hinaus gesondert zu prüfen.
- Datenlöschung in App (ein Briefing und gesamte lokale Historie) spätestens vor Beta ergänzen. Backups standardmäßig bewusst konfigurieren, da sie Briefings/Transkripte enthalten.

## 9. Risiken und Gegenmaßnahmen

| Risiko | Auswirkung | Gegenmaßnahme |
|---|---|---|
| YouTube ändert internen Caption-Zugriff | Transkriptabruf bricht | Version pinnen, Adapter kapseln, Monitoring/Fixtures, Updatepfad und optional später Audio-Fallback |
| Cloud-IP wird von YouTube blockiert | Häufige `RequestBlocked`-/`IpBlocked`-Fehler | Hosting-Spike M0, Backoff; nur bei Bedarf seriöser rotierender Proxy, keine Umgehung geschützter Inhalte |
| `gpt-5.6-sol` ist im Projekt nicht verfügbar | Keine Briefings | M0-Preflight; exakter, konfigurierbarer Modellname; sichtbarer Blocker statt stiller Modellwechsel |
| Sehr lange Transkripte | Kontextlimit, hohe Kosten/Latenz | Budgetprüfung, Map-Reduce, Fortschritt, Limit und Kostentelemetrie ohne Inhalte |
| Android beendet Prozess/Netz fällt aus | scheinbar verlorener Job/Duplikat | Room zuerst, WorkManager/Polling, Idempotency-Key, Resume/Retry |
| Prompt Injection im Transkript | verfälschtes Briefing | Daten delimitieren, Systemregeln priorisieren, keine Tools durch Modell, Ausgabe als untrusted Markdown behandeln |
| YouTube-Quota erschöpft | Metadaten fehlen | Caching, minimale Parts, Quota-Monitoring; Titel/Kanal ggf. klar als fehlend markieren |
| RapidAPI-Free-Quota erschöpft oder verursacht Kosten | Fallback fällt aus bzw. erzeugt unerwartete Kosten | Opt-in standardmäßig aus, lokaler Zähler/Warnung, kein automatischer Retry bei `429`, Dashboard als maßgebliche Anzeige |
| Nutzer-Key gelangt in Logs/Backups | Konto- und Kostenmissbrauch | Keystore-gestützte Verschlüsselung, Backup-Ausschluss, sensitive Header redigieren, nur In-Memory im Backend, Rotationshinweis |

## 10. Spätere Ausbaustufen

1. **Qualität:** Audio-Transkription als expliziter Fallback, Sprechererkennung, Kapitelbilder, Zitatansicht mit Sprung zur Zeitmarke, Faktencheck gegen vom Nutzer erlaubte Quellen.
2. **Organisation:** Suche, Filter, Tags, Favoriten, Ordner, Archiv, Export als Markdown-Datei/PDF, Sammelbriefings und Vergleich mehrerer Videos.
3. **Automatisierung:** Playlist-/Kanal-Abos, Hintergrundwarteschlange, Benachrichtigung bei Abschluss, Android App Links und Share mehrerer URLs.
4. **Personalisierung:** Stilvorlagen importieren/exportieren, Variablen, pro Kanal zugeordneter Standardstil, Vorschau und Stilversionen.
5. **Betrieb:** Nutzerkonten, verschlüsselte Synchronisation, Self-Hosting-Paket (Docker Compose), serverseitige Queue, Kostenbudgets, Observability und Admin-Ansicht.
6. **Barrierefreiheit/Tablet:** Master-Detail-Layout, Tastaturkürzel, TalkBack-Audit, dynamische Schrift, frei wählbare Darstellung.

## 11. Research-Notizen und Quellen

Stand der Recherche: **29. August 2026**.

- `youtube-transcript-api` unterstützt manuelle und automatisch erzeugte Untertitel, Übersetzung und benötigt keinen Headless Browser. Es ist eine inoffizielle Schnittstelle und dokumentiert typische Cloud-IP-Sperren sowie Proxy-Konfiguration: [Projekt-README](https://github.com/jdepoix/youtube-transcript-api).
- Android empfängt Text über einen Manifest-Intent-Filter für `ACTION_SEND`, `CATEGORY_DEFAULT` und `text/plain`: [Android Developers – Receive simple data](https://developer.android.com/develop/ui/compose/sharing/receive).
- Android teilt Text über `ACTION_SEND` und einen Sharesheet/`Intent.createChooser`: [Android Developers – Send simple data](https://developer.android.com/training/sharing/send).
- Offizielle Metadatenfelder und Quota-Kosten: [YouTube Data API – Videos: list](https://developers.google.com/youtube/v3/docs/videos/list) und [Channels: list](https://developers.google.com/youtube/v3/docs/channels/list).
- OpenAI-Integration basiert auf der serverseitigen [Responses API](https://platform.openai.com/docs/api-reference/responses). Den vom Auftrag vorgegebenen Modellnamen vor Implementierung gegen die Modellverfügbarkeit des konkreten Projekts prüfen.
- Optionaler Fallback: [RapidAPI – youtube-transcripts](https://rapidapi.com/8v2FWW4H6AmKw89/api/youtube-transcripts). Endpoint, Header und aktuelles Tariflimit vor Implementierung erneut im RapidAPI-Dashboard verifizieren; Limits und Preise können sich ändern.
