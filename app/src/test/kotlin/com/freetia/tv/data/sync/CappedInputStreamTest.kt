package com.freetia.tv.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class CappedInputStreamTest {

    @Test
    fun readsUpToTheCap() {
        val bytes = ByteArray(100) { 1 }
        assertEquals(100, CappedInputStream(bytes.inputStream(), 100).readBytes().size)
    }

    @Test
    fun failsPastTheCap() {
        val bytes = ByteArray(101) { 1 }
        assertThrows(IOException::class.java) { CappedInputStream(bytes.inputStream(), 100).readBytes() }
    }
}
