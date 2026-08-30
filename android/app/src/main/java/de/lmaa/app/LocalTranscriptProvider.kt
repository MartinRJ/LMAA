package de.lmaa.app

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class TranscriptSegment(
    val text: String,
    val startSeconds: Double,
    val durationSeconds: Double,
)

data class TranscriptDocument(
    val videoId: String,
    val languageCode: String,
    val isGenerated: Boolean,
    val provider: String,
    val segments: List<TranscriptSegment>,
) {
    val characterCount: Int = segments.sumOf { it.text.length }
}

sealed interface TranscriptFetchResult {
    data class Success(val document: TranscriptDocument) : TranscriptFetchResult
    data class Failure(val code: String) : TranscriptFetchResult
}

class LocalTranscriptProvider(context: Context) {
    private val applicationContext = context.applicationContext

    suspend fun fetch(
        videoId: String,
        preferredLanguages: List<String> = listOf("de", "de-DE", "en", "en-US", "en-GB"),
    ): TranscriptFetchResult = withContext(Dispatchers.IO) {
        runCatching {
            ensurePythonStarted()
            val payload = Python.getInstance()
                .getModule("lmaa_transcript")
                .callAttr(
                    "fetch_transcript_json",
                    videoId,
                    preferredLanguages.joinToString(","),
                )
                .toString()
            TranscriptJsonDecoder.decode(payload)
        }.getOrElse {
            TranscriptFetchResult.Failure("PYTHON_BRIDGE_ERROR")
        }
    }

    private fun ensurePythonStarted() {
        if (Python.isStarted()) return
        synchronized(pythonStartupLock) {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(applicationContext))
            }
        }
    }

    private companion object {
        val pythonStartupLock = Any()
    }
}

internal object TranscriptJsonDecoder {
    fun decode(payload: String): TranscriptFetchResult {
        val root = JSONObject(payload)
        if (root.optString("status") == "error") {
            return TranscriptFetchResult.Failure(
                root.optString("error").ifBlank { "MALFORMED_RESPONSE" },
            )
        }
        if (root.optString("status") != "ok") {
            return TranscriptFetchResult.Failure("MALFORMED_RESPONSE")
        }

        val segmentsJson = root.getJSONArray("segments")
        val segments = buildList {
            repeat(segmentsJson.length()) { index ->
                val segment = segmentsJson.getJSONObject(index)
                add(
                    TranscriptSegment(
                        text = segment.getString("text"),
                        startSeconds = segment.getDouble("startSeconds"),
                        durationSeconds = segment.getDouble("durationSeconds"),
                    ),
                )
            }
        }
        if (segments.isEmpty()) return TranscriptFetchResult.Failure("EMPTY_TRANSCRIPT")

        return TranscriptFetchResult.Success(
            TranscriptDocument(
                videoId = root.getString("videoId"),
                languageCode = root.getString("languageCode"),
                isGenerated = root.getBoolean("isGenerated"),
                provider = root.getString("provider"),
                segments = segments,
            ),
        )
    }
}
