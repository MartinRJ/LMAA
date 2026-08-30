package de.lmaa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderHttpClientTest {
    @Test
    fun sharedClientHasBoundedTimeoutsNoRedirectsAndNoAutomaticRetry() {
        val client = ProviderHttpClient.shared

        assertEquals(30_000, client.connectTimeoutMillis)
        assertEquals(180_000, client.readTimeoutMillis)
        assertEquals(180_000, client.writeTimeoutMillis)
        assertEquals(200_000, client.callTimeoutMillis)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertFalse(client.retryOnConnectionFailure)
    }
}
