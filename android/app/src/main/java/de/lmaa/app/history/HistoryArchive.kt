package de.lmaa.app.history

import androidx.room.withTransaction
import de.lmaa.app.transcriptContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal const val ARCHIVE_LIMIT = 64 * 1024 * 1024

internal class HistoryArchive(private val db: LmaaDatabase) {
    private val dao = db.briefingDao()

    private suspend fun record(b: BriefingEntity): JSONObject {
        val v = requireNotNull(dao.video(b.videoId))
        val t = requireNotNull(dao.transcript(b.transcriptId))
        return JSONObject().put("id", b.portableId).put("videoId", b.videoId)
            .put("title", v.title).put("channel", v.channelTitle).put("markdown", b.markdown)
            .put("ts", b.createdAtEpochMillis).put("model", b.modelSnapshot)
            .put("styleName", b.styleNameSnapshot).put("styleInstructions", b.styleInstructionsSnapshot)
            .put("styleOutputLanguage", b.styleOutputLanguageSnapshot)
            .put("transcript", JSONObject().put("content", transcriptContent(t))
                .put("label", if (t.provider == "rapidapi") "RAPIDAPI_RAW_RESPONSE" else "TRANSKRIPT")
                .put("language", t.languageCode).put("provider", t.provider))
            .put("messages", JSONArray().apply {
                db.chatDao().messages(b.id).forEach { m -> put(JSONObject().put("id", m.id)
                    .put("role", m.role).put("content", m.content).put("ts", m.ts)) }
            })
    }

    suspend fun export(): String = withContext(Dispatchers.IO) {
        db.withTransaction {
            JSONObject().put("format", "lmaa-history").put("version", 1)
                .put("briefings", JSONArray().apply { dao.exportBriefings().forEach { put(record(it)) } })
                .put("styles", JSONArray().apply {
                    db.briefingStyleDao().observeAll().first().filter { !it.isBuiltIn }.forEach {
                        put(JSONObject().put("name", it.name).put("instructions", it.instructions)
                            .put("outputLanguage", it.outputLanguage))
                    }
                }).toString().also { validateArchive(it) }
        }
    }

    suspend fun import(text: String): Int = withContext(Dispatchers.IO) {
        val archive = validateArchive(text)
        db.withTransaction {
            var added = 0
            val records = archive.getJSONArray("briefings")
            for (i in 0 until records.length()) {
                val b = records.getJSONObject(i)
                val existing = dao.findPortable(b.getString("id"))
                val id = if (existing != null) {
                    val old = record(existing)
                    for (key in listOf("videoId", "markdown", "ts", "model", "styleName", "styleInstructions", "styleOutputLanguage")) {
                        require(old.get(key) == b.get(key)) { "ARCHIVE_ID_CONFLICT" }
                    }
                    require(old.getJSONObject("transcript").getString("content") == b.getJSONObject("transcript").getString("content")) { "ARCHIVE_ID_CONFLICT" }
                    existing.id
                } else {
                    val videoId = b.getString("videoId")
                    if (dao.video(videoId) == null) dao.upsertVideo(VideoEntity(videoId,
                        "https://www.youtube.com/watch?v=$videoId", b.getString("title"), null,
                        b.getString("channel"), null, null, null, "", b.getLong("ts")))
                    val t = b.getJSONObject("transcript")
                    val transcriptId = dao.insertTranscript(TranscriptEntity(videoId = videoId,
                        provider = t.getString("provider"), languageCode = t.getString("language"),
                        isGenerated = false, segmentsJson = "[]", plainText = t.getString("content"), fetchedAtEpochMillis = b.getLong("ts")))
                    added++
                    dao.insertBriefing(BriefingEntity(videoId = videoId, portableId = b.getString("id"),
                        transcriptId = transcriptId, styleNameSnapshot = b.getString("styleName"),
                        styleInstructionsSnapshot = b.getString("styleInstructions"),
                        styleOutputLanguageSnapshot = b.getString("styleOutputLanguage"),
                        modelSnapshot = b.getString("model"), markdown = b.getString("markdown"),
                        mapChunkCount = 1, status = "COMPLETED", errorCode = null, createdAtEpochMillis = b.getLong("ts")))
                }
                val messages = b.getJSONArray("messages")
                for (j in 0 until messages.length()) {
                    val m = messages.getJSONObject(j)
                    val message = ChatMessageEntity(m.getString("id"), id, m.getString("role"), m.getString("content"), m.getLong("ts"))
                    val old = db.chatDao().find(message.id)
                    if (old == null) db.chatDao().insert(message)
                    else require(old.copy(pending = false, errorCode = null) == message) { "ARCHIVE_MESSAGE_CONFLICT" }
                }
            }
            val styles = archive.optJSONArray("styles") ?: JSONArray()
            val repo = BriefingStyleRepository(db.briefingStyleDao())
            repo.ensureDefault()
            for (i in 0 until styles.length()) {
                val style = styles.getJSONObject(i)
                val known = db.briefingStyleDao().observeAll().first()
                if (known.any { it.instructions == style.getString("instructions") && it.outputLanguage == style.getString("outputLanguage") }) continue
                var name = style.getString("name")
                var suffix = 1
                while (known.any { it.name.equals(name, ignoreCase = true) }) name = style.getString("name").take(60) + " (Import ${suffix++})"
                repo.create(name, style.getString("instructions"), style.getString("outputLanguage"))
            }
            added
        }
    }
}

internal fun validateArchive(text: String): JSONObject {
    require(text.toByteArray().size <= ARCHIVE_LIMIT) { "ARCHIVE_TOO_LARGE" }
    val root = JSONObject(text)
    require(root.getString("format") == "lmaa-history" && root.get("version") is Number && root.getDouble("version") == 1.0) { "ARCHIVE_VERSION_INVALID" }
    fun string(o: JSONObject, key: String, max: Int, nonempty: Boolean = false): String {
        val value = o.get(key)
        require(value is String && value.length <= max && (!nonempty || value.isNotBlank())) { "ARCHIVE_FIELD_INVALID" }
        return value
    }
    val ids = mutableSetOf<String>()
    val messageIds = mutableSetOf<String>()
    val records = root.getJSONArray("briefings")
    require(records.length() <= 10000)
    for (i in 0 until records.length()) {
        val b = records.getJSONObject(i)
        val id = string(b, "id", 100, true)
        require(id.matches(Regex("[A-Za-z0-9_-]+")) && ids.add(id))
        require(string(b, "videoId", 11).matches(Regex("[A-Za-z0-9_-]{11}")))
        string(b, "title", 10000); string(b, "channel", 10000)
        string(b, "markdown", 2_000_000, true); string(b, "model", 100, true)
        string(b, "styleName", 200); string(b, "styleInstructions", 16000); string(b, "styleOutputLanguage", 40)
        require(b.get("ts") is Number && b.getDouble("ts") == b.getLong("ts").toDouble() && b.getLong("ts") in 0..9007199254740991L)
        val t = b.getJSONObject("transcript")
        string(t, "content", 8_000_000); string(t, "language", 100); string(t, "provider", 100)
        require(string(t, "label", 100) in listOf("TRANSKRIPT", "RAPIDAPI_RAW_RESPONSE"))
        val messages = b.getJSONArray("messages")
        require(messages.length() <= 100000)
        for (j in 0 until messages.length()) {
            val m = messages.getJSONObject(j)
            val mid = string(m, "id", 120, true)
            require(mid.matches(Regex("[A-Za-z0-9_-]+")) && messageIds.add(mid))
            require(string(m, "role", 20) in listOf("user", "assistant"))
            string(m, "content", 2_000_000, true)
            require(m.get("ts") is Number && m.getDouble("ts") == m.getLong("ts").toDouble() && m.getLong("ts") in 0..9007199254740991L)
        }
    }
    require(!root.has("styles") || root.isNull("styles") || root.get("styles") is JSONArray)
    val styles = root.optJSONArray("styles") ?: JSONArray()
    require(styles.length() <= 1000)
    for (i in 0 until styles.length()) {
        val s = styles.getJSONObject(i)
        string(s, "name", 80, true); string(s, "instructions", 8000, true); string(s, "outputLanguage", 40, true)
    }
    return root
}
