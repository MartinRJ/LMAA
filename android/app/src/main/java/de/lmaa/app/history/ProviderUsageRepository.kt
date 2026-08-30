package de.lmaa.app.history

import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal data class ProviderUsage(
    val month: String,
    val attempts: Int,
    val successes: Int,
    val lastStatus: String?,
) {
    val remaining: Int
        get() = (ProviderUsageRepository.RAPIDAPI_MONTHLY_LIMIT - attempts).coerceAtLeast(0)
    val warningLevel: UsageWarningLevel
        get() = when {
            attempts >= ProviderUsageRepository.RAPIDAPI_MONTHLY_LIMIT ->
                UsageWarningLevel.EXHAUSTED
            attempts >= ProviderUsageRepository.RAPIDAPI_CRITICAL_THRESHOLD ->
                UsageWarningLevel.CRITICAL
            attempts >= ProviderUsageRepository.RAPIDAPI_WARNING_THRESHOLD ->
                UsageWarningLevel.WARNING
            else -> UsageWarningLevel.NORMAL
        }
}

internal enum class UsageWarningLevel { NORMAL, WARNING, CRITICAL, EXHAUSTED }

internal class ProviderUsageRepository(
    private val dao: ProviderUsageDao,
    private val monthProvider: () -> String = { YearMonth.now().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val rapidApiCurrentMonth: Flow<ProviderUsage> = currentRapidApiUsage()

    fun currentRapidApiUsage(): Flow<ProviderUsage> {
        val month = monthProvider()
        return dao.observe(RAPIDAPI_PROVIDER, month).map { entity ->
            ProviderUsage(
                month = month,
                attempts = entity?.attempts ?: 0,
                successes = entity?.successes ?: 0,
                lastStatus = entity?.lastStatus,
            )
        }
    }

    suspend fun ensureDevelopmentBaseline() {
        if (monthProvider() != DEVELOPMENT_BASELINE_MONTH) return
        dao.insertBaselineIfMissing(
            provider = RAPIDAPI_PROVIDER,
            month = DEVELOPMENT_BASELINE_MONTH,
            attempts = DEVELOPMENT_BASELINE_ATTEMPTS,
            successes = DEVELOPMENT_BASELINE_SUCCESSES,
            lastStatus = "MIGRATED_TEST_BASELINE",
            updatedAt = clock(),
        )
    }

    suspend fun recordRapidApiAttempt(success: Boolean, status: String) {
        dao.recordAttempt(
            provider = RAPIDAPI_PROVIDER,
            month = monthProvider(),
            successIncrement = if (success) 1 else 0,
            status = status.take(80),
            updatedAt = clock(),
        )
    }

    companion object {
        const val RAPIDAPI_MONTHLY_LIMIT = 100
        const val RAPIDAPI_WARNING_THRESHOLD = 80
        const val RAPIDAPI_CRITICAL_THRESHOLD = 95
        private const val RAPIDAPI_PROVIDER = "rapidapi"
        private const val DEVELOPMENT_BASELINE_MONTH = "2026-08"
        private const val DEVELOPMENT_BASELINE_ATTEMPTS = 4
        private const val DEVELOPMENT_BASELINE_SUCCESSES = 4
    }
}
