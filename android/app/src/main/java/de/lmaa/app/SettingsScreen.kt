package de.lmaa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.lmaa.app.history.ProviderUsage
import de.lmaa.app.history.ProviderUsageRepository
import de.lmaa.app.history.UsageWarningLevel
import de.lmaa.app.secrets.ProviderSecretStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    secretStoreState: SecretStoreUiState,
    usageRepository: ProviderUsageRepository,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
            OpenAiKeySettings(secretStoreState)
            RapidApiSettings(secretStoreState, usageRepository)
        }
    }
}

@Composable
private fun RapidApiSettings(
    secretStoreState: SecretStoreUiState,
    usageRepository: ProviderUsageRepository,
) {
    val usage by usageRepository.rapidApiCurrentMonth.collectAsState(
        initial = ProviderUsage("", 0, 0, null),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
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
                is SecretStoreUiState.Ready -> RapidApiEditor(secretStoreState.store)
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
    val usageContext = stringResource(
        R.string.rapidapi_usage_context,
        usage.remaining,
    )
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = attemptColor)) {
                append(usageCount)
            }
            withStyle(SpanStyle(color = basicHintColor)) {
                append(basicLimit)
            }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                append(usageContext)
            }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun RapidApiEditor(store: ProviderSecretStore) {
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
    var input by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ProtectSecretInputFromScreenshots(!current.hasRapidApiKey || editingReplacement)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.rapidapi_opt_in))
            Text(
                text = stringResource(R.string.rapidapi_opt_in_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = current.rapidApiEnabled,
            onCheckedChange = { enabled ->
                working = true
                failed = false
                scope.launch {
                    try {
                        store.setRapidApiEnabled(enabled)
                    } catch (_: Exception) {
                        failed = true
                    } finally {
                        working = false
                    }
                }
            },
            enabled = current.hasRapidApiKey && !working && !editingReplacement,
        )
    }

    if (current.hasRapidApiKey && !editingReplacement) {
        Text(ProviderSecretStore.MASK, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    input = ""
                    failed = false
                    editingReplacement = true
                },
                enabled = !working,
            ) { Text(stringResource(R.string.rapidapi_key_replace)) }
            OutlinedButton(
                onClick = {
                    working = true
                    failed = false
                    scope.launch {
                        try {
                            store.clearRapidApiKey()
                            input = ""
                            editingReplacement = false
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = !working,
            ) { Text(stringResource(R.string.rapidapi_key_delete)) }
        }
    } else {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                failed = false
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
                    failed = false
                    scope.launch {
                        try {
                            store.saveRapidApiKey(input)
                            input = ""
                            editingReplacement = false
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            working = false
                        }
                    }
                },
                enabled = input.isNotBlank() && !working,
            ) { Text(stringResource(R.string.rapidapi_key_save)) }
            if (current.hasRapidApiKey) {
                OutlinedButton(
                    onClick = {
                        input = ""
                        failed = false
                        editingReplacement = false
                    },
                    enabled = !working,
                ) { Text(stringResource(R.string.cancel)) }
            }
        }
    }

    if (working) CircularProgressIndicator()
    if (failed) {
        Text(
            text = stringResource(R.string.rapidapi_operation_error),
            color = MaterialTheme.colorScheme.error,
        )
    }
}
