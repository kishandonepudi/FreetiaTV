package com.freetia.tv.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidationTest {
    private fun doc(
        settings: String = "{}",
        playlists: String = "[]",
        extra: String = "",
        schema: Int = 1,
        format: String = BACKUP_FORMAT,
    ) = """{"format":"$format","schema":$schema,"settings":$settings,"playlists":$playlists$extra}"""

    private fun reason(text: String) = (BackupCodec.parse(text) as BackupOpen.Invalid).reason

    @Test
    fun acceptsAMinimalDocument() {
        assertTrue(BackupCodec.parse(doc()) is BackupOpen.Ok)
    }

    @Test
    fun rejectsOversizeInput() {
        val big = "x".repeat(MAX_IMPORT_CHARS + 1)
        assertTrue(reason(big).contains("too large"))
        assertTrue(BackupFiles.open(big, null) is BackupOpen.Invalid)
    }

    @Test
    fun rejectsNonBackupText() {
        assertTrue(BackupCodec.parse("not json at all") is BackupOpen.Invalid)
        assertTrue(BackupCodec.parse(doc(format = "something-else")) is BackupOpen.Invalid)
        assertTrue(BackupCodec.parse("[]") is BackupOpen.Invalid)
    }

    @Test
    fun rejectsWrongOrNewerSchema() {
        assertTrue(reason(doc(schema = 99)).contains("newer"))
        assertTrue(reason(doc(schema = 0)).contains("format"))
        assertTrue(reason("""{"format":"$BACKUP_FORMAT"}""").contains("format"))
    }

    @Test
    fun rejectsNonHttpLinks() {
        for (bad in listOf("file:///sdcard/a.m3u", "content://x/y", "javascript:alert(1)", "ftp://a.b/c", "https://", "not a url", "http://a b.com")) {
            val text = doc(playlists = """[{"name":"A","url":"$bad"}]""")
            assertTrue("accepted $bad", BackupCodec.parse(text) is BackupOpen.Invalid)
        }
        assertTrue(BackupCodec.parse(doc(playlists = """[{"name":"A","url":"https://a.b/c.m3u"}]""")) is BackupOpen.Ok)
        assertTrue(BackupCodec.parse(doc(playlists = """[{"name":"A","url":"HTTP://a.b/c.m3u"}]""")) is BackupOpen.Ok)
        assertTrue(BackupCodec.parse(doc(extra = ""","epgSourceUrl":"file:///x"""")) is BackupOpen.Invalid)
    }

    @Test
    fun urlRulesBoundLength() {
        assertFalse(UrlRules.isHttpUrl("https://a.b/" + "x".repeat(UrlRules.MAX_LENGTH)))
        assertTrue(UrlRules.isHttpUrl("https://a.b/x"))
    }

    @Test
    fun validatesSettingValues() {
        assertTrue(BackupCodec.parse(doc(settings = """{"text_size":125,"theme_mode":"dark"}""")) is BackupOpen.Ok)
        for (bad in listOf(
            """{"text_size":5000}""", """{"text_size":"big"}""", """{"theme_mode":"neon"}""", """{"high_contrast":"yes"}""",
            """{"subtitle_bg_opacity":3}""", """{"text_size":125.5}""", """{"color_button_red":"launch-missiles"}""",
        )) {
            assertTrue("accepted $bad", BackupCodec.parse(doc(settings = bad)) is BackupOpen.Invalid)
        }
    }

    @Test
    fun ignoresUnknownAndExcludedSettings() {
        val ok = BackupCodec.parse(doc(settings = """{"made_up":1,"pin_hash":"x","active_profile_id":9,"text_size":100}""")) as BackupOpen.Ok
        assertEquals(mapOf<String, Any>("text_size" to 100), ok.data.settings)
    }

    @Test
    fun validatesChannelReferencesAndLimits() {
        val pl = """[{"name":"A"}]"""
        assertTrue(BackupCodec.parse(doc(playlists = pl, extra = ""","favourites":[{"p":0,"id":"a","n":"A"}]""")) is BackupOpen.Ok)
        assertTrue(BackupCodec.parse(doc(playlists = pl, extra = ""","favourites":[{"p":3,"id":"a","n":"A"}]""")) is BackupOpen.Invalid)
        assertTrue(BackupCodec.parse(doc(playlists = pl, extra = ""","favourites":[{"p":0,"id":"","n":""}]""")) is BackupOpen.Invalid)
        val many = (1..MAX_BACKUP_REFS + 1).joinToString(",", "[", "]") { """{"p":0,"id":"a","n":"A"}""" }
        assertTrue(BackupCodec.parse(doc(playlists = pl, extra = ""","favourites":$many""")) is BackupOpen.Invalid)
        val tooManyPlaylists = (1..MAX_BACKUP_PLAYLISTS + 1).joinToString(",", "[", "]") { """{"name":"P$it"}""" }
        assertTrue(BackupCodec.parse(doc(playlists = tooManyPlaylists)) is BackupOpen.Invalid)
        assertTrue(BackupCodec.parse(doc(playlists = """[{"name":""}]""")) is BackupOpen.Invalid)
    }

    @Test
    fun validatesLocksAndPinData() {
        assertTrue(BackupCodec.parse(doc(extra = ""","locks":{"categories":[{"scope":"galaxy","value":"x"}]}""")) is BackupOpen.Invalid)
        assertTrue(BackupCodec.parse(doc(extra = ""","pin":{"safety_pin_hash":"<script>"}""")) is BackupOpen.Invalid)
        val ok = BackupCodec.parse(doc(extra = ""","pin":{"safety_pin_hash":"YWJj:ZGVm","unknown":"zzz"}""")) as BackupOpen.Ok
        assertEquals(mapOf("safety_pin_hash" to "YWJj:ZGVm"), ok.data.pinSecrets)
        assertNull((BackupCodec.parse(doc()) as BackupOpen.Ok).data.pinSecrets)
        assertNotNull(ok)
    }

    @Test
    fun clampsPlaylistOptions() {
        val ok = BackupCodec.parse(doc(playlists = """[{"name":"A","refreshMinutes":99999999}]""")) as BackupOpen.Ok
        assertEquals(7 * 24 * 60, ok.data.playlists.single().refreshMinutes)
    }
}
