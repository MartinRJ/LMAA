# LMAA-Android-App

Das Android-Gerüst nutzt Kotlin, Jetpack Compose/Material 3, minSdk 26 und
targetSdk 36. Die Application ID ist vorläufig `de.lmaa.app`.

Die App ist die vollständige Produktlaufzeit. Sie darf keinen LMAA-eigenen
Backend-Dienst voraussetzen. Der Primärtranskriptpfad ist mit Chaquopy
17/Python 3.10 und `youtube-transcript-api==1.2.4` direkt in dieses Android-Modul
integriert und auf dem Zieltablet verifiziert. oEmbed, OpenAI Responses und der optionale
RapidAPI-Fallback werden direkt aus Android über HTTPS aufgerufen.

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
- OkHttp/MockWebServer 5.3.0 für direkte Provideradapter und Vertragstests

Der Build wird mit `gradlew.bat testDebugUnitTest assembleDebug lintDebug`
ausgeführt. Für den Zielgerätetest ist anschließend die Debug-APK auf dem
Galaxy Tab S7+ unter Android 13 zu installieren.

Gradles Configuration Cache ist deaktiviert, weil Chaquopy 17 während der
Konfigurationsphase den passenden Build-Python-Prozess startet. Normale
Build-Caches und Parallelisierung bleiben aktiv; ein Offline-Rebuild nach dem
ersten Dependency-Download ist verifiziert.

Am 2026-08-30 bestanden Unit-Tests, Debug-APK-Build und Android-Lint mit der
vorhandenen SDK-Platform 36. `local.properties` ist ignoriert und muss lokal
auf den jeweiligen SDK-Root verweisen. Die Debug-APK wurde anschließend auf dem
Galaxy Tab S7+ 5G (`SM-T976B`, Android 13/API 33, One UI 5.1.1) installiert und
kalt gestartet. Hochformat, initiales Layout und die Normalisierung einer
`youtu.be`-URL zur kanonischen `youtube.com/watch`-URL wurden visuell und über
die UI-Hierarchie geprüft; App-Abstürze traten nicht auf. Querformat, Dark Mode,
große Schrift und Samsung-Sharesheet bleiben spätere Gerätesmokes.

Die direkte URL-Eingabe arbeitet bereits mit einer Host-Whitelist, akzeptiert
nur die geplanten URL-Formen und konstruiert die Vorschau ausschließlich aus
der validierten elfstelligen Video-ID. Der lokale Transcript-Request läuft
außerhalb des Main Threads und gibt ausschließlich normalisierte DTOs oder
kontrollierte Fehlercodes an Compose zurück. Room, oEmbed und Briefing-Erzeugung
folgen in den nächsten vertikalen Schritten.

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
Fallbackrequest und keinen Retry bei HTTP 429. RapidAPI bleibt bei 3/100.

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

Die AndroidX-Komponenten stammen aus dem stabilen Google-Maven-Kanal und sind
Apache-2.0-lizenziert. JUnit 4.13.2 ist EPL-1.0-lizenziert. Versionen sind fest
eingetragen bzw. über die fest gepinnte Compose BOM kontrolliert.

Chaquopy ist seit Version 12.0.1 frei und MIT-lizenziert. Version 17 unterstützt
laut Hersteller AGP 7.3–9.2, Python 3.10–3.14 und minSdk 24; die formale
Kompatibilität, der konkrete Build und reale Geräteabrufe sind verifiziert.
`youtube-transcript-api`, Requests, urllib3 und charset-normalizer sind MIT-
lizenziert; defusedxml und idna BSD-3-Clause, Certifi MPL-2.0.
