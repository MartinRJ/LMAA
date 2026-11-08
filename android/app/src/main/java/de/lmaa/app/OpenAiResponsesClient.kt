package de.lmaa.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed interface TextGenerationResult {
    data class Success(val text: String) : TextGenerationResult
    data class Failure(val code: String) : TextGenerationResult
}

internal class OpenAiResponsesClient(
    private val client: OkHttpClient = ProviderHttpClient.shared,
    private val endpoint: HttpUrl = DEFAULT_ENDPOINT,
) {
    suspend fun generate(
        apiKey: String,
        instructions: String,
        input: Any,
        maxOutputTokens: Int,
        webSearch: Boolean = false,
    ): TextGenerationResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext TextGenerationResult.Failure("OPENAI_KEY_MISSING")
        val payload = JSONObject()
            .put("model", MODEL)
            .put("instructions", instructions)
            .put("input", input)
            .put("max_output_tokens", maxOutputTokens)
            .put("reasoning", JSONObject().put("effort", "medium"))
            .put("store", false)
            .put("tools", JSONArray())
        if (webSearch) {
            payload.getJSONArray("tools").put(JSONObject().put("type", "web_search"))
            payload.put("tool_choice", "auto")
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext TextGenerationResult.Failure("OPENAI_HTTP_${response.code}")
                }
                val text = extractOutputText(JSONObject(response.body.string()), webSearch)
                if (text.isBlank()) TextGenerationResult.Failure("OPENAI_EMPTY_OUTPUT")
                else TextGenerationResult.Success(text.trim())
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: JSONException) {
            TextGenerationResult.Failure("OPENAI_MALFORMED_RESPONSE")
        } catch (_: Exception) {
            TextGenerationResult.Failure("OPENAI_NETWORK_ERROR")
        }
    }

    private fun extractOutputText(root: JSONObject, webSearch: Boolean): String {
        val output = root.getJSONArray("output")
        return buildList {
            repeat(output.length()) { outputIndex ->
                val item = output.getJSONObject(outputIndex)
                if (item.optString("type") != "message") return@repeat
                val content = item.optJSONArray("content") ?: return@repeat
                repeat(content.length()) { contentIndex ->
                    val part = content.getJSONObject(contentIndex)
                    if (part.optString("type") == "output_text") {
                        add(if (webSearch) citedText(part) else part.getString("text"))
                    }
                }
            }
        }.joinToString("\n")
    }

    private fun citedText(part: JSONObject): String {
        val text = part.getString("text")
        val annotations = part.optJSONArray("annotations") ?: return text
        val length = text.codePointCount(0, text.length)
        val links = mutableListOf<Pair<Int, String>>()
        for (i in 0 until annotations.length()) {
            val citation = annotations.optJSONObject(i) ?: continue
            val url = citation.optString("url")
            if (citation.optString("type") != "url_citation" || !isAllowedMarkdownLink(url)) continue
            val end = citation.optInt("end_index", length)
            val offset = text.offsetByCodePoints(0, if (end in 0..length) end else length)
            val href = url.replace("(", "%28").replace(")", "%29")
            if (text.take(offset).endsWith("]($url)") && url == href) continue
            val host = java.net.URI(url).host
            links += offset to " [Quelle: $host]($href)"
        }
        return StringBuilder(text).apply {
            links.asReversed().sortedByDescending { it.first }.forEach { (offset, link) -> insert(offset, link) }
        }.toString()
    }

    companion object {
        const val MODEL = "gpt-5.6-sol"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val DEFAULT_ENDPOINT = HttpUrl.Builder()
            .scheme("https")
            .host("api.openai.com")
            .addPathSegment("v1")
            .addPathSegment("responses")
            .build()
    }
}
