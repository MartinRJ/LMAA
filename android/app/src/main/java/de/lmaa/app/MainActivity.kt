package de.lmaa.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lmaa.app.secrets.ProviderSecretStore
import de.lmaa.app.history.BriefingHistoryItem
import de.lmaa.app.history.BriefingHistoryRepository
import de.lmaa.app.history.LmaaDatabase
import de.lmaa.app.history.StoredBriefing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var shareSequence = 0L
    private val incomingShare = mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptShareIntent(intent)
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
                LmaaApp(
                    transcriptProvider = LocalTranscriptProvider(context),
                    historyRepository = remember(context) {
                        BriefingHistoryRepository(
                            LmaaDatabase.getInstance(context).briefingDao(),
                        )
                    },
                    secretStoreState = secretStoreState,
                    incomingShare = incomingShare.value,
                    onShareConsumed = { sequence ->
                        if (incomingShare.value?.sequence == sequence) {
                            incomingShare.value = null
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShareIntent(intent)
    }

    private fun acceptShareIntent(intent: Intent?) {
        val sharedText = intent?.takeIf {
            it.action == Intent.ACTION_SEND && it.type?.startsWith("text/") == true
        }?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (sharedText.isNotEmpty()) {
            shareSequence += 1
            incomingShare.value = IncomingShare(shareSequence, sharedText)
        }
    }
}

private data class IncomingShare(val sequence: Long, val text: String)

@Composable
private fun LmaaApp(
    transcriptProvider: TranscriptProvider,
    historyRepository: BriefingHistoryRepository,
    secretStoreState: SecretStoreUiState,
    incomingShare: IncomingShare?,
    onShareConsumed: (Long) -> Unit,
) {
    var detail by remember { mutableStateOf<StoredBriefing?>(null) }
    HandleSystemBack(enabled = detail != null) { detail = null }

    val currentDetail = detail
    if (currentDetail == null) {
        LmaaHomeScreen(
            transcriptProvider = transcriptProvider,
            historyRepository = historyRepository,
            secretStoreState = secretStoreState,
            incomingShare = incomingShare,
            onShareConsumed = onShareConsumed,
            onBriefingReady = { detail = it },
        )
    } else {
        BriefingDetailScreen(currentDetail, onBack = { detail = null })
    }
}

@Composable
private fun HandleSystemBack(enabled: Boolean, onBack: () -> Unit) {
    val activity = LocalActivity.current as? ComponentActivity
    val callback = remember {
        object : OnBackPressedCallback(enabled) {
            override fun handleOnBackPressed() = onBack()
        }
    }
    callback.isEnabled = enabled
    DisposableEffect(activity, callback) {
        activity?.onBackPressedDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LmaaHomeScreen(
    transcriptProvider: TranscriptProvider,
    historyRepository: BriefingHistoryRepository,
    secretStoreState: SecretStoreUiState,
    incomingShare: IncomingShare?,
    onShareConsumed: (Long) -> Unit,
    onBriefingReady: (StoredBriefing) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var analysisState by remember { mutableStateOf<AnalysisUiState>(AnalysisUiState.Idle) }
    var analysisJob by remember { mutableStateOf<Job?>(null) }
    val metadataProvider = remember { YoutubeOEmbedMetadataProvider() }
    val coroutineScope = rememberCoroutineScope()
    val history by historyRepository.history.collectAsState(initial = emptyList())

    fun startAnalysis(rawInput: String) {
        analysisJob?.cancel()
        input = rawInput
        analysisState = AnalysisUiState.Loading(AnalysisStage.TRANSCRIPT)
        analysisJob = coroutineScope.launch {
            val parsed = YoutubeUrlParser.parse(rawInput)
            if (parsed !is YoutubeUrlParseResult.Success) {
                analysisState = AnalysisUiState.Error(
                    when (parsed) {
                        YoutubeUrlParseResult.Error.EMPTY -> "URL_EMPTY"
                        YoutubeUrlParseResult.Error.INVALID -> "URL_INVALID"
                        YoutubeUrlParseResult.Error.AMBIGUOUS -> "URL_AMBIGUOUS"
                    },
                )
                return@launch
            }

            val store = (secretStoreState as? SecretStoreUiState.Ready)?.store
            val hasOpenAiKey = try {
                store?.status?.first()?.hasOpenAiKey == true
            } catch (_: Exception) {
                false
            }
            if (store == null || !hasOpenAiKey) {
                analysisState = AnalysisUiState.Error("OPENAI_KEY_MISSING")
                return@launch
            }

            val pipeline = BriefingPipeline(
                transcriptProvider = transcriptProvider,
                metadataProvider = metadataProvider,
                briefingCreator = BriefingService(OpenAiBriefingTextGenerator(store)),
            )
            try {
                when (
                    val result = pipeline.analyze(rawInput) { stage ->
                        analysisState = AnalysisUiState.Loading(stage)
                    }
                ) {
                    is AnalysisResult.Success -> {
                        analysisState = AnalysisUiState.Loading(AnalysisStage.PERSISTING)
                        val stored = try {
                            historyRepository.save(result.analysis)
                        } catch (_: Exception) {
                            analysisState = AnalysisUiState.Error("LOCAL_SAVE_ERROR")
                            return@launch
                        }
                        analysisState = AnalysisUiState.Idle
                        onBriefingReady(stored)
                    }
                    is AnalysisResult.Failure -> analysisState = AnalysisUiState.Error(result.code)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                analysisState = AnalysisUiState.Error("BRIEFING_PIPELINE_ERROR")
            }
        }
    }

    LaunchedEffect(incomingShare?.sequence, secretStoreState) {
        val share = incomingShare
        if (share != null && secretStoreState is SecretStoreUiState.Ready) {
            startAnalysis(share.text)
            onShareConsumed(share.sequence)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        HomeContent(
            input = input,
            onInputChanged = {
                input = it
                if (analysisState !is AnalysisUiState.Loading) {
                    analysisState = AnalysisUiState.Idle
                }
            },
            onAnalyze = { startAnalysis(input) },
            onCancelAnalysis = {
                analysisJob?.cancel()
                analysisJob = null
                analysisState = AnalysisUiState.Idle
            },
            secretStoreState = secretStoreState,
            analysisState = analysisState,
            history = history,
            onOpenHistory = { briefingId ->
                coroutineScope.launch {
                    val stored = try {
                        historyRepository.find(briefingId)
                    } catch (_: Exception) {
                        null
                    }
                    if (stored == null) {
                        analysisState = AnalysisUiState.Error("BRIEFING_NOT_FOUND")
                    } else {
                        onBriefingReady(stored)
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
    onInputChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCancelAnalysis: () -> Unit,
    secretStoreState: SecretStoreUiState,
    analysisState: AnalysisUiState,
    history: List<BriefingHistoryItem>,
    onOpenHistory: (Long) -> Unit,
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
            enabled = analysisState !is AnalysisUiState.Loading,
            isError = analysisState is AnalysisUiState.Error && analysisState.code.startsWith("URL_"),
        )
        Button(
            onClick = onAnalyze,
            enabled = analysisState !is AnalysisUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_briefing_one_step))
        }

        when (analysisState) {
            AnalysisUiState.Idle -> Unit
            is AnalysisUiState.Loading -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnalysisProgress(analysisState.stage)
                OutlinedButton(onClick = onCancelAnalysis) {
                    Text(stringResource(R.string.cancel_analysis))
                }
            }
            is AnalysisUiState.Error -> Text(
                text = analysisErrorMessage(analysisState.code),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (history.isEmpty()) {
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
        } else {
            history.forEach { item -> HistoryItem(item, onOpenHistory) }
        }
    }
}

@Composable
private fun AnalysisProgress(stage: AnalysisStage) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(
            text = stringResource(
                when (stage) {
                    AnalysisStage.TRANSCRIPT -> R.string.analysis_transcript
                    AnalysisStage.METADATA -> R.string.analysis_metadata
                    AnalysisStage.BRIEFING -> R.string.analysis_briefing
                    AnalysisStage.PERSISTING -> R.string.analysis_persisting
                },
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun HistoryItem(
    item: BriefingHistoryItem,
    onOpen: (Long) -> Unit,
) {
    OutlinedButton(
        onClick = { onOpen(item.briefingId) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.history_item_meta,
                    item.channelTitle,
                    formatHistoryTime(item.createdAtEpochMillis),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingDetailScreen(
    briefing: StoredBriefing,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.briefing_detail_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = briefing.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = briefing.channelTitle,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    R.string.briefing_detail_model,
                    briefing.model,
                    briefing.transcriptLanguage,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { openCanonicalVideo(context, briefing.canonicalUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.open_youtube_video))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { copyMarkdown(context, briefing.markdown) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.copy_markdown))
                }
                OutlinedButton(
                    onClick = { shareMarkdown(context, briefing.markdown) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.share_briefing))
                }
            }
            SafeMarkdown(briefing.markdown)
        }
    }
}

private fun openCanonicalVideo(context: Context, canonicalUrl: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(canonicalUrl)))
}

private fun copyMarkdown(context: Context, markdown: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("LMAA-Briefing", markdown))
}

private fun shareMarkdown(context: Context, markdown: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, markdown)
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_briefing)),
    )
}

private fun formatHistoryTime(epochMillis: Long): String =
    HISTORY_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

@Composable
private fun analysisErrorMessage(code: String): String = when (code) {
    "URL_EMPTY" -> stringResource(R.string.url_error_empty)
    "URL_INVALID" -> stringResource(R.string.url_error_invalid)
    "URL_AMBIGUOUS" -> stringResource(R.string.url_error_ambiguous)
    "OPENAI_KEY_MISSING" -> stringResource(R.string.openai_key_missing)
    "LOCAL_SAVE_ERROR" -> stringResource(R.string.local_save_error)
    "TRANSCRIPTS_DISABLED", "NO_TRANSCRIPT_FOUND" ->
        stringResource(R.string.no_transcript_available)
    else -> stringResource(R.string.analysis_error, code)
}

private sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Loading(val stage: AnalysisStage) : AnalysisUiState
    data class Error(val code: String) : AnalysisUiState
}

internal sealed interface SecretStoreUiState {
    data object Loading : SecretStoreUiState
    data class Ready(val store: ProviderSecretStore) : SecretStoreUiState
    data object Error : SecretStoreUiState
}

private val HISTORY_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
