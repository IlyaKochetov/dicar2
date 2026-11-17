package com.dicar.vehicle.data.source.bydauto.adb

import java.io.ByteArrayOutputStream

/**
 * Assemble fragmented bytes into lines on '\n'. Shell v2 stdout frame boundaries do not
 * match line boundaries, so buffering is required. A separate class enables unit testing.
 */
class ByteLineBuffer {

    private val buffer = ByteArrayOutputStream(256)

    fun append(bytes: ByteArray, length: Int = bytes.size) {
        buffer.write(bytes, 0, length)
    }

    /** Return the next complete line without newline or trailing '\r', or null if incomplete. */
    fun nextLine(): String? {
        val bytes = buffer.toByteArray()
        val nl = bytes.indexOf('\n'.code.toByte())
        if (nl < 0) return null
        val line = String(bytes, 0, nl, Charsets.UTF_8).trimEnd('\r')
        buffer.reset()
        if (nl + 1 < bytes.size) buffer.write(bytes, nl + 1, bytes.size - nl - 1)
        return line
    }

    private fun ByteArray.indexOf(b: Byte): Int {
        for (i in indices) if (this[i] == b) return i
        return -1
    }
}
