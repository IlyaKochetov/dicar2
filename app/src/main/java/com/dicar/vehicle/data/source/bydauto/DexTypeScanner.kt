package com.dicar.vehicle.data.source.bydauto

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal dex parser: read the header and type_ids to list type descriptors such as "Landroid/hardware/bydauto/ac/BYDAutoAcDevice;".
 * Find bydauto classes in head-unit framework jars without the deprecated DexFile API and dalvik-cache write access.
 *
 * Dex format: header 0x3C = string_ids_off, 0x40 = type_ids_size, 0x44 = type_ids_off;
 * type_id = string index; string_id = string_data offset; string_data = uleb128 length + MUTF-8 + '\0'.
 */
object DexTypeScanner {

    class Result(val typeCount: Int, val matches: List<String>)

    private const val HEADER_SIZE = 0x70

    fun scan(dex: ByteArray, prefix: String): Result {
        if (dex.size < HEADER_SIZE || dex[0] != 'd'.code.toByte() || dex[1] != 'e'.code.toByte() || dex[2] != 'x'.code.toByte()) {
            return Result(0, emptyList())
        }
        val buf = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val stringIdsOff = buf.getInt(0x3C)
        val typeIdsSize = buf.getInt(0x40)
        val typeIdsOff = buf.getInt(0x44)
        val prefixBytes = prefix.toByteArray(Charsets.US_ASCII)
        val out = ArrayList<String>()
        for (i in 0 until typeIdsSize) {
            val stringIdx = buf.getInt(typeIdsOff + i * 4)
            var pos = buf.getInt(stringIdsOff + stringIdx * 4)
            while (dex[pos].toInt() and 0x80 != 0) pos++ // Skip uleb128 length
            pos++
            if (!startsWith(dex, pos, prefixBytes)) continue
            var end = pos
            while (dex[end].toInt() != 0) end++
            out += String(dex, pos, end - pos, Charsets.UTF_8)
        }
        return Result(typeIdsSize, out)
    }

    private fun startsWith(data: ByteArray, offset: Int, prefix: ByteArray): Boolean {
        if (offset + prefix.size > data.size) return false
        for (i in prefix.indices) if (data[offset + i] != prefix[i]) return false
        return true
    }
}
