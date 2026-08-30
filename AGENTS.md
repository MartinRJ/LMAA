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
   innerhalb der APK. Dieser Gerätepfad ist vor weiterem Providerausbau zu
   implementieren und live zu verifizieren.
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
| M0 – Lokale Laufzeit und Spikes | in Arbeit | 2026-08-30 | Android-Gerüst/Renderer und Desktop-Providerreferenz bestehen; als Nächstes Chaquopy plus lokaler Transcript-Liveabruf auf dem Tablet. |
| M1 – Persistenter Happy Path | offen | 2026-08-30 | Lokales Transkript bis Room-persistierter Markdown-Detailansicht. |
| M2 – Share und Export | offen | 2026-08-29 | Share-Intent, Copy/Share und Wiederaufnahme. |
| M3 – Stile und Fallback-Einstellungen | offen | 2026-08-30 | Stil-CRUD, Snapshots, Keystore-Key-UX und RapidAPI-Zähler. |
| M4 – Härtung und APK | offen | 2026-08-29 | Fehlermatrix, Secret-Scan, Signing und Gerätesmokes. |

## Früh zu validierende Annahmen

- Chaquopy 17 unterstützt formal AGP 9.2.1, Python 3.10, minSdk 26 und ARM64.
  Paketinstallation und realer Transcript-Abruf sind auf dem Tablet noch nicht
  verifiziert.
- `youtube-transcript-api` nutzt eine undokumentierte YouTube-Schnittstelle;
  Änderungen oder IP-Sperren bleiben möglich.
- Der direkte OpenAI-Key im persönlichen Client widerspricht der allgemeinen
  OpenAI-Empfehlung für Client-Apps. BYOK, DataStore/Tink mit Android Keystore,
  Backup-Ausschluss, projektgebundener Key, hartes Spend-Limit und
  Sideload-Grenze reduzieren, beseitigen das Risiko aber nicht.
- Das offizielle AndroidX-Modul `datastore-tink` ist derzeit Alpha. M0 muss
  deshalb per Dependency-/Gerätespike zwischen diesem Modul und einer stabilen
  Proto-DataStore-Integration mit eigenem Tink-AEAD-Serializer entscheiden;
  die Sicherheitsinvarianten dürfen sich dadurch nicht ändern.
- Zugriff auf `gpt-5.6-sol` ist im Referenzprototyp praktisch verifiziert; der
  direkte Android-Request ist offen.
- oEmbed-Felder Kanal-ID, Veröffentlichungsdatum und Dauer sind im MVP nullable.
- Die vorläufige Application ID lautet `de.lmaa.app`; Release-Key und
  endgültige ID folgen vor M4.

## Entscheidungsprotokoll

| Datum | Entscheidung | Begründung |
|---|---|---|
| 2026-08-29 | Python/FastAPI-Backend vorgesehen | Ursprüngliche Planung zur einfachen Python-Einbindung; am 2026-08-30 wegen falscher Interpretation von „lokal“ aufgehoben. |
| 2026-08-30 | Autonome Android-App ohne LMAA-Backend | Entspricht dem persönlichen Tablet-Workflow, vermeidet Hosting/Betriebskosten und macht RapidAPI nur zum seltenen Fallback. |
| 2026-08-30 | Chaquopy 17/Python 3.10 für den Primärprovider | Offizielle Matrix passt zu AGP 9.2.1/minSdk 26/ARM64; `youtube-transcript-api` ist reines Python. Geräteverifikation bleibt M0-Pflicht. |
| 2026-08-30 | Direkte OpenAI-Nutzung mit persönlichem BYOK | Kein eigener Server gewünscht; Risiko wird durch nutzereingegebenen restriktiven Key, DataStore/Tink, Android Keystore, Backup-Ausschluss und Spend-Limit begrenzt. |
| 2026-08-30 | Proto DataStore + Tink AEAD als Secret-Store | Nur Ciphertext wird persistiert; das Tink-Keyset ist über Android Keystore geschützt. `EncryptedSharedPreferences` wurde wegen Deprecation verworfen; unverschlüsselte Preferences sind verboten. Die konkrete Integration wird in M0 nach Stabilitäts- und Gerätetest festgelegt. |
| 2026-08-30 | YouTube oEmbed als MVP-Metadatenpfad | Titel, Kanalname und Thumbnail genügen ohne zusätzlichen Key; weitere Felder bleiben nullable. |
| 2026-08-29 | Room und Snapshots | Briefings bleiben offline und unveränderlich; Stiländerungen verändern keine Historie. |
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
