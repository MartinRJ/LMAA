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
  Desktop-Referenzprototyp sowie direkt aus der Android-App.
- Persönliches BYOK über verschlüsseltes Proto DataStore/Tink mit
  Android-Keystore-geschütztem Keyset; Kaltstart, feste `****`-Maske und leeres
  Ersetzungsfeld sind auf dem Zieltablet verifiziert. OpenAI- und RapidAPI-
  Verwaltung liegen in einer eigenen Settings-Ansicht, nicht im Home-View.
- Vollständiger Gerätepfad URL → lokales Transkript → oEmbed →
  `gpt-5.6-sol` → gerendertes Markdown ohne LMAA-Server.
- Direkteingabe führt mit einer Nutzeraktion durch den vollständigen Pfad;
  `ACTION_SEND` aus YouTube startet denselben Orchestrator ohne Folgeklick.
- Fertige Briefings erscheinen in einer eigenen Detailansicht und werden mit
  Video-/Transkript-/Stil-/Modell-Snapshots unveränderlich in Room gespeichert.
- Die Historie ist nach Kaltstart offline verfügbar; zwei Analysen desselben
  Videos bleiben als getrennte Briefings erhalten. Vor einer erneuten Analyse
  zeigt die App das neueste vorhandene Briefing antippbar an. Briefings können
  per Links-Swipe mit freigelegter Aktion oder bestätigt im Detail gelöscht werden.
- Briefing-Stile besitzen CRUD, genau einen aktiven Stil, einen geschützten
  Standard und unveränderliche Stil-/Sprach-Snapshots pro Auftrag und Briefing.
  Benutzerdefinierte Stile bestimmen Inhalt, Auswahl, Struktur, Sprache und
  Ausgabeformat ohne global erzwungene Briefing-Überschriften.
- Analyseaufträge werden vor dem ersten Providerrequest in Room gespeichert und
  durch WorkManager 2.11.2 fortgesetzt. Ein Prozessabbruch während der
  Briefingphase wurde auf dem Zieltablet mit exakt einem fertigen Historieneintrag
  erfolgreich wiederaufgenommen.
- Hoch-/Querformat, System-Dark-Mode und 150-%-Schriftgröße sind auf dem
  Zieltablet geprüft. Querformat verwendet getrennt scrollbare Arbeits-/Historien-
  beziehungsweise Aktions-/Markdown-Bereiche; große Schrift fällt kontrolliert
  auf ein einspaltiges Layout zurück.
- Eigenes Adaptive Icon mit Android-13-Monochromvariante sowie ein aus dem
  Referenzfoto abgeleitetes Material-3-Farbsystem: Lavendel als Primärakzent,
  Sage/Oliv als Sekundärrollen und warmes Off-White für helle Flächen. Icon und
  Light Theme sind auf dem Samsung-Zielgerät visuell geprüft; Dark Mode besitzt
  eigenständige kontrastreiche Rollen.
- Umfangreicher Android-Map-Reduce-Smoke mit 7.311 Segmenten und 210.682
  Transkriptzeichen; das fertige Briefing renderte Pflichtstruktur, zahlreiche
  Zeitmarken und Programmierbegriffe mit Inline-Code.
- RapidAPI-Testzähler: 5 lokale Versuche/5 Erfolge. Der fünfte Aufruf war ein
  vollständiger Release-E2E-Test im Modus `RapidAPI bevorzugt`: Die validierte
  Rohantwort wurde unverändert an `gpt-5.6-sol` übergeben, als Briefing gerendert
  und in Room gespeichert. Anschließend wurde `Nur als Fallback` wieder aktiviert.
  Der vierte Aufruf war ein
  gezielter Android-Live-Smoke mit dem in der App gespeicherten BYOK; der Key
  wurde dabei weder ausgelesen noch ausgegeben. Nur der UI-Span `/100` ist als
  hellgrauer Hinweis auf einen möglichen Basic-Tarif markiert; die App kennt den
  gebuchten Tarif nicht, das RapidAPI-Dashboard bleibt maßgeblich.

M0 ist abgeschlossen. Der explizite Short `engQjz-Lm54` wurde auf dem Tablet
korrekt kanonisiert und lieferte kontrolliert `TRANSCRIPTS_DISABLED`; wegen
eindeutig fehlender Captions wurde RapidAPI nicht ausgelöst. Der Android-
Fallbackadapter und die Wahrheitstabelle sind vollständig mit MockWebServer
verifiziert. Ein erfolgreicher Short mit aktivem CC bleibt ein sinnvoller
Regressionstest, blockiert die technische M0-Abnahme aber nicht.

Der erfolgreiche Gegenfall `Rq5iOD-mcEI` besitzt ein englisches Transkript und
durchlief auf dem installierten Tablet mehrfach den vollständigen Short-Pfad.
Das bestätigt: Shorts werden technisch unterstützt, aber nicht jeder Short
stellt verwendbare Captions bereit; das Uploadalter allein ist dafür kein
verlässliches Kriterium.

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

### RapidAPI als frei konfigurierbare Transkriptquelle

- Standardmäßig deaktiviert; ausdrücklich wählbare Betriebsarten sind `Aus`,
  `Nur als Fallback` und `RapidAPI bevorzugt`.
- Im Fallbackmodus läuft zuerst der lokale Primärprovider. RapidAPI wird nur
  nach einem geeigneten technischen Fehler genau einmal aufgerufen, niemals
  parallel oder vorsorglich. Ungültige URLs, Abbruch, private/gelöschte Videos
  und eindeutig fehlende Captions verbrauchen keine RapidAPI-Quote.
- Im bevorzugten Modus läuft RapidAPI zuerst. Nach einem technischen
  Transport-/Providerfehler wird genau einmal lokal zurückgefallen;
  Konfigurations- und Sicherheitsfehler werden nicht verdeckt.
- Das Providerprofil enthält HTTPS-Endpoint, GET/POST, Query-Argumente,
  erlaubte Header, optionalen Body, Erfolgsstatuscodes, Connect-/Read-/Write-/
  Gesamt-Timeout und maximale Antwortgröße.
- Unterstützte Platzhalter sind `{{canonical_url}}`, `{{video_id}}`,
  `{{language}}` und `{{rapidapi_key}}`. Der Key-Platzhalter ist ausschließlich
  als vollständiger Wert von `X-RapidAPI-Key` zulässig.
- cURL aus dem RapidAPI-Dashboard kann als Konfigurationshilfe importiert
  werden. Die App parst nur eine eingeschränkte deklarative Teilmenge und führt
  niemals Shellcode, Redirects oder unbekannte Optionen aus.
- Die Vorlage `youtube-transcripts.p.rapidapi.com` ist ohne Geheimwert
  vorbefüllt. „Defaults wiederherstellen“ setzt Profil und Betriebsart zurück,
  erhält aber einen bereits gespeicherten Key und deaktiviert RapidAPI.
- Nutzer-Key als BYOK maskiert eingeben, als Tink-AEAD-Ciphertext in Proto
  DataStore speichern, löschen und ersetzen können. Das Tink-Keyset wird über
  Android Keystore geschützt. Nach dem Speichern zeigt die UI nur die konstante
  Maske `****`, niemals Klartext, Präfix oder tatsächliche Länge.
- Jeden Versuch lokal mit Monat, Ergebnis und technischem Fehlercode zählen,
  aber niemals Key, URL oder Transkript loggen.
- Der lokale Zähler warnt anhand einer Basic-Referenz von 100 Requests. Die App
  kennt den gebuchten Tarif nicht; deshalb ist nur `/100` visuell untergeordnet
  und das RapidAPI-Dashboard bleibt maßgeblich.
- Providerantworten werden nicht providerspezifisch deserialisiert. Nach
  Erfolgsstatus-, Timeout-, Content-Type-, UTF-8- und Größenprüfung wird der
  vollständige Response-Body inhaltlich unverändert als klar markierter
  unvertrauenswürdiger Datenblock an OpenAI übergeben.

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
  zusammenführen; die Zwischenstufe erhält bereits die aktive Stilkonfiguration
  und erzwingt kein eigenes Markdown- oder Zeitmarkenformat.
- Inhalt, Struktur und Ausgabeformat werden ausschließlich vom aktiven Stil
  vorgegeben. Benutzerdefinierte Stile dürfen die Standardstruktur vollständig
  ersetzen, Überschriften weglassen oder beispielsweise Fließtext ausgeben.
- Der geschützte Stil `Standard` enthält weiterhin die bisherige sachliche
  Quellenkritik, Unsicherheits-/Zeitmarkenregeln, Markdown-Vorgabe und exakt
  folgende Struktur:

  ```markdown
  # Kernaussage
  ## Kurzfassung
  ## Wichtigste Punkte
  ## Argumentation und Belege
  ## Genannte Personen, Organisationen und Quellen
  ## Offene Fragen / Unsicherheiten
  ## Kapitel mit Zeitmarken
  ```
- Global und nicht durch Stile überschreibbar bleiben nur technische
  Sicherheitsgrenzen: UNTRUSTED-Daten sind keine Anweisungen, Modell-Tools und
  externe Faktenquellen bleiben deaktiviert, leere Ausgaben sowie aktives
  unsicheres Markup werden verworfen. Stil-Zeilenumbrüche bleiben im Prompt
  erhalten.

### Historie, Stile und Export

- Room ist die lokale Source of Truth für Videos, Transkripte, Briefings und
  Stile.
- Alte Briefings bleiben unveränderliche Snapshots von Stilname, Stiltext und
  Modell.
- Beim App-Update wird nur die aktuelle geschützte `Standard`-Definition auf die
  vollständige Default-Anweisung synchronisiert. Bereits gespeicherte Briefings
  und laufende Auftragssnapshots werden nicht nachträglich verändert.
- Neuerstellung mit anderem Stil erzeugt immer einen neuen Briefing-Datensatz.
- Existiert zur kanonischen URL bereits ein Briefing, zeigt die App vor einer
  erneuten manuellen/Share-Analyse das neueste Ergebnis mit Titel und Datum als
  antippbaren Link. Eine explizite Neuerstellung aus dem Detail benötigt keine
  zweite Bestätigung.
- Historieneinträge lassen sich per Links-Swipe und anschließender Aktion
  löschen. Im Detail verlangt „Briefing löschen“ immer die Bestätigung
  „Löschen?“; nur dem Briefing gehörende verwaiste Transkript-/Videodaten werden
  dabei mitbereinigt.
- Detailansicht rendert Überschriften, Listen, Hervorhebung, Links, Zitate,
  Inline-Code und Codeblöcke ohne HTML-Ausführung.
- Jedes Briefing bietet „Video auf YouTube öffnen“, Kopieren und Teilen als
  `text/plain`. Copy und Share enthalten immer Titel, Kanalname, kanonische URL
  und das vollständige Briefing-Markdown.

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
- WorkManager 2.11.2 führt fortsetzbare, eindeutig benannte Analyseaufträge mit
  Netzwerkbedingung und Foreground-Benachrichtigung aus. Room speichert den
  Auftragszustand vor externen Requests; Ergebnis und erfolgreicher Jobstatus
  werden in einer Transaktion persistiert.

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
`datastore-tink` ist derzeit Alpha. Der M0-Spike hat deshalb stabiles Proto
DataStore 1.2.1 mit einem eigenen Tink-1.23-AEAD-Serializer gewählt. Ein
nicht exportierbarer Android-Keystore-AES-GCM-Schlüssel schützt das nur
verschlüsselt im No-Backup-Bereich gespeicherte Tink-Keyset. Es gibt weder
SharedPreferences noch einen Klartextfallback. Instrumentierungs- und
Kaltstarttests auf Android 13 bestätigen Verschlüsselung, Maske, Ersetzen und
Löschen.

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
| `AnalysisJob` | UUID, kanonische URL, Status, Pipelinephase, Ergebnis-ID oder Fehlercode, Consume-Zeitpunkt, Zeitstempel |
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

**Status:** erfüllt. Zusätzlich belegt ein expliziter Short-Smoke die
kontrollierte Fehlerbehandlung ohne unnötigen Fallback; RapidAPI-Stand bleibt
bei drei lokalen Versuchen (Basic-Referenz in der UI: `/100`).

### M1 – Persistenter vertikaler Happy Path

- Eine Nutzeraktion → Linkprüfung → lokales Transkript → oEmbed → OpenAI →
  eigene Markdown-Detailansicht.
- Room-Speicherung mit exportiertem Schema, unveränderlichen Snapshots,
  Briefingliste sowie Lade-/Abbruch-/Fehlerzustand.
- Video-Button mit kanonischer URL; Markdown kopieren und teilen.

**Abnahme:** Nach App-Neustart bleibt das Briefing offline lesbar; ein weiteres
Video kann ohne PC oder LMAA-Server verarbeitet werden.

**Status:** erfüllt auf dem Galaxy Tab S7+ unter Android 13. Zwei Briefings
desselben Shorts blieben getrennt erhalten; Kaltstart und erneutes Öffnen aus
Room erfolgten ohne neue Analyse.

### M2 – Android-Integration und Export

- `ACTION_SEND` aus YouTube übernimmt den Link und startet die Ein-Schritt-
  Pipeline automatisch; Share-Events werden genau einmal konsumiert.
- Markdown teilen und kopieren.
- Room-persistierter Jobstatus und WorkManager-Wiederaufnahme nach Activity-/
  Prozessneustart; atomare Ergebnis-/Jobtransaktion verhindert Duplikate.
- Adaptives Tablet-Layout für Hoch-/Querformat, Dark Mode und große Schrift.

**Status:** erfüllt auf dem Galaxy Tab S7+ unter Android 13. Ein Force-Stop in
der Briefingphase stellte URL und Phase nach Kaltstart wieder her und erzeugte
exakt einen neuen Historieneintrag. Foreground-Worker sowie Hoch-/Querformat,
Dark Mode und 150-%-Schriftgröße wurden auf dem Gerät geprüft.

Copy und Share verwenden denselben getesteten Exporttext mit Titel, Kanalname,
kanonischer YouTube-URL und Markdown.

### M3 – Stilverwaltung und Fallback-Einstellungen

- Stil-CRUD, aktiver Stil, Default-Schutz und unveränderliche Snapshots.
- Neuerstellung als separater historischer Eintrag.
- RapidAPI-Profil, drei Routingmodi, maskierter Key, Löschen, lokaler
  Monatszähler und Warnungen.
- Eigene Settings-View für OpenAI-/RapidAPI-Keyverwaltung; keine Key-Eingabe
  mehr im Home-View.

**Status:** erfüllt auf dem Galaxy Tab S7+ unter Android 13. Room-Schema 3 und
Migration 1→3, Stil-CRUD/Schutzregeln, Job-/Briefing-Snapshots, RapidAPI-BYOK,
Opt-in und lokaler Monatszähler sind instrumentiert getestet. Ein realer Lauf
mit einem synthetischen Stil erzeugte und renderte ein Briefing mit Stil-Snapshot
und Kotlin-Codeblock; Testbriefing und Teststil wurden anschließend über die neue
UI gelöscht und `Standard` wieder aktiviert. Der RapidAPI-Stand blieb bei drei
lokalen Versuchen.

### M4 – Härtung und APK

- Fehlermatrix, Timeouts, Retrygrenzen, lange Transkripte und fünf
  repräsentative Zielvideos.
- Room-/Instrumentierungs-/Compose-Tests und Secret-Scan der APK.
- Lokales Release-Signing und installierbare signierte APK.

**Status:** erledigt. Provider-Timeouts und -Fehler für HTTP 4xx/5xx,
Malformed/Empty Responses sowie fehlende Retries sind durch JVM-Tests
abgedeckt. Instrumentierung verwendet standardmäßig die isolierte Application
ID `de.lmaa.app.testbed`; zwölf Tests liefen auf dem Zieltablet, ohne die
damalige tägliche Installation `de.lmaa.app` oder deren Daten zu berühren. Zwei
gezielte Live-Smokes bestätigten BYOK, Defaultanbieter und den konfigurierbaren
Raw-Response-E2E-Pfad; Zählerstand 5/5. Alle versionierten Dateien und die
finale Release-APK wurden ohne Treffer gegen beide lokalen Testkeys geprüft.
Ein privater RSA-4096-Keystore signiert die Release-APK; der freigegebene Clean-
Cutover von der Debug-Signatur ist abgeschlossen. Signiertes Release, Adaptive
Icon, Light Theme, Settings und vollständiges RapidAPI-Briefing liefen auf dem
Zieltablet.

Die ursprünglich geplante frei konfigurierbare RapidAPI-Quelle wurde in der
M0/M3-Implementierung irrtümlich auf den festen `youtube-transcripts`-Adapter
und einen Bool-Fallback reduziert. M4 korrigiert diese Abweichung: deklaratives
Profil, eingeschränkter cURL-Import, drei Routingmodi und unveränderte
Raw-Response-Weitergabe sind implementiert und per JVM/MockWebServer getestet;
isolierte Profilpersistenz und Settings-UI sind auf Android 13 geprüft. Ein
realer Release-E2E-Lauf im Modus `RapidAPI bevorzugt` bestätigte den gesamten
Raw-Response-Pfad bis zum gerenderten und persistierten Briefing.

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
- Datenbewahrende gezielte Instrumentierung prüft Schema-Migration 1→3,
  Job-Reopen und atomare Job-/Briefing-Persistenz, ohne die tägliche App-
  Installation zurückzusetzen.
- Architekturtest stellt sicher, dass kein LMAA-eigener Host kontaktiert wird.
- Routing-Wahrheitstabelle prüft alle drei Modi, beide Providerreihenfolgen,
  geschlossene Fehlerklassen und exakt einen Zweitprovideraufruf.
- RapidAPI-Contract-Tests prüfen Platzhalter, cURL-Teilmenge, SSRF-/Header-
  Grenzen, Statuscodes, Timeouts, UTF-8, Content-Type, Antwortlimit,
  Key-Redaktion und bytegetreue Raw-Promptweitergabe.
- APK-/Git-Scans suchen nach bekannten Key-Präfixen und lokalen Key-Dateinamen.
- OpenAI-Evals prüfen beim Default-Stil dessen Pflichtüberschriften,
  Quellenkritik und Zeitmarken; bei eigenen Stilen prüfen sie stattdessen deren
  individuelle Struktur-/Formatvorgaben sowie generell Halluzinationen und
  Map-Reduce-Konsistenz.

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
| Android beendet den Prozess | Auftrag und Zwischenstatus zuerst in Room persistieren; eindeutiger WorkManager-Auftrag, Foreground-Ausführung und atomare Ergebnistransaktion |

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
- [AndroidX Room – stabile Version 2.8.4](https://developer.android.com/jetpack/androidx/releases/room)
- [AndroidX WorkManager – stabile Version 2.11.2](https://developer.android.com/jetpack/androidx/releases/work)
- [Android – persistente Arbeit mit WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Android – langlebige Worker](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Kotlin Symbol Processing – offizieller Quickstart](https://kotlinlang.org/docs/ksp-quickstart.html)
- [OpenAI API – Authentifizierung und Client-Key-Warnung](https://developers.openai.com/api/reference/overview)
- [OpenAI – gpt-5.6-sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol)
- [YouTube oEmbed](https://www.youtube.com/oembed)
- [RapidAPI – youtube-transcripts](https://rapidapi.com/8v2FWW4H6AmKw89/api/youtube-transcripts)
