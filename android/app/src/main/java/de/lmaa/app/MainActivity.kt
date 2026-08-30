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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
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
import de.lmaa.app.history.BriefingStyle
import de.lmaa.app.history.BriefingStyleRepository
import de.lmaa.app.history.LmaaDatabase
import de.lmaa.app.history.ProviderUsageRepository
import de.lmaa.app.history.StoredBriefing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private var shareSequence = 0L
    private val incomingShare = mutableStateOf<IncomingShare?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        acceptShareIntent(intent)
        setContent {
            LmaaTheme {
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
                val styleRepository = remember(database) {
                    BriefingStyleRepository(database.briefingStyleDao())
                }
                val usageRepository = remember(database) {
                    ProviderUsageRepository(database.providerUsageDao())
                }
                val rapidApiSettingsRepository = remember(context) {
                    RapidApiSettingsRepository.getInstance(context)
                }
                LaunchedEffect(styleRepository, usageRepository) {
                    styleRepository.ensureDefault()
                    usageRepository.ensureDevelopmentBaseline()
                }
                LmaaApp(
                    historyRepository = remember(database) {
                        BriefingHistoryRepository(database.briefingDao())
                    },
                    analysisJobRepository = remember(database) {
                        AnalysisJobRepository(database.analysisJobDao())
                    },
                    workScheduler = remember(context) { AnalysisWorkScheduler(context) },
                    styleRepository = styleRepository,
                    usageRepository = usageRepository,
                    rapidApiSettingsRepository = rapidApiSettingsRepository,
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
        val sharedText = extractIncomingYoutubeText(
            action = intent?.action,
            mimeType = intent?.type,
            sharedText = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
        if (sharedText != null) {
            shareSequence += 1
            incomingShare.value = IncomingShare(shareSequence, sharedText)
        }
    }
}

internal fun extractIncomingYoutubeText(
    action: String?,
    mimeType: String?,
    sharedText: String?,
): String? {
    val candidate = sharedText.takeIf {
        action == Intent.ACTION_SEND && mimeType == "text/plain"
    }
    return candidate?.trim()?.takeIf(String::isNotEmpty)
}

private data class IncomingShare(val sequence: Long, val text: String)

private data class PendingDuplicateRequest(
    val canonicalUrl: String,
    val existingBriefing: StoredBriefing,
)

@Composable
private fun LmaaApp(
    historyRepository: BriefingHistoryRepository,
    analysisJobRepository: AnalysisJobRepository,
    workScheduler: AnalysisWorkScheduler,
    styleRepository: BriefingStyleRepository,
    usageRepository: ProviderUsageRepository,
    rapidApiSettingsRepository: RapidApiSettingsRepository,
    secretStoreState: SecretStoreUiState,
    incomingShare: IncomingShare?,
    onShareConsumed: (Long) -> Unit,
) {
    val appScope = rememberCoroutineScope()
    var destination by rememberSaveable { mutableStateOf(AppDestination.HOME) }
    var detailId by rememberSaveable { mutableStateOf<Long?>(null) }
    var regenerationUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<StoredBriefing?>(null) }
    LaunchedEffect(detailId) {
        val id = detailId
        detail = if (id == null) null else historyRepository.find(id)
    }
    LaunchedEffect(incomingShare?.sequence) {
        if (incomingShare != null) {
            detailId = null
            destination = AppDestination.HOME
        }
    }
    HandleSystemBack(enabled = detailId != null || destination != AppDestination.HOME) {
        if (detailId != null) detailId = null else destination = AppDestination.HOME
    }

    val currentDetail = detail
    when {
        detailId != null && currentDetail != null -> BriefingDetailScreen(
            briefing = currentDetail,
            onBack = { detailId = null },
            onRegenerate = {
                regenerationUrl = currentDetail.canonicalUrl
                detailId = null
                destination = AppDestination.HOME
            },
            onDelete = {
                appScope.launch {
                    if (historyRepository.delete(currentDetail.briefingId)) {
                        detail = null
                        detailId = null
                    }
                }
            },
        )
        detailId != null -> CircularProgressIndicator()
        destination == AppDestination.SETTINGS -> SettingsScreen(
            secretStoreState = secretStoreState,
            usageRepository = usageRepository,
            rapidApiSettingsRepository = rapidApiSettingsRepository,
            onBack = { destination = AppDestination.HOME },
        )
        destination == AppDestination.STYLES -> BriefingStylesScreen(
            repository = styleRepository,
            onBack = { destination = AppDestination.HOME },
        )
        else -> LmaaHomeScreen(
            historyRepository = historyRepository,
            analysisJobRepository = analysisJobRepository,
            workScheduler = workScheduler,
            styleRepository = styleRepository,
            secretStoreState = secretStoreState,
            incomingShare = incomingShare,
            regenerationUrl = regenerationUrl,
            onShareConsumed = onShareConsumed,
            onRegenerationConsumed = { regenerationUrl = null },
            onOpenSettings = { destination = AppDestination.SETTINGS },
            onOpenStyles = { destination = AppDestination.STYLES },
            onBriefingReady = {
                detail = it
                detailId = it.briefingId
            },
        )
    }
}

private enum class AppDestination { HOME, SETTINGS, STYLES }

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
    styleRepository: BriefingStyleRepository,
    secretStoreState: SecretStoreUiState,
    incomingShare: IncomingShare?,
    regenerationUrl: String?,
    onShareConsumed: (Long) -> Unit,
    onRegenerationConsumed: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStyles: () -> Unit,
    onBriefingReady: (StoredBriefing) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var localErrorCode by rememberSaveable { mutableStateOf<String?>(null) }
    var enqueueInProgress by remember { mutableStateOf(false) }
    var pendingDuplicate by remember { mutableStateOf<PendingDuplicateRequest?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val history by historyRepository.history.collectAsState(initial = emptyList())
    val activeStyle by styleRepository.active.collectAsState(initial = null)
    val currentJob by analysisJobRepository.current.collectAsState(initial = null)
    val analysisState = analysisUiState(currentJob, localErrorCode, enqueueInProgress)

    fun startAnalysis(rawInput: String, checkForDuplicate: Boolean = true) {
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
            val selectedStyle = try {
                activeStyle ?: styleRepository.ensureDefault()
            } catch (_: Exception) {
                localErrorCode = "STYLE_LOAD_ERROR"
                enqueueInProgress = false
                return@launch
            }

            if (checkForDuplicate) {
                val existing = try {
                    historyRepository.findLatest(parsed.canonicalUrl)
                } catch (_: Exception) {
                    localErrorCode = "LOCAL_HISTORY_ERROR"
                    enqueueInProgress = false
                    return@launch
                }
                if (existing != null) {
                    pendingDuplicate = PendingDuplicateRequest(parsed.canonicalUrl, existing)
                    enqueueInProgress = false
                    return@launch
                }
            }
            val job = try {
                analysisJobRepository.create(
                    canonicalUrl = parsed.canonicalUrl,
                    style = selectedStyle.snapshot(),
                    styleId = selectedStyle.id,
                )
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

    LaunchedEffect(regenerationUrl, secretStoreState) {
        val url = regenerationUrl
        if (url != null && secretStoreState is SecretStoreUiState.Ready) {
            startAnalysis(url, checkForDuplicate = false)
            onRegenerationConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onOpenStyles) {
                        Text(stringResource(R.string.styles_title))
                    }
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.settings_title))
                    }
                },
            )
        },
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
            activeStyle = activeStyle,
            analysisState = analysisState,
            history = history,
            onDeleteHistory = { briefingId ->
                coroutineScope.launch {
                    localErrorCode = try {
                        historyRepository.delete(briefingId)
                        null
                    } catch (_: Exception) {
                        "LOCAL_DELETE_ERROR"
                    }
                }
            },
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
    pendingDuplicate?.let { duplicate ->
        AlertDialog(
            onDismissRequest = { pendingDuplicate = null },
            title = { Text(stringResource(R.string.duplicate_briefing_title)) },
            text = {
                TextButton(
                    onClick = {
                        pendingDuplicate = null
                        onBriefingReady(duplicate.existingBriefing)
                    },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.duplicate_briefing_link,
                            duplicate.existingBriefing.title,
                            formatHistoryTime(duplicate.existingBriefing.createdAtEpochMillis),
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDuplicate = null
                        startAnalysis(duplicate.canonicalUrl, checkForDuplicate = false)
                    },
                ) {
                    Text(stringResource(R.string.create_again))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDuplicate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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
    activeStyle: BriefingStyle?,
    analysisState: AnalysisUiState,
    history: List<BriefingHistoryItem>,
    onDeleteHistory: (Long) -> Unit,
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
                        activeStyle = activeStyle,
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
                    HistoryContent(history, onOpenHistory, onDeleteHistory)
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
                    activeStyle = activeStyle,
                    analysisState = analysisState,
                )
                Spacer(Modifier.height(8.dp))
                HistoryContent(history, onOpenHistory, onDeleteHistory)
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
    activeStyle: BriefingStyle?,
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.active_style_title),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = activeStyle?.name ?: stringResource(R.string.style_loading),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                activeStyle?.let {
                    Text(
                        text = stringResource(R.string.style_language, it.outputLanguage),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
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
    onDeleteHistory: (Long) -> Unit,
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
            history.forEach { item ->
                HistoryItem(
                    item = item,
                    onOpen = onOpenHistory,
                    onDelete = onDeleteHistory,
                )
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
    onDelete: (Long) -> Unit,
) {
    val revealWidthPx = with(LocalDensity.current) { 112.dp.toPx() }
    var horizontalOffset by remember(item.briefingId) { mutableFloatStateOf(0f) }
    Box(modifier = Modifier.fillMaxWidth()) {
        if (horizontalOffset < 0f) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onDelete(item.briefingId) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
        OutlinedButton(
            onClick = {
                if (horizontalOffset < 0f) horizontalOffset = 0f else onOpen(item.briefingId)
            },
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(horizontalOffset.roundToInt(), 0) }
                .pointerInput(item.briefingId, revealWidthPx) {
                    detectHorizontalDragGestures(
                        onDragCancel = { horizontalOffset = 0f },
                        onDragEnd = {
                            horizontalOffset = if (horizontalOffset <= -revealWidthPx / 3f) {
                                -revealWidthPx
                            } else {
                                0f
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        horizontalOffset = (horizontalOffset + dragAmount)
                            .coerceIn(-revealWidthPx, 0f)
                    }
                },
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
                        item.styleName,
                        formatHistoryTime(item.createdAtEpochMillis),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BriefingDetailScreen(
    briefing: StoredBriefing,
    onBack: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by rememberSaveable(briefing.briefingId) { mutableStateOf(false) }
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
                        BriefingSummary(
                            context = context,
                            briefing = briefing,
                            onRegenerate = onRegenerate,
                            onRequestDelete = { confirmDelete = true },
                        )
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
                    BriefingSummary(
                        context = context,
                        briefing = briefing,
                        onRegenerate = onRegenerate,
                        onRequestDelete = { confirmDelete = true },
                    )
                    SafeMarkdown(briefing.markdown)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.briefing_delete_title)) },
            text = {
                Text(stringResource(R.string.briefing_delete_confirmation, briefing.title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BriefingSummary(
    context: Context,
    briefing: StoredBriefing,
    onRegenerate: () -> Unit,
    onRequestDelete: () -> Unit,
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
            Text(
                text = stringResource(
                    R.string.briefing_detail_style,
                    briefing.styleName,
                    briefing.styleOutputLanguage,
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
                onClick = { copyBriefing(context, briefing) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.copy_markdown))
            }
            OutlinedButton(
                onClick = { shareBriefing(context, briefing) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.share_briefing))
            }
            OutlinedButton(
                onClick = onRegenerate,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.regenerate_active_style))
            }
            TextButton(
                onClick = onRequestDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.briefing_delete_button))
            }
}

private fun openCanonicalVideo(context: Context, canonicalUrl: String) {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(canonicalUrl)))
}

private fun copyBriefing(context: Context, briefing: StoredBriefing) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(
        ClipData.newPlainText("LMAA-Briefing", buildBriefingExport(briefing)),
    )
}

private fun shareBriefing(context: Context, briefing: StoredBriefing) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, buildBriefingExport(briefing))
    }
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.share_briefing)),
    )
}

internal fun buildBriefingExport(briefing: StoredBriefing): String = buildString {
    appendLine("Titel: ${briefing.title.asSingleLine()}")
    appendLine("Kanal: ${briefing.channelTitle.asSingleLine()}")
    appendLine("URL: ${briefing.canonicalUrl}")
    appendLine()
    appendLine("---")
    appendLine()
    append(briefing.markdown.trim())
}

private fun String.asSingleLine(): String =
    lineSequence().joinToString(" ") { it.trim() }.trim()

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
    "STYLE_LOAD_ERROR" -> stringResource(R.string.style_load_error)
    "LOCAL_DELETE_ERROR" -> stringResource(R.string.briefing_delete_error)
    "LOCAL_HISTORY_ERROR" -> stringResource(R.string.local_history_error)
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
