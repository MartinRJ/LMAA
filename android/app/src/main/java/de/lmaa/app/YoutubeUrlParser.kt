package de.lmaa.app

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private val videoIdPattern = Regex("^[A-Za-z0-9_-]{11}$")
private val urlPattern = Regex("https?://[^\\s<>\"']+", RegexOption.IGNORE_CASE)
private const val trailingSharePunctuation = ".,;:!?)]}"
private val longHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com")

sealed interface YoutubeUrlParseResult {
    data class Success(val videoId: String, val canonicalUrl: String) : YoutubeUrlParseResult

    enum class Error : YoutubeUrlParseResult {
        EMPTY,
        INVALID,
        AMBIGUOUS,
    }
}
object YoutubeUrlParser {
    fun parse(sharedText: String): YoutubeUrlParseResult {
        if (sharedText.isBlank()) return YoutubeUrlParseResult.Error.EMPTY

        val references = urlPattern.findAll(sharedText)
            .map { it.value.trimEnd(*trailingSharePunctuation.toCharArray()) }
            .mapNotNull(::parseCandidate)
            .associateBy { it.videoId }

        return when (references.size) {
            0 -> YoutubeUrlParseResult.Error.INVALID
            1 -> references.values.first()
            else -> YoutubeUrlParseResult.Error.AMBIGUOUS
        }
    }

    private fun parseCandidate(candidate: String): YoutubeUrlParseResult.Success? {
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.userInfo != null || uri.port != -1) return null

        val host = uri.host?.lowercase() ?: return null
        val videoId = when (host) {
            "youtu.be" -> shortPathVideoId(uri.rawPath)
            in longHosts -> longPathVideoId(uri.rawPath, uri.rawQuery)
            else -> null
        } ?: return null

        if (!videoIdPattern.matches(videoId)) return null
        return YoutubeUrlParseResult.Success(
            videoId = videoId,
            canonicalUrl = "https://www.youtube.com/watch?v=$videoId",
        )
    }

    private fun shortPathVideoId(rawPath: String?): String? {
        if (rawPath == null || '%' in rawPath) return null
        val segments = rawPath.split('/').filter(String::isNotEmpty)
        return segments.singleOrNull()
    }

    private fun longPathVideoId(rawPath: String?, rawQuery: String?): String? {
        if (rawPath == null || '%' in rawPath) return null
        val segments = rawPath.split('/').filter(String::isNotEmpty)
        if (segments == listOf("watch")) {
            return queryValues(rawQuery, "v").singleOrNull()
        }
        if (segments.size == 2 && segments.first() in setOf("shorts", "live")) {
            return segments.last()
        }
        return null
    }

    private fun queryValues(rawQuery: String?, key: String): List<String> =
        rawQuery.orEmpty()
            .split('&')
            .filter(String::isNotEmpty)
            .map { parameter -> parameter.split('=', limit = 2) }
            .filter { parts -> decode(parts.first()) == key }
            .map { parts -> decode(parts.getOrElse(1) { "" }) }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
            .getOrDefault("")
}
