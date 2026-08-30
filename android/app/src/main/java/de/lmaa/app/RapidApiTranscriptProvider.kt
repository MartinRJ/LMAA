package de.lmaa.app

import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal class RapidApiTranscriptProvider(
    private val apiKey: String,
    private val profile: RapidApiProfile = RapidApiProfile.DEFAULT,
    private val client: OkHttpClient = ProviderHttpClient.shared,
    private val endpoint: HttpUrl? = null,
    private val onRequestFinished: suspend (success: Boolean, status: String) -> Unit = { _, _ -> },
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
        try {
            RapidApiProfileValidator.requireValid(profile)
        } catch (exception: IllegalArgumentException) {
            return@withContext TranscriptFetchResult.Failure(
                exception.message?.takeIf { it.startsWith("RAPIDAPI_") }
                    ?: "RAPIDAPI_PROFILE_INVALID",
            )
        }

        val language = preferredLanguages.firstOrNull(::isAllowedLanguage) ?: "en"
        val context = TemplateContext(
            canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
            videoId = videoId,
            language = language,
            apiKey = apiKey,
        )
        val request = buildRequest(context)
        val requestClient = client.newBuilder()
            .connectTimeout(profile.connectTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(profile.readTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .writeTimeout(profile.writeTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(profile.callTimeoutSeconds.toLong(), TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()

        val result = try {
            requestClient.newCall(request).execute().use { response ->
                when {
                    response.code == 429 -> TranscriptFetchResult.Failure(
                        "RAPIDAPI_QUOTA_EXCEEDED",
                    )
                    response.code !in parseSuccessStatusCodes(profile.successStatusCodes) ->
                        TranscriptFetchResult.Failure("RAPIDAPI_HTTP_${response.code}")
                    else -> decodeRawResponse(videoId, language, response.body)
                }
            }
        } catch (exception: CancellationException) {
            withContext(NonCancellable) {
                runCatching { onRequestFinished(false, "CANCELLED") }
            }
            throw exception
        } catch (_: SocketTimeoutException) {
            TranscriptFetchResult.Failure("RAPIDAPI_TIMEOUT")
        } catch (_: Exception) {
            TranscriptFetchResult.Failure("RAPIDAPI_NETWORK_ERROR")
        }
        onRequestFinished(
            result is TranscriptFetchResult.Success,
            when (result) {
                is TranscriptFetchResult.Success -> "SUCCESS"
                is TranscriptFetchResult.Failure -> result.code
            },
        )
        result
    }

    private fun buildRequest(context: TemplateContext): Request {
        val baseUrl = endpoint ?: requireNotNull(profile.endpoint.toHttpUrlOrNull())
        val url = baseUrl.newBuilder().apply {
            profile.queryParameters.forEach { entry ->
                addQueryParameter(entry.name, render(entry.value, context))
            }
        }.build()
        val builder = Request.Builder().url(url)
        profile.headers.forEach { entry ->
            builder.header(entry.name, render(entry.value, context))
        }
        return when (profile.method) {
            RapidApiHttpMethod.GET -> builder.get().build()
            RapidApiHttpMethod.POST -> {
                val mediaType = profile.headers
                    .firstOrNull { it.name.equals("Content-Type", true) }
                    ?.value
                    ?.toMediaTypeOrNull()
                    ?: "text/plain; charset=utf-8".toMediaTypeOrNull()
                builder.post(render(profile.bodyTemplate, context).toRequestBody(mediaType)).build()
            }
        }
    }

    private fun decodeRawResponse(
        videoId: String,
        requestedLanguage: String,
        body: okhttp3.ResponseBody,
    ): TranscriptFetchResult {
        val mediaType = body.contentType()
            ?: return TranscriptFetchResult.Failure("RAPIDAPI_CONTENT_TYPE_MISSING")
        val normalizedMediaType = "${mediaType.type}/${mediaType.subtype}".lowercase()
        if (normalizedMediaType !in ALLOWED_CONTENT_TYPES) {
            return TranscriptFetchResult.Failure("RAPIDAPI_CONTENT_TYPE_NOT_ALLOWED")
        }
        val charset = mediaType.charset(StandardCharsets.UTF_8)
        if (charset != StandardCharsets.UTF_8) {
            return TranscriptFetchResult.Failure("RAPIDAPI_CHARSET_NOT_UTF8")
        }
        if (body.contentLength() > profile.maxResponseBytes) {
            return TranscriptFetchResult.Failure("RAPIDAPI_RESPONSE_TOO_LARGE")
        }
        val source = body.source()
        source.request(profile.maxResponseBytes.toLong() + 1)
        val bytes = source.readByteArray(
            minOf(source.buffer.size, profile.maxResponseBytes.toLong() + 1),
        )
        if (bytes.size > profile.maxResponseBytes) {
            return TranscriptFetchResult.Failure("RAPIDAPI_RESPONSE_TOO_LARGE")
        }
        val raw = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            return TranscriptFetchResult.Failure("RAPIDAPI_RESPONSE_INVALID_UTF8")
        }
        if (raw.isBlank()) return TranscriptFetchResult.Failure("RAPIDAPI_EMPTY_RESPONSE")
        return TranscriptFetchResult.Success(
            TranscriptDocument(
                videoId = videoId,
                languageCode = requestedLanguage,
                isGenerated = false,
                provider = "rapidapi:${profile.name}",
                segments = emptyList(),
                rawContent = raw,
            ),
        )
    }

    private fun render(template: String, context: TemplateContext): String = template
        .replace("{{canonical_url}}", context.canonicalUrl)
        .replace("{{video_id}}", context.videoId)
        .replace("{{language}}", context.language)
        .replace("{{rapidapi_key}}", context.apiKey)

    private fun isAllowedLanguage(value: String): Boolean = LANGUAGE_PATTERN.matches(value)

    private data class TemplateContext(
        val canonicalUrl: String,
        val videoId: String,
        val language: String,
        val apiKey: String,
    )

    private companion object {
        val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        val LANGUAGE_PATTERN = Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})?$")
        val ALLOWED_CONTENT_TYPES = setOf(
            "application/json",
            "text/json",
            "text/plain",
            "text/vtt",
            "application/xml",
            "text/xml",
        )
    }
}
