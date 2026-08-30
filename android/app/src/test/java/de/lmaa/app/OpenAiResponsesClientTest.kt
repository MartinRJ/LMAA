package de.lmaa.app

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OpenAiResponsesClientTest {
    @Test
    fun requestPinsModelDisablesStorageAndToolsAndParsesOutputText() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(
                        """{"output":[{"type":"message","content":[{"type":"output_text","text":"# Kernaussage\nTest"}]}]}""",
                    )
                    .build(),
            )
            val client = OpenAiResponsesClient(endpoint = server.url("/v1/responses"))

            val result = client.generate("sk-synthetic", "Instruktion", "Eingabe", 123)

            assertEquals("# Kernaussage\nTest", (result as TextGenerationResult.Success).text)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("Bearer sk-synthetic", request.headers["Authorization"])
            val body = JSONObject(requireNotNull(request.body).utf8())
            assertEquals("gpt-5.6-sol", body.getString("model"))
            assertFalse(body.getBoolean("store"))
            assertEquals(0, body.getJSONArray("tools").length())
            assertEquals("medium", body.getJSONObject("reasoning").getString("effort"))
            assertEquals(123, body.getInt("max_output_tokens"))
        } finally {
            server.close()
        }
    }
}
