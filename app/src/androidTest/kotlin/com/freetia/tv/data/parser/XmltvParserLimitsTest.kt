package com.freetia.tv.data.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

@RunWith(AndroidJUnit4::class)
class XmltvParserLimitsTest {

    private fun xml(body: String) = ByteArrayInputStream("<tv>$body</tv>".toByteArray())

    private fun programme(channel: String, title: String, extra: String = "") =
        "<programme start=\"20261114120000 +0000\" stop=\"20261114130000 +0000\" channel=\"$channel\">" +
            "<title>$title</title>$extra</programme>"

    @Test
    fun oversizeFields_areClippedNotDropped() = runTest {
        val seen = mutableListOf<XmltvProgramme>()
        val limits = XmltvLimits(maxTitle = 10, maxDescription = 20, maxCategory = 5, maxId = 8)
        val input = xml(
            programme(
                "channelidthatislong", "T".repeat(500),
                "<desc>${"D".repeat(500)}</desc><category>${"C".repeat(50)}</category>",
            ),
        )

        XmltvParser(limits).parse(input, onChannel = {}, onProgramme = { seen += it })

        assertEquals(1, seen.size)
        assertEquals(10, seen[0].title.length)
        assertEquals(20, seen[0].description.length)
        assertEquals(5, seen[0].category.length)
        assertEquals(8, seen[0].channel.length)
    }

    @Test
    fun nonHttpIcons_areDropped() = runTest {
        val seen = mutableListOf<XmltvProgramme>()
        val input = xml(
            programme("a", "One", "<icon src=\"file:///etc/passwd\"/>") +
                programme("a", "Two", "<icon src=\"https://img.example/x.png\"/>"),
        )

        XmltvParser().parse(input, onChannel = {}, onProgramme = { seen += it })

        assertEquals("", seen[0].icon)
        assertEquals("https://img.example/x.png", seen[1].icon)
    }

    @Test
    fun programmeCount_isCapped() = runTest {
        var count = 0
        val input = xml((1..20).joinToString("") { programme("a", "P$it") })

        val result = XmltvParser(XmltvLimits(maxProgrammes = 5)).parse(input, onChannel = {}, onProgramme = { count++ })

        assertEquals(5, count)
        assertTrue(result is ParseResult.Partial)
    }
}
