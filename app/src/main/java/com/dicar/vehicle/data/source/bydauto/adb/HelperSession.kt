package com.dicar.vehicle.data.source.bydauto.adb

import android.util.Log
import dadb.AdbShellPacket
import dadb.AdbShellStream

/**
 * Wrap a shell v2 stream with a line-oriented protocol interface.
 *
 * Shell v2 ([AdbShellStream]) separates stdout and stderr frames so warnings from the helper
 * or app_process do not mix with protocol lines assembled from stdout.
 */
internal class HelperExitedException : IllegalStateException()

class HelperSession(private val shell: AdbShellStream) {

    @Volatile
    var alive = true
        private set

    private val pending = ByteLineBuffer()

    fun request(line: String): String {
        writeLine(line)
        return readLine() ?: throw HelperExitedException()
    }

    /**
     * Write all requests at once and receive the same number of replies in order, using one
     * round trip per poll instead of one per field.
     */
    fun requestBatch(lines: List<String>): List<String> {
        if (lines.isEmpty()) return emptyList()
        shell.write(buildString {
            append("BEGIN ").append(lines.size).append('\n')
            lines.forEach { append(it).append('\n') }
        })
        return List(lines.size) { readLine() ?: throw HelperExitedException() }
    }

    private fun writeLine(line: String) {
        shell.write(line + "\n")
    }

    /** Read a line without its newline; return null if the helper exits. */
    fun readLine(): String? {
        while (true) {
            pending.nextLine()?.let { return it }
            val packet = try {
                shell.read()
            } catch (e: Exception) {
                alive = false
                throw e
            }
            when (packet) {
                is AdbShellPacket.StdOut -> pending.append(packet.payload)
                is AdbShellPacket.StdError -> Log.w(TAG, "helper stderr: ${String(packet.payload).trim()}")
                is AdbShellPacket.Exit -> {
                    alive = false
                    return pending.nextLine()
                }
            }
        }
    }

    fun close() {
        alive = false
        runCatching { shell.close() }
    }

    private companion object {
        const val TAG = "HelperSession"
    }
}
