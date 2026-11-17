package com.dicar.vehicle.data.source.bydauto

import android.content.Context
import com.dicar.vehicle.data.source.bydauto.BydDevice.CallResult
import com.dicar.vehicle.data.source.bydauto.adb.AdbTransport

/**
 * Low-level vehicle data access with two implementations:
 * - [InProcessBydAccess]: reflection in the app process (signature permissions deny access on many DiLink systems);
 * - [AdbBydAccess]: reads and writes through a helper launched via the head unit's local adbd.
 *
 * [BydAutoDataSource] selects an available channel transparently.
 * All methods are called on the repository's single IO thread.
 */
interface BydAccess {
    val label: String

    /** Low-cost availability probe without establishing a full session. */
    fun probeAvailable(): Boolean

    /** Establish or validate the channel; throw with a user-visible message if unavailable. */
    fun ensureReady()

    fun readGetter(className: String, method: String, args: IntArray): CallResult
    fun readFid(devId: Int, fid: Int, isFloat: Boolean): CallResult
    fun callSetter(className: String, method: String, args: IntArray): CallResult
    fun writeFid(devId: Int, fid: Int, value: Int): CallResult

    /** Read multiple items; the default reads individually, while cross-process channels should use one round trip. */
    fun readBatch(requests: List<RawReq>): List<CallResult> = requests.map {
        when (it) {
            is RawReq.Getter -> readGetter(it.className, it.method, it.args)
            is RawReq.Fid -> readFid(it.devId, it.fid, it.isFloat)
        }
    }

    fun close() {}
}

/** Low-level read request independent of [BydApiMap.Src], with FID resolved for this vehicle. */
sealed interface RawReq {
    data class Getter(val className: String, val method: String, val args: IntArray) : RawReq
    data class Fid(val devId: Int, val fid: Int, val isFloat: Boolean) : RawReq
}

/** In-process access. FID reads and writes use BYDAutoDeviceManager wrapped by BydDevice. */
class InProcessBydAccess(context: Context) : BydAccess {

    override val label = "进程内"

    private val ctx = BydPermissionContext(context.applicationContext)
    private val devices = HashMap<String, BydDevice>()
    private fun device(className: String) = devices.getOrPut(className) { BydDevice(ctx, className) }
    private val manager get() = device(BydApiMap.DEVICE_MANAGER_CLASS)

    override fun probeAvailable(): Boolean =
        // Reading a value, even an error code, means permission was not denied; denial returns Error.
        readGetter("android.hardware.bydauto.statistic.BYDAutoStatisticDevice", "getElecPercentageValue", IntArray(0)) is CallResult.Value

    override fun ensureReady() = Unit

    override fun readGetter(className: String, method: String, args: IntArray) = device(className).call(method, *args)

    override fun readFid(devId: Int, fid: Int, isFloat: Boolean) =
        manager.call(if (isFloat) "getDouble" else "getInt", devId, fid)

    override fun callSetter(className: String, method: String, args: IntArray) = device(className).call(method, *args)

    override fun writeFid(devId: Int, fid: Int, value: Int) = manager.call("setInt", devId, fid, value)
}

/** Read and write through the helper process under the debugging identity. */
class AdbBydAccess(private val transport: AdbTransport) : BydAccess {

    override val label = "无线调试"

    override fun probeAvailable(): Boolean = transport.isPortOpen()

    /** Reuse a live session without PING on every call to save a round trip. */
    override fun ensureReady() = transport.ensureConnected()

    override fun readGetter(className: String, method: String, args: IntArray) =
        parse(transport.request(line("MG", className, method, args)))

    override fun readFid(devId: Int, fid: Int, isFloat: Boolean) =
        parse(transport.request("${if (isFloat) "GD" else "GI"} $devId $fid"))

    override fun callSetter(className: String, method: String, args: IntArray) =
        parse(transport.request(line("MC", className, method, args)))

    override fun writeFid(devId: Int, fid: Int, value: Int) =
        parse(transport.request("SI $devId $fid $value"))

    override fun readBatch(requests: List<RawReq>): List<CallResult> {
        if (requests.isEmpty()) return emptyList()
        val lines = requests.map {
            when (it) {
                is RawReq.Getter -> line("MG", it.className, it.method, it.args)
                is RawReq.Fid -> "${if (it.isFloat) "GD" else "GI"} ${it.devId} ${it.fid}"
            }
        }
        return transport.requestBatch(lines).map(::parse)
    }

    override fun close() = transport.reset()

    private fun line(op: String, className: String, method: String, args: IntArray) =
        "$op $className $method ${args.size}${args.joinToString("") { " $it" }}"

    private fun parse(reply: String) = parseReply(reply)

    companion object {
        /** Parse the helper response; see HelperMain.render for the protocol. */
        internal fun parseReply(reply: String): CallResult {
            val sp = reply.indexOf(' ')
            val tag = if (sp < 0) reply else reply.substring(0, sp)
            val body = if (sp < 0) "" else reply.substring(sp + 1)
            return when (tag) {
                "I" -> CallResult.Value(body.toLongOrNull()?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it })
                "D" -> CallResult.Value(body.toDoubleOrNull())
                "A" -> CallResult.Value(
                    body.split(',').filter { it.isNotBlank() }.map { it.trim().toDouble().toInt() }.toIntArray()
                )
                "N" -> CallResult.Value(null)
                "S" -> CallResult.Value(body)
                "M" -> CallResult.Missing
                else -> CallResult.Error(IllegalStateException(if (tag == "E") body else reply))
            }
        }
    }
}
