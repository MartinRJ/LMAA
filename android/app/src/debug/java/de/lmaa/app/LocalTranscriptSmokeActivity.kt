package de.lmaa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

class LocalTranscriptSmokeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        val provider = LocalTranscriptProvider(applicationContext)
        setContent {
            MaterialTheme {
                TranscriptSmokeScreen(videoId, provider)
            }
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "video_id"
    }
}

@Composable
private fun TranscriptSmokeScreen(
    videoId: String,
    provider: LocalTranscriptProvider,
) {
    val result by produceState<TranscriptFetchResult?>(null, videoId) {
        value = provider.fetch(videoId)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Local transcript device smoke", style = MaterialTheme.typography.headlineSmall)
        Text("videoId=$videoId", fontFamily = FontFamily.Monospace)
        when (val current = result) {
            null -> {
                CircularProgressIndicator()
                Text("SMOKE_RUNNING")
            }
            is TranscriptFetchResult.Failure -> {
                Text("SMOKE_ERROR", color = MaterialTheme.colorScheme.error)
                Text("code=${current.code}", fontFamily = FontFamily.Monospace)
            }
            is TranscriptFetchResult.Success -> {
                val document = current.document
                Text("SMOKE_OK", color = MaterialTheme.colorScheme.primary)
                Text("provider=${document.provider}", fontFamily = FontFamily.Monospace)
                Text("language=${document.languageCode}", fontFamily = FontFamily.Monospace)
                Text("generated=${document.isGenerated}", fontFamily = FontFamily.Monospace)
                Text("segments=${document.segments.size}", fontFamily = FontFamily.Monospace)
                Text("characters=${document.characterCount}", fontFamily = FontFamily.Monospace)
            }
        }
    }
}
