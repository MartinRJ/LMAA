package de.lmaa.app

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.lmaa.app.secrets.ProviderSecretStore
import kotlinx.coroutines.launch

@Composable
internal fun OpenAiKeySettings(secretStoreState: SecretStoreUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.openai_key_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.openai_key_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            when (secretStoreState) {
                SecretStoreUiState.Loading -> CircularProgressIndicator()
                SecretStoreUiState.Error -> Text(
                    text = stringResource(R.string.secret_store_error),
                    color = MaterialTheme.colorScheme.error,
                )
                is SecretStoreUiState.Ready -> OpenAiKeyEditor(secretStoreState.store)
            }
        }
    }
}

@Composable
private fun OpenAiKeyEditor(store: ProviderSecretStore) {
    val status by store.status.collectAsState(initial = null)
    val currentStatus = status
    if (currentStatus == null) {
        CircularProgressIndicator()
        return
    }

    var editingReplacement by rememberSaveable { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var operationState by remember { mutableStateOf<KeyOperationState>(KeyOperationState.Idle) }
    val coroutineScope = rememberCoroutineScope()

    ProtectSecretInputFromScreenshots(
        enabled = !currentStatus.hasOpenAiKey || editingReplacement,
    )

    if (!currentStatus.isAvailable) {
        Text(
            text = stringResource(R.string.secret_store_error),
            color = MaterialTheme.colorScheme.error,
        )
        return
    }

    if (currentStatus.hasOpenAiKey && !editingReplacement) {
        StoredKeyControls(
            operationState = operationState,
            onReplace = {
                input = ""
                operationState = KeyOperationState.Idle
                editingReplacement = true
            },
            onDelete = {
                operationState = KeyOperationState.Working
                coroutineScope.launch {
                    operationState = try {
                        store.clearOpenAiKey()
                        input = ""
                        editingReplacement = false
                        KeyOperationState.Idle
                    } catch (_: Exception) {
                        KeyOperationState.Error
                    }
                }
            },
        )
    } else {
        KeyInputControls(
            input = input,
            isReplacement = editingReplacement,
            operationState = operationState,
            onInputChanged = {
                input = it
                operationState = KeyOperationState.Idle
            },
            onSave = {
                operationState = KeyOperationState.Working
                coroutineScope.launch {
                    operationState = try {
                        store.saveOpenAiKey(input)
                        input = ""
                        editingReplacement = false
                        KeyOperationState.Idle
                    } catch (_: Exception) {
                        KeyOperationState.Error
                    }
                }
            },
            onCancel = if (currentStatus.hasOpenAiKey) {
                {
                    input = ""
                    editingReplacement = false
                    operationState = KeyOperationState.Idle
                }
            } else {
                null
            },
        )
    }

    if (operationState == KeyOperationState.Error) {
        Text(
            text = stringResource(R.string.openai_key_operation_error),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun StoredKeyControls(
    operationState: KeyOperationState,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
) {
    Text(
        text = ProviderSecretStore.MASK,
        style = MaterialTheme.typography.titleLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onReplace,
            enabled = operationState != KeyOperationState.Working,
        ) {
            Text(stringResource(R.string.openai_key_replace))
        }
        OutlinedButton(
            onClick = onDelete,
            enabled = operationState != KeyOperationState.Working,
        ) {
            Text(stringResource(R.string.openai_key_delete))
        }
    }
}

@Composable
private fun KeyInputControls(
    input: String,
    isReplacement: Boolean,
    operationState: KeyOperationState,
    onInputChanged: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    OutlinedTextField(
        value = input,
        onValueChange = onInputChanged,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.openai_key_label)) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        singleLine = true,
        enabled = operationState != KeyOperationState.Working,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onSave,
            enabled = input.isNotBlank() && operationState != KeyOperationState.Working,
        ) {
            Text(
                stringResource(
                    if (isReplacement) R.string.openai_key_replace_save
                    else R.string.openai_key_save,
                ),
            )
        }
        if (onCancel != null) {
            OutlinedButton(
                onClick = onCancel,
                enabled = operationState != KeyOperationState.Working,
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
    if (operationState == KeyOperationState.Working) CircularProgressIndicator()
}

private enum class KeyOperationState {
    Idle,
    Working,
    Error,
}

@Composable
internal fun ProtectSecretInputFromScreenshots(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        val window = (view.context as? Activity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
