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
8. RapidAPI ist standardmäßig aus und ausschließlich ein nachgelagerter
   Fallback nach geeigneten technischen Fehlern des lokalen Primärproviders.
   Tests verwenden MockWebServer; reale Aufrufe nur bei diagnostischem Bedarf.
9. Jede RapidAPI-Anfrage wird lokal technisch gezählt. Aktueller
   Entwicklungsstand: 3 Versuche/3 Erfolge von nominal 100 Monatsrequests,
   konservativ 97 verbleibend; das Dashboard ist maßgeblich.
10. Der Code unter `backend/` ist ein Referenz- und Testprototyp. Er darf
    Providersemantik und Prompts belegen, ist aber weder Produktlaufzeit noch
    Nachweis für lokale Android-Anforderungen.
11. Eingehende URLs, Providerdaten, Transkripte und Modell-Markdown sind
    unvertrauenswürdig. Hosts/IDs validieren, HTML nicht ausführen,
    Link-Schemes begrenzen und keine Modell-Tools aktivieren.
12. Alte Briefings sind unveränderliche historische Ergebnisse. Neuerstellung
    erzeugt einen neuen Datensatz mit Stil- und Modell-Snapshot.
13. Jedes Briefing zeigt einen sichtbaren Link/Button zur ausschließlich aus
    der validierten Video-ID konstruierten kanonischen HTTPS-URL.
14. Keine vollständigen fremden Transkripte oder realen inhaltlichen
    Providerantworten committen. Tests verwenden synthetische Fixtures.
15. Jede Room-Schemaänderung braucht eine Migration und einen Migrationstest.
16. Neue Dependencies werden begründet, kontrolliert gepinnt und auf Lizenz,
    Wartungsstand, Android 13, ARM64 und Offline-Build-Reproduzierbarkeit geprüft.
17. `connectedDebugAndroidTest` darf nicht gegen die täglich genutzte
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
| M3 – Stile und Fallback-Einstellungen | offen | 2026-08-30 | Als Nächstes eigene Settings-View statt Keyverwaltung im Home-View; danach Stil-CRUD, RapidAPI-Opt-in und Zähler. |
| M4 – Härtung und APK | offen | 2026-08-29 | Fehlermatrix, Secret-Scan, Signing und Gerätesmokes. |

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
