package de.lmaa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.lmaa.app.history.ProviderUsage
import de.lmaa.app.history.ProviderUsageRepository
import de.lmaa.app.history.UsageWarningLevel
import de.lmaa.app.secrets.ProviderSecretStore
import kotlinx.coroutines.launch

@Composable
internal fun RapidApiSettingsCard(
    secretStoreState: SecretStoreUiState,
    usageRepository: ProviderUsageRepository,
    settingsRepository: RapidApiSettingsRepository,
) {
    val usage by usageRepository.rapidApiCurrentMonth.collectAsState(
        initial = ProviderUsage("", 0, 0, null),
    )
    val configuration by settingsRepository.state.collectAsState(
        initial = RapidApiConfiguration(),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.rapidapi_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.rapidapi_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            RapidApiUsageText(usage)
            when (secretStoreState) {
                SecretStoreUiState.Loading -> CircularProgressIndicator()
                SecretStoreUiState.Error -> Text(
                    text = stringResource(R.string.secret_store_error),
                    color = MaterialTheme.colorScheme.error,
                )
                is SecretStoreUiState.Ready -> RapidApiEditor(
                    store = secretStoreState.store,
                    configuration = configuration,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}

@Composable
private fun RapidApiUsageText(usage: ProviderUsage) {
    val attemptColor = when (usage.warningLevel) {
        UsageWarningLevel.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
        UsageWarningLevel.WARNING -> MaterialTheme.colorScheme.tertiary
        UsageWarningLevel.CRITICAL,
        UsageWarningLevel.EXHAUSTED,
        -> MaterialTheme.colorScheme.error
    }
    val basicHintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val month = usage.month.ifBlank { "–" }
    val usageCount = stringResource(R.string.rapidapi_usage_count, month, usage.attempts)
    val basicLimit = stringResource(
        R.string.rapidapi_usage_basic_limit,
        ProviderUsageRepository.RAPIDAPI_MONTHLY_LIMIT,
    )
    val usageContext = stringResource(R.string.rapidapi_usage_context, usage.remaining)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = attemptColor)) { append(usageCount) }
            withStyle(SpanStyle(color = basicHintColor)) { append(basicLimit) }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(usageContext)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun RapidApiEditor(
    store: ProviderSecretStore,
    configuration: RapidApiConfiguration,
    settingsRepository: RapidApiSettingsRepository,
) {
    val status by store.status.collectAsState(initial = null)
    val current = status
    if (current == null) {
        CircularProgressIndicator()
        return
    }
    if (!current.isAvailable) {
        Text(stringResource(R.string.secret_store_error), color = MaterialTheme.colorScheme.error)
        return
    }

    var editingReplacement by rememberSaveable { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var curlInput by rememberSaveable { mutableStateOf("") }
    var draft by remember(configuration.profile) {
        mutableStateOf(RapidApiProfileDraft.from(configuration.profile))
    }
    var working by remember { mutableStateOf(false) }
    var errorCode by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ProtectSecretInputFromScreenshots(
        !current.hasRapidApiKey || editingReplacement || curlInput.isNotBlank(),
    )

    Text(
        text = stringResource(R.string.rapidapi_routing_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    RapidApiRoutingMode.entries.forEach { mode ->
        Row(modifier = Modifier.fillMaxWidth()) {
            RadioButton(
                selected = configuration.routingMode == mode,
                onClick = {
                    working = true
                    errorCode = null
                    scope.launch {
                        runCatching {
                            if (mode != RapidApiRoutingMode.OFF) {
                                check(current.hasRapidApiKey) { "RAPIDAPI_KEY_MISSING" }
                            }
                            settingsRepository.setRoutingMode(mode)
                        }.onFailure { errorCode = it.message ?: "RAPIDAPI_SETTINGS_ERROR" }
                        working = false
                    }
                },
                enabled = !working && (mode == RapidApiRoutingMode.OFF || current.hasRapidApiKey),
            )
            Column {
                Text(stringResource(mode.labelResource))
                Text(
                    text = stringResource(mode.descriptionResource),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    Text(
        text = stringResource(R.string.rapidapi_key_section),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (current.hasRapidApiKey && !editingReplacement) {
        Text(ProviderSecretStore.MASK, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    keyInput = ""
                    errorCode = null
                    editingReplacement = true
                },
                enabled = !working,
            ) { Text(stringResource(R.string.rapidapi_key_replace)) }
            OutlinedButton(
                onClick = {
                    working = true
                    errorCode = null
                    scope.launch {
                        runCatching {
                            settingsRepository.setRoutingMode(RapidApiRoutingMode.OFF)
                            store.clearRapidApiKey()
                            keyInput = ""
                            editingReplacement = false
                        }.onFailure { errorCode = "RAPIDAPI_KEY_DELETE_FAILED" }
                        working = false
                    }
                },
                enabled = !working,
            ) { Text(stringResource(R.string.rapidapi_key_delete)) }
        }
    } else {
        OutlinedTextField(
            value = keyInput,
            onValueChange = {
                keyInput = it
                errorCode = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.rapidapi_key_label)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
            ),
            singleLine = true,
            enabled = !working,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    working = true
                    errorCode = null
                    scope.launch {
                        runCatching { store.saveRapidApiKey(keyInput) }
                            .onSuccess {
                                keyInput = ""
                                editingReplacement = false
                            }
                            .onFailure { errorCode = "RAPIDAPI_KEY_SAVE_FAILED" }
                        working = false
                    }
                },
                enabled = keyInput.isNotBlank() && !working,
            ) { Text(stringResource(R.string.rapidapi_key_save)) }
            if (current.hasRapidApiKey) {
                OutlinedButton(
                    onClick = {
                        keyInput = ""
                        editingReplacement = false
                    },
                    enabled = !working,
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }

    Text(
        text = stringResource(R.string.rapidapi_profile_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    ProfileTextField(draft.name, { draft = draft.copy(name = it) }, R.string.rapidapi_profile_name)
    ProfileTextField(
        draft.endpoint,
        { draft = draft.copy(endpoint = it) },
        R.string.rapidapi_profile_endpoint,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        RapidApiHttpMethod.entries.forEach { method ->
            Row {
                RadioButton(
                    selected = draft.method == method,
                    onClick = { draft = draft.copy(method = method) },
                )
                Text(method.name)
            }
        }
    }
    Text(
        text = stringResource(R.string.rapidapi_placeholder_help),
        style = MaterialTheme.typography.bodySmall,
    )
    ProfileTextField(
        draft.queryParameters,
        { draft = draft.copy(queryParameters = it) },
        R.string.rapidapi_profile_query,
        minLines = 3,
    )
    ProfileTextField(
        draft.headers,
        { draft = draft.copy(headers = it) },
        R.string.rapidapi_profile_headers,
        minLines = 3,
    )
    ProfileTextField(
        draft.bodyTemplate,
        { draft = draft.copy(bodyTemplate = it) },
        R.string.rapidapi_profile_body,
        minLines = 3,
    )
    ProfileTextField(
        draft.successStatusCodes,
        { draft = draft.copy(successStatusCodes = it) },
        R.string.rapidapi_profile_success_codes,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericProfileField(
            draft.connectTimeoutSeconds,
            { draft = draft.copy(connectTimeoutSeconds = it) },
            R.string.rapidapi_profile_connect_timeout,
            Modifier.weight(1f),
        )
        NumericProfileField(
            draft.readTimeoutSeconds,
            { draft = draft.copy(readTimeoutSeconds = it) },
            R.string.rapidapi_profile_read_timeout,
            Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumericProfileField(
            draft.writeTimeoutSeconds,
            { draft = draft.copy(writeTimeoutSeconds = it) },
            R.string.rapidapi_profile_write_timeout,
            Modifier.weight(1f),
        )
        NumericProfileField(
            draft.callTimeoutSeconds,
            { draft = draft.copy(callTimeoutSeconds = it) },
            R.string.rapidapi_profile_call_timeout,
            Modifier.weight(1f),
        )
    }
    NumericProfileField(
        draft.maxResponseBytes,
        { draft = draft.copy(maxResponseBytes = it) },
        R.string.rapidapi_profile_max_response,
        Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                working = true
                errorCode = null
                scope.launch {
                    runCatching { settingsRepository.saveProfile(draft.toProfile()) }
                        .onFailure { errorCode = it.message ?: "RAPIDAPI_PROFILE_INVALID" }
                    working = false
                }
            },
            enabled = !working,
        ) { Text(stringResource(R.string.rapidapi_profile_save)) }
        OutlinedButton(
            onClick = {
                working = true
                errorCode = null
                scope.launch {
                    runCatching {
                        settingsRepository.restoreDefaults()
                    }.onFailure { errorCode = "RAPIDAPI_DEFAULTS_FAILED" }
                    working = false
                }
            },
            enabled = !working,
        ) { Text(stringResource(R.string.rapidapi_profile_restore_defaults)) }
    }

    Text(
        text = stringResource(R.string.rapidapi_curl_title),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(R.string.rapidapi_curl_explanation),
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(
        value = curlInput,
        onValueChange = {
            curlInput = it
            errorCode = null
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.rapidapi_curl_label)) },
        minLines = 4,
        enabled = !working,
    )
    Button(
        onClick = {
            working = true
            errorCode = null
            scope.launch {
                runCatching {
                    val imported = RapidApiCurlImporter.parse(curlInput)
                    settingsRepository.saveProfile(imported.profile)
                    settingsRepository.setRoutingMode(RapidApiRoutingMode.OFF)
                    imported.apiKey?.let { store.saveRapidApiKey(it) }
                    curlInput = ""
                }.onFailure { errorCode = it.message ?: "RAPIDAPI_CURL_INVALID" }
                working = false
            }
        },
        enabled = curlInput.isNotBlank() && !working,
    ) { Text(stringResource(R.string.rapidapi_curl_import)) }

    if (working) CircularProgressIndicator()
    errorCode?.let {
        Text(
            text = stringResource(R.string.rapidapi_operation_error_code, it),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        minLines = minLines,
        singleLine = minLines == 1,
    )
}

@Composable
private fun NumericProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: Int,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all(Char::isDigit)) onValueChange(it) },
        modifier = modifier,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

private data class RapidApiProfileDraft(
    val name: String,
    val endpoint: String,
    val method: RapidApiHttpMethod,
    val queryParameters: String,
    val headers: String,
    val bodyTemplate: String,
    val successStatusCodes: String,
    val connectTimeoutSeconds: String,
    val readTimeoutSeconds: String,
    val writeTimeoutSeconds: String,
    val callTimeoutSeconds: String,
    val maxResponseBytes: String,
) {
    fun toProfile(): RapidApiProfile = RapidApiProfile(
        name = name.trim(),
        endpoint = endpoint.trim(),
        method = method,
        queryParameters = parseEntries(queryParameters),
        headers = parseEntries(headers),
        bodyTemplate = bodyTemplate,
        successStatusCodes = successStatusCodes.trim(),
        connectTimeoutSeconds = connectTimeoutSeconds.toIntOrNull()
            ?: throw IllegalArgumentException("RAPIDAPI_CONNECT_TIMEOUT_INVALID"),
        readTimeoutSeconds = readTimeoutSeconds.toIntOrNull()
            ?: throw IllegalArgumentException("RAPIDAPI_READ_TIMEOUT_INVALID"),
        writeTimeoutSeconds = writeTimeoutSeconds.toIntOrNull()
            ?: throw IllegalArgumentException("RAPIDAPI_WRITE_TIMEOUT_INVALID"),
        callTimeoutSeconds = callTimeoutSeconds.toIntOrNull()
            ?: throw IllegalArgumentException("RAPIDAPI_CALL_TIMEOUT_INVALID"),
        maxResponseBytes = maxResponseBytes.toIntOrNull()
            ?: throw IllegalArgumentException("RAPIDAPI_RESPONSE_LIMIT_INVALID"),
    ).also(RapidApiProfileValidator::requireValid)

    companion object {
        fun from(profile: RapidApiProfile) = RapidApiProfileDraft(
            name = profile.name,
            endpoint = profile.endpoint,
            method = profile.method,
            queryParameters = profile.queryParameters.toLines(),
            headers = profile.headers.toLines(),
            bodyTemplate = profile.bodyTemplate,
            successStatusCodes = profile.successStatusCodes,
            connectTimeoutSeconds = profile.connectTimeoutSeconds.toString(),
            readTimeoutSeconds = profile.readTimeoutSeconds.toString(),
            writeTimeoutSeconds = profile.writeTimeoutSeconds.toString(),
            callTimeoutSeconds = profile.callTimeoutSeconds.toString(),
            maxResponseBytes = profile.maxResponseBytes.toString(),
        )
    }
}

private fun parseEntries(value: String): List<RapidApiTemplateEntry> = value.lineSequence()
    .map(String::trim)
    .filter(String::isNotEmpty)
    .map { line ->
        val separator = line.indexOf('=')
        require(separator > 0) { "RAPIDAPI_ARGUMENT_FORMAT_INVALID" }
        RapidApiTemplateEntry(line.substring(0, separator).trim(), line.substring(separator + 1))
    }
    .toList()

private fun List<RapidApiTemplateEntry>.toLines(): String =
    joinToString("\n") { "${it.name}=${it.value}" }

private val RapidApiRoutingMode.labelResource: Int
    get() = when (this) {
        RapidApiRoutingMode.OFF -> R.string.rapidapi_mode_off
        RapidApiRoutingMode.FALLBACK -> R.string.rapidapi_mode_fallback
        RapidApiRoutingMode.PREFERRED -> R.string.rapidapi_mode_preferred
    }

private val RapidApiRoutingMode.descriptionResource: Int
    get() = when (this) {
        RapidApiRoutingMode.OFF -> R.string.rapidapi_mode_off_description
        RapidApiRoutingMode.FALLBACK -> R.string.rapidapi_mode_fallback_description
        RapidApiRoutingMode.PREFERRED -> R.string.rapidapi_mode_preferred_description
    }
