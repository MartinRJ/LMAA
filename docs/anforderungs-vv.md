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
| LOC-001 | Kein LMAA-Server, kein PC, keine Domain | **validiert**; entspricht dem persönlichen mobilen Workflow und vermeidet zusätzliche Betriebskosten | **nicht erfüllt**; die Providerpipeline existiert bisher nur im FastAPI-Referenzprototyp | vollständiger Geräte-Smoke ohne konfigurierten Backend-Host |
| TRN-001 | `youtube-transcript-api` läuft primär in der APK | **validiert**; Chaquopy 17 passt formal zu AGP 9.2.1, minSdk 26, Python 3.10 und ARM64 | **offen**; kein Chaquopy-Plugin und keine Python-Bridge im Android-Modul | APK-Build und realer Abruf auf dem Galaxy Tab |
| TRN-002 | Kein API-Key für den Primärtranskriptpfad | **validiert**; die Bibliothek fordert keinen Key und keinen Headless Browser | **nur Desktop verifiziert** | Geräteabruf ohne gesetzten YouTube-/RapidAPI-Key |
| FAL-001 | RapidAPI nur nach geeignetem Primärfehler und Opt-in | **validiert**; schont das persönliche 100-Request-Kontingent | **Referenzlogik verifiziert, Android offen**; bisher 3/100 Live-Versuche | MockWebServer-Wahrheitstabelle in Android; kein weiterer Live-Aufruf nötig |
| MET-001 | Titel, Kanalname und Thumbnail über oEmbed | **validiert**; genügt dem MVP ohne zusätzlichen Key | **Desktop verifiziert, Android offen** | direkter Android-oEmbed-Test mit nullable ID/Datum/Dauer |
| AIG-001 | Briefing mit exakt `gpt-5.6-sol`, ohne Modellfallback | **validiert**; Modell und Responses-Endpunkt existieren und wurden praktisch erreicht | **Desktop verifiziert, Android offen** | direkter Android-Request mit `store=false` und ohne Tools |
| SEC-001 | Persönliches BYOK; kein Secret in APK, Git, Logs oder Backup | **bedingt validiert**; Proto DataStore + Tink AEAD mit Android-Keystore-geschütztem Keyset erfüllt das lokale Bedrohungsmodell, direkter Client-Key bleibt ein Restrisiko | **teilweise erfüllt**; Gitignore/Smokes sind sicher, Android-Keyablage fehlt | Integrations-/Stabilitätsspike, Backupregeln, feste `****`-Maske und APK-Secret-Scan |
| DAT-001 | Historie und Snapshots lokal in Room | **validiert** | **offen** | Schema, Migrationstest und Neustart-Smoke |
| UI-001 | Sicheres, langes Markdown einschließlich Code | **validiert** | **erfüllt**; Parsertests und Zielgerät-Smokes bestanden | Regressionstests fortführen |
| COST-001 | Kein eigener Server; RapidAPI meist innerhalb Basic/Free | **validiert**; eigener Server entfällt, RapidAPI bleibt Ausnahme | **Architektur noch nicht umgesetzt** | Netzwerkzieltest und lokaler Monatszähler |

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

### Noch nicht bewiesene Annahmen

- Installation aller Python-Abhängigkeiten durch Chaquopy im konkreten
  Gradle-Projekt.
- TLS/CA- und HTTP-Verhalten von `requests` auf dem Galaxy Tab.
- Kompatibilität des undokumentierten YouTube-Webclient-Abrufs mit der mobilen
  Geräte-IP und aktuellen YouTube-Antworten.
- Laufzeit, Speicherbedarf, APK-Größe und Verhalten nach Prozessneustart.

Diese Punkte sind der erste Implementierungs- und Testauftrag von M0. Desktop-
Erfolge ersetzen den Gerätebeweis nicht.

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

Das offizielle AndroidX-Modul `datastore-tink` ist derzeit Alpha. Noch offen ist
daher nur die konkrete Integration: offizielles Modul nach erfolgreichem
Stabilitäts-/Gerätetest oder stabiles Proto DataStore mit eigenem
Tink-AEAD-Serializer. Diese technische Auswahl darf die validierten
Sicherheitsanforderungen nicht ändern: ausschließlich Ciphertext persistieren,
Keyset Keystore-gestützt schützen, Backups ausschließen und niemals Klartext
anzeigen, loggen oder sichern.

## Verification-Plan für M0

1. Chaquopy/Python und exakt gepinnte Transcript-Abhängigkeiten bauen.
2. Instrumentierungstest ruft die Python-Bridge mit synthetischen Daten auf und
   prüft DTO-/Fehlernormalisierung.
3. Geräte-Smokes rufen manuelle/automatische, deutsche/englische, Short- und
   lange Transkripte primär lokal ab.
4. MockWebServer erzwingt jeden zulässigen und unzulässigen Fallbackfall und zählt
   ausgehende RapidAPI-Requests.
5. Direkter oEmbed- und OpenAI-Pfad läuft auf dem Tablet; OpenAI antwortet mit
   exakt `gpt-5.6-sol` und allen Pflichtüberschriften.
6. Netzwerkprüfung zeigt ausschließlich erwartete Providerhosts und keinen
   LMAA-eigenen Server.
7. UI-Test prüft nach dem Speichern ausschließlich `****`, ein leeres
   Ersetzungsfeld und atomisches Löschen ohne Klartextwiederanzeige.
8. APK-, Git- und Log-Scan findet weder lokalen Testkey noch bekannte
   Key-Präfixe; Backupregeln schließen Secret-/Room-Dateien aus.
9. App-Neustart und Prozessabbruch erhalten Auftrag und fertiges Briefing lokal.

## Historische Abweichung

Die ursprüngliche Planung entschied sich wegen der Python-Bibliothek für
FastAPI. Dadurch wurden Hosting, Server-Contracts und RapidAPI-Weiterleitung
ausgebaut, während der entscheidende Chaquopy-Gerätespike ausblieb. Diese
Implementierung verifiziert Providerverhalten, aber nicht LOC-001 oder TRN-001.

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
