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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lmaa.app.secrets.ProviderSecretStore
import de.lmaa.app.history.AnalysisJob
import de.lmaa.app.history.AnalysisJobRepository
import de.lmaa.app.history.AnalysisJobStatus
import de.lmaa.app.history.BriefingHistoryItem
import de.lmaa.app.history.BriefingHistoryRepository
import de.lmaa.app.history.LmaaDatabase
import de.lmaa.app.history.StoredBriefing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
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
            MaterialTheme(
                colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) {
                    darkColorScheme()
                } else {
                    lightColorScheme()
                },
            ) {
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
                val database = remember(context) { LmaaDatabase.getInstance(context) }
                LmaaApp(
                    historyRepository = remember(database) {
                        BriefingHistoryRepository(database.briefingDao())
                    },
                    analysisJobRepository = remember(database) {
                        AnalysisJobRepository(database.analysisJobDao())
                    },
                    workScheduler = remember(context) { AnalysisWorkScheduler(context) },
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
    historyRepository: BriefingHistoryRepository,
    analysisJobRepository: AnalysisJobRepository,
    workScheduler: AnalysisWorkScheduler,
    secretStoreState: SecretStoreUiState,
    incomingShare: IncomingShare?,
    onShareConsumed: (Long) -> Unit,
) {
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var detail by remember { mutableStateOf<StoredBriefing?>(null) }
    LaunchedEffect(detailId) {
        val id = detailId
        detail = if (id == null) null else historyRepository.find(id)
    }
    HandleSystemBack(enabled = detailId != null) { detailId = null }

    val currentDetail = detail
    if (detailId == null) {
        LmaaHomeScreen(
            historyRepository = historyRepository,
            analysisJobRepository = analysisJobRepository,
            workScheduler = workScheduler,
            secretStoreState = secretStoreState,
            incomingShare = incomingShare,
            onShareConsumed = onShareConsumed,
            onBriefingReady = {
                detail = it
                detailId = it.briefingId
            },
        )
    } else if (currentDetail != null) {
        BriefingDetailScreen(currentDetail, onBack = { detailId = null })
    } else {
        CircularProgressIndicator()
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
    historyRepository: BriefingHistoryRepository,
    analysisJobRepository: AnalysisJobRepository,
    workScheduler: AnalysisWorkScheduler,
    secretStoreState: SecretStoreUiState,
    incomingShare: IncomingShare?,
    onShareConsumed: (Long) -> Unit,
    onBriefingReady: (StoredBriefing) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var localErrorCode by rememberSaveable { mutableStateOf<String?>(null) }
    var enqueueInProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val history by historyRepository.history.collectAsState(initial = emptyList())
    val currentJob by analysisJobRepository.current.collectAsState(initial = null)
    val analysisState = analysisUiState(currentJob, localErrorCode, enqueueInProgress)

    fun startAnalysis(rawInput: String) {
        if (enqueueInProgress) return
        input = rawInput
        localErrorCode = null
        enqueueInProgress = true
        coroutineScope.launch {
            val parsed = YoutubeUrlParser.parse(rawInput)
            if (parsed !is YoutubeUrlParseResult.Success) {
                localErrorCode = when (parsed) {
                    YoutubeUrlParseResult.Error.EMPTY -> "URL_EMPTY"
                    YoutubeUrlParseResult.Error.INVALID -> "URL_INVALID"
                    YoutubeUrlParseResult.Error.AMBIGUOUS -> "URL_AMBIGUOUS"
                }
                enqueueInProgress = false
                return@launch
            }

            val store = (secretStoreState as? SecretStoreUiState.Ready)?.store
            val hasOpenAiKey = try {
                store?.status?.first()?.hasOpenAiKey == true
            } catch (_: Exception) {
                false
            }
            if (store == null || !hasOpenAiKey) {
                localErrorCode = "OPENAI_KEY_MISSING"
                enqueueInProgress = false
                return@launch
            }
            currentJob?.takeIf { !it.status.isTerminal }?.let {
                workScheduler.cancel(analysisJobRepository, it.jobId)
            }
            currentJob?.takeIf { it.status.isTerminal }?.let {
                analysisJobRepository.consumeResult(it.jobId)
            }
            val job = try {
                analysisJobRepository.create(parsed.canonicalUrl)
            } catch (_: Exception) {
                localErrorCode = "LOCAL_JOB_ERROR"
                enqueueInProgress = false
                return@launch
            }
            try {
                workScheduler.enqueue(job.jobId)
            } catch (_: Exception) {
                analysisJobRepository.markFailed(job.jobId, "LOCAL_SCHEDULER_ERROR")
            } finally {
                enqueueInProgress = false
            }
        }
    }

    LaunchedEffect(analysisJobRepository, workScheduler) {
        try {
            workScheduler.reconcile(analysisJobRepository)
        } catch (_: Exception) {
            localErrorCode = "LOCAL_SCHEDULER_ERROR"
        }
    }

    LaunchedEffect(currentJob?.jobId, currentJob?.status) {
        val job = currentJob
        if (job?.status == AnalysisJobStatus.SUCCEEDED && job.briefingId != null) {
            val stored = try {
                historyRepository.find(job.briefingId)
            } catch (_: Exception) {
                null
            }
            if (stored == null) {
                localErrorCode = "BRIEFING_NOT_FOUND"
            } else {
                analysisJobRepository.consumeResult(job.jobId)
                onBriefingReady(stored)
            }
        }
    }

    LaunchedEffect(currentJob?.jobId) {
        currentJob?.takeIf { !it.status.isTerminal }?.let { input = it.canonicalUrl }
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
                localErrorCode = null
                currentJob?.takeIf { job -> job.status.isTerminal }?.let { job ->
                    coroutineScope.launch { analysisJobRepository.consumeResult(job.jobId) }
                }
            },
            onAnalyze = { startAnalysis(input) },
            onCancelAnalysis = {
                currentJob?.let { job ->
                    coroutineScope.launch { workScheduler.cancel(analysisJobRepository, job.jobId) }
                }
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
                        localErrorCode = "BRIEFING_NOT_FOUND"
                    } else {
                        onBriefingReady(stored)
                    }
                }
            },
            contentPadding = contentPadding,
        )
    }
}

private val AnalysisJobStatus.isTerminal: Boolean
    get() = this == AnalysisJobStatus.SUCCEEDED ||
        this == AnalysisJobStatus.FAILED ||
        this == AnalysisJobStatus.CANCELLED

private fun analysisUiState(
    job: AnalysisJob?,
    localErrorCode: String?,
    enqueueInProgress: Boolean,
): AnalysisUiState {
    if (localErrorCode != null) return AnalysisUiState.Error(localErrorCode)
    if (enqueueInProgress) return AnalysisUiState.Loading(AnalysisStage.TRANSCRIPT)
    return when (job?.status) {
        AnalysisJobStatus.ENQUEUED,
        AnalysisJobStatus.RUNNING,
        -> AnalysisUiState.Loading(job.stage ?: AnalysisStage.TRANSCRIPT)
        AnalysisJobStatus.FAILED -> AnalysisUiState.Error(job.errorCode ?: "BRIEFING_PIPELINE_ERROR")
        AnalysisJobStatus.SUCCEEDED,
        AnalysisJobStatus.CANCELLED,
        null,
        -> AnalysisUiState.Idle
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val fontScale = LocalDensity.current.fontScale
        if (AdaptiveLayoutPolicy.useTwoPane(maxWidth.value, fontScale)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HomePrimaryContent(
                        input = input,
                        onInputChanged = onInputChanged,
                        onAnalyze = onAnalyze,
                        onCancelAnalysis = onCancelAnalysis,
                        secretStoreState = secretStoreState,
                        analysisState = analysisState,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HistoryContent(history, onOpenHistory)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                HomePrimaryContent(
                    input = input,
                    onInputChanged = onInputChanged,
                    onAnalyze = onAnalyze,
                    onCancelAnalysis = onCancelAnalysis,
                    secretStoreState = secretStoreState,
                    analysisState = analysisState,
                )
                Spacer(Modifier.height(8.dp))
                HistoryContent(history, onOpenHistory)
            }
        }
    }
}

@Composable
private fun HomePrimaryContent(
    input: String,
    onInputChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    onCancelAnalysis: () -> Unit,
    secretStoreState: SecretStoreUiState,
    analysisState: AnalysisUiState,
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
}

@Composable
private fun HistoryContent(
    history: List<BriefingHistoryItem>,
    onOpenHistory: (Long) -> Unit,
) {
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            val useTwoPane = AdaptiveLayoutPolicy.useTwoPane(
                maxWidth.value,
                LocalDensity.current.fontScale,
            )
            if (useTwoPane) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 380.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BriefingSummary(context, briefing)
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        SafeMarkdown(briefing.markdown)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    BriefingSummary(context, briefing)
                    SafeMarkdown(briefing.markdown)
                }
            }
        }
    }
}

@Composable
private fun BriefingSummary(context: Context, briefing: StoredBriefing) {
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
            OutlinedButton(
                onClick = { copyMarkdown(context, briefing.markdown) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.copy_markdown))
            }
            OutlinedButton(
                onClick = { shareMarkdown(context, briefing.markdown) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.share_briefing))
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
    "LOCAL_JOB_ERROR", "LOCAL_SCHEDULER_ERROR" -> stringResource(R.string.local_job_error)
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
