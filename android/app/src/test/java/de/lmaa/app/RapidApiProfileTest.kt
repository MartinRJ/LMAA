package de.lmaa.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RapidApiProfileTest {
    @Test
    fun defaultProfileIsValidAndJsonRoundTrips() {
        RapidApiProfileValidator.requireValid(RapidApiProfile.DEFAULT)
        assertEquals(
            RapidApiProfile.DEFAULT,
            RapidApiProfile.fromJson(RapidApiProfile.DEFAULT.toJson()),
        )
        assertEquals(100, parseSuccessStatusCodes("200-299").size)
    }

    @Test
    fun validatorRejectsSsrfKeyLeakAndUnapprovedHeaders() {
        val invalid = listOf(
            RapidApiProfile.DEFAULT.copy(endpoint = "https://example.com/transcript"),
            RapidApiProfile.DEFAULT.copy(
                queryParameters = listOf(RapidApiTemplateEntry("key", "{{rapidapi_key}}")),
            ),
            RapidApiProfile.DEFAULT.copy(
                headers = RapidApiProfile.DEFAULT.headers +
                    RapidApiTemplateEntry("Authorization", "Bearer synthetic"),
            ),
            RapidApiProfile.DEFAULT.copy(bodyTemplate = "{{unknown_placeholder}}"),
            RapidApiProfile.DEFAULT.copy(
                headers = RapidApiProfile.DEFAULT.headers.map {
                    if (it.name == "Accept") it.copy(value = "{{rapidapi_key}}") else it
                },
            ),
        )
        invalid.forEach { profile ->
            assertThrows(IllegalArgumentException::class.java) {
                RapidApiProfileValidator.requireValid(profile)
            }
        }
    }

    @Test
    fun restrictedCurlImportExtractsKeyWithoutKeepingItInProfile() {
        val imported = RapidApiCurlImporter.parse(
            """curl --request GET --url 'https://sample.p.rapidapi.com/transcript?video={{video_id}}' --header 'X-RapidAPI-Host: sample.p.rapidapi.com' --header 'X-RapidAPI-Key: test-key-not-real' --header 'Accept: application/json'""",
        )

        assertEquals("test-key-not-real", imported.apiKey)
        assertEquals("https://sample.p.rapidapi.com/transcript", imported.profile.endpoint)
        assertEquals("{{video_id}}", imported.profile.queryParameters.single().value)
        assertEquals(
            "{{rapidapi_key}}",
            imported.profile.headers.single { it.name.equals("X-RapidAPI-Key", true) }.value,
        )
        assertFalse(imported.profile.toJson().contains("test-key-not-real"))
    }

    @Test
    fun curlImportDoesNotExecuteOrAcceptShellSyntax() {
        assertThrows(IllegalArgumentException::class.java) {
            RapidApiCurlImporter.parse(
                "curl 'https://sample.p.rapidapi.com/transcript' | powershell",
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RapidApiCurlImporter.parse(
                "curl 'https://sample.p.rapidapi.com/transcript' --location",
            )
        }
    }

    @Test
    fun curlWithoutLiteralKeyKeepsSecretSeparate() {
        val imported = RapidApiCurlImporter.parse(
            "curl --url https://sample.p.rapidapi.com/transcript " +
                "--header 'X-RapidAPI-Host: sample.p.rapidapi.com' " +
                "--header 'X-RapidAPI-Key: {{rapidapi_key}}'",
        )
        assertNull(imported.apiKey)
        assertTrue(imported.profile.headers.any { it.value == "{{rapidapi_key}}" })
    }
}
