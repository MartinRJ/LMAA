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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lmaa.app.secrets.ProviderSecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val context = LocalContext.current.applicationContext
                var secretStoreState by remember {
                    mutableStateOf<SecretStoreUiState>(SecretStoreUiState.Loading)
                }
                LaunchedEffect(context) {
                    secretStoreState = try {
                        SecretStoreUiState.Ready(
                            withContext(Dispatchers.IO) {
                                ProviderSecretStore.getInstance(context)
                            },
                        )
                    } catch (_: Exception) {
                        SecretStoreUiState.Error
                    }
                }
                LmaaHomeScreen(
                    transcriptProvider = LocalTranscriptProvider(context),
                    secretStoreState = secretStoreState,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LmaaHomeScreen(
    transcriptProvider: LocalTranscriptProvider,
    secretStoreState: SecretStoreUiState,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<YoutubeUrlParseResult?>(null) }
    var transcriptState by remember { mutableStateOf<TranscriptUiState>(TranscriptUiState.Idle) }
    var briefingState by remember { mutableStateOf<BriefingUiState>(BriefingUiState.Idle) }
    val metadataProvider = remember { YoutubeOEmbedMetadataProvider() }
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
                briefingState = BriefingUiState.Idle
            },
            onValidate = {
                result = YoutubeUrlParser.parse(input)
                transcriptState = TranscriptUiState.Idle
                briefingState = BriefingUiState.Idle
            },
            secretStoreState = secretStoreState,
            transcriptState = transcriptState,
            briefingState = briefingState,
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
            onGenerateBriefing = {
                val success = result as? YoutubeUrlParseResult.Success
                val transcript = (transcriptState as? TranscriptUiState.Ready)?.document
                val secretStore = (secretStoreState as? SecretStoreUiState.Ready)?.store
                if (success != null && transcript != null && secretStore != null) {
                    briefingState = BriefingUiState.Loading
                    coroutineScope.launch {
                        briefingState = try {
                            when (val metadata = metadataProvider.fetch(success.videoId)) {
                                is MetadataFetchResult.Failure -> BriefingUiState.Error(metadata.code)
                                is MetadataFetchResult.Success -> {
                                    val service = BriefingService(
                                        OpenAiBriefingTextGenerator(secretStore),
                                    )
                                    when (
                                        val briefing = service.create(
                                            transcript,
                                            metadata.metadata,
                                            success.canonicalUrl,
                                        )
                                    ) {
                                        is BriefingGenerationResult.Failure ->
                                            BriefingUiState.Error(briefing.code)
                                        is BriefingGenerationResult.Success ->
                                            BriefingUiState.Ready(briefing.document)
                                    }
                                }
                            }
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (_: Exception) {
                            BriefingUiState.Error("BRIEFING_PIPELINE_ERROR")
                        }
                    }
                } else {
                    briefingState = BriefingUiState.Error("OPENAI_KEY_MISSING")
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
    secretStoreState: SecretStoreUiState,
    transcriptState: TranscriptUiState,
    briefingState: BriefingUiState,
    onFetchTranscript: () -> Unit,
    onGenerateBriefing: () -> Unit,
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
        OpenAiKeySettings(secretStoreState)
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
                briefingState = briefingState,
                onFetchTranscript = onFetchTranscript,
                onGenerateBriefing = onGenerateBriefing,
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
    briefingState: BriefingUiState,
    onFetchTranscript: () -> Unit,
    onGenerateBriefing: () -> Unit,
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
                is TranscriptUiState.Ready -> {
                    TranscriptSummary(transcriptState.document)
                    Button(
                        onClick = onGenerateBriefing,
                        enabled = briefingState !is BriefingUiState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.generate_briefing))
                    }
                }
                is TranscriptUiState.Error -> Text(
                    stringResource(R.string.transcript_error, transcriptState.code),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when (briefingState) {
                BriefingUiState.Idle -> Unit
                BriefingUiState.Loading -> {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.briefing_loading))
                }
                is BriefingUiState.Error -> Text(
                    stringResource(R.string.briefing_error, briefingState.code),
                    color = MaterialTheme.colorScheme.error,
                )
                is BriefingUiState.Ready -> {
                    Text(
                        stringResource(R.string.briefing_ready, briefingState.document.model),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    SafeMarkdown(briefingState.document.markdown)
                }
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

private sealed interface BriefingUiState {
    data object Idle : BriefingUiState
    data object Loading : BriefingUiState
    data class Ready(val document: BriefingDocument) : BriefingUiState
    data class Error(val code: String) : BriefingUiState
}

internal sealed interface SecretStoreUiState {
    data object Loading : SecretStoreUiState
    data class Ready(val store: ProviderSecretStore) : SecretStoreUiState
    data object Error : SecretStoreUiState
}
