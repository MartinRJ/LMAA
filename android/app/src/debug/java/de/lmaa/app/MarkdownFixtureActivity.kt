package de.lmaa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MarkdownFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SafeMarkdown(
                        markdown = MARKDOWN_FIXTURE,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                    )
                }
            }
        }
    }
}

private const val MARKDOWN_FIXTURE = """# Kernaussage

Das Video erklärt **synthetisch**, wie LMAA Briefings rendert.

## Kurzfassung

Der Renderer zeigt *Hervorhebung*, `Inline-Code` und sichere Links als Compose-Text.

## Wichtigste Punkte

- HTML wird nicht ausgeführt.
- [Video öffnen](https://www.youtube.com/watch?v=ABCDEFGHIJK)
- [Unsicherer Link bleibt Text](javascript:alert(1))

## Argumentation und Belege

1. Eingabe wird in Blöcke zerlegt.
2. Nur HTTPS-Links werden interaktiv.

> Dies ist ein synthetisches Testfixture ohne fremde Inhalte.

## Genannte Personen, Organisationen und Quellen

Keine realen Quellen.

## Offene Fragen / Unsicherheiten

Tabellen und verschachtelte Listen fallen im MVP auf Klartext zurück.

## Kapitel mit Zeitmarken

- [00:00](https://www.youtube.com/watch?v=ABCDEFGHIJK&t=0s) Start

```kotlin
data class Briefing(
    val title: String,
    val markdown: String,
)

fun render(briefing: Briefing): String =
    if (briefing.markdown.isBlank()) "Kein Inhalt" else briefing.markdown

val longEndpoint = "https://www.youtube.com/watch?v=ABCDEFGHIJK&feature=share&test=horizontal-scroll"
<script>bleibt sichtbarer Code und wird niemals ausgeführt</script>
```

## Technische Detailanalyse

Der erste Verarbeitungsschritt normalisiert die URL und übernimmt ausschließlich die validierte Video-ID. Danach werden Metadaten und Transkript getrennt geladen.

- oEmbed liefert Titel, Kanalname und Thumbnail.
- Der Primärprovider lädt verfügbare Untertitel.
- Lange Transkripte werden in geordnete Chunks zerlegt.
- Das Ergebnis wird als unvertrauenswürdiges Markdown gerendert.

## Datenfluss

1. Die App validiert die Video-ID und lädt das Transkript lokal über Chaquopy.
2. Metadaten kommen schlüssellos über oEmbed direkt auf das Tablet.
3. OpenAI erzeugt das Briefing ohne Tools und ohne stillen Modell-Fallback.
4. Die App speichert das abgeschlossene Ergebnis unveränderlich in Room.

> Lange Inhalte müssen lesbar bleiben, ohne dass Auswahl, Links oder Codeblöcke die vertikale Navigation blockieren.

## Weitere Prüfpunkte

Dieser zusätzliche synthetische Abschnitt erzwingt mehr als eine Bildschirmseite. Der Test scrollt vom Anfang bis zu dieser Schlussmarke und anschließend wieder zurück.

- Schlussmarke: `VERTICAL_SCROLL_END`
- Erwartung: keine abgeschnittenen Blöcke
- Erwartung: kein Layoutsprung
- Erwartung: kein AndroidRuntime-Crash
"""
