package de.lmaa.app

import java.net.URI
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject

data class VideoMetadata(
    val videoId: String,
    val title: String,
    val channelId: String?,
    val channelTitle: String,
    val publishedAt: Instant?,
    val durationIso8601: String?,
    val durationSeconds: Int?,
    val thumbnailUrl: String,
    val fetchedAt: Instant,
)

sealed interface MetadataFetchResult {
    data class Success(val metadata: VideoMetadata) : MetadataFetchResult
    data class Failure(val code: String) : MetadataFetchResult
}

internal interface MetadataProvider {
    suspend fun fetch(videoId: String): MetadataFetchResult
}

class YoutubeOEmbedMetadataProvider internal constructor(
    private val client: OkHttpClient = ProviderHttpClient.shared,
    private val endpoint: HttpUrl = DEFAULT_ENDPOINT,
) : MetadataProvider {
    override suspend fun fetch(videoId: String): MetadataFetchResult = withContext(Dispatchers.IO) {
        if (!VIDEO_ID_PATTERN.matches(videoId)) {
            return@withContext MetadataFetchResult.Failure("INVALID_VIDEO_ID")
        }
        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        val requestUrl = endpoint.newBuilder()
            .addQueryParameter("url", canonicalUrl)
            .addQueryParameter("format", "json")
            .build()
        val request = Request.Builder().url(requestUrl).get().build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext MetadataFetchResult.Failure("VIDEO_NOT_FOUND")
                if (!response.isSuccessful) {
                    return@withContext MetadataFetchResult.Failure("OEMBED_HTTP_${response.code}")
                }
                val payload = JSONObject(response.body.string())
                val title = payload.requiredText("title")
                val channelTitle = payload.requiredText("author_name")
                val thumbnailUrl = payload.requiredHttpsUrl("thumbnail_url")
                MetadataFetchResult.Success(
                    VideoMetadata(
                        videoId = videoId,
                        title = title,
                        channelId = null,
                        channelTitle = channelTitle,
                        publishedAt = null,
                        durationIso8601 = null,
                        durationSeconds = null,
                        thumbnailUrl = thumbnailUrl,
                        fetchedAt = Instant.now(),
                    ),
                )
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: JSONException) {
            MetadataFetchResult.Failure("OEMBED_MALFORMED_RESPONSE")
        } catch (_: Exception) {
            MetadataFetchResult.Failure("OEMBED_NETWORK_ERROR")
        }
    }

    private fun JSONObject.requiredText(name: String): String =
        getString(name).trim().takeIf(String::isNotEmpty)
            ?: throw JSONException("Pflichtfeld fehlt")

    private fun JSONObject.requiredHttpsUrl(name: String): String {
        val value = requiredText(name)
        val uri = URI(value)
        if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.port != -1) {
            throw JSONException("Ungültige HTTPS-URL")
        }
        return value
    }

    private companion object {
        val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        val DEFAULT_ENDPOINT = HttpUrl.Builder()
            .scheme("https")
            .host("www.youtube.com")
            .addPathSegment("oembed")
            .build()
    }
}
