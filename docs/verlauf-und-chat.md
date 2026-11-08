# Portabler Verlauf und Chat

## Übertragen ohne Account

1. Auf dem bisherigen Gerät in **Einstellungen → Verlauf übertragen → Verlauf exportieren**
   (Extension: **Verwaltung → Verlauf exportieren**) die JSON-Datei speichern.
2. Datei per Quick Share, USB oder einem selbst gewählten Dateitransfer übertragen.
3. Auf dem Zielgerät **Verlauf importieren** wählen. Vorhandene Briefings bleiben
   erhalten; derselbe Export kann wiederholt importiert werden. Neue Nachrichten
   werden anhand ihrer IDs ergänzt. Benutzerstile werden zusätzlich angelegt;
   der bereits aktive Stil auf dem Zielgerät bleibt aktiv.
4. API-Keys auf einem neuen Gerät separat eingeben. Der Transfer enthält weder Keys
   noch Providerprofile, Kontingentzähler oder laufende Jobs.

Die Datei ist Klartext und enthält persönliche Inhalte. Sie sollte privat bleiben.
Der automatische Android-Cloud-/D2D-Backup-Ausschluss bleibt unverändert. Ein Export
muss erfolgen, solange die Historie noch auf dem bisherigen Gerät vorhanden ist.

## Gemeinsames Format, Version 1

```json
{
  "format": "lmaa-history",
  "version": 1,
  "briefings": [{
    "id": "stabile-portable-id",
    "videoId": "abcdefghijk",
    "title": "Synthetisches Video",
    "channel": "Testkanal",
    "markdown": "Synthetisches Briefing",
    "ts": 1,
    "model": "gpt-5.6-sol",
    "styleName": "Kurz",
    "styleInstructions": "Ein Satz.",
    "styleOutputLanguage": "Deutsch",
    "transcript": {
      "content": "[00:00:01] Synthetischer vollständiger Quelltext.",
      "label": "TRANSKRIPT",
      "language": "de",
      "provider": "primary"
    },
    "messages": [{ "id": "message-1", "role": "user", "content": "Warum?", "ts": 2 }]
  }],
  "styles": [{ "name": "Kurz", "instructions": "Ein Satz.", "outputLanguage": "Deutsch" }]
}
```

Zeitwerte sind ganzzahlige Unix-Millisekunden. `label` ist `TRANSKRIPT` oder
`RAPIDAPI_RAW_RESPONSE`; `role` ausschließlich `user` oder `assistant`. IDs sind
alphanumerisch mit Bindestrich/Unterstrich. Die kanonische URL wird aus der
validierten Video-ID konstruiert, nicht aus der Datei übernommen. Metadaten und
Markdown werden weiter als untrusted behandelt.

Grenzen: 64 MiB pro Datei, 10.000 Briefings, 100.000 Nachrichten pro Briefing,
1.000 eigene Stile. Neue Formatversionen werden kontrolliert zurückgewiesen.
Importierte Inhalte ersetzen keine bestehenden historischen Ergebnisse. Bei
abweichendem Inhalt unter derselben ID wird der gesamte Import abgebrochen. Dabei werden auch Sprache und Anbieter des Transkripts verglichen. Beide Apps melden nach erfolgreichem Import die Anzahl neu hinzugefügter Briefings und Chatnachrichten. Ein unveränderter Wiederimport ergibt für beide Zähler null.
Android verwendet eine Room-Transaktion; die Extension validiert und führt alles
zusammen, bevor sie einen gemeinsamen Storage-Schreibvorgang ausführt.

Android vergibt bei Migration 3→4 zufällige portable IDs für bestehende Briefings;
neue Briefings nutzen UUIDs. Extension-Altdaten behalten ihre vorhandenen IDs.
Chat-IDs bleiben auf allen Geräten gleich. Chatnachrichten werden beim Löschen des
zugehörigen Android-Briefings durch den Fremdschlüssel mitgelöscht.

## Websuche im Chat

Android und Extension bieten `gpt-5.6-sol` im Briefing-Chat das Responses-Tool `web_search` mit automatischer Auswahl an. Suchergebnisse gelten als unvertrauenswürdige Inhalte. HTTPS-Quellen werden direkt in der Antwort als Markdownlinks gespeichert und über das vorhandene Archivformat übertragen. Die Briefing-Erstellung verwendet weiterhin eine leere Toolliste.

## Chat-Kontext und ältere Daten

Jede Anfrage enthält Quelle und Briefing sowie einen zusammenhängenden neuesten
Ausschnitt der Nachrichten. Die Quelle wird nicht zusammengefasst. UTF-8-Bytes
bilden eine konservative Obergrenze für Byte-BPE-Tokens; das gemeinsame Budget ist
300.000 Bytes plus separat reservierter Modellausgabe. Jeder Text erhält zusätzlich
64 Bytes Framing-Reserve. Der Verlauf selbst wird niemals beschnitten.

Wenn bereits Quelle und letzte Frage das Budget übersteigen, wird kein Request
gesendet. Die Oberfläche zeigt einen Kontextfehler. Das ist bewusst konservativ;
es ist keine Behauptung über die exakte Tokenisierung/Kontextgröße des Providers.

Frühere Extension-Versionen speicherten das Transkript nicht. Für solche Einträge
verwendet der Chat das erhaltene Briefing und kennzeichnet die fehlende Quelle im
Request. Neue Analysen speichern den vollständigen Quelltext. Android-Altdaten
besitzen bereits Transkripttext/Segmente und benötigen keinen erneuten Abruf.

## Funktionsvergleich

| Funktion | Android | Extension |
|---|---|---|
| Gezielte Anweisung und zusätzlicher später normaler Abschluss | implementiert; atomare DB-Statusgrenze | implementiert; Gate vor Modellphase |
| Chat, Verlauf, Retry, keine Chat-Zusammenfassung | Room + WorkManager | chrome.storage.local + expliziter Retry nach Tab-Neustart |
| Verlaufstransfer | gemeinsames JSON v1 | gemeinsames JSON v1 |
| Eigene Stile, geschützter Standard, Sprach-/Anweisungssnapshot | vorhanden; Cold-Start-Auswahl korrigiert | Schutz, Sprache, sichtbarer aktiver Stil ergänzt |
| Zeitmarken | bestehender sicherer Renderer | einfache MM:SS/HH:MM:SS zusätzlich verlinkt; Code bleibt Text |
| Neuerstellung, Kopieren, Teilen, Löschen | vorhanden | Neuerstellung und Teilen/Dateiexport ergänzt |
| Lange Transkripte im Briefing | bestehendes Map-Reduce | stilgesteuertes Chunking ergänzt; Originalquelle bleibt erhalten |
| RapidAPI | Profile inkl. einzelner Connect/Read/Write-Timeouts | POST-Body, Statuscodes, Antwortlimit, Gesamt-Timeout, eingeschränkter cURL-Import, Defaults und Zähler ergänzt |
| Wiederaufnahme einer Analyse | WorkManager automatisch | gespeicherter Auftrag mit ursprünglichem Stil; explizites Fortsetzen nach Tab-Abbruch |
| Secret-Speicher | Keystore/Tink | weiterhin persönlicher BYOK in lokalem Extension-Speicher |

Browser-Fetch bietet keine getrennten Connect-/Read-/Write-Timeouts wie OkHttp.
Browser-Clipboard, Teilen und Shortcuts folgen den jeweiligen Plattform-APIs.
Eine Chrome-Extension kann Android WorkManager/Keystore nicht identisch abbilden.

## Gerätetest und Release vom 2026-11-08

Galaxy Tab S12 (SM-X846B, Android 17): Die isolierte Gerätesuite meldete `OK (17 tests)`. Der Archiv-Wiederimport vergleicht Zeitstempel numerisch. Das mit demselben Zertifikat signierte Update auf 0.3.0 wurde über der bestehenden Installation gestartet. Für den Chat-Websuchstand bestanden 57 Android-JVM- und 21 Extension-Tests sowie der Browsertest mit Quellenlink nach Archivtransfer. Ein separater API-Live-Test mit öffentlicher Frage lieferte einen Suchaufruf und eine URL-Zitation.

## Verifikation vor dem Gerätetest

- Android: 56 JVM-Tests, Debug-APK, Instrumentierungskompilierung und Lint bestanden.
- `python android/scripts/check-migration.py`: tatsächliche 3→4-SQL gegen
  Room-Schema 4, Bestandsdaten, IDs nach Reopen, Fremdschlüssel/Cascade bestanden.
- Neue Room-Gerätetests für 1→4/3→4, Import/Merge/Rollback und Pausegrenze sind
  vorbereitet, aber mangels angeschlossenem Android-Gerät nicht ausgeführt.
- Extension: 20 Node-Vertragstests und ein Playwright-Lauf mit echter entpackter
  Extension und ausschließlich synthetischen Providerantworten bestanden.
- Geräte-Smokes, reale Modellantworten und das signierte Release-Update bleiben
  für die Freigabe auf dem Samsung erforderlich. Niemals zum Installieren einer
  anders signierten APK die vorhandene produktive App deinstallieren.
