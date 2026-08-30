package de.lmaa.app

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal enum class RapidApiRoutingMode {
    OFF,
    FALLBACK,
    PREFERRED,
}

internal enum class RapidApiHttpMethod {
    GET,
    POST,
}

internal data class RapidApiTemplateEntry(
    val name: String,
    val value: String,
)

internal data class RapidApiProfile(
    val name: String,
    val endpoint: String,
    val method: RapidApiHttpMethod,
    val queryParameters: List<RapidApiTemplateEntry>,
    val headers: List<RapidApiTemplateEntry>,
    val bodyTemplate: String,
    val successStatusCodes: String,
    val connectTimeoutSeconds: Int,
    val readTimeoutSeconds: Int,
    val writeTimeoutSeconds: Int,
    val callTimeoutSeconds: Int,
    val maxResponseBytes: Int,
) {
    fun toJson(): String = JSONObject()
        .put("name", name)
        .put("endpoint", endpoint)
        .put("method", method.name)
        .put("queryParameters", queryParameters.toJson())
        .put("headers", headers.toJson())
        .put("bodyTemplate", bodyTemplate)
        .put("successStatusCodes", successStatusCodes)
        .put("connectTimeoutSeconds", connectTimeoutSeconds)
        .put("readTimeoutSeconds", readTimeoutSeconds)
        .put("writeTimeoutSeconds", writeTimeoutSeconds)
        .put("callTimeoutSeconds", callTimeoutSeconds)
        .put("maxResponseBytes", maxResponseBytes)
        .toString()

    companion object {
        val DEFAULT = RapidApiProfile(
            name = "youtube-transcripts",
            endpoint = "https://youtube-transcripts.p.rapidapi.com/youtube/transcript",
            method = RapidApiHttpMethod.GET,
            queryParameters = listOf(
                RapidApiTemplateEntry("url", "{{canonical_url}}"),
                RapidApiTemplateEntry("videoId", "{{video_id}}"),
                RapidApiTemplateEntry("chunkSize", "100"),
                RapidApiTemplateEntry("text", "false"),
                RapidApiTemplateEntry("lang", "{{language}}"),
            ),
            headers = listOf(
                RapidApiTemplateEntry(
                    "X-RapidAPI-Host",
                    "youtube-transcripts.p.rapidapi.com",
                ),
                RapidApiTemplateEntry("X-RapidAPI-Key", "{{rapidapi_key}}"),
                RapidApiTemplateEntry("Accept", "application/json"),
            ),
            bodyTemplate = "",
            successStatusCodes = "200-299",
            connectTimeoutSeconds = 30,
            readTimeoutSeconds = 180,
            writeTimeoutSeconds = 180,
            callTimeoutSeconds = 200,
            maxResponseBytes = 2_000_000,
        )

        fun fromJson(json: String): RapidApiProfile {
            val root = JSONObject(json)
            return RapidApiProfile(
                name = root.getString("name"),
                endpoint = root.getString("endpoint"),
                method = RapidApiHttpMethod.valueOf(root.getString("method")),
                queryParameters = root.getJSONArray("queryParameters").toEntries(),
                headers = root.getJSONArray("headers").toEntries(),
                bodyTemplate = root.optString("bodyTemplate"),
                successStatusCodes = root.getString("successStatusCodes"),
                connectTimeoutSeconds = root.getInt("connectTimeoutSeconds"),
                readTimeoutSeconds = root.getInt("readTimeoutSeconds"),
                writeTimeoutSeconds = root.getInt("writeTimeoutSeconds"),
                callTimeoutSeconds = root.getInt("callTimeoutSeconds"),
                maxResponseBytes = root.getInt("maxResponseBytes"),
            ).also(RapidApiProfileValidator::requireValid)
        }
    }
}

internal object RapidApiProfileValidator {
    private val placeholderPattern = Regex("\\{\\{[a-z_]+\\}\\}")
    private val allowedPlaceholders = setOf(
        "{{canonical_url}}",
        "{{video_id}}",
        "{{language}}",
        "{{rapidapi_key}}",
    )
    private val headerNamePattern = Regex("^[A-Za-z0-9-]{1,64}$")
    private val allowedHeaders = setOf(
        "accept",
        "content-type",
        "x-rapidapi-host",
        "x-rapidapi-key",
    )

    fun requireValid(profile: RapidApiProfile) {
        require(profile.name.isNotBlank() && profile.name.length <= 80) {
            "RAPIDAPI_PROFILE_NAME_INVALID"
        }
        val endpoint = requireNotNull(profile.endpoint.toHttpUrlOrNull()) {
            "RAPIDAPI_ENDPOINT_INVALID"
        }
        require(
            endpoint.isHttps &&
                endpoint.username.isEmpty() &&
                endpoint.password.isEmpty() &&
                endpoint.port == 443 &&
                endpoint.host.endsWith(".p.rapidapi.com") &&
                endpoint.query == null &&
                endpoint.fragment == null,
        ) { "RAPIDAPI_ENDPOINT_NOT_ALLOWED" }
        require(profile.method != RapidApiHttpMethod.GET || profile.bodyTemplate.isEmpty()) {
            "RAPIDAPI_GET_BODY_NOT_ALLOWED"
        }
        require(profile.bodyTemplate.length <= 100_000) { "RAPIDAPI_BODY_TOO_LARGE" }

        validateEntries(profile.queryParameters, header = false)
        validateEntries(profile.headers, header = true)
        validatePlaceholders(profile.bodyTemplate)
        require(profile.queryParameters.map { it.name }.distinct().size == profile.queryParameters.size) {
            "RAPIDAPI_DUPLICATE_QUERY"
        }
        require(
            profile.headers.map { it.name.lowercase() }.distinct().size == profile.headers.size,
        ) { "RAPIDAPI_DUPLICATE_HEADER" }

        val hostHeader = profile.headers.singleOrNull { it.name.equals("X-RapidAPI-Host", true) }
        require(hostHeader?.value == endpoint.host) { "RAPIDAPI_HOST_HEADER_INVALID" }
        val keyHeader = profile.headers.singleOrNull { it.name.equals("X-RapidAPI-Key", true) }
        require(keyHeader?.value == "{{rapidapi_key}}") { "RAPIDAPI_KEY_HEADER_INVALID" }
        require(
            profile.headers
                .filterNot { it.name.equals("X-RapidAPI-Key", true) }
                .none { "{{rapidapi_key}}" in it.value },
        ) { "RAPIDAPI_KEY_PLACEHOLDER_LOCATION_INVALID" }

        val nonHeaderTemplates = profile.queryParameters.map { it.value } + profile.bodyTemplate
        require(nonHeaderTemplates.none { "{{rapidapi_key}}" in it }) {
            "RAPIDAPI_KEY_PLACEHOLDER_LOCATION_INVALID"
        }
        parseSuccessStatusCodes(profile.successStatusCodes)
        require(profile.connectTimeoutSeconds in 1..60) { "RAPIDAPI_CONNECT_TIMEOUT_INVALID" }
        require(profile.readTimeoutSeconds in 1..300) { "RAPIDAPI_READ_TIMEOUT_INVALID" }
        require(profile.writeTimeoutSeconds in 1..300) { "RAPIDAPI_WRITE_TIMEOUT_INVALID" }
        require(profile.callTimeoutSeconds in 1..600) { "RAPIDAPI_CALL_TIMEOUT_INVALID" }
        require(profile.maxResponseBytes in 1_024..10_000_000) {
            "RAPIDAPI_RESPONSE_LIMIT_INVALID"
        }
    }

    private fun validateEntries(entries: List<RapidApiTemplateEntry>, header: Boolean) {
        require(entries.size <= 40) { "RAPIDAPI_TOO_MANY_ARGUMENTS" }
        entries.forEach { entry ->
            require(entry.name.isNotBlank() && entry.name.length <= 128) {
                "RAPIDAPI_ARGUMENT_NAME_INVALID"
            }
            require(entry.value.length <= 100_000 && '\r' !in entry.value && '\n' !in entry.value) {
                "RAPIDAPI_ARGUMENT_VALUE_INVALID"
            }
            if (header) {
                require(headerNamePattern.matches(entry.name)) { "RAPIDAPI_HEADER_NAME_INVALID" }
                require(entry.name.lowercase() in allowedHeaders) { "RAPIDAPI_HEADER_NOT_ALLOWED" }
            }
            validatePlaceholders(entry.value)
        }
    }

    private fun validatePlaceholders(template: String) {
        val found = placeholderPattern.findAll(template).map { it.value }.toSet()
        require(found.all { it in allowedPlaceholders }) { "RAPIDAPI_PLACEHOLDER_UNKNOWN" }
        require("{{" !in placeholderPattern.replace(template, "")) {
            "RAPIDAPI_PLACEHOLDER_INVALID"
        }
    }
}

internal fun parseSuccessStatusCodes(value: String): Set<Int> {
    val result = mutableSetOf<Int>()
    value.split(',').map(String::trim).filter(String::isNotEmpty).forEach { token ->
        val range = token.split('-').map(String::trim)
        when (range.size) {
            1 -> result += range.single().toIntOrNull()
                ?: throw IllegalArgumentException("RAPIDAPI_SUCCESS_STATUS_INVALID")
            2 -> {
                val start = range[0].toIntOrNull()
                    ?: throw IllegalArgumentException("RAPIDAPI_SUCCESS_STATUS_INVALID")
                val end = range[1].toIntOrNull()
                    ?: throw IllegalArgumentException("RAPIDAPI_SUCCESS_STATUS_INVALID")
                require(start <= end && end - start <= 100) {
                    "RAPIDAPI_SUCCESS_STATUS_INVALID"
                }
                result += start..end
            }
            else -> throw IllegalArgumentException("RAPIDAPI_SUCCESS_STATUS_INVALID")
        }
    }
    require(result.isNotEmpty() && result.all { it in 100..599 }) {
        "RAPIDAPI_SUCCESS_STATUS_INVALID"
    }
    return result
}

internal data class RapidApiCurlImport(
    val profile: RapidApiProfile,
    val apiKey: String?,
)

internal object RapidApiCurlImporter {
    fun parse(command: String): RapidApiCurlImport {
        require(command.length in 1..50_000 && '\u0000' !in command) {
            "RAPIDAPI_CURL_INVALID"
        }
        val normalized = command.replace(Regex("\\\\\\r?\\n"), " ")
        require(!Regex("(?:&&|\\|\\||[;<>`]|\\$\\()" ).containsMatchIn(normalized)) {
            "RAPIDAPI_CURL_SHELL_SYNTAX_NOT_ALLOWED"
        }
        val tokens = tokenize(normalized)
        require(tokens.firstOrNull()?.substringAfterLast('/')?.lowercase() in setOf("curl", "curl.exe")) {
            "RAPIDAPI_CURL_INVALID"
        }

        var method: RapidApiHttpMethod? = null
        var url: String? = null
        var body = ""
        val headers = mutableListOf<RapidApiTemplateEntry>()
        var importedKey: String? = null
        var index = 1
        while (index < tokens.size) {
            val token = tokens[index]
            fun nextValue(): String {
                index += 1
                require(index < tokens.size) { "RAPIDAPI_CURL_OPTION_VALUE_MISSING" }
                return tokens[index]
            }
            when {
                token == "-X" || token == "--request" -> {
                    method = parseMethod(nextValue())
                }
                token.startsWith("--request=") -> method = parseMethod(token.substringAfter('='))
                token == "-H" || token == "--header" -> {
                    val parsed = parseHeader(nextValue())
                    if (parsed.name.equals("X-RapidAPI-Key", true)) {
                        if (parsed.value != "{{rapidapi_key}}") importedKey = parsed.value
                        headers += parsed.copy(value = "{{rapidapi_key}}")
                    } else {
                        headers += parsed
                    }
                }
                token.startsWith("--header=") -> {
                    val parsed = parseHeader(token.substringAfter('='))
                    if (parsed.name.equals("X-RapidAPI-Key", true)) {
                        if (parsed.value != "{{rapidapi_key}}") importedKey = parsed.value
                        headers += parsed.copy(value = "{{rapidapi_key}}")
                    } else {
                        headers += parsed
                    }
                }
                token in setOf("-d", "--data", "--data-raw", "--data-binary") -> {
                    body = nextValue()
                    if (method == null) method = RapidApiHttpMethod.POST
                }
                token.startsWith("--data=") || token.startsWith("--data-raw=") ||
                    token.startsWith("--data-binary=") -> {
                    body = token.substringAfter('=')
                    if (method == null) method = RapidApiHttpMethod.POST
                }
                token == "--url" -> url = nextValue()
                token.startsWith("--url=") -> url = token.substringAfter('=')
                token == "--compressed" || token == "-s" || token == "--silent" -> Unit
                token.startsWith("-") -> throw IllegalArgumentException(
                    "RAPIDAPI_CURL_OPTION_NOT_ALLOWED",
                )
                url == null -> url = token
                else -> throw IllegalArgumentException("RAPIDAPI_CURL_INVALID")
            }
            index += 1
        }

        val parsedUrl = requireNotNull(url?.toHttpUrlOrNull()) { "RAPIDAPI_ENDPOINT_INVALID" }
        val query = buildList {
            repeat(parsedUrl.querySize) { queryIndex ->
                add(
                    RapidApiTemplateEntry(
                        parsedUrl.queryParameterName(queryIndex),
                        parsedUrl.queryParameterValue(queryIndex).orEmpty(),
                    ),
                )
            }
        }
        val endpoint = parsedUrl.newBuilder().query(null).fragment(null).build()
        if (headers.none { it.name.equals("X-RapidAPI-Host", true) }) {
            headers += RapidApiTemplateEntry("X-RapidAPI-Host", endpoint.host)
        }
        if (headers.none { it.name.equals("X-RapidAPI-Key", true) }) {
            headers += RapidApiTemplateEntry("X-RapidAPI-Key", "{{rapidapi_key}}")
        }
        val profile = RapidApiProfile.DEFAULT.copy(
            name = "Importiertes RapidAPI-Profil",
            endpoint = endpoint.toString(),
            method = method ?: RapidApiHttpMethod.GET,
            queryParameters = query,
            headers = headers,
            bodyTemplate = body,
        ).also(RapidApiProfileValidator::requireValid)
        return RapidApiCurlImport(profile, importedKey?.trim()?.takeIf(String::isNotEmpty))
    }

    private fun parseMethod(value: String): RapidApiHttpMethod = try {
        RapidApiHttpMethod.valueOf(value.uppercase())
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("RAPIDAPI_METHOD_NOT_ALLOWED")
    }

    private fun parseHeader(value: String): RapidApiTemplateEntry {
        val separator = value.indexOf(':')
        require(separator > 0) { "RAPIDAPI_CURL_HEADER_INVALID" }
        return RapidApiTemplateEntry(
            value.substring(0, separator).trim(),
            value.substring(separator + 1).trim(),
        )
    }

    private fun tokenize(value: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaped = false
        value.forEach { character ->
            when {
                escaped -> {
                    current.append(character)
                    escaped = false
                }
                character == '\\' && quote != '\'' -> escaped = true
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == null && character.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(character)
            }
        }
        require(!escaped && quote == null) { "RAPIDAPI_CURL_QUOTE_INVALID" }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }
}

private fun List<RapidApiTemplateEntry>.toJson(): JSONArray = JSONArray().apply {
    forEach { entry -> put(JSONObject().put("name", entry.name).put("value", entry.value)) }
}

private fun JSONArray.toEntries(): List<RapidApiTemplateEntry> = buildList {
    repeat(length()) { index ->
        val entry = getJSONObject(index)
        add(RapidApiTemplateEntry(entry.getString("name"), entry.getString("value")))
    }
}
