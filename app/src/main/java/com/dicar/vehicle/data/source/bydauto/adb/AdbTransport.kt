package com.dicar.vehicle.data.source.bydauto.adb

import android.content.Context
import android.util.Log
import com.dicar.vehicle.R
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Reports debug-port connection, authorization and helper startup failures with a user-readable reason. */
class AdbUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Manage the local head-unit adbd connection (127.0.0.1:5555) and helper process [com.dicar.vehicle.helper.HelperMain].
 *
 * - ADB keys persist in app-private storage; the first connection prompts for debugging authorization;
 * - The helper runs as shell and provides access to signature-protected vehicle data;
 * - External methods run on the repository's single IO thread; a lock also prevents reentry.
 */
class AdbTransport(context: Context) {

    private val appContext = context.applicationContext
    private val keyDir = File(appContext.filesDir, "adb")
    private val lock = Any()

    private var dadb: Dadb? = null
    private var session: HelperSession? = null

    /** Probe whether the debug port is open without a handshake. */
    fun isPortOpen(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(HOST, PORT), PORT_PROBE_TIMEOUT_MS); true }
    } catch (e: Exception) {
        false
    }

    /** Establish the connection and helper if needed; throw [AdbUnavailableException] when unavailable. */
    fun ensureConnected() = synchronized(lock) { ensureSession(); Unit }

    /** Send and receive one line; throw [AdbUnavailableException] on connection or helper failure. */
    fun request(line: String): String = withSession { it.request(line) }

    /** Batch requests into one round trip. */
    fun requestBatch(lines: List<String>): List<String> = withSession { it.requestBatch(lines) }

    private fun <T> withSession(block: (HelperSession) -> T): T = synchronized(lock) {
        val s = ensureSession()
        try {
            block(s)
        } catch (e: AdbUnavailableException) {
            throw e
        } catch (e: Exception) {
            resetLocked() // Reset the broken stream and reconnect on the next poll.
            throw AdbUnavailableException(appContext.getString(R.string.adb_comm_failed, if (e is HelperExitedException) appContext.getString(R.string.adb_helper_exited) else e.message.orEmpty()), e)
        }
    }

    fun reset() = synchronized(lock) { resetLocked() }

    private fun ensureSession(): HelperSession {
        session?.let { if (it.alive) return it }
        val connection = dadb ?: createDadb().also { dadb = it }

        val apk = appContext.applicationInfo.sourceDir
        // exec replaces sh with app_process so the helper exits when the stream closes; stdout carries protocol, logcat carries logs.
        val cmd = "CLASSPATH=$apk exec app_process /system/bin --nice-name=dicar_helper " +
            "com.dicar.vehicle.helper.HelperMain"
        val shell = try {
            connection.openShell(cmd)
        } catch (e: Exception) {
            resetLocked()
            throw AdbUnavailableException(appContext.getString(R.string.adb_connect_failed, e.message.orEmpty()), e)
        }

        val s = HelperSession(shell)
        val ready = try {
            s.readLine()
        } catch (e: Exception) {
            s.close(); throw AdbUnavailableException(appContext.getString(R.string.adb_helper_no_response, e.message.orEmpty()), e)
        }
        if (ready == null || !ready.startsWith("READY")) {
            s.close()
            throw AdbUnavailableException(appContext.getString(R.string.adb_helper_start_failed, ready ?: appContext.getString(R.string.adb_no_output)))
        }
        Log.i(TAG, "helper ready: $ready")
        session = s
        return s
    }

    private fun createDadb(): Dadb {
        keyDir.mkdirs()
        val priv = File(keyDir, "adbkey")
        val pub = File(keyDir, "adbkey.pub")
        if (!priv.exists() || !pub.exists()) {
            AdbKeyPair.generate(priv, pub)
            Log.i(TAG, "generated adb key")
        }
        val keyPair = AdbKeyPair.read(priv, pub)
        return try {
            Dadb.create(HOST, PORT, keyPair, connectTimeout = CONNECT_TIMEOUT_MS, socketTimeout = SOCKET_TIMEOUT_MS, keepAlive = true)
        } catch (e: Exception) {
            throw AdbUnavailableException(appContext.getString(R.string.adb_create_failed, e.message.orEmpty()), e)
        }
    }

    private fun resetLocked() {
        session?.close(); session = null
        runCatching { dadb?.close() }; dadb = null
    }

    private companion object {
        const val TAG = "AdbTransport"
        const val HOST = "127.0.0.1"
        const val PORT = 5555
        const val PORT_PROBE_TIMEOUT_MS = 300
        const val CONNECT_TIMEOUT_MS = 8_000
        const val SOCKET_TIMEOUT_MS = 5_000
    }
}
