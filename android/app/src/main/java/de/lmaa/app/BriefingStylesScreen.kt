package de.lmaa.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
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
import de.lmaa.app.history.BriefingStyle
import de.lmaa.app.history.BriefingStyleRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BriefingStylesScreen(
    repository: BriefingStyleRepository,
    onBack: () -> Unit,
) {
    val styles by repository.styles.collectAsState(initial = emptyList())
    var editorStyleId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var styleToDelete by remember { mutableStateOf<BriefingStyle?>(null) }
    var operationError by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.styles_title)) },
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
                text = stringResource(R.string.styles_explanation),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = {
                    creating = true
                    editorStyleId = null
                    operationError = null
                },
                enabled = !creating && editorStyleId == null,
            ) { Text(stringResource(R.string.style_create)) }

            if (creating) {
                StyleEditor(
                    initial = null,
                    onSave = { name, instructions, language ->
                        scope.launch {
                            operationError = try {
                                repository.create(name, instructions, language)
                                creating = false
                                null
                            } catch (exception: Exception) {
                                exception.message ?: "STYLE_SAVE_FAILED"
                            }
                        }
                    },
                    onCancel = { creating = false },
                )
            }

            if (styles.isEmpty()) CircularProgressIndicator()
            styles.forEach { style ->
                if (editorStyleId == style.id) {
                    StyleEditor(
                        initial = style,
                        onSave = { name, instructions, language ->
                            scope.launch {
                                operationError = try {
                                    repository.update(style.id, name, instructions, language)
                                    editorStyleId = null
                                    null
                                } catch (exception: Exception) {
                                    exception.message ?: "STYLE_SAVE_FAILED"
                                }
                            }
                        },
                        onCancel = { editorStyleId = null },
                    )
                } else {
                    StyleCard(
                        style = style,
                        onActivate = {
                            scope.launch {
                                operationError = try {
                                    repository.setActive(style.id)
                                    null
                                } catch (exception: Exception) {
                                    exception.message ?: "STYLE_ACTIVATE_FAILED"
                                }
                            }
                        },
                        onEdit = {
                            creating = false
                            editorStyleId = style.id
                            operationError = null
                        },
                        onDelete = { styleToDelete = style },
                    )
                }
            }

            if (operationError != null) {
                Text(
                    text = styleErrorMessage(operationError),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    styleToDelete?.let { style ->
        AlertDialog(
            onDismissRequest = { styleToDelete = null },
            title = { Text(stringResource(R.string.style_delete_title)) },
            text = { Text(stringResource(R.string.style_delete_confirmation, style.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        styleToDelete = null
                        scope.launch {
                            operationError = try {
                                repository.delete(style.id)
                                null
                            } catch (exception: Exception) {
                                exception.message ?: "STYLE_DELETE_FAILED"
                            }
                        }
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { styleToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StyleCard(
    style: BriefingStyle,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = style.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (style.isActive) {
                    Text(
                        text = stringResource(R.string.style_active),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = stringResource(R.string.style_language, style.outputLanguage),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(style.instructions, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!style.isActive) {
                    Button(onClick = onActivate) {
                        Text(stringResource(R.string.style_activate))
                    }
                }
                if (!style.isBuiltIn) {
                    OutlinedButton(onClick = onEdit) {
                        Text(stringResource(R.string.edit))
                    }
                    OutlinedButton(onClick = onDelete, enabled = !style.isActive) {
                        Text(stringResource(R.string.delete))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.style_built_in_protected),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StyleEditor(
    initial: BriefingStyle?,
    onSave: (String, String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var instructions by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.instructions.orEmpty())
    }
    var language by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.outputLanguage ?: "Deutsch")
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    if (initial == null) R.string.style_editor_create
                    else R.string.style_editor_edit,
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.style_name)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = language,
                onValueChange = { language = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.style_output_language)) },
                singleLine = true,
            )
            OutlinedTextField(
                value = instructions,
                onValueChange = { instructions = it.take(8_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.style_instructions)) },
                minLines = 5,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSave(name, instructions, language) },
                    enabled = name.isNotBlank() && instructions.isNotBlank() && language.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    }
}

@Composable
private fun styleErrorMessage(code: String?): String = when (code) {
    "STYLE_NAME_EXISTS" -> stringResource(R.string.style_error_duplicate)
    "BUILT_IN_STYLE_PROTECTED" -> stringResource(R.string.style_error_built_in)
    "ACTIVE_STYLE_PROTECTED" -> stringResource(R.string.style_error_active)
    "STYLE_NAME_INVALID", "STYLE_INSTRUCTIONS_INVALID", "STYLE_LANGUAGE_INVALID" ->
        stringResource(R.string.style_error_invalid)
    else -> stringResource(R.string.style_error_generic)
}
