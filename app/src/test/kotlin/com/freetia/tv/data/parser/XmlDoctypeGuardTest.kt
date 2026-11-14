package com.freetia.tv.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class XmlDoctypeGuardTest {

    private fun passThrough(xml: String): String = XmlDoctypeGuard.wrap(xml.byteInputStream()).readBytes().decodeToString()

    @Test
    fun plainXmltvPassesUnchanged() {
        val xml = "﻿<?xml version=\"1.0\"?>\n<!-- generated -->\n<tv><channel id=\"a\"/></tv>"
        assertEquals(xml, passThrough(xml))
    }

    @Test
    fun doctypeIsRejected() {
        val xml = "<?xml version=\"1.0\"?>\n<!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n<tv/>"
        assertThrows(XmlDoctypeException::class.java) { passThrough(xml) }
    }

    @Test
    fun entityBombIsRejected() {
        val xml = "<!DOCTYPE lolz [<!ENTITY lol \"lol\"><!ENTITY lol2 \"&lol;&lol;\">]><tv>&lol2;</tv>"
        assertThrows(XmlDoctypeException::class.java) { passThrough(xml) }
    }

    @Test
    fun doctypeAfterCommentIsRejected() {
        val xml = "<!-- a -- b --><!DOCTYPE tv><tv/>"
        assertThrows(XmlDoctypeException::class.java) { passThrough(xml) }
    }

    @Test
    fun utf16DoctypeIsRejected() {
        val bytes = "<!DOCTYPE tv><tv/>".toByteArray(Charsets.UTF_16)
        assertThrows(XmlDoctypeException::class.java) { XmlDoctypeGuard.wrap(bytes.inputStream()) }
    }

    @Test
    fun overlongPrologIsRejected() {
        val xml = "<!--" + "x".repeat(XmlDoctypeGuard.MAX_PROLOG_BYTES) + "--><tv/>"
        assertThrows(XmlDoctypeException::class.java) { passThrough(xml) }
    }
}
