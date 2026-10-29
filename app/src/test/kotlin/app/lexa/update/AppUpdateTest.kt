package app.lexa.update

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test

class AppUpdateTest {
    @Test fun `versions compare by numbers`() {
        assertTrue(isNewer("0.10.0", "0.9.0"))
        assertTrue(isNewer("v0.3.0", "0.2.0"))
        assertFalse(isNewer("0.3.0", "0.3.0"))
        assertFalse(isNewer("0.2.9", "0.3"))
    }

    @Test fun `release json gives version, notes and the apk`() {
        val body = """
            {"tag_name":"v0.3.0","body":"- Перевод с русского\n","assets":[
              {"name":"notes.txt","browser_download_url":"https://x/notes.txt","size":1},
              {"name":"Lexa-0.3.0.apk","browser_download_url":"https://x/Lexa-0.3.0.apk","size":20000000}]}
        """.trimIndent()
        assertEquals(
            Release("0.3.0", "- Перевод с русского", "https://x/Lexa-0.3.0.apk", 20_000_000),
            parseRelease(Json, body),
        )
    }

    @Test fun `a release without an apk is ignored`() =
        assertNull(parseRelease(Json, """{"tag_name":"v0.3.0","body":"","assets":[]}"""))
}
