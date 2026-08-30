package de.lmaa.app.history

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderUsageTest {
    @Test
    fun warningLevels_followBasicAccountThresholds() {
        assertEquals(UsageWarningLevel.NORMAL, usage(79).warningLevel)
        assertEquals(UsageWarningLevel.WARNING, usage(80).warningLevel)
        assertEquals(UsageWarningLevel.CRITICAL, usage(95).warningLevel)
        assertEquals(UsageWarningLevel.EXHAUSTED, usage(100).warningLevel)
        assertEquals(0, usage(101).remaining)
    }

    private fun usage(attempts: Int) = ProviderUsage(
        month = "2030-01",
        attempts = attempts,
        successes = 0,
        lastStatus = null,
    )
}
