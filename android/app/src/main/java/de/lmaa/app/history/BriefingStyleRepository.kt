package de.lmaa.app.history

import de.lmaa.app.BriefingStyleSnapshot
import de.lmaa.app.DEFAULT_BRIEFING_STYLE
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal data class BriefingStyle(
    val id: Long,
    val name: String,
    val instructions: String,
    val outputLanguage: String,
    val isActive: Boolean,
    val isBuiltIn: Boolean,
) {
    fun snapshot() = BriefingStyleSnapshot(name, instructions, outputLanguage)
}

internal class BriefingStyleRepository(
    private val dao: BriefingStyleDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val styles: Flow<List<BriefingStyle>> = dao.observeAll().map { entities ->
        entities.map { it.toModel() }
    }
    val active: Flow<BriefingStyle?> = dao.observeActive().map { it?.toModel() }

    suspend fun ensureDefault(): BriefingStyle {
        dao.findByNormalizedName(normalizeName(DEFAULT_BRIEFING_STYLE.name))?.let { existing ->
            val now = clock()
            if (
                existing.isBuiltIn &&
                (
                    existing.instructions != DEFAULT_BRIEFING_STYLE.instructions ||
                        existing.outputLanguage != DEFAULT_BRIEFING_STYLE.outputLanguage
                )
            ) {
                check(
                    dao.updateBuiltInDefaults(
                        id = existing.id,
                        instructions = DEFAULT_BRIEFING_STYLE.instructions,
                        outputLanguage = DEFAULT_BRIEFING_STYLE.outputLanguage,
                        updatedAt = now,
                    ) == 1,
                ) { "BUILT_IN_STYLE_REFRESH_FAILED" }
            }
            if (dao.findActive() == null) dao.setActive(existing.id, now)
            return requireNotNull(dao.find(existing.id)).toModel()
        }
        val now = clock()
        val id = dao.insert(
            BriefingStyleEntity(
                name = DEFAULT_BRIEFING_STYLE.name,
                normalizedName = normalizeName(DEFAULT_BRIEFING_STYLE.name),
                instructions = DEFAULT_BRIEFING_STYLE.instructions,
                outputLanguage = DEFAULT_BRIEFING_STYLE.outputLanguage,
                isActive = dao.findActive() == null,
                isBuiltIn = true,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        return requireNotNull(dao.find(id)).toModel()
    }

    suspend fun activeSnapshot(): BriefingStyleSnapshot =
        (dao.findActive()?.toModel() ?: ensureDefault()).snapshot()

    suspend fun create(name: String, instructions: String, outputLanguage: String): BriefingStyle {
        val validated = validate(name, instructions, outputLanguage)
        check(dao.findByNormalizedName(validated.normalizedName) == null) {
            "STYLE_NAME_EXISTS"
        }
        val now = clock()
        val id = dao.insert(
            BriefingStyleEntity(
                name = validated.name,
                normalizedName = validated.normalizedName,
                instructions = validated.instructions,
                outputLanguage = validated.outputLanguage,
                isActive = false,
                isBuiltIn = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
            ),
        )
        return requireNotNull(dao.find(id)).toModel()
    }

    suspend fun update(id: Long, name: String, instructions: String, outputLanguage: String) {
        val existing = requireNotNull(dao.find(id)) { "STYLE_NOT_FOUND" }
        check(!existing.isBuiltIn) { "BUILT_IN_STYLE_PROTECTED" }
        val validated = validate(name, instructions, outputLanguage)
        val duplicate = dao.findByNormalizedName(validated.normalizedName)
        check(duplicate == null || duplicate.id == id) { "STYLE_NAME_EXISTS" }
        check(
            dao.updateCustom(
                id = id,
                name = validated.name,
                normalizedName = validated.normalizedName,
                instructions = validated.instructions,
                outputLanguage = validated.outputLanguage,
                updatedAt = clock(),
            ) == 1,
        ) { "STYLE_UPDATE_FAILED" }
    }

    suspend fun setActive(id: Long) = dao.setActive(id, clock())

    suspend fun delete(id: Long) {
        val existing = requireNotNull(dao.find(id)) { "STYLE_NOT_FOUND" }
        check(!existing.isBuiltIn) { "BUILT_IN_STYLE_PROTECTED" }
        check(!existing.isActive) { "ACTIVE_STYLE_PROTECTED" }
        check(dao.deleteInactiveCustom(id) == 1) { "STYLE_DELETE_FAILED" }
    }

    private fun validate(
        name: String,
        instructions: String,
        outputLanguage: String,
    ): ValidatedStyle {
        val cleanName = name.trim()
        val cleanInstructions = instructions.trim()
        val cleanLanguage = outputLanguage.trim()
        require(cleanName.isNotEmpty() && cleanName.length <= 80) { "STYLE_NAME_INVALID" }
        require(cleanInstructions.isNotEmpty() && cleanInstructions.length <= 8_000) {
            "STYLE_INSTRUCTIONS_INVALID"
        }
        require(cleanLanguage.isNotEmpty() && cleanLanguage.length <= 40) {
            "STYLE_LANGUAGE_INVALID"
        }
        return ValidatedStyle(
            cleanName,
            normalizeName(cleanName),
            cleanInstructions,
            cleanLanguage,
        )
    }

    private fun normalizeName(value: String): String = value.trim().lowercase(Locale.ROOT)

    private data class ValidatedStyle(
        val name: String,
        val normalizedName: String,
        val instructions: String,
        val outputLanguage: String,
    )

    private fun BriefingStyleEntity.toModel() = BriefingStyle(
        id = id,
        name = name,
        instructions = instructions,
        outputLanguage = outputLanguage,
        isActive = isActive,
        isBuiltIn = isBuiltIn,
    )
}
