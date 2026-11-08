package de.lmaa.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.lmaa.app.history.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HistoryTransferCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archive = remember { HistoryArchive(LmaaDatabase.getInstance(context)) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val text = archive.export()
                    requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(text.toByteArray()) }
                }
                status = "Export gespeichert. Die Datei kann per Quick Share oder USB übertragen werden."
            } catch (_: Exception) { status = "Export fehlgeschlagen. Bitte erneut versuchen." }
            finally { busy = false }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) {
                    val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(out.size() + n <= ARCHIVE_LIMIT)
                            out.write(buffer, 0, n)
                        }
                        out.toByteArray()
                    }
                    archive.import(Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString())
                }
                status = "Import abgeschlossen. Neue Briefings: ${count.briefings} · Neue Chatnachrichten: ${count.messages}"
            } catch (_: Exception) { status = "Import abgelehnt: ungültige, zu große oder widersprüchliche Datei. Bestehende Daten bleiben erhalten." }
            finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Verlauf übertragen", style = MaterialTheme.typography.titleLarge)
            Text("Briefings, Transkripte, Chats und eigene Stile zwischen Android und Extension übertragen. Ohne Account. API-Keys bleiben auf diesem Gerät.")
            Text("Die Exportdatei enthält deinen Verlauf im Klartext. Bewahre sie privat auf.")
            Button(enabled = !busy, onClick = { export.launch("lmaa-verlauf.json") }) { Text("Verlauf exportieren") }
            OutlinedButton(enabled = !busy, onClick = { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Verlauf importieren") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (status.isNotEmpty()) Text(status)
        }
    }
}
