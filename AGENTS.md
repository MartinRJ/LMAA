# AGENTS.md – Arbeitsanweisung und Fortschritt für LMAA

Diese Datei gilt für das gesamte Repository. `README.md` und
`docs/anforderungs-vv.md` sind verbindlich mitzulesen.

## Ziel

> **Das Ziel sei:** So schnell wie möglich einen auf einem Samsung Galaxy Tab
> S7+ 5G mit Android 13 / One UI 5.1.1 eigenständig funktionierenden MVP
> bereitzustellen, der YouTube-Links über Share oder Direkteingabe annimmt, das
> Transkript primär lokal in der APK mit `youtube-transcript-api` abruft, daraus
> mit exakt `gpt-5.6-sol` ein Briefing erzeugt und Ergebnisse lokal verwaltet.

ASAP bedeutet: zuerst den vollständigen lokalen vertikalen Pfad liefern.
Server-, Cloud- oder Pro-Ausbaustufen dürfen diesen Pfad nicht verdrängen.

## Verbindliche Architektur

1. Die Produkt-App läuft ohne LMAA-eigenen Server, Hosting-Dienst, Domain oder
   PC. Keine Produktfunktion darf einen Backend-Host voraussetzen.
2. Android: Kotlin, Jetpack Compose/Material 3, Room als lokale Source of Truth,
   minSdk 26; Android 13 auf dem Galaxy Tab ist zwingendes Testziel.
3. `youtube-transcript-api==1.2.4` läuft primär über Chaquopy 17/Python 3.10
   innerhalb der APK. Dieser Gerätepfad ist auf dem Zieltablet verifiziert und
   bleibt vor jedem optionalen RapidAPI-Pfad auszuführen.
4. YouTube oEmbed, OpenAI Responses und optional RapidAPI werden direkt von der
   App über HTTPS aufgerufen. Es gibt keinen App→FastAPI-Produktvertrag.
5. Das OpenAI-Modell heißt exakt `gpt-5.6-sol`. Nie still auf ein anderes
   Modell wechseln.
6. OpenAI- und RapidAPI-Key verwenden persönliches BYOK und werden ausschließlich
   vom Nutzer zur Laufzeit eingegeben. Persistiert wird nur Tink-AEAD-Ciphertext
   in Proto DataStore; das Tink-Keyset wird mit einem nicht exportierbaren
   Android-Keystore-Schlüssel geschützt. Secret-Store und App-Daten sind aus
   Cloud-/D2D-Backups auszuschließen. Nach dem Speichern zeigt die UI nur die
   konstante Maske `****`, nie Präfix, Länge oder Klartext. Normale
   `SharedPreferences` und `EncryptedSharedPreferences` sind keine Zielarchitektur.
7. Die direkte OpenAI-Nutzung ist eine auf den persönlichen Sideload-MVP
   begrenzte Sicherheitsausnahme. Kein Key darf in APK, Git, Ressourcen,
   `BuildConfig`, Logs, Screenshots, Fixtures oder Telemetrie erscheinen.
8. RapidAPI ist eine frei konfigurierbare, standardmäßig ausgeschaltete
   Transkriptquelle mit den Modi `Aus`, `Nur als Fallback` und `RapidAPI
   bevorzugt`. Provider werden nie parallel aufgerufen; ein technischer Fehler
   darf genau einen Aufruf des jeweils anderen Providers auslösen.
9. RapidAPI-Profile sind deklarativ: ausschließlich HTTPS auf
   `*.p.rapidapi.com`, GET/POST, begrenzte Query-/Header-/Body-Templates,
   Erfolgsstatuscodes, Timeouts und Antwortlimit. Unterstützte Platzhalter sind
   `{{canonical_url}}`, `{{video_id}}`, `{{language}}` und
   `{{rapidapi_key}}`; der Key darf nur vollständiger Wert von
   `X-RapidAPI-Key` sein. cURL wird nur eingeschränkt geparst und nie ausgeführt.
10. RapidAPI-Antworten nicht providerspezifisch deserialisieren. Nach
    Transport-, Status-, Content-Type-, UTF-8- und Größenprüfung den kompletten
    Body inhaltlich unverändert als UNTRUSTED-Block an OpenAI übergeben.
11. Jede RapidAPI-Anfrage wird lokal technisch gezählt. Aktueller
    Entwicklungsstand: 5 Versuche/5 Erfolge. Die App kennt den Tarif nicht;
   `/100` und der daraus berechnete Restwert sind ausschließlich als
   Basic-Tarif-Hinweis zu kennzeichnen, das Dashboard ist maßgeblich.
12. Der Code unter `backend/` ist ein Referenz- und Testprototyp. Er darf
    Providersemantik und Prompts belegen, ist aber weder Produktlaufzeit noch
    Nachweis für lokale Android-Anforderungen.
13. Eingehende URLs, Providerdaten, Transkripte und Modell-Markdown sind
    unvertrauenswürdig. Hosts/IDs validieren, HTML nicht ausführen,
    Link-Schemes begrenzen und keine Modell-Tools aktivieren.
14. Alte Briefings sind unveränderliche historische Ergebnisse. Neuerstellung
    erzeugt einen neuen Datensatz mit Stil- und Modell-Snapshot.
15. Redaktionelle Vorgaben wie Pflichtabschnitte, Quellenkritik,
    Unsicherheitsbehandlung, Zeitmarken und Markdown gehören ausschließlich in
    den geschützten Default-Stil. Benutzerdefinierte Stile bestimmen Inhalt,
    Auswahl, Struktur, Sprache und Ausgabeformat ohne globale Strukturprüfung.
    Unveränderlich bleiben nur technische Sicherheitsgrenzen für UNTRUSTED-
    Daten, deaktivierte Modell-Tools/externe Faktenquellen, leere Ausgaben und
    aktives unsicheres Markup.
16. Jedes Briefing zeigt einen sichtbaren Link/Button zur ausschließlich aus
    der validierten Video-ID konstruierten kanonischen HTTPS-URL.
17. Keine vollständigen fremden Transkripte oder realen inhaltlichen
    Providerantworten committen. Tests verwenden synthetische Fixtures.
18. Jede Room-Schemaänderung braucht eine Migration und einen Migrationstest.
19. Neue Dependencies werden begründet, kontrolliert gepinnt und auf Lizenz,
    Wartungsstand, Android 13, ARM64 und Offline-Build-Reproduzierbarkeit geprüft.
20. `connectedDebugAndroidTest` darf nicht gegen die täglich genutzte
    Debug-Installation laufen: Der Gradle-/UTP-Lauf kann App-Daten einschließlich
    BYOK und Historie ersetzen. Gerätetests stattdessen über eine isolierte
    Test-Application-ID oder datenbewahrend per gezieltem `adb install -r` plus
    Instrumentation ausführen.

## Arbeitsreihenfolge pro Änderung

1. `README.md`, diese Datei und `docs/anforderungs-vv.md` vollständig lesen.
2. Anforderung validieren: Trifft sie den persönlichen Tablet-Workflow und ist
   sie widerspruchsfrei?
3. Nachweis festlegen: Welcher Test verifiziert die Umsetzung auf dem
   Zielgerät?
4. Den kleinsten offenen lokalen vertikalen Teil umsetzen. Bis TRN-001
   verifiziert ist, hat Chaquopy/`youtube-transcript-api` Vorrang vor
   RapidAPI-, Backend- oder Komfortarbeit.
5. Tests möglichst zuerst oder gemeinsam mit Produktionscode ergänzen.
6. Formatter/Linter, Unit-Tests und betroffenen Build ausführen.
   Android-/Python-Bridgeänderungen zusätzlich auf dem echten Tablet prüfen.
7. Secrets, Backups, Logs, Netzwerkziele und Providerkontingente prüfen.
8. Fortschritt nur für tatsächlich bestandene Nachweise dokumentieren.
9. README/V&V aktualisieren, wenn Anforderungen, Architektur, Risiken,
   Schnittstellen oder Abnahmekriterien geändert werden.

## Statusdefinitionen

- `offen`: nicht begonnen.
- `in Arbeit`: begonnen, aber Abnahmekriterium nicht erfüllt.
- `blockiert`: konkrete externe Voraussetzung verhindert die Abnahme.
- `erledigt`: validierte Anforderung implementiert und verifiziert.

Desktop- oder Backend-Erfolg macht einen Android-Gerätepfad nicht `erledigt`.
Keine geschätzten Prozentwerte verwenden.

## Fortschrittsübersicht

| Milestone | Status | Letzte Änderung | Nachweis / nächster Schritt |
|---|---|---:|---|
| Planung und Research | erledigt | 2026-08-30 | Anforderungen auf autonome Tablet-App korrigiert; V&V-Matrix und Primärquellen dokumentiert. |
| M0 – Lokale Laufzeit und Spikes | erledigt | 2026-08-30 | Vollständiger Android-Pfad, BYOK, oEmbed/OpenAI, kurzer/langer/Short-Gerätesmoke und Android-RapidAPI-Mockvertrag sind verifiziert. |
| M1 – Persistenter Happy Path | erledigt | 2026-08-30 | Ein-Schritt-Pipeline, Room-Historie, eigener Detail-View und Kaltstart-/Offline-Smoke sind erfüllt. |
| M2 – Share und Export | erledigt | 2026-08-30 | `ACTION_SEND`, Copy/Share, persistente WorkManager-Wiederaufnahme sowie Hoch-/Querformat, Dark Mode und 150-%-Schriftgröße sind auf Android 13 verifiziert. |
| M3 – Stile und Fallback-Einstellungen | erledigt | 2026-08-30 | Settings, Stil-CRUD/-Snapshots, RapidAPI-BYOK/Opt-in/Zähler, Duplikathinweis und Briefing-Löschung sind auf Android 13 verifiziert. |
| M4 – Härtung und APK | erledigt | 2026-08-30 | Fehlermatrix, zwölf isolierte Gerätetests, konfigurierbarer RapidAPI-Raw-E2E, Secret-Scan, RSA-4096-Release-Signing, Clean-Cutover sowie Icon/Theme-Smoke auf Android 13 bestanden. |
| Post-MVP – YouTube-Linkregistrierung | erledigt | 2026-08-30 | Exaktes `ACTION_SEND`-/`text/plain`-Ziel positiv und alle erweiterten Typen/Actions negativ auf Android 13 verifiziert; Direct-Share-Versuch entfernt, LMAA erfolgreich per Samsung Good Lock angeheftet. |

## Früh zu validierende Annahmen

- Chaquopy 17, Python 3.10, ARM64 und die gepinnten Transcript-Pakete bauen
  online wie offline. Reale manuelle/automatische Transkripte bis 7.311 Segmente
  wurden auf dem Tablet abgerufen; APK-Größe beträgt rund 27,8 MB.
- `youtube-transcript-api` nutzt eine undokumentierte YouTube-Schnittstelle;
  Änderungen oder IP-Sperren bleiben möglich.
- Der direkte OpenAI-Key im persönlichen Client widerspricht der allgemeinen
  OpenAI-Empfehlung für Client-Apps. BYOK, DataStore/Tink mit Android Keystore,
  Backup-Ausschluss, projektgebundener Key, hartes Spend-Limit und
  Sideload-Grenze reduzieren, beseitigen das Risiko aber nicht.
- Das offizielle AndroidX-Modul `datastore-tink` ist derzeit Alpha. Gewählt ist
  deshalb stabiles Proto DataStore 1.2.1 mit eigenem Tink-1.23-AEAD-Serializer;
  Keyset-, Ciphertext- und Kaltstart-Smokes sind auf Android 13 bestanden.
- Der direkte Android-Responses-Request mit exakt `gpt-5.6-sol`, `store=false`
  und leerer Toolliste ist auf dem Zieltablet praktisch verifiziert.
- oEmbed-Felder Kanal-ID, Veröffentlichungsdatum und Dauer sind im MVP nullable.
- Die vorläufige Application ID lautet `de.lmaa.app`; Release-Key und
  endgültige ID folgen vor M4.

## Entscheidungsprotokoll

| Datum | Entscheidung | Begründung |
|---|---|---|
| 2026-08-29 | Python/FastAPI-Backend vorgesehen | Ursprüngliche Planung zur einfachen Python-Einbindung; am 2026-08-30 wegen falscher Interpretation von „lokal“ aufgehoben. |
| 2026-08-30 | Autonome Android-App ohne LMAA-Backend | Entspricht dem persönlichen Tablet-Workflow, vermeidet Hosting/Betriebskosten und macht RapidAPI nur zum seltenen Fallback. |
| 2026-08-30 | Chaquopy 17/Python 3.10 für den Primärprovider | Offizielle Matrix passt zu AGP 9.2.1/minSdk 26/ARM64; APK-Build, Offline-Rebuild und mehrere reale Geräteabrufe sind verifiziert. |
| 2026-08-30 | Direkte OpenAI-Nutzung mit persönlichem BYOK | Kein eigener Server gewünscht; Risiko wird durch nutzereingegebenen restriktiven Key, DataStore/Tink, Android Keystore, Backup-Ausschluss und Spend-Limit begrenzt. |
| 2026-08-30 | Proto DataStore + Tink AEAD als Secret-Store | Nur Ciphertext wird persistiert; das Tink-Keyset ist über Android Keystore geschützt. `EncryptedSharedPreferences` wurde wegen Deprecation verworfen; unverschlüsselte Preferences sind verboten. |
| 2026-08-30 | Stabiler eigener DataStore-AEAD-Serializer statt `datastore-tink` Alpha | Proto DataStore 1.2.1 und Tink Android 1.23.0 sind stabil; ein nicht exportierbarer Keystore-AES-GCM-Schlüssel schützt das verschlüsselte Tink-Keyset im No-Backup-Bereich, ohne SharedPreferences oder Klartextfallback. |
| 2026-08-30 | YouTube oEmbed als MVP-Metadatenpfad | Titel, Kanalname und Thumbnail genügen ohne zusätzlichen Key; weitere Felder bleiben nullable. |
| 2026-08-29 | Room und Snapshots | Briefings bleiben offline und unveränderlich; Stiländerungen verändern keine Historie. |
| 2026-08-30 | Room 2.8.4 mit exportiertem Schema 1 | Reifer stabiler AndroidX-Zweig statt des unmittelbar zuvor erschienenen Room-3-Major-Releases; Video, Transkript und Briefing werden normalisiert und Briefing-/Stil-/Modellstände historisiert. |
| 2026-08-30 | Room-Schema 2 plus WorkManager 2.11.2 für Analyseaufträge | Persistenter Jobstatus vor Providerzugriffen, eindeutige Work-Namen, Foreground-Ausführung und atomare Ergebnistransaktion ermöglichen Wiederaufnahme ohne doppelte Briefings. |
| 2026-08-30 | Share-Intent wird genau einmal konsumiert | Direkteingabe und Share nutzen denselben Orchestrator; ein konsumiertes Share-Event darf bei Rücknavigation keinen zweiten Providerrequest starten. |
| 2026-08-29 | RapidAPI als Opt-in-Fallback | Schützt gegen geeignete Primärfehler; Quote und Kosten erfordern strikte Nachordnung. |
| 2026-08-30 | Nativer Compose-Markdown-Renderer | Zielgerätetest deckt Sicherheitsregeln, Code sowie horizontalen und vertikalen Scroll ab. |
| 2026-08-30 | Isolierte Application ID für Instrumentierung | `de.lmaa.app.testbed` verhindert, dass reguläre Gerätetests BYOK und Room-Historie der täglichen App ersetzen. |
| 2026-08-30 | Redaktionelle Promptvorgaben liegen im Default-Stil | Benutzerdefinierte Stile sollen Struktur und Ausgabe möglichst frei kontrollieren; nur technische Daten-/Sicherheitsgrenzen bleiben global. |

## Änderungsprotokoll

### 2026-08-29 – Ursprüngliche Planung

- Android-/FastAPI-Architektur, Milestones, Datenmodell und Risiken geplant.
- RapidAPI als standardmäßig deaktivierter Fallback spezifiziert.
- Video-Link, Stil-Snapshots und lokale Room-Historie aufgenommen.

### 2026-08-30 – Gerüste und Referenzspikes

- Android-Gerüst, URL-Parser und sicherer Compose-Markdown-Renderer umgesetzt.
- Debug-APK auf Galaxy Tab S7+ 5G unter Android 13 installiert und geprüft.
- FastAPI-Referenzprototyp für Primärtranskript, oEmbed, RapidAPI und
  `gpt-5.6-sol` erstellt; 50 Python-Tests bestanden.
- Primärtranskripte und umfangreiche Modellbriefings auf dem Desktop live
  getestet.
- RapidAPI lokal dreimal erfolgreich aufgerufen; Zähler 3/100.

Diese Nachweise belegen Providerlogik, aber nicht die autonome
Android-Produktarchitektur.

### 2026-08-30 – Architekturabweichung festgestellt und Anforderungen korrigiert

- Milestone/Scope: Anforderungs-V&V und M0-Neuausrichtung.
- Ursache: „lokal“ war in der ursprünglichen Planung nur als lokale Historie
  interpretiert worden; Transkript und Briefing wurden fälschlich in ein
  FastAPI-Backend verlagert.
- Korrektur: Kein LMAA-Server. Primärtranskript per Chaquopy auf dem Tablet;
  oEmbed/OpenAI/RapidAPI direkt aus Android.
- Validation: Chaquopy-Kompatibilität, Python-Paketmetadaten, Android-Keystore,
  Backupregeln und OpenAI-Client-Key-Hinweis anhand der in README/V&V
  verlinkten Primärquellen geprüft.
- Verification: Markdown/UI bestehen; lokale Transkript- und direkte
  OpenAI-Pfade sind korrekt als offen markiert. FastAPI bleibt nur
  Referenzprototyp.
- RapidAPI: Keine weiteren Live-Aufrufe für die Architekturkorrektur; Stand
  bleibt 3/100.
- Nächster Schritt: Chaquopy-Integration und realer
  `youtube-transcript-api`-Abruf auf dem Zieltablet.

### 2026-08-30 – BYOK-Secret-Store korrigiert

- Milestone/Scope: Anforderungs-V&V und M0-Security-Spike.
- Validierte Anforderung: OpenAI- und RapidAPI-Key werden per persönlichem BYOK
  eingegeben, nur als Tink-AEAD-Ciphertext in Proto DataStore persistiert und
  durch ein Android-Keystore-geschütztes Tink-Keyset abgesichert.
- Korrektur: Die zwischenzeitlich fälschlich als Stakeholderentscheidung
  dokumentierte Nutzung von `EncryptedSharedPreferences` wurde aufgehoben.
  Normale und verschlüsselte SharedPreferences sind keine Zielarchitektur.
- UI-/Backup-Invarianten: konstante Maske `****`, leeres Ersetzungsfeld, kein
  Klartext in Logs/Backups und vollständiges Entfernen beim Löschen.
- Offen: Das offizielle `datastore-tink`-Modul ist Alpha. M0 entscheidet per
  Dependency-/Gerätespike zwischen diesem Modul und stabilem Proto DataStore
  mit eigenem Tink-AEAD-Serializer.
- Verifiziert mit: Dokumentations-Konsistenzsuche und `git diff --check`;
  Implementierung und Zielgerätetest bleiben offen.
- RapidAPI: keine Live-Anfrage; Entwicklungsstand unverändert 3/100.

### 2026-08-30 – Lokaler Primärtranskriptpfad auf Android verifiziert

- Milestone/Scope: M0, TRN-001 und TRN-002.
- Validierte Anforderung: `youtube-transcript-api==1.2.4` läuft ohne API-Key,
  Backend oder PC innerhalb der Android-App; RapidAPI bleibt nachgelagerter
  Opt-in-Fallback.
- Umgesetzt: Chaquopy 17.0.0/Python 3.10 für `arm64-v8a`, vollständig gepinnte
  Python-Abhängigkeiten, schmale JSON-Bridge, kontrollierte Fehlercodes,
  Coroutine-Ausführung außerhalb des Main Threads und produktive Compose-UI.
- Verifiziert mit: Ruff/Format, 53 Pytest-Tests, Gradle
  `testDebugUnitTest assembleDebug lintDebug`, zusätzlichem Offline-Rebuild und
  APK-Secret-Scan; alle Checks bestanden.
- Manuell/ADB-geprüft auf Galaxy Tab S7+ 5G, Android 13: manuelles Deutsch
  (60 Segmente), automatisch erzeugtes Deutsch (1.660), automatisch erzeugtes
  Englisch (27) und langes Englisch (7.311/210.682 Zeichen). Die produktive
  URL→Abruf-UI sowie `INVALID_VIDEO_ID` wurden ebenfalls verifiziert.
- Toolchainbefund: Chaquopy 17 ist nicht mit Gradles Configuration Cache
  kompatibel; dieser ist projektweit deaktiviert, normale Build-Caches und
  Parallelisierung bleiben aktiv. Die Debug-APK ist rund 27,8 MB groß.
- Offen: in diesem damaligen Zwischenstand waren oEmbed/OpenAI, BYOK und Short
  noch offen; die nachfolgenden M0-Nachweise haben diese Punkte geschlossen.
- RapidAPI: kein Aufruf; Entwicklungsstand unverändert 3/100.

### 2026-08-30 – BYOK, oEmbed und direkter OpenAI-Pfad auf Android verifiziert

- Milestone/Scope: M0, LOC-001, MET-001, AIG-001 und SEC-001.
- Validierte Anforderung: Die persönliche App erzeugt ohne LMAA-Server ein
  Briefing aus lokalem Transkript, schlüssellosem oEmbed und direktem
  `gpt-5.6-sol`; der nutzereingegebene Key bleibt Keystore-gestützt geschützt.
- Umgesetzt: Proto DataStore 1.2.1 mit eigenem Tink-1.23-AEAD-Serializer,
  Android-Keystore-Keysetschutz, No-Backup-Dateien und vollständige
  Backup-Ausschlüsse; feste `****`-Maske, leeres Ersetzungsfeld und Löschen;
  direkte oEmbed-/Responses-Adapter, deterministisches Map-Reduce und sicherer
  Markdown-Pfad.
- Verifiziert mit: 15 JVM-Tests einschließlich MockWebServer-Verträgen, Gradle
  `testDebugUnitTest assembleDebug lintDebug assembleDebugAndroidTest`, APK-/
  Git-Secret-Scan und Android-Instrumentierungstest; alle bestanden.
- Manuell/ADB-geprüft auf Galaxy Tab S7+ 5G, Android 13: Key nach Kaltstart nur
  als `****`, leeres Ersetzungsfeld, kurzer Gesamtpfad mit 27 Segmenten sowie
  umfangreicher Map-Reduce-Gesamtpfad mit 7.311 Segmenten/210.682 Zeichen;
  oEmbed und reale Briefings mit exakt `gpt-5.6-sol`, allen
  Pflichtüberschriften, Zeitmarken und Inline-Code. Keine
  AndroidRuntime-Exception.
- Offen/Blocker: in diesem Zwischenstand waren Short-Smoke und Android-
  RapidAPI-Mockvertrag noch offen; beide wurden im folgenden M0-Abschluss
  verifiziert. Room-Persistenz beginnt in M1.
- Relevante Entscheidung: stabiles Proto DataStore mit eigenem AEAD-Serializer
  statt des Alpha-Moduls `datastore-tink`; kein Klartext- oder
  SharedPreferences-Fallback.
- RapidAPI: kein Aufruf; Entwicklungsstand unverändert 3/100.

### 2026-08-30 – M0 mit Short- und Android-Fallbacknachweis abgeschlossen

- Milestone/Scope: M0, TRN-001, FAL-001 und COST-001.
- Validierte Anforderung: Shorts werden über denselben lokalen Primärprovider
  kontrolliert verarbeitet; RapidAPI bleibt ausschließlich Opt-in-Fallback für
  geeignete technische Primärfehler und verbraucht bei semantischen Fehlern
  keine Quote.
- Umgesetzt: Android-`TranscriptProvider`-Vertrag,
  `RapidApiTranscriptProvider` mit festem HTTPS-Host und sensitiven Headern
  sowie `TranscriptFallbackResolver` mit geschlossener Fehler-Whitelist.
- Verifiziert mit: 22 Android-JVM-Tests, darunter MockWebServer-Verträge und
  vollständige Fallback-Wahrheitstabelle; Gradle
  `testDebugUnitTest assembleDebug lintDebug` bestanden.
- Manuell/ADB-geprüft auf Galaxy Tab S7+ 5G, Android 13: Der explizite Short
  `engQjz-Lm54` wurde aus `/shorts/` kanonisiert und lieferte kontrolliert
  `TRANSCRIPTS_DISABLED`. RapidAPI wurde regelkonform nicht aufgerufen. Ein
  erfolgreicher Short mit aktivem CC bleibt ein ergänzender Regressionstest,
  ist aber kein M0-Abnahmeblocker.
- Offen/Blocker: keine für M0. Als Nächstes M1 mit Room-persistiertem Happy Path.
- Relevante Entscheidung: Fallback nur für `REQUEST_BLOCKED` und
  `REQUEST_FAILED`; kein Fallback für fehlende/gesperrte Captions, ungültige
  IDs, nicht verfügbare Videos, interne App-Fehler oder Abbruch; kein Retry bei
  HTTP 429.
- RapidAPI: kein Live-Aufruf; Entwicklungsstand unverändert 3/100.

### 2026-08-30 – M1 mit Ein-Schritt-Pipeline und Room-Historie abgeschlossen

- Milestone/Scope: M1 sowie vorgezogene Teile von M2.
- Validierte Anforderung: Linkprüfung, lokales Transkript und Briefing-Erzeugung
  sind aus Nutzersicht eine Aktion; das Ergebnis liegt in einem eigenen View
  und bleibt nach App-Neustart offline in einer unveränderlichen Historie.
- Umgesetzt: Sequenzieller Pipeline-Orchestrator, einzelner Analyse-Button,
  phasenbezogener Lade-/Abbruch-/Fehlerzustand, `ACTION_SEND` mit automatischem
  Start, Consume-once-Semantik, eigener Detail-View, Video-/Copy-/Share-Aktionen
  sowie Room 2.8.4 mit normalisierten Video-/Transkript-/Briefing-Tabellen,
  Stil-/Modell-Snapshots und exportiertem Schema 1.
- Verifiziert mit: 25 JVM-Tests, Gradle `--offline testDebugUnitTest
  assembleDebug lintDebug assembleDebugAndroidTest`, zwei Android-13-
  Instrumentierungstests und Room-Datenbanktest mit zwei unveränderlichen
  Einträgen sowie Schließen/Wiederöffnen; alle inhaltlichen Checks bestanden.
- Manuell geprüft auf: Galaxy Tab S7+ 5G, Android 13. Der CC-Short
  `Rq5iOD-mcEI` durchlief Share → lokales Transkript → oEmbed →
  `gpt-5.6-sol` → Room → Detailansicht. Zwei absichtliche Analysen erzeugten
  exakt zwei Historieneinträge. Nach APK-Update und Kaltstart waren Key-Maske
  und Historie vorhanden; der Detail-View öffnete aus Room ohne neue Analyse.
  Der Nutzer bestätigte zusätzlich das Teilen direkt aus dem Samsung-/YouTube-
  Sharesheet an die installierte LMAA-App.
- Offen/Blocker: M1 hat keine offenen Abnahmepunkte. M2 behält
  Prozesswiederaufnahme und Layoutvarianten. Für M3 ist eine eigene
  Settings-View verbindliches nächstes UX-To-do; Keyverwaltung verlässt den
  Home-View.
- Testinfrastruktur-Befund: Ein Gradle-UTP-`connectedDebugAndroidTest` ersetzte
  unerwartet die Daten der täglichen Debug-Installation. Lokale Key-Dateien
  blieben unberührt; der App-Key wurde anschließend vom Nutzer neu eingegeben.
  Dieser Task darf künftig nur isoliert oder datenbewahrend per gezielter
  Instrumentation laufen.
- Relevante Entscheidung: Share-Events werden nach Start atomar konsumiert;
  Rücknavigation darf keinen Providerrequest wiederholen.
- RapidAPI: kein Aufruf; Entwicklungsstand unverändert 3/100.

### 2026-08-30 – M2 mit Prozesswiederaufnahme und adaptivem Tablet-Layout abgeschlossen

- Milestone/Scope: M2, DAT-001, UX-001, UI-002, INT-001 und TST-001.
- Validierte Anforderung: Lange Analysen dürfen Activity-/Prozessneustarts nicht
  an die Compose-Lebensdauer koppeln; das tägliche Tablet benötigt lesbare,
  getrennt scrollbare Ansichten in Hoch-/Querformat, Dark Mode und großer
  Systemschrift.
- Umgesetzt: Room-Schema 2 mit `analysis_jobs`, Migration 1→2, atomare
  Job-/Briefing-Transaktion, WorkManager-`CoroutineWorker` mit eindeutigem
  Auftrag, Netzwerkbedingung, Foreground-Benachrichtigung und Reconciliation
  nach Kaltstart. Home und Detail verwenden ab ausreichender effektiver Breite
  Zwei-Spalten-Layouts; große Schrift fällt einspaltig zurück. Theme und
  Systemleisten folgen dem System-Dark-Mode.
- Verifiziert mit: Gradle `--offline testDebugUnitTest assembleDebug
  assembleDebugAndroidTest lintDebug` (100 Tasks, erfolgreich), gezielter
  Android-Instrumentierung für Job-Reopen, Migration 1→2, atomare Persistenz
  und bestehende immutable Historie (4 Tests, alle bestanden).
- Manuell geprüft auf: Galaxy Tab S7+ 5G, Android 13. Force-Stop während
  `BRIEFING`, Kaltstart mit wiederhergestellter URL/Phase und Abschluss zu exakt
  einem neuen Historieneintrag. Zwei getrennte Scrollbereiche im Querformat,
  Dark-Mode-Kontrast sowie 150-%-Schrift in Home und Detail wurden visuell und
  per UI-Hierarchie geprüft. Ein finaler Short-ohne-Captions-Smoke startete und
  stoppte den WorkManager-Foreground-Dienst kontrolliert ohne Crash oder
  Historieneintrag.
- Offen/Blocker: keine für M2. M3 beginnt mit der verbindlichen Settings-View,
  danach Stilverwaltung und RapidAPI-Opt-in/Zähler.
- Testinfrastruktur: Keine UTP-Neuinstallation. Gezielte Instrumentierung und
  `adb install -r` erhielten BYOK-Maske sowie vier persönliche
  Historieneinträge; die Tablet-Systemwerte wurden nach Layouttests exakt
  zurückgestellt.
- Relevante Entscheidung: Der App-Abbruchknopf markiert den Room-Job zuerst als
  abgebrochen und storniert danach WorkManager. Systemunterbrechungen bleiben
  dagegen wiederanlauffähig.
- RapidAPI: kein Live-Aufruf; Entwicklungsstand unverändert 3/100.

### 2026-08-30 – M3 Settings, Stile und Historienpflege abgeschlossen

- Milestone/Scope: M3, FAL-001, SEC-001, DAT-001, UX-002, HIS-001,
  STY-001 und EXP-001.
- Umgesetzt: Eigene Settings-Ansicht statt Keyeingabe auf Home; OpenAI- und
  RapidAPI-BYOK über denselben DataStore/Tink/Keystore-Store; RapidAPI-Opt-in,
  atomisches Deaktivieren beim Key-Löschen und lokaler Monatszähler mit
  Warnschwellen. Nur `/100` ist hellgrauer Basic-Tarif-Hinweis. Stil-CRUD mit
  geschütztem Standard, genau einem aktiven Stil sowie unveränderlichen Job- und
  Briefing-Snapshots. Neuerstellung aus dem Detail erzeugt einen neuen Datensatz.
  Vor sonstiger erneuter Analyse zeigt ein Dialog das neueste Briefing derselben
  kanonischen URL antippbar an. Historieneinträge unterstützen Links-Swipe mit
  freigelegter Löschaktion; Detail-Löschung verlangt „Löschen?“. Copy und Share
  enthalten Titel, Kanal, kanonische URL und Markdown.
- Verifiziert mit: Gradle `--offline testDebugUnitTest assembleDebug
  assembleDebugAndroidTest lintDebug` (100 Tasks, erfolgreich) sowie gezielter
  Android-Instrumentierung für Migration 1→3, Historie/Löschung, Job-Reopen,
  Stil-CRUD/-Schutz, Providerzähler und beide verschlüsselten BYOK-Felder
  (10 Tests, alle bestanden).
- Manuell geprüft auf: Galaxy Tab S7+ 5G, Android 13. `adb install -r` erhielt
  `****` und fünf bestehende Briefings. Duplikatdialog wählte aus mehreren
  Treffern das neueste Briefing; dessen Link öffnete den Detail-View.
  Löschen-Dialog wurde zunächst abgebrochen. Visueller Regressionstest fand und
  behob dauerhaft sichtbare Swipe-Hintergrundaktionen; final legt nur der
  tatsächlich nach links geschobene Eintrag „Löschen“ frei. Ein synthetischer
  `M3-Test`-Stil wurde angelegt, aktiviert und in einem realen Briefing mit
  Kotlin-Codeblock gesnapshottet. Das erzeugte Briefing wurde bestätigt gelöscht,
  danach `Standard` aktiviert und der Teststil gelöscht; Endzustand: fünf
  ursprüngliche Briefings, aktiver Standard, gespeicherter OpenAI-Key maskiert.
- Offen/Blocker: keine für M3. M4 übernimmt Release-Signing, isolierte
  Test-Application-ID, Accessibility-/Gesture-Regression, Fehlermatrix und
  repräsentative Ende-zu-Ende-Smokes.
- Sicherheits-/Kostenprüfung: Kein Keywert gelesen, geloggt, gescreenshottet
  oder aus lokalen Keydateien übernommen. RapidAPI wurde nicht live aufgerufen;
  lokaler Stand unverändert 3 Versuche/3 Erfolge. Der reale M3-Lauf verwendete
  ausschließlich den in der App gespeicherten OpenAI-BYOK.
- Relevante Entscheidung: Das RapidAPI-Kontingent ist unbekannt; `/100` ist
  visuell untergeordnet und gilt nur bedingt für Basic. Explizite Neuerstellung
  aus einem geöffneten Briefing benötigt keine redundante Duplikatbestätigung.

### 2026-08-30 – M4 Providerhärtung und isolierte Gerätetests

- Milestone/Scope: M4, FAL-001, TST-001, SEC-001 und REL-001.
- Validierte Anforderung: Providerfehler müssen deterministisch und ohne
  unbegrenzte Retries enden; Instrumentierung darf persönliche App-Daten nicht
  verändern; Releases benötigen eine langlebige private Signatur.
- Umgesetzt: Tests für gemeinsame Timeouts, HTTP 429/503, malformed/empty
  Providerantworten und Einzelrequest-Grenzen. Default-Testvariante
  `instrumented` mit Application ID `de.lmaa.app.testbed`; explizit gesperrter
  RapidAPI-Live-Smoke. Ignorierte Release-Signing-Konfiguration samt
  Platzhaltervorlage und `verifyReleaseSigning`-Gate.
- Verifiziert mit: Gradle `testInstrumentedUnitTest assembleDebug
  assembleInstrumented assembleInstrumentedAndroidTest lintDebug` (146 Tasks,
  erfolgreich), elf isolierten Instrumentierungstests auf Android 13 sowie
  Git-/APK-Scan gegen beide lokalen Testkeys (0 Treffer in vier APKs).
- Manuell geprüft auf: RapidAPI-Einstellungen zeigten beide BYOK-Felder nur als
  `****`, aktivierten Fallback und Zählerstand 3. Ein exakt einmal
  freigeschalteter Live-Smoke war erfolgreich und erhöhte den lokalen Stand auf
  4 Versuche/4 Erfolge. Danach blieb ausschließlich `de.lmaa.app` installiert.
- Offen/Blocker: Die tägliche `de.lmaa.app` ist debug-signiert; ein neuer
  Release-Key kann sie bei gleicher Application ID nicht datenbewahrend
  ersetzen. Zuerst Übergang per Historienexport/-import, bewusstem Neuaufbau
  oder temporärer paralleler ID festlegen; dann langlebigen privaten Keystore
  interaktiv erstellen und signierten Geräte-Smoke ausführen.
- Relevante Entscheidung: Live-Provider-Smokes bleiben standardmäßig
  assumption-gated; normale Instrumentierung zielt nie auf die tägliche App.

### 2026-08-30 – Verlorene konfigurierbare RapidAPI-Anforderung wiederhergestellt

- Milestone/Scope: M4, FAL-001 bis FAL-003.
- Validierte Anforderung: Andere RapidAPI-Transkriptanbieter müssen ohne neuen
  providerspezifischen Parser konfigurierbar sein; RapidAPI darf optional
  Primärquelle sein. cURL dient nur als deklarative Eingabe und darf nie
  ausgeführt werden.
- Abweichungsursache: Die frühere Planung war nicht in den gemergten Git-Refs
  enthalten. M0/M3 implementierten deshalb nur einen festen
  `youtube-transcripts`-Adapter mit Bool-Fallback und dokumentierten die
  ursprüngliche Anforderung fälschlich als erfüllt.
- Umgesetzt: separates Profil-DataStore ohne Key, sichere Templates und
  Platzhalter, GET/POST, Status-/Timeout-/Größengrenzen, eingeschränkter
  cURL-Import, Defaults, drei Routingmodi sowie vollständige Raw-Response-
  Weitergabe im promptseitigen UNTRUSTED-Block.
- Verifiziert mit: Kotlin-/Proto-/Android-Testkompilierung und
  `testInstrumentedUnitTest`; Profil-/JSON-, SSRF-, Header-, Keyplatzierungs-,
  cURL-, Routing-, HTTP- und Raw-Promptverträge bestanden.
- Manuell geprüft auf: noch offen; Release/Deployment bleibt bis zum isolierten
  Settings-/Persistenztest gestoppt.
- Offen/Blocker: isolierte Instrumentierung auf Android 13, visueller
  Settings-Smoke und ein gezielter realer Raw-Providerlauf.
- Relevante Entscheidung: Providerprofile enthalten nie den Key. Nur
  `X-RapidAPI-Key: {{rapidapi_key}}` darf ihn zur Requestzeit materialisieren.

### 2026-08-30 – M4 mit Release, RapidAPI-Raw-E2E und visueller Identität abgeschlossen

- Milestone/Scope: M4, FAL-001 bis FAL-003, REL-001 und UI-003.
- Validierte Anforderung: Die frei konfigurierbare RapidAPI-Quelle muss auf dem
  produktiven Release bis zum Briefing funktionieren; das persönliche Sideload-
  Release braucht eine stabile Signatur sowie ein eigenständiges, angenehmes
  Erscheinungsbild aus dem bereitgestellten Referenzfoto.
- Umgesetzt: langlebiges lokales RSA-4096-Signing samt sicherer Erzeugungshilfe;
  Adaptive Icon mit Android-13-Monochromvariante; kontrastierte Material-3-
  Light-/Dark-Rollen aus Lavendel, Sage/Oliv und warmem Off-White.
- Verifiziert mit: `testInstrumentedUnitTest assembleDebug` (erfolgreich vor
  dem Lint-Schritt), anschließend `lintDebug verifyReleaseSigning
  assembleRelease` (Build erfolgreich); `apksigner verify --verbose`; zwölf
  isolierte Instrumentierungstests; vollständige Byte-Suche gegen beide lokalen
  Testkeys in versionierten Dateien und Release-APK (je 0 Treffer).
- Manuell geprüft auf: freigegebener Clean-Cutover auf dem Galaxy Tab,
  Kaltstart und datenbewahrendes `adb install -r`; Adaptive Icon in runder
  Samsung-Maske; helles Home-Theme; maskierte BYOK-Felder; Profil-Settings.
  Ein Share von `Rq5iOD-mcEI` im Modus `RapidAPI bevorzugt` durchlief reale
  RapidAPI-Rohantwort, `gpt-5.6-sol`, Room und Detail-Rendering. Der Zähler stieg
  exakt auf 5 Versuche/5 Erfolge; danach wurde `Nur als Fallback` aktiviert.
- Offen/Blocker: keine für M4. Keystore und `signing.properties` müssen als
  zusammengehöriges Wiederherstellungsset außerhalb des Repositories gesichert
  werden.
- Relevante Entscheidung: Das fotoabgeleitete Raster bleibt die farbige Icon-
  Quelle; Android erhält zusätzlich deklarative Adaptive-/Monochrom-Layer.

### 2026-08-30 – Vollständig stilgesteuerte Briefing-Struktur

- Milestone/Scope: Stilverwaltung nach M4, STY-002.
- Validierte Anforderung: Pflichtabschnitte, Quellenkritik, Unsicherheiten,
  Zeitmarken und Markdown sind Eigenschaften des geschützten Standardstils.
  Eigene Stile müssen diese vollständig ersetzen können.
- Umgesetzt: vollständiger bisheriger Briefing-Vertrag in `Standard`; format-
  neutraler Final- und Map-Prompt; keine globale Überschriftenvalidierung;
  mehrzeilige Stiltexte bleiben erhalten. `ensureDefault` aktualisiert nur die
  aktuelle geschützte Default-Definition, nicht historische Briefing- oder
  Auftragssnapshots.
- Verifiziert mit: 52 Python-Tests; Android-JVM-Tests, Kotlin-Kompilierung,
  Debug-/Release-Build und Lint; 13 isolierte Instrumentierungstests auf Android
  13. Vertragstests decken freie Ein-Satz-Ausgabe, Default-Struktur,
  Map-Reduce-Stilweitergabe, leere Ausgabe und unsicheres aktives Markup ab;
  die Instrumentierung aktualisiert außerdem eine veraltete Default-Definition.
- Manuell geprüft auf: Galaxy Tab S7+ mit dem temporären Stil `FreierTest` und
  realem OpenAI-Lauf. Ergebnis war exakt ein kurzer Satz ohne Überschrift,
  Liste, Zeitmarke oder Markdown-Struktur. Danach Testbriefing und Teststil
  gelöscht und `Standard` wieder aktiviert. RapidAPI blieb bei 5/5.
- Offen/Blocker: keine.
- Relevante Entscheidung: Benutzerstil ist die höchste redaktionelle
  Anweisungsebene; globale Prompts bleiben auf technische Grenzen beschränkt.

### 2026-08-30 – YouTube-Share-Vertrag finalisiert

- Milestone/Scope: Post-MVP, INT-001.
- Validierte Anforderung: LMAA muss ein korrektes YouTube-Text-Share-Ziel sein;
  die von Samsung verwaltete Position im Sharesheet ist davon getrennte Nutzer-/
  Systemkonfiguration.
- Umgesetzt: ausschließlich `ACTION_SEND` mit `text/plain` in Manifest und
  Laufzeit. `text/*`, Deep-Link-Filter und der erfolglose Direct-Share-Shortcut
  wurden vollständig entfernt; der Geräte-Shortcut-Store wurde bereinigt.
- Verifiziert mit: JVM-Vertragstests; gemergtes Release-Manifest; Gradle
  `testInstrumentedUnitTest assembleInstrumented
  assembleInstrumentedAndroidTest lintDebug verifyReleaseSigning
  assembleRelease` (176 Tasks, erfolgreich); isolierter PackageManager-Test auf
  Android 13. Die installierte Release-App löst für `text/plain` exakt einmal
  und für `text/html`, Bilder sowie `ACTION_VIEW` gar nicht auf.
- Manuell geprüft auf: Galaxy Tab S7+ 5G, Android 13 / One UI 5.1.1. Ein
  Direct-Share-Shortcut mit Rang 0 erschien wegen Samsungs Kommunikations-
  priorisierung nicht. Nach dessen Entfernung hat der Nutzer LMAA erfolgreich
  per Samsung Good Lock neben Quick Share, Telegram und Outlook angeheftet.
- Offen/Blocker: keine.
- Relevante Entscheidung: App-Code erzwingt kein Sharesheet-Ranking und bildet
  LMAA nicht künstlich als Kommunikationsziel ab. Der App-Vertrag bleibt minimal;
  die bevorzugte Position wird einmalig über die vorgesehene Samsung-
  Systemkonfiguration festgelegt.

## Vorlage für Fortschrittseinträge

```markdown
### YYYY-MM-DD – Kurztitel

- Milestone/Scope:
- Validierte Anforderung:
- Umgesetzt:
- Verifiziert mit:
- Manuell geprüft auf:
- Offen/Blocker:
- Relevante Entscheidung:
```
