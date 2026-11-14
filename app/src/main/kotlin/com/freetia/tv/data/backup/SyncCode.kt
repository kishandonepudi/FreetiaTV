package com.freetia.tv.data.backup

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Device-to-device sync without a server (§5): a compact backup is compressed and written as upper-case letters and
 * the digits 2-7, so it fits a QR code's alphanumeric mode and can also be read out and typed on the other device.
 */
object SyncCode {
    const val PREFIX = "FTV1"
    /** Above this a QR code gets too dense for a TV across a room; the file route is offered instead. */
    const val MAX_QR_CHARS = 2_200
    private const val MAX_INFLATED = MAX_IMPORT_CHARS
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encode(json: String): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        val out = ByteArrayOutputStream()
        try {
            deflater.setInput(json.toByteArray(Charsets.UTF_8))
            deflater.finish()
            val buf = ByteArray(4096)
            while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        } finally {
            deflater.end()
        }
        return PREFIX + base32(out.toByteArray())
    }

    /** The JSON text inside [code] (dashes, spaces and case ignored), or null when it is not a valid, bounded code. */
    fun decode(code: String): String? {
        val clean = code.uppercase().filter { it.isLetterOrDigit() }
        if (!clean.startsWith(PREFIX) || clean.length > MAX_IMPORT_CHARS) return null
        val bytes = unbase32(clean.substring(PREFIX.length)) ?: return null
        val inflater = Inflater(true)
        val out = ByteArrayOutputStream()
        try {
            inflater.setInput(bytes)
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATED) return null
            }
        } catch (e: DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Groups of five for reading aloud or typing. */
    fun grouped(code: String): String = code.chunked(5).joinToString(" ")

    fun qrMatrix(text: String): BitMatrix? = try {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 1),
        )
    } catch (e: WriterException) {
        null
    }

    private fun base32(bytes: ByteArray): String {
        val sb = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun unbase32(text: String): ByteArray? {
        val out = ByteArrayOutputStream(text.length * 5 / 8)
        var buffer = 0
        var bits = 0
        for (c in text) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        return out.toByteArray()
    }
}
