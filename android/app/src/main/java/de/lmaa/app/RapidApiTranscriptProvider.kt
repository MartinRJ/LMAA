package de.lmaa.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class RapidApiTranscriptProvider(
    private val apiKey: String,
    private val client: OkHttpClient = ProviderHttpClient.shared,
    private val endpoint: HttpUrl = DEFAULT_ENDPOINT,
) : TranscriptProvider {
    override suspend fun fetch(
        videoId: String,
        preferredLanguages: List<String>,
    ): TranscriptFetchResult = withContext(Dispatchers.IO) {
        if (!VIDEO_ID_PATTERN.matches(videoId)) {
            return@withContext TranscriptFetchResult.Failure("INVALID_VIDEO_ID")
        }
        if (apiKey.isBlank()) {
            return@withContext TranscriptFetchResult.Failure("RAPIDAPI_KEY_MISSING")
        }
        val language = preferredLanguages.firstOrNull(::isAllowedLanguage) ?: "en"
        val requestUrl = endpoint.newBuilder()
            .addQueryParameter("url", "https://www.youtube.com/watch?v=$videoId")
            .addQueryParameter("videoId", videoId)
            .addQueryParameter("chunkSize", "100")
            .addQueryParameter("text", "false")
            .addQueryParameter("lang", language)
            .build()
        val request = Request.Builder()
            .url(requestUrl)
            .header("X-RapidAPI-Host", HOST)
            .header("X-RapidAPI-Key", apiKey)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    return@withContext TranscriptFetchResult.Failure("RAPIDAPI_QUOTA_EXCEEDED")
                }
                if (!response.isSuccessful) {
                    return@withContext TranscriptFetchResult.Failure(
                        "RAPIDAPI_HTTP_${response.code}",
                    )
                }
                decode(videoId, language, JSONObject(response.body.string()))
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: JSONException) {
            TranscriptFetchResult.Failure("RAPIDAPI_MALFORMED_RESPONSE")
        } catch (_: Exception) {
            TranscriptFetchResult.Failure("RAPIDAPI_NETWORK_ERROR")
        }
    }

    private fun decode(
        videoId: String,
        requestedLanguage: String,
        payload: JSONObject,
    ): TranscriptFetchResult {
        val content = payload.optJSONArray("content")
            ?: return TranscriptFetchResult.Failure("RAPIDAPI_EMPTY_TRANSCRIPT")
        if (content.length() == 0) {
            return TranscriptFetchResult.Failure("RAPIDAPI_EMPTY_TRANSCRIPT")
        }
        val segments = buildList {
            repeat(content.length()) { index -> add(decodeSegment(content, index)) }
        }
        return TranscriptFetchResult.Success(
            TranscriptDocument(
                videoId = videoId,
                languageCode = payload.optString("lang").ifBlank { requestedLanguage },
                isGenerated = payload.optBoolean("isGenerated", false),
                provider = "rapidapi",
                segments = segments,
            ),
        )
    }

    private fun decodeSegment(content: JSONArray, index: Int): TranscriptSegment {
        val segment = content.getJSONObject(index)
        val text = segment.getString("text").trim()
        if (text.isEmpty()) throw JSONException("Leeres Segment")
        val offsetMilliseconds = when {
            segment.has("offset") -> segment.getDouble("offset")
            segment.has("start") -> segment.getDouble("start")
            else -> 0.0
        }
        val durationMilliseconds = segment.optDouble("duration", 0.0)
        if (!offsetMilliseconds.isFinite() || !durationMilliseconds.isFinite()) {
            throw JSONException("Ungültige Zeitangabe")
        }
        return TranscriptSegment(
            text = text,
            startSeconds = (offsetMilliseconds / 1_000.0).coerceAtLeast(0.0),
            durationSeconds = (durationMilliseconds / 1_000.0).coerceAtLeast(0.0),
        )
    }

    private fun isAllowedLanguage(value: String): Boolean = LANGUAGE_PATTERN.matches(value)

    private companion object {
        const val HOST = "youtube-transcripts.p.rapidapi.com"
        val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        val LANGUAGE_PATTERN = Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$")
        val DEFAULT_ENDPOINT = HttpUrl.Builder()
            .scheme("https")
            .host(HOST)
            .addPathSegment("youtube")
            .addPathSegment("transcript")
            .build()
    }
}
