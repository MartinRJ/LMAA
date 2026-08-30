# LMAA-Android-App

Das Android-Gerüst nutzt Kotlin, Jetpack Compose/Material 3, minSdk 26 und
targetSdk 36. Die Application ID ist vorläufig `de.lmaa.app`.

Die App ist die vollständige Produktlaufzeit. Sie darf keinen LMAA-eigenen
Backend-Dienst voraussetzen. Der Primärtranskriptpfad ist mit Chaquopy
17/Python 3.10 und `youtube-transcript-api==1.2.4` direkt in dieses Android-Modul
integriert und auf dem Zieltablet verifiziert. oEmbed, OpenAI Responses und der optionale
RapidAPI-Fallback werden direkt aus Android über HTTPS aufgerufen.

Das UI verwendet ein eigenes Material-3-Schema aus Lavendel, Sage/Oliv und
warmem Off-White mit separaten Light-/Dark-Rollen. Das Launcher-Asset ist ein
Adaptive Icon mit maskensicherem Vordergrund und monochromer Android-13-
Variante; `AndroidManifest.xml` referenziert reguläres und rundes Icon.

Provider-Keys folgen persönlichem BYOK: Der Nutzer trägt sie einmalig in ein
Passwortfeld ein. Die App persistiert nur Tink-AEAD-Ciphertext in Proto
DataStore; das Tink-Keyset wird über Android Keystore geschützt. Anschließend
zeigt die UI konstant `****`; Ersetzen beginnt immer mit einem leeren Feld.
Secret-Store und App-Daten werden vollständig aus Cloud- und D2D-Backups
ausgeschlossen. `EncryptedSharedPreferences` und unverschlüsselte
`SharedPreferences` sind keine Zielarchitektur. Da das offizielle
`datastore-tink`-Modul derzeit Alpha ist, verwendet die App stabiles Proto
DataStore 1.2.1 mit eigenem Tink-1.23-AEAD-Serializer. Das verschlüsselte
Tink-Keyset wird ohne SharedPreferences durch einen Android-Keystore-AES-GCM-
Schlüssel geschützt und im No-Backup-Bereich gespeichert.

## Toolchain

- JDK 17
- Android Gradle Plugin 9.2.1
- Gradle 9.4.1
- Kotlin/Compose-Compiler 2.3.21
- Compose BOM 2025.08.00 (Compose 1.9.x, kompatibel mit compileSdk 36)
- AndroidX Activity Compose 1.11.0
- AndroidX Core KTX 1.17.0
- Android SDK Platform 36 und Build Tools 36.0.0
- Chaquopy 17.0.0, Python 3.10, ausschließlich `arm64-v8a`
- `youtube-transcript-api==1.2.4` mit vollständig gepinnten Python-Abhängigkeiten
- Proto DataStore 1.2.1, Protobuf 4.32.1 und Tink Android 1.23.0
- Room 2.8.4 mit KSP 2.3.10 und exportiertem Schema 3
- WorkManager 2.11.2 für persistente, langlebige Analyseaufträge
- OkHttp/MockWebServer 5.3.0 für direkte Provideradapter und Vertragstests

Der vollständige lokale Build wird mit `gradlew.bat testInstrumentedUnitTest
assembleDebug assembleInstrumented assembleInstrumentedAndroidTest lintDebug`
ausgeführt. Die Variante `instrumented` verwendet die isolierte Application ID
`de.lmaa.app.testbed`; Instrumentierung darf dadurch nicht auf Daten der täglich
genutzten App `de.lmaa.app` zugreifen. Für einen Zielgerätetest werden
`app-instrumented.apk` und `app-instrumented-androidTest.apk` installiert.

Gradles Configuration Cache ist deaktiviert, weil Chaquopy 17 während der
Konfigurationsphase den passenden Build-Python-Prozess startet. Normale
Build-Caches und Parallelisierung bleiben aktiv; ein Offline-Rebuild nach dem
ersten Dependency-Download ist verifiziert.

Am 2026-08-30 bestanden Unit-Tests, Debug-APK-Build und Android-Lint mit der
vorhandenen SDK-Platform 36. `local.properties` ist ignoriert und muss lokal
auf den jeweiligen SDK-Root verweisen. Die Debug-APK wurde anschließend auf dem
Galaxy Tab S7+ 5G (`SM-T976B`, Android 13/API 33, One UI 5.1.1) installiert und
kalt gestartet. Hoch-/Querformat, Dark Mode, 150-%-Schriftgröße und die
Normalisierung einer `youtu.be`-URL zur kanonischen `youtube.com/watch`-URL
wurden visuell und über die UI-Hierarchie geprüft; App-Abstürze traten nicht
auf. Der Samsung-/YouTube-Sharesheet-Smoke ist ebenfalls bestanden.

Die direkte URL-Eingabe arbeitet mit einer Host-Whitelist, akzeptiert nur die
geplanten URL-Formen und konstruiert Providerrequests ausschließlich aus der
validierten elfstelligen Video-ID. Ein einzelner Button sequenziert Linkprüfung,
lokales Transkript, oEmbed, OpenAI und Room-Speicherung. `ACTION_SEND` aus der
YouTube-App startet denselben Orchestrator automatisch und wird genau einmal
konsumiert. Der manuelle Samsung-/YouTube-Sharesheet-Smoke ist bestanden.

Das Ergebnis erscheint in einer eigenen scrollbaren Detailansicht mit
kanonischem Video-Intent, Kopieren, Teilen und bestätigtem Löschen. Copy und
Share enthalten Titel, Kanal, kanonische URL und Markdown. Room speichert
normalisierte Video-, Transkript- und Briefing-Daten; Stiltext, Ausgabesprache
und Modell werden pro Briefing gesnapshottet. Vor einer erneuten Analyse
verweist ein Dialog auf das neueste Briefing derselben URL. Zwei Analysen
desselben Videos bleiben getrennte Datensätze. Die
Historie und ihr Detail-View wurden nach APK-Update und Kaltstart ohne erneute
Analyse geöffnet. Schema 3 liegt unter `app/schemas`; die Migration 1→3 ist
durch einen datenbewahrenden Instrumentierungstest verifiziert. Jede spätere
Änderung benötigt ebenfalls Migration plus Migrationstest.

Die eigene Settings-Ansicht verwaltet OpenAI- und RapidAPI-BYOK. RapidAPI ist
als deklaratives Providerprofil mit `Aus`, `Nur als Fallback` und `RapidAPI
bevorzugt` konfigurierbar. Endpoint, GET/POST, Query, erlaubte Header, Body,
Erfolgsstatuscodes, vier Timeouts und Antwortlimit sind editierbar. Die
Platzhalter `{{canonical_url}}`, `{{video_id}}`, `{{language}}` und
`{{rapidapi_key}}` werden kontrolliert eingesetzt; der Key bleibt ausschließlich
im verschlüsselten Secret-Store. Ein eingeschränkter cURL-Importer liest
Dashboard-Beispiele deklarativ, führt aber keinen Befehl aus. Defaults stellen
die bekannte `youtube-transcripts`-Vorlage wieder her, erhalten den Key und
setzen den Modus auf `Aus`. Nach Status-/Content-Type-/UTF-8-/Größenprüfung geht
der komplette Response-Body ohne providerspezifische Deserialisierung als
UNTRUSTED-Block an OpenAI.

Die Stilansicht unterstützt benutzerdefinierte Stile, Aktivierung, Bearbeitung
und Löschung; der integrierte Standardstil ist geschützt. Eigene Stile steuern
Inhalt, Auswahl, Struktur, Sprache und Ausgabeformat vollständig. Die bisher
globalen Pflichtüberschriften sowie Regeln zu Quellenkritik, Unsicherheiten,
Zeitmarken und Markdown liegen jetzt ausschließlich in `Standard`. Map-Reduce
erhält die aktive Stilkonfiguration ohne eigenes Format; Zeilenumbrüche bleiben
erhalten. Nur technische UNTRUSTED-/Tool-/Providergrenzen, leere Ausgabe und
aktives unsicheres Markup bleiben global. `ensureDefault` synchronisiert eine
bereits installierte geschützte Default-Definition, während Analyseauftrag und
Briefing Name, Anweisung und Ausgabesprache weiterhin als unveränderlichen
Snapshot speichern. Der lokale RapidAPI-Zähler erfasst Versuche und Erfolge. `/100` wird
nur als hellgrauer Basic-Tarif-Hinweis dargestellt; das gebuchte Kontingent ist
der App nicht bekannt.

Vor dem ersten Providerrequest legt die App einen `analysis_jobs`-Datensatz an.
Ein eindeutig benannter WorkManager-`CoroutineWorker` führt die Pipeline mit
Netzwerkbedingung und Foreground-Benachrichtigung aus. Resultat und erfolgreicher
Jobstatus werden atomar gespeichert. Ein Force-Stop während `BRIEFING` stellte
nach Kaltstart URL und Phase wieder her und erzeugte exakt einen neuen
Historieneintrag. Abbruch in der UI markiert zuerst den Room-Job und storniert
danach die WorkManager-Arbeit.

Bei ausreichender effektiver Breite zeigen Home und Detail zwei getrennt
scrollbare Bereiche. Bei 150-%-Systemschrift wird einspaltig mit gestapelten,
vollbreiten Aktionen gerendert. MaterialTheme und Systemleisten folgen dem
System-Dark-Mode.

Der lokale Primärpfad ist auf dem Galaxy Tab verifiziert. Reale Geräte-Smokes
lieferten manuelles Deutsch (60 Segmente), automatisch erzeugtes Deutsch
(1.660), automatisch erzeugtes Englisch (27) und ein langes englisches
Transkript (7.311 Segmente/210.682 Zeichen). Die produktive URL→Abruf-UI und die
Vorvalidierung `INVALID_VIDEO_ID` wurden ebenfalls geprüft; RapidAPI wurde nicht
aufgerufen. Die Debug-APK ist rund 27,8 MB groß.

Der direkte oEmbed-/OpenAI-Pfad aus Android mit BYOK-Secret-Store ist auf dem
Zieltablet verifiziert. Ein kurzer Test lieferte lokal 27 Transkriptsegmente;
ein umfangreicher Map-Reduce-Test verarbeitete 7.311 Segmente und 210.682
Zeichen. Beide riefen anschließend oEmbed und exakt `gpt-5.6-sol` direkt auf
und renderten alle Pflichtüberschriften ohne Backend. Der lange Test enthielt
zahlreiche Zeitmarken und Programmierbegriffe als Inline-Code. Der Key blieb
nach Kaltstart und APK-Update als `****`
maskiert; Ersetzen begann leer. RapidAPI wurde nicht aufgerufen. Für den
M0-Abschluss wurden zusätzlich ein expliziter Short und die Android-RapidAPI-
Wahrheitstabelle geprüft: `engQjz-Lm54` wurde korrekt kanonisiert und endete
wegen deaktivierter Captions kontrolliert ohne RapidAPI-Aufruf. MockWebServer
belegt Opt-in, Keypflicht, geschlossene Fehler-Whitelist, genau einen
Fallbackrequest und keinen Retry bei HTTP 429. RapidAPI steht nach einem
weiteren vollständigen Release-E2E-Lauf über Raw-Response und OpenAI bei fünf
lokal dokumentierten Versuchen/Erfolgen;
`/100` ist nur ein bedingter Basic-Tarif-Hinweis.

Der erfolgreiche Short `Rq5iOD-mcEI` lieferte ein englisches Transkript und
durchlief mehrfach den vollständigen Share-/Briefingpfad. Das Gegenpaar mit
`engQjz-Lm54` belegt, dass Short-Unterstützung und Caption-Verfügbarkeit
getrennte Eigenschaften sind.

Gerätetesthinweis: Gradles UTP-Task `connectedDebugAndroidTest` kann die Daten
der installierten Debug-App ersetzen. Er darf deshalb nicht gegen die täglich
genutzte Installation mit BYOK/Room-Historie laufen. M4 stellt deshalb
standardmäßig gegen `de.lmaa.app.testbed` zusammen. Dreizehn isolierte
Instrumentierungstests liefen auf dem Zieltablet; der optionale RapidAPI-
Live-Smoke war ohne explizites Instrumentierungsargument übersprungen. Für den
einmaligen Live-Smoke wurde der Test gezielt gegen `debug` gebaut, verwendete
den bereits in der App gespeicherten BYOK und erhöhte den lokalen Zähler exakt
von 3 auf 4. Die Testbed-Pakete wurden danach entfernt. Nach dem Release-Cutover
bestätigte ein weiterer Lauf im Modus `RapidAPI bevorzugt` die unveränderte Raw-
Response-Übergabe bis zum gespeicherten Briefing und erhöhte den Stand auf 5/5;
anschließend wurde `Nur als Fallback` wieder aktiviert.

## Release-Signing

Private Signing-Dateien bleiben lokal und sind ignoriert. Die versionierte
Vorlage `signing.properties.example` wird nach `signing.properties` kopiert und
verweist standardmäßig auf `signing/lmaa-release.jks`. Passwörter und Keystore
dürfen weder in Git noch in Supportausgaben erscheinen. `verifyReleaseSigning`
stoppt `assembleRelease` und `bundleRelease`, wenn Konfiguration oder Keystore
fehlen. Nach der einmaligen interaktiven Keystore-Erstellung baut
`gradlew.bat assembleRelease` die signierte APK.

`scripts/create-release-signing.ps1` erzeugt den lokalen RSA-4096-Keystore und
zufällige Passwörter ohne Secret-Ausgabe. Der einmalig freigegebene Clean-
Cutover ist abgeschlossen: `de.lmaa.app` läuft release-signiert auf dem
Zieltablet. Künftige Releases mit demselben gesicherten Keystore können per
`adb install -r` ohne Datenverlust aktualisiert werden. Eine Deinstallation ist
kein regulärer Bestandteil des Build-/Testablaufs. Keystore und
`signing.properties` müssen gemeinsam außerhalb des Repositories gesichert
werden.

Die M3-Settings- und Stilansichten sind auf dem Zielgerät verifiziert. Ein
synthetischer Stil wurde angelegt, aktiviert, in einem realen OpenAI-Briefing
gesnapshottet und danach zusammen mit dem erzeugten Testbriefing wieder entfernt;
der Endzustand ist `Standard`. Nach dem Release-Cutover enthält die neue lokale
Historie das erfolgreiche Raw-Response-Testbriefing; RapidAPI steht bei 5/5.
Nach der Freigabe benutzerdefinierter Ausgabeformen erzeugte ein weiterer
temporärer Stil live exakt einen kurzen Satz ohne Überschrift, Liste, Zeitmarke
oder Markdown-Struktur. Testbriefing und Stil wurden anschließend gelöscht,
`Standard` erneut aktiviert und der RapidAPI-Stand blieb unverändert bei 5/5.

Der native Compose-Renderer `SafeMarkdown` deckt Überschriften, Absätze, Listen,
Zitate, Trennlinien, Hervorhebungen, Inline-Code, Codeblöcke und Markdown-Links ab.
Er interpretiert kein HTML und macht ausschließlich HTTPS-Links ohne Userinfo oder
expliziten Port interaktiv. Das vollständige Debug-Fixture wurde am 2026-08-30 auf
dem entsperrten Zieltablet visuell sowie per UI-Hierarchie geprüft; es trat kein
AndroidRuntime-Crash auf.

Ein verlängertes synthetisches Fixture prüfte zusätzlich mehrzeiligen Kotlin-Code,
Einrückung, lange Codezeilen und HTML-Literaltext. Horizontaler Scroll innerhalb des
Codeblocks und vertikaler Scroll über ein mehrseitiges Briefing wurden auf dem
Zieltablet manuell bestätigt. Ein beim unsicheren Link verbleibendes `)` wurde als
Parserfehler identifiziert, behoben und mit einem Regressionstest abgesichert.

Die AndroidX-Komponenten einschließlich Room 2.8.4 stammen aus dem stabilen
Google-Maven-Kanal und sind Apache-2.0-lizenziert. KSP 2.3.10 ist
Apache-2.0-lizenziert und wird ausschließlich zur Build-Zeit für Room-Codegen
verwendet. JUnit 4.13.2 ist EPL-1.0-lizenziert. Versionen sind fest eingetragen
bzw. über die fest gepinnte Compose BOM kontrolliert.

Chaquopy ist seit Version 12.0.1 frei und MIT-lizenziert. Version 17 unterstützt
laut Hersteller AGP 7.3–9.2, Python 3.10–3.14 und minSdk 24; die formale
Kompatibilität, der konkrete Build und reale Geräteabrufe sind verifiziert.
`youtube-transcript-api`, Requests, urllib3 und charset-normalizer sind MIT-
lizenziert; defusedxml und idna BSD-3-Clause, Certifi MPL-2.0.
