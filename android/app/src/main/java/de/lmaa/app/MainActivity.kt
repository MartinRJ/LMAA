package de.lmaa.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                LmaaHomeScreen(LocalTranscriptProvider(applicationContext))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LmaaHomeScreen(transcriptProvider: LocalTranscriptProvider) {
    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<YoutubeUrlParseResult?>(null) }
    var transcriptState by remember { mutableStateOf<TranscriptUiState>(TranscriptUiState.Idle) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        HomeContent(
            input = input,
            result = result,
            onInputChanged = {
                input = it
                result = null
                transcriptState = TranscriptUiState.Idle
            },
            onValidate = {
                result = YoutubeUrlParser.parse(input)
                transcriptState = TranscriptUiState.Idle
            },
            transcriptState = transcriptState,
            onFetchTranscript = {
                val success = result as? YoutubeUrlParseResult.Success
                if (success != null) {
                    transcriptState = TranscriptUiState.Loading
                    coroutineScope.launch {
                        transcriptState = when (val fetched = transcriptProvider.fetch(success.videoId)) {
                            is TranscriptFetchResult.Success -> TranscriptUiState.Ready(fetched.document)
                            is TranscriptFetchResult.Failure -> TranscriptUiState.Error(fetched.code)
                        }
                    }
                }
            },
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun HomeContent(
    input: String,
    result: YoutubeUrlParseResult?,
    onInputChanged: (String) -> Unit,
    onValidate: () -> Unit,
    transcriptState: TranscriptUiState,
    onFetchTranscript: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.app_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            value = input,
            onValueChange = onInputChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.url_label)) },
            placeholder = { Text(stringResource(R.string.url_placeholder)) },
            singleLine = false,
            isError = result is YoutubeUrlParseResult.Error,
            supportingText = {
                errorMessage(result)?.let { Text(it) }
            },
        )
        Button(onClick = onValidate, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.validate_link))
        }

        if (result is YoutubeUrlParseResult.Success) {
            LinkPreview(
                result = result,
                transcriptState = transcriptState,
                onFetchTranscript = onFetchTranscript,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(
                text = stringResource(R.string.history_empty),
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LinkPreview(
    result: YoutubeUrlParseResult.Success,
    transcriptState: TranscriptUiState,
    onFetchTranscript: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.ready_for_analysis),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.canonical_url),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(result.canonicalUrl, style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = onFetchTranscript,
                enabled = transcriptState !is TranscriptUiState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.fetch_local_transcript))
            }
            when (transcriptState) {
                TranscriptUiState.Idle -> Unit
                TranscriptUiState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.transcript_loading))
                }
                is TranscriptUiState.Ready -> TranscriptSummary(transcriptState.document)
                is TranscriptUiState.Error -> Text(
                    stringResource(R.string.transcript_error, transcriptState.code),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TranscriptSummary(document: TranscriptDocument) {
    Text(
        text = stringResource(R.string.transcript_ready),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Text(stringResource(R.string.transcript_language, document.languageCode))
    Text(
        stringResource(
            R.string.transcript_kind,
            if (document.isGenerated) {
                stringResource(R.string.transcript_generated)
            } else {
                stringResource(R.string.transcript_manual)
            },
        ),
    )
    Text(stringResource(R.string.transcript_segments, document.segments.size))
    Text(stringResource(R.string.transcript_characters, document.characterCount))
}

@Composable
private fun errorMessage(result: YoutubeUrlParseResult?): String? = when (result) {
    YoutubeUrlParseResult.Error.EMPTY -> stringResource(R.string.url_error_empty)
    YoutubeUrlParseResult.Error.AMBIGUOUS -> stringResource(R.string.url_error_ambiguous)
    YoutubeUrlParseResult.Error.INVALID -> stringResource(R.string.url_error_invalid)
    else -> null
}

private sealed interface TranscriptUiState {
    data object Idle : TranscriptUiState
    data object Loading : TranscriptUiState
    data class Ready(val document: TranscriptDocument) : TranscriptUiState
    data class Error(val code: String) : TranscriptUiState
}
