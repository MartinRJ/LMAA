# Local Media Analysis Assistant (LMAA)

LMAA ist eine persönliche Android-App für YouTube-Briefings auf einem Samsung
Galaxy Tab S7+ 5G mit Android 13 / One UI 5.1.1.

> **Das Ziel sei:** So schnell wie möglich einen auf dem Zieltablet eigenständig
> funktionierenden MVP bereitzustellen, der YouTube-Links über das Android-
> Teilen-Menü oder per Direkteingabe annimmt, das Transkript primär **lokal in der
> App** mit `youtube-transcript-api` abruft, daraus mit `gpt-5.6-sol` ein
> konfigurierbares Briefing erzeugt, Ergebnisse lokal historisiert und Markdown
> teilen oder kopieren kann.

## Verbindliche Laufzeitgrenze

„Lokal“ bedeutet für LMAA:

- Die App benötigt keinen eigenen Server, keinen PC im LAN, keine Domain und
  keinen Hosting-Dienst.
- URL-Verarbeitung, Transcript-Provider-Auswahl, Chunking, Promptaufbau,
  Fehlerbehandlung und Persistenz laufen auf dem Tablet.
- `youtube-transcript-api` wird mit Chaquopy in die APK eingebettet.
- Die App kommuniziert direkt per HTTPS mit YouTube/oEmbed, OpenAI und nur bei
  aktiviertem Fallback mit RapidAPI.
- OpenAI-Inferenz ist naturgemäß nicht offline oder lokal. Bereits gespeicherte
  Briefings bleiben offline lesbar, kopierbar und teilbar.

Ein FastAPI-Dienst ist **kein Bestandteil der Produktarchitektur**. Der vorhandene
Code unter `backend/` ist ausschließlich ein Referenz- und Testprototyp für
Providerverhalten, Prompts und synthetische Contract-Tests.

## Aktueller Stand und Architekturkorrektur

Am 2026-08-30 wurde festgestellt, dass der ursprüngliche Plan „lokal“ nur auf
Room-Historie bezog und die Analyse fälschlich in ein FastAPI-Backend verlagerte.
Diese Entscheidung ist aufgehoben.

Bereits verifiziert:

- Android-Gerüst mit Compose/Material 3, minSdk 26, compile/targetSdk 36.
- Chaquopy 17.0.0 mit Python 3.10 und `youtube-transcript-api==1.2.4` ist für
  `arm64-v8a` in die APK eingebettet und offline reproduzierbar gebaut.
- Reale Primärtranskripte werden direkt auf dem Galaxy Tab abgerufen: manuelles
  Deutsch, automatisch erzeugtes Deutsch/Englisch sowie ein langes Transkript
  mit 7.311 Segmenten und 210.682 Zeichen.
- Strikte YouTube-URL-Validierung und kanonische Video-URL.
- Sicherer nativer Markdown-Renderer; Codeblöcke horizontal und lange Briefings
  vertikal auf dem Zieltablet scrollbar.
- Schlüsselloser oEmbed-Metadatenpfad als Referenz.
- Modellzugriff auf exakt `gpt-5.6-sol` und Map-Reduce-Promptlogik im
  Desktop-Referenzprototyp.
- RapidAPI-Testzähler: 3 von 100 Requests, konservativ 97 verbleibend.

Noch nicht verifiziert und damit M0-blockierend:

- Direkter OpenAI-Responses-Aufruf aus der Android-App.
- BYOK-Eingabe und verschlüsselte Speicherung der Provider-Keys über Proto
  DataStore + Tink AEAD mit Android-Keystore-geschütztem Keyset.
- Vollständiger App-Pfad ohne Zugriff auf einen LMAA-eigenen Server.

Die detaillierte Anforderungsprüfung und Nachweismatrix steht in
[`docs/anforderungs-vv.md`](docs/anforderungs-vv.md).

## 1. MVP-Umfang

### Link erfassen

- `Intent.ACTION_SEND` mit `text/plain` aus der YouTube-App empfangen.
- Link direkt eingeben oder einfügen.
- `youtube.com/watch?v=…`, `youtu.be/…`, `/shorts/…` und `/live/…`
  normalisieren.
- Nur bekannte YouTube-Hosts und eine exakt validierte elfstellige Video-ID
  akzeptieren.
- Externe Video-Intents ausschließlich aus der validierten ID als kanonische
  HTTPS-URL konstruieren.

### Transkript lokal abrufen

- `youtube-transcript-api==1.2.4` über Chaquopy 17.0 in der Android-App
  ausführen.
- Zuerst manuelle deutsche Untertitel wählen, danach geeignete Originalsprache
  und automatisch erzeugte Untertitel.
- Segmente mit Text, Startzeit und Dauer in ein Kotlin-internes Modell
  normalisieren.
- Keine YouTube-Data-API und keinen API-Key für den Primärtranskriptpfad
  voraussetzen.
- Fehler für fehlende Captions, private/gelöschte Videos, IP-Sperren,
  Netzwerkfehler und Parseränderungen unterscheidbar darstellen.

`youtube-transcript-api` verwendet einen undokumentierten YouTube-Webclient-
Zugriff. Er kann durch YouTube-Änderungen ausfallen. Mobilfunk-/Privatanschlüsse
sind weniger typisch für Rechenzentrums-IP-Sperren, ein Erfolg ist trotzdem nur
durch Tests auf dem Zielgerät belegt.

### RapidAPI ausschließlich als Fallback

- Standardmäßig deaktiviert.
- Nur nach einem geeigneten technischen Fehler des lokalen Primärproviders,
  niemals parallel und niemals vorsorglich.
- Kein Fallback bei ungültiger URL, bewusstem Abbruch, privatem/gelöschtem Video
  oder eindeutig nicht vorhandenem Transkript.
- Direkter HTTPS-Aufruf vom Tablet an den fest konfigurierten RapidAPI-Host.
- Nutzer-Key als BYOK maskiert eingeben, als Tink-AEAD-Ciphertext in Proto
  DataStore speichern, löschen und ersetzen können. Das Tink-Keyset wird über
  Android Keystore geschützt. Nach dem Speichern zeigt die UI nur die konstante
  Maske `****`, niemals Klartext, Präfix oder tatsächliche Länge.
- Jeden Versuch lokal mit Monat, Ergebnis und technischem Fehlercode zählen,
  aber niemals Key, URL oder Transkript loggen.
- Der lokale Zähler warnt rechtzeitig vor dem persönlichen Basic/Free-Limit von
  100 Requests; maßgeblich bleibt das RapidAPI-Dashboard.

Weitere Live-Aufrufe erfolgen in M0 nur, wenn ein Fallbackfehler auf dem Tablet
ohne realen Provider nicht diagnostizierbar ist. Tests verwenden ansonsten
synthetische Fixtures und einen Mock-Webserver.

### Metadaten

- YouTube oEmbed direkt aus der App und ohne API-Key aufrufen.
- Nur Titel, Kanalname und HTTPS-Thumbnail übernehmen; Embed-HTML ignorieren.
- Kanal-ID, Veröffentlichungsdatum und Dauer bleiben im MVP nullable.
- Der vorhandene Data-API-v3-Prototyp ist nicht aktiv und kein MVP-Bestandteil.

### Briefing erzeugen

- OpenAI Responses API direkt aus der Android-App aufrufen.
- Modellname exakt `gpt-5.6-sol`; kein stiller Fallback.
- `store=false`, keine Modell-Tools und klar abgegrenzte unvertrauenswürdige
  Metadaten-/Transkriptbereiche verwenden.
- Lange Transkripte deterministisch segmentieren und per Map-Reduce
  zusammenführen; Reihenfolge und Zeitbezüge erhalten.
- Ausgabe als Markdown mit mindestens:

  ```markdown
  # Kernaussage
  ## Kurzfassung
  ## Wichtigste Punkte
  ## Argumentation und Belege
  ## Genannte Personen, Organisationen und Quellen
  ## Offene Fragen / Unsicherheiten
  ## Kapitel mit Zeitmarken
  ```

### Historie, Stile und Export

- Room ist die lokale Source of Truth für Videos, Transkripte, Briefings und
  Stile.
- Alte Briefings bleiben unveränderliche Snapshots von Stilname, Stiltext und
  Modell.
- Neuerstellung mit anderem Stil erzeugt immer einen neuen Briefing-Datensatz.
- Detailansicht rendert Überschriften, Listen, Hervorhebung, Links, Zitate,
  Inline-Code und Codeblöcke ohne HTML-Ausführung.
- Jedes Briefing bietet „Video auf YouTube öffnen“, Kopieren und Teilen als
  `text/plain`.

### Nicht im ersten MVP

- Eigener Backend-Dienst oder Cloud-Hosting.
- Login, Synchronisation, Mehrgerätebetrieb und kollaborative Bibliothek.
- Audio-Transkription für Videos ohne Captions.
- Playlist-/Kanal-Batches, Play-Store-Veröffentlichung und Offline-KI-Inferenz.

## 2. Zielarchitektur

```text
YouTube-App / Direkteingabe
           │ ACTION_SEND oder URL
           ▼
Android-App
  Compose UI → ViewModel → Repository → Room
                         │
                         ├─ Chaquopy/Python
                         │    └─ youtube-transcript-api → Primärtranskript
                         │
                         ├─ Kotlin-HTTPS → YouTube oEmbed → Metadaten
                         ├─ Kotlin-HTTPS → OpenAI Responses → Markdown
                         └─ Kotlin-HTTPS → RapidAPI → optionaler Fallback
```

### Android und eingebettetes Python

- Kotlin, Jetpack Compose/Material 3, Coroutines und Room.
- Chaquopy 17.0.0, Python 3.10 und ausschließlich die für das Zielgerät
  erforderliche ABI `arm64-v8a` zunächst im persönlichen Build.
- Python-Quellen unter `android/app/src/main/python`; eine schmale Bridge gibt
  ausschließlich normalisierte DTOs oder kontrollierte Fehlercodes an Kotlin
  zurück.
- Python-/Netzwerkzugriffe nie auf dem Main Thread ausführen.
- Chaquopy- und Paketinitialisierung, APK-Größe, Kaltstart und Speicherverbrauch
  auf dem Galaxy Tab messen.
- WorkManager nur für fortsetzbare Aufträge einsetzen; Room speichert den
  Auftragszustand vor externen Requests.

Chaquopy 17 unterstützt laut Hersteller AGP 7.3–9.2, Python 3.10–3.14 und
minSdk 24. Das passt formal zu AGP 9.2.1, Python 3.10, minSdk 26 und dem
ARM64-Zielgerät. APK-Build, Offline-Rebuild, Python-TLS und reale Abrufe auf dem
Galaxy Tab sind verifiziert. Die Debug-APK ist mit eingebettetem Python rund
27,8 MB groß; detaillierte Kaltstart-/Speichermessungen bleiben offen.

### Interne Provider-Schnittstellen

Es gibt keinen HTTP-Vertrag zwischen App und eigenem Backend. Austauschbare
interne Adapter kapseln Provider:

```text
TranscriptProvider.fetch(videoId, preferredLanguages)
MetadataProvider.fetch(videoId)
BriefingGenerator.generate(metadata, transcript, styleSnapshot)
```

Der Pipeline-Orchestrator ruft immer zuerst den lokalen TranscriptProvider auf.
RapidAPI darf ausschließlich nach einem explizit klassifizierten technischen
Fehler und aktivem Opt-in aufgerufen werden.

## 3. BYOK und persönliches Bedrohungsmodell

OpenAI weist ausdrücklich darauf hin, API-Keys nicht in Client-Apps
offenzulegen. Die serverlose Anforderung und die offizielle Empfehlung stehen
damit in einem Zielkonflikt.

Für diesen ausschließlich persönlichen, sideloaded MVP gilt BYOK: Der einzige
Nutzer trägt seinen eigenen projektgebundenen Provider-Key in der App ein. Dafür
gilt folgende begrenzte Ausnahme:

- Keys werden vom Nutzer zur Laufzeit eingegeben; niemals in APK, Git,
  Ressourcen, `BuildConfig`, Fixtures oder Screenshots einbauen.
- Nur mit Tink AEAD verschlüsselter Ciphertext wird in Proto DataStore
  persistiert. Das Tink-Keyset wird mit einem nicht exportierbaren Schlüssel im
  Android Keystore geschützt.
- Nach erfolgreichem Speichern zeigt die App ausschließlich `****`. „Ersetzen“
  öffnet ein leeres Passwortfeld; der vorhandene Key wird nie zurück in die UI
  geladen. „Löschen“ entfernt den Eintrag und deaktiviert den jeweiligen Provider
  atomar, soweit dieser optional ist.
- Ciphertexte, Room-Daten und Einstellungen werden von Cloud- und
  Device-to-Device-Backups ausgeschlossen.
- Klartext existiert nur kurzzeitig im App-Prozess für den jeweiligen
  Authorization-Header und wird nie geloggt oder persistiert.
- Getrennte, projektgebundene Test-/Produktkeys mit minimalen Berechtigungen,
  Rotation und hartem Spend-Limit verwenden.
- Bei Geräteverlust, Root-Kompromittierung oder verdächtigem Verbrauch Keys
  sofort widerrufen.

Der Keystore reduziert Extraktionsrisiken, macht einen direkt von einer App
verwendeten OpenAI-Key aber nicht gleichwertig zu einem serverseitigen Secret.
Vor Verteilung an weitere Nutzer ist diese Ausnahme ungültig; dann wäre eine
neue Architekturprüfung erforderlich.

`EncryptedSharedPreferences` und `MasterKey` sind offiziell deprecated und
deshalb ausdrücklich **nicht** die Zielarchitektur. Unverschlüsselte
`SharedPreferences` sind ebenfalls unzulässig. Das offizielle AndroidX-Modul
`datastore-tink` ist derzeit Alpha. M0 entscheidet daher nach Dependency- und
Gerätetest, ob dieses Modul oder stabiles Proto DataStore mit einem eigenen
Tink-AEAD-Serializer verwendet wird. In beiden Fällen gelten dieselben
Invarianten: nur Ciphertext persistieren, Keyset über Android Keystore schützen,
keinen Klartext loggen oder sichern und atomisches Ersetzen/Löschen ermöglichen.

Die lokalen Dateien `OpenAI API KEY.txt` und
`youtube-transcripts Key.txt` dienen ausschließlich expliziten
Entwicklungs-Smokes, bleiben ignoriert und dürfen nie in die APK gelangen.

## 4. Lokales Datenmodell

| Entität | Wesentliche Felder |
|---|---|
| `Video` | `videoId` (PK), `canonicalUrl`, `title`, `channelId?`, `channelTitle`, `publishedAt?`, `durationIso8601?`, `thumbnailUrl`, `fetchedAt` |
| `Transcript` | `id`, `videoId` (FK), `provider` (`primary`/`rapidapi`), `languageCode`, `isGenerated`, `segmentsJson`, `plainText`, `fetchedAt` |
| `BriefingStyle` | `id`, `name`, `instructions`, `outputLanguage`, `isActive`, `isBuiltIn`, Zeitstempel |
| `Briefing` | `id`, `videoId` (FK), Stil-/Modell-Snapshots, `markdown`, `status`, `errorCode`, Zeitstempel |
| `ProviderUsage` | `provider`, `month`, `attempts`, `successes`, letzter technischer Status |

Room-Migrationen benötigen ab Schema-Version 1 einen Migrationstest. Weder
Provider-Keys noch vollständige Providerantworten gehören in Room.

## 5. Milestones

### M0 – Lokale Laufzeit und technische Spikes

- Android-Gerüst, CI-Workflow und Debug-APK.
- Chaquopy 17/Python 3.10/`arm64-v8a` integrieren.
- `youtube-transcript-api==1.2.4` in die APK installieren und über eine
  Kotlin-Python-Bridge aufrufen.
- Auf dem Zieltablet manuelle und automatisch erzeugte deutsche/englische
  Untertitel sowie Short und langes Video primär lokal testen.
- oEmbed und OpenAI Responses direkt aus Android anbinden.
- BYOK-Eingabe mit Proto DataStore + Tink AEAD, Android-Keystore-geschütztem
  Keyset, fester `****`-Maske und Backup-Ausschluss implementieren.
- RapidAPI-Fallbackadapter mit MockWebServer verifizieren; keine weiteren
  Live-Aufrufe ohne diagnostischen Bedarf.
- Markdown-Renderer mit langem Code-/Scroll-Fixture prüfen.

**Abnahme:** Die App startet auf Android 13 und erzeugt für ein festes Testvideo
ohne PC und ohne LMAA-Server ein Briefing aus einem lokal abgerufenen Transkript.
Netzwerk- und Adaptertests belegen, dass RapidAPI bei erfolgreichem Primärpfad
nicht aufgerufen wird. Kein Secret befindet sich in APK, Git oder Backup.

### M1 – Persistenter vertikaler Happy Path

- Direkteingabe → lokales Transkript → oEmbed → OpenAI → Markdown-Detailansicht.
- Room-Speicherung, Briefingliste, Lade-/Abbruch-/Fehlerzustand.
- Video-Button mit kanonischer URL.

**Abnahme:** Nach App-Neustart bleibt das Briefing offline lesbar; ein weiteres
Video kann ohne PC oder LMAA-Server verarbeitet werden.

### M2 – Android-Integration und Export

- `ACTION_SEND` aus YouTube.
- Markdown teilen und kopieren.
- Wiederaufnahme nach Activity-/Prozessneustart.
- Tablet-Layout, Querformat, Dark Mode und große Schrift.

### M3 – Stilverwaltung und Fallback-Einstellungen

- Stil-CRUD, aktiver Stil, Default-Schutz und unveränderliche Snapshots.
- Neuerstellung als separater historischer Eintrag.
- RapidAPI-Opt-in, maskierter Key, Löschen, lokaler Monatszähler und Warnungen.

### M4 – Härtung und APK

- Fehlermatrix, Timeouts, Retrygrenzen, lange Transkripte und fünf
  repräsentative Zielvideos.
- Room-/Instrumentierungs-/Compose-Tests und Secret-Scan der APK.
- Lokales Release-Signing und installierbare signierte APK.

## 6. Teststrategie und V&V

- **Validation:** Prüft, ob die Anforderung den persönlichen Stakeholderbedarf
  trifft und frei von unaufgelösten Widersprüchen ist.
- **Verification:** Prüft durch Tests und Artefakte, ob die Implementierung diese
  Anforderung tatsächlich erfüllt.
- Python-Unit-Tests laufen sowohl auf Desktop als auch, für Android-spezifische
  Risiken, als Instrumentierungs-/Bridge-Tests auf dem Tablet.
- Provideradapter verwenden synthetische Fixtures und MockWebServer.
- Geräte-Smokes prüfen primäre Transcript-Arten, Shorts, lange Videos,
  Mobilfunk/WLAN, Prozessneustart und Scrollverhalten.
- Architekturtest stellt sicher, dass kein LMAA-eigener Host kontaktiert wird.
- Fallback-Wahrheitstabelle prüft Opt-in, Key, zulässigen Primärfehler und exakt
  einen RapidAPI-Aufruf.
- APK-/Git-Scans suchen nach bekannten Key-Präfixen und lokalen Key-Dateinamen.
- OpenAI-Evals prüfen Pflichtüberschriften, Stiltreue, Zeitmarken,
  Halluzinationen und Map-Reduce-Konsistenz.

## 7. Risiken

| Risiko | Gegenmaßnahme |
|---|---|
| YouTube ändert die undokumentierte Caption-Schnittstelle | Paket pinnen, Adapter kapseln, lokale Fixtures, kontrollierter Updatepfad, optionaler RapidAPI-Fallback |
| Chaquopy/Paket funktioniert auf Android anders als auf Desktop | Geräteabruf ist verifiziert; Python-/ABI-/Paketversionen bleiben gepinnt und Teil der Regression |
| Python erhöht APK-Größe oder Startzeit | nur `arm64-v8a`, Lazy-Start messen, keine unnötigen Pakete |
| OpenAI-Key wird aus der Client-App missbraucht | persönliches BYOK, Sideload-Grenze, DataStore/Tink + Android Keystore, Backup-Ausschluss, restriktiver Projektkey, hartes Spend-Limit, Rotation |
| Offizielles `datastore-tink` ist Alpha | M0-Dependency-/Gerätespike; alternativ stabiles Proto DataStore mit eigenem Tink-AEAD-Serializer, identische Sicherheitsinvarianten |
| RapidAPI-Quote erschöpft | primär lokaler Abruf, Opt-in, kein Retry bei 429, lokaler Zähler und Dashboardwarnung |
| Sehr lange Transkripte | deterministisches Chunking, Map-Reduce, Abbruch/Wiederaufnahme |
| Prompt Injection oder schädliches Markdown | Daten delimitieren, keine Modell-Tools, HTML nicht ausführen, Links strikt begrenzen |
| Android beendet den Prozess | Auftrag und Zwischenstatus zuerst in Room persistieren, WorkManager nur bei Bedarf |

## 8. Primärquellen

- [Chaquopy 17 – Gradle-Plugin, Python-Versionen und Pip-Pakete](https://chaquo.com/chaquopy/doc/current/android.html)
- [Chaquopy – Version-/AGP-/minSdk-Matrix](https://chaquo.com/chaquopy/doc/current/versions.html)
- [Chaquopy – Open-Source-Lizenz](https://chaquo.com/chaquopy/license/)
- [youtube-transcript-api – offizielles Projekt](https://github.com/jdepoix/youtube-transcript-api)
- [Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [Android – EncryptedSharedPreferences (deprecated)](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences)
- [Android – Security Checklist: Keystore und Tink](https://developer.android.com/privacy-and-security/security-tips)
- [AndroidX DataStore – Tink-Verschlüsselungsmodul](https://developer.android.com/jetpack/androidx/releases/datastore)
- [Android – sensible Daten aus Backups ausschließen](https://developer.android.com/privacy-and-security/risks/backup-best-practices)
- [OpenAI API – Authentifizierung und Client-Key-Warnung](https://developers.openai.com/api/reference/overview)
- [OpenAI – gpt-5.6-sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol)
- [YouTube oEmbed](https://www.youtube.com/oembed)
- [RapidAPI – youtube-transcripts](https://rapidapi.com/8v2FWW4H6AmKw89/api/youtube-transcripts)
