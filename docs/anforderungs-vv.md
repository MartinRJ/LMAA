# Anforderungsvalidierung und -verifikation

Stand: 2026-08-30

## Zweck und Begriffe

Dieses Dokument trennt zwei Fragen:

- **Validation:** Ist die Anforderung in sich konsistent und entspricht sie dem
  tatsächlichen Bedarf der Stakeholder?
- **Verification:** Ist die validierte Anforderung in Code, Konfiguration und
  Tests nachweisbar korrekt umgesetzt?

Primärer Stakeholder ist der Nutzer und Betreiber des persönlichen Galaxy Tab
S7+ 5G. Weitere Perspektiven sind Datenschutz/Secret-Schutz, Wartbarkeit und die
begrenzten Providerkontingente.

## Korrigierte Stakeholderanforderung

LMAA muss auf dem Android-Tablet ohne eigenen Server, Hosting-Dienst oder
laufenden PC funktionieren. Der Primärtranskriptabruf erfolgt innerhalb der App
mit `youtube-transcript-api`. Externe Netzwerkziele sind nur YouTube/oEmbed,
OpenAI und bei explizit aktiviertem Fallback RapidAPI.

„Komplett lokal“ bezeichnet damit die App-Laufzeit, Orchestrierung,
Transkriptbeschaffung und Persistenz. Es bedeutet nicht Offline-Inferenz:
`gpt-5.6-sol` wird weiterhin über die OpenAI API ausgeführt.

## V&V-Matrix

| ID | Anforderung | Validation | Verification am 2026-08-30 | Nächster Nachweis |
|---|---|---|---|---|
| LOC-001 | Kein LMAA-Server, kein PC, keine Domain | **validiert**; entspricht dem persönlichen mobilen Workflow und vermeidet zusätzliche Betriebskosten | **erfüllt**; produktiver Ein-Schritt-Pfad und Room-Historie laufen autonom auf dem Tablet, gespeicherte Details öffnen nach Kaltstart ohne Analyse | Netzwerkziel-Regression in M4 |
| TRN-001 | `youtube-transcript-api` läuft primär in der APK | **validiert**; Chaquopy 17 passt zu AGP 9.2.1, minSdk 26, Python 3.10 und ARM64 | **erfüllt**; APK-/Offline-Build, mehrere reale Abrufe, kontrollierter Short ohne Captions und erfolgreicher CC-Short `Rq5iOD-mcEI` bestanden | weitere repräsentative Videos in M4 |
| TRN-002 | Kein API-Key für den Primärtranskriptpfad | **validiert**; die Bibliothek fordert keinen Key und keinen Headless Browser | **erfüllt auf dem Zielgerät**; lokale Key-Dateien fehlen in der APK, RapidAPI wurde nicht aufgerufen | APK-Secret-Scan fortführen |
| FAL-001 | RapidAPI nur nach geeignetem Primärfehler und Opt-in | **validiert**; schont das persönliche 100-Request-Kontingent | **erfüllt für M0**; Android-Adapter und Wahrheitstabelle erzwingen Opt-in, Key, Fehler-Whitelist, exakt einen Request und keinen 429-Retry; Short mit deaktivierten Captions löste keinen Fallback aus | M3 ergänzt Einstellungs-UX und Monatszähler |
| MET-001 | Titel, Kanalname und Thumbnail über oEmbed | **validiert**; genügt dem MVP ohne zusätzlichen Key | **erfüllt**; MockWebServer-Verträge und realer Android-oEmbed-Pfad bestanden, weitere Felder bleiben nullable | Regression in M1 |
| AIG-001 | Briefing mit exakt `gpt-5.6-sol`, ohne Modellfallback | **validiert**; Modell und Responses-Endpunkt existieren und wurden praktisch erreicht | **erfüllt für M0**; kurzer und umfangreicher direkter Android-Pfad mit `store=false`, leerer Toolliste, Map-Reduce, allen Pflichtüberschriften, Zeitmarken und Inline-Code bestanden | weitere repräsentative Inhalte in M1/M4 evaluieren |
| SEC-001 | Persönliches BYOK; kein Secret in APK, Git, Logs oder Backup | **bedingt validiert**; Proto DataStore + Tink AEAD mit Android-Keystore-geschütztem Keyset erfüllt das lokale Bedrohungsmodell, direkter Client-Key bleibt ein Restrisiko | **erfüllt für M0**; Ciphertext-/Keystore-Instrumentierung, No-Backup-Regeln, Kaltstart, `****`, leeres Ersetzen sowie APK-/Git-Scan bestanden | M3 verschiebt Keyverwaltung in eigene Settings-View; Restrisiko bleibt akzeptierte Ausnahme |
| DAT-001 | Historie, Snapshots und Analyseaufträge lokal in Room | **validiert** | **erfüllt**; Room 2.8.4, exportiertes Schema 2, validierte Migration 1→2, normalisierte Daten, persistente Jobs, getrennte immutable Briefings, Reopen-Tests und reale Kaltstart-/Offline-Smokes bestanden | jede folgende Schemaänderung mit Migration und Migrationstest |
| UI-001 | Sicheres, langes Markdown einschließlich Code | **validiert** | **erfüllt**; Parsertests und Zielgerät-Smokes bestanden | Regressionstests fortführen |
| UX-001 | Linkprüfung, Transkript und Briefing sind eine Nutzeraktion | **validiert**; entspricht dem täglichen „informiert statt ansehen“-Workflow | **erfüllt**; Direkteingabe besitzt einen Analyse-Button, `ACTION_SEND` startet denselben Orchestrator automatisch; Room/WorkManager stellen laufende Aufträge nach Prozessabbruch wieder her | Regression in M4 |
| UI-002 | Briefing erscheint in einem eigenen View | **validiert** | **erfüllt**; eigener Detail-View mit System-Zurück, Video, Kopieren und Teilen; Hoch-/Querformat, getrennte Scrollbereiche, Dark Mode und 150-%-Schrift live geprüft | Regression in M4 |
| INT-001 | LMAA ist installiertes `text/plain`-Share-Ziel für YouTube | **validiert** | **erfüllt**; PackageManager listet `de.lmaa.app/.MainActivity`; reale Shares und der manuelle Nutzer-Smoke direkt aus dem Samsung-/YouTube-Sharesheet führten ohne Folgeklick zum Briefing; Consume-once verhindert Wiederholung | Regression in M2/M4 |
| TST-001 | Tests dürfen tägliche BYOK-/Historiedaten nicht zerstören | **validiert**; persönliche App-Daten sind produktive Stakeholderdaten | **erfüllt für M2**; `adb install -r` plus gezielte Instrumentierung statt UTP erhielten BYOK-Maske und vier Historieneinträge; Migration und Persistenztests bestanden | isolierte Test-Application-ID bleibt M4-Härtung |
| RES-001 | Laufende Analyse übersteht Activity-/Prozessneustart ohne doppeltes Briefing | **validiert**; umfangreiche Modellläufe dürfen nicht an eine sichtbare Activity gekoppelt sein | **erfüllt**; persistenter Room-Job, eindeutige WorkManager-Arbeit, Foreground-Ausführung und atomare Ergebnistransaktion; Force-Stop in `BRIEFING` wurde zu exakt einem neuen Eintrag fortgesetzt | Reboot-/Doze-Regression in M4 |
| COST-001 | Kein eigener Server; RapidAPI meist innerhalb Basic/Free | **validiert**; eigener Server entfällt, RapidAPI bleibt Ausnahme | **erfüllt für M0**; Hauptpfad und semantischer Short-Fehler benötigen keinen Server und keinen RapidAPI-Aufruf; Adapter hat keinen automatischen Retry | M3 ergänzt lokalen Monatszähler |

## Machbarkeitsprüfung des lokalen Transkriptpfads

### Passende Randbedingungen

- Chaquopy 17.0 unterstützt Android Gradle Plugin 7.3 bis 9.2 und minSdk 24.
  Das Projekt verwendet AGP 9.2.1 und minSdk 26.
- Chaquopy 17 bietet Python 3.10 bis 3.14. Python 3.10 ist bereits für den
  Desktop-Referenzprototyp verwendet und unterstützt 64-Bit-Android-ABIs.
- Das Zielgerät ist ARM64; zunächst genügt `arm64-v8a`.
- `youtube-transcript-api==1.2.4` unterstützt Python 3.8 bis kleiner 3.15. Die
  installierte Distribution enthält keine nativen `.so`, `.pyd` oder `.dll` und
  hängt nur von `requests` sowie `defusedxml` ab.
- Chaquopy kann reine Python-Pakete über seinen Pip-Block in die App einbauen.

### Verifizierte Befunde und Restpunkte

- Chaquopy installiert die vollständig gepinnten Python-Abhängigkeiten und
  reproduziert den Build anschließend offline.
- TLS/CA- und HTTP-Verhalten von `requests` funktioniert auf dem Galaxy Tab.
- Der undokumentierte YouTube-Webclient-Abruf lieferte über die Geräte-IP
  manuelle und automatisch erzeugte deutsche/englische Transkripte.
- Ein langes Transkript mit 7.311 Segmenten und 210.682 Zeichen überquerte die
  Python/Kotlin-Bridge erfolgreich; die Debug-APK ist rund 27,8 MB groß.
- Shorts mit und ohne Captions sowie die Prozesswiederaufnahme während der
  Briefingphase sind explizit verifiziert. Detaillierte Laufzeit-/
  Speichermessung bleibt offen.

## Zielkonflikt: serverloser MVP und OpenAI-Key

Die OpenAI-Dokumentation verlangt, API-Keys nicht in Browsern oder Apps
offenzulegen und empfiehlt serverseitige Secret-Verwahrung. Gleichzeitig schließt
LOC-001 einen eigenen Server aus. Beide Forderungen sind in strenger Form nicht
gleichzeitig erfüllbar.

Für den persönlichen Sideload-MVP wird der Konflikt so begrenzt:

1. BYOK: Der Key wird nicht eingebaut, sondern vom einzigen Nutzer eingegeben.
2. Nur mit Tink AEAD verschlüsselter Ciphertext wird in Proto DataStore
   persistiert; das Tink-Keyset wird durch einen nicht exportierbaren Schlüssel
   im Android Keystore geschützt.
3. Key-Ciphertext und App-Daten sind von Cloud- und Device-to-Device-Backups
   ausgeschlossen.
4. Der OpenAI-Projektkey besitzt ein hartes Spend-Limit und wird bei Verlust oder
   Auffälligkeiten rotiert.
5. Der Klartext wird nur für den HTTPS-Authorization-Header kurzzeitig im
   App-Prozess materialisiert und nie geloggt.
6. Nach dem Speichern zeigt die UI konstant `****`; sie lädt den gespeicherten
   Klartext nicht zurück und verrät weder Präfix noch Länge.
7. Die App bleibt persönlicher Sideload. Vor Weitergabe an andere Personen wird
   diese Ausnahme neu validiert.

Bewertung: **funktional validierbar, sicherheitstechnisch nur bedingt validiert**.
Android Keystore reduziert Risiken, beseitigt aber nicht die grundsätzliche
Client-Key-Abweichung von der OpenAI-Empfehlung.

Zusätzlicher Befund: `EncryptedSharedPreferences` und `MasterKey` sind seit
AndroidX Security Crypto 1.1.0 deprecated. Sie wurden daher ebenso wie
unverschlüsselte `SharedPreferences` als Zielarchitektur verworfen. Gewählt ist
Proto DataStore + Tink AEAD mit Android-Keystore-geschütztem Keyset.

Das offizielle AndroidX-Modul `datastore-tink` ist derzeit Alpha. Der Spike hat
deshalb stabiles Proto DataStore 1.2.1 mit eigenem Tink-1.23-AEAD-Serializer
gewählt. Das verschlüsselte Tink-Keyset liegt im No-Backup-Bereich und wird durch
einen nicht exportierbaren Android-Keystore-AES-GCM-Schlüssel geschützt. Der
Instrumentierungstest belegt den Roundtrip, fehlenden Klartext im Dateibytestrom
und atomisches Löschen; ein Kaltstart-Smoke belegt die persistente `****`-Maske.

## Verification-Plan für M0

1. **Erfüllt:** Chaquopy/Python und exakt gepinnte Transcript-Abhängigkeiten
   bauen online und offline.
2. **Erfüllt:** Drei synthetische Python-Bridge-Tests prüfen Normalisierung,
   Vorvalidierung und Sprachcodefilter; reale Geräte-Smokes prüfen die
   Python/Kotlin-Grenze.
3. **Erfüllt:** Geräte-Smokes rufen manuelle/automatische, deutsche/englische,
   kurze und lange Transkripte primär lokal ab; ein expliziter Short wurde
   kontrolliert als `TRANSCRIPTS_DISABLED` klassifiziert.
4. **Erfüllt:** MockWebServer erzwingt jeden zulässigen und unzulässigen
   Fallbackfall und zählt ausgehende RapidAPI-Requests.
5. **Erfüllt:** Direkter oEmbed- und OpenAI-Pfad läuft auf dem Tablet; OpenAI
   antwortete mit exakt `gpt-5.6-sol` und allen Pflichtüberschriften.
6. **Erfüllt:** Netzwerkprüfung zeigt ausschließlich erwartete Providerhosts und keinen
   LMAA-eigenen Server.
7. **Erfüllt:** UI-Test prüft nach dem Speichern ausschließlich `****`, ein leeres
   Ersetzungsfeld und atomisches Löschen ohne Klartextwiederanzeige.
8. **Erfüllt und fortlaufend:** APK-, Git- und Log-Scan findet weder lokalen Testkey noch bekannte
   Key-Präfixe; Backupregeln schließen Secret-/Room-Dateien aus.
9. **Erfüllt:** App-Neustart erhält fertige Briefings lokal; Room und
   WorkManager nehmen einen laufenden Auftrag nach Prozessabbruch wieder auf,
   ohne einen doppelten Historieneintrag zu erzeugen.

## Historische Abweichung

Die ursprüngliche Planung entschied sich wegen der Python-Bibliothek für
FastAPI. Dadurch wurden Hosting, Server-Contracts und RapidAPI-Weiterleitung
ausgebaut, während der entscheidende Chaquopy-Gerätespike ausblieb. Der damalige
Prototyp verifiziert LOC-001 oder TRN-001 nicht; die aktuelle Android-
Implementierung erfüllt inzwischen TRN-001 und den Transcript-Anteil von
LOC-001.

Der Backend-Prototyp darf deshalb nur als Testreferenz verwendet werden. Neue
Produktlogik wird zuerst in Android implementiert; keine Funktion darf einen
LMAA-Backend-Host voraussetzen.

## Quellen

- [Chaquopy 17 – Android/Gradle/Python/Pip](https://chaquo.com/chaquopy/doc/current/android.html)
- [Chaquopy – Versionsmatrix](https://chaquo.com/chaquopy/doc/current/versions.html)
- [youtube-transcript-api](https://github.com/jdepoix/youtube-transcript-api)
- [OpenAI API – Authentifizierung](https://developers.openai.com/api/reference/overview)
- [OpenAI – gpt-5.6-sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android – EncryptedSharedPreferences (deprecated)](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Android – Security Checklist: Keystore und Tink](https://developer.android.com/privacy-and-security/security-tips)
- [AndroidX DataStore – Tink-Verschlüsselungsmodul](https://developer.android.com/jetpack/androidx/releases/datastore)
- [Android – Backup-Sicherheit](https://developer.android.com/privacy-and-security/risks/backup-best-practices)
