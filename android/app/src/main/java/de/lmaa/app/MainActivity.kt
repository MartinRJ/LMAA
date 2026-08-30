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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                LmaaHomeScreen()
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LmaaHomeScreen() {
    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<YoutubeUrlParseResult?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
    ) { contentPadding ->
        HomeContent(
            input = input,
            result = result,
            onInputChanged = {
                input = it
                result = null
            },
            onValidate = { result = YoutubeUrlParser.parse(input) },
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
            LinkPreview(result)
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
private fun LinkPreview(result: YoutubeUrlParseResult.Success) {
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
        }
    }
}

@Composable
private fun errorMessage(result: YoutubeUrlParseResult?): String? = when (result) {
    YoutubeUrlParseResult.Error.EMPTY -> stringResource(R.string.url_error_empty)
    YoutubeUrlParseResult.Error.AMBIGUOUS -> stringResource(R.string.url_error_ambiguous)
    YoutubeUrlParseResult.Error.INVALID -> stringResource(R.string.url_error_invalid)
    else -> null
}
