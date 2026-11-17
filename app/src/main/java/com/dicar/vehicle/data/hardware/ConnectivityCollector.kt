package com.dicar.vehicle.data.hardware

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * 网络。
 *
 * 刻意**不读** Wi-Fi 名称和 BSSID：那两项从安卓 8.1 起要定位权限，为了一个信息展示页
 * 去申请定位很不划算。连接质量相关的（速率、频段、信号）不需要权限，照常显示。
 */
fun collectNetwork(context: Context): HwSection {
    val pm = context.packageManager
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    return section(HwCategory.NETWORK) {
        block("Current connection") {
            val active = runCatching { cm?.activeNetwork }.getOrNull()
            val caps = runCatching { active?.let { cm?.getNetworkCapabilities(it) } }.getOrNull()
            item("Type", caps?.let { transportName(it) } ?: "Not connected")
            item("Downlink bandwidth", caps?.let { HwFormat.kbps(it.linkDownstreamBandwidthKbps) })
            item("Uplink bandwidth", caps?.let { HwFormat.kbps(it.linkUpstreamBandwidthKbps) })
            flag("Internet access verified", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            flag("Metered network", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)?.let { !it })
        }

        block("Wi-Fi") {
            item("Hardware", pm.hasSystemFeature(PackageManager.FEATURE_WIFI))
            flag("Enabled", runCatching { wifi?.isWifiEnabled }.getOrNull(), yes = "On", no = "Off")
            item("5 GHz", runCatching { wifi?.is5GHzBandSupported }.getOrNull())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item("6 GHz", runCatching { wifi?.is6GHzBandSupported }.getOrNull())
                item("Wi-Fi 6", runCatching {
                    wifi?.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AX)
                }.getOrNull())
                item("Wi-Fi 5", runCatching {
                    wifi?.isWifiStandardSupported(ScanResult.WIFI_STANDARD_11AC)
                }.getOrNull())
            }
            item("Wi-Fi Direct", pm.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT))
            item("Wi-Fi Aware", pm.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE))
            item("Wi-Fi RTT ranging", pm.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT))
            @Suppress("DEPRECATION") // 新接口要注册回调，一次性读取用旧的就够
            val conn = runCatching { wifi?.connectionInfo }.getOrNull()
            if (conn != null && conn.networkId != -1) {
                item("Link speed", conn.linkSpeed.takeIf { it > 0 }?.let { "$it Mbps" })
                item("Frequency", conn.frequency.takeIf { it > 0 }?.let { "$it MHz" })
                item("Signal strength", "${conn.rssi} dBm")
                @Suppress("DEPRECATION")
                item("IP address", intToIp(conn.ipAddress))
            }
        }

        block("Mobile network") {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            item("Hardware", pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY))
            item("Network type", phoneType(runCatching { tm?.phoneType }.getOrNull()))
            item("Carrier", runCatching { tm?.networkOperatorName }.getOrNull())
            item("SIM status", simState(runCatching { tm?.simState }.getOrNull()))
            item("eSIM", pm.hasSystemFeature("android.hardware.telephony.euicc"))
        }

        block("Bluetooth") {
            item("Hardware", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
            item("Bluetooth LE", pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
            // 适配器状态在安卓 12 起要 BLUETOOTH_CONNECT 运行时权限；
            // 车机是安卓 10，清单里的 BLUETOOTH 就够用。拿不到就显示未知，不去申请权限
            val adapter = runCatching {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            }.getOrNull()
            flag("Enabled", runCatching { adapter?.isEnabled }.getOrNull(), yes = "On", no = "Off")
            item("BLE advertising", runCatching { adapter?.isMultipleAdvertisementSupported }.getOrNull())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item("LE Audio", runCatching { adapter?.isLeAudioSupported == FEATURE_SUPPORTED }.getOrNull())
            }
        }

        block("Other wireless") {
            item("NFC", pm.hasSystemFeature(PackageManager.FEATURE_NFC))
            item("GPS", pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS))
            item("Network location", pm.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK))
            item("USB host", pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST))
            item("USB accessory", pm.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY))
            item("Ethernet", pm.hasSystemFeature("android.hardware.ethernet"))
        }

        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
        if (interfaces.isNotEmpty()) {
            block("Network interfaces") {
                interfaces.filter { runCatching { it.isUp }.getOrDefault(false) }.forEach { nif ->
                    val addresses = runCatching {
                        nif.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress }
                    }.getOrNull().orEmpty().filterNotNull()
                    item(
                        nif.name,
                        listOfNotNull(
                            addresses.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            runCatching { "MTU ${nif.mtu}" }.getOrNull(),
                            if (runCatching { nif.isLoopback }.getOrDefault(false)) "Loopback" else null,
                        ).joinToString(" · ").ifEmpty { "Enabled" },
                    )
                }
            }
        }
    }
}

/** 音频。采样率、延迟、当前接入的设备。 */
fun collectAudio(context: Context): HwSection {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val pm = context.packageManager

    return section(HwCategory.AUDIO) {
        block {
            // 这两个数决定了能不能做低延迟音频：按它们开 AudioTrack 才能走快速通道
            item("Output sample rate", am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.let { "$it Hz" })
            item("Output buffer", am?.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.let { "$it frames" })
            item("Low-latency audio", pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY))
            item("Professional audio", pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO))
            item("Microphone", pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE))
            item("MIDI", pm.hasSystemFeature(PackageManager.FEATURE_MIDI))
            item(
                "Unprocessed audio source",
                am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)?.let { it == "true" },
            )
        }

        block("Volume levels") {
            VOLUME_STREAMS.forEach { (stream, label) ->
                item(label, am?.getStreamMaxVolume(stream)?.let { max ->
                    "${am.getStreamVolume(stream)} / $max"
                })
            }
        }

        val outputs = runCatching { am?.getDevices(AudioManager.GET_DEVICES_OUTPUTS) }.getOrNull().orEmpty()
        if (outputs.isNotEmpty()) {
            block("Output devices") {
                outputs.forEach { d -> item(audioDeviceType(d.type), audioDeviceDetail(d)) }
            }
        }
        val inputs = runCatching { am?.getDevices(AudioManager.GET_DEVICES_INPUTS) }.getOrNull().orEmpty()
        if (inputs.isNotEmpty()) {
            block("Input devices") {
                inputs.forEach { d -> item(audioDeviceType(d.type), audioDeviceDetail(d)) }
            }
        }
    }
}

private fun audioDeviceDetail(d: AudioDeviceInfo): String = listOfNotNull(
    d.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() },
    d.sampleRates.takeIf { it.isNotEmpty() }?.let { "${it.min()}~${it.max()} Hz" },
    d.channelCounts.takeIf { it.isNotEmpty() }?.let { "${it.max()} channels" },
).joinToString(" · ").ifEmpty { "Connected" }

private val VOLUME_STREAMS = listOf(
    AudioManager.STREAM_MUSIC to "Media",
    AudioManager.STREAM_RING to "Ringtone",
    AudioManager.STREAM_ALARM to "Alarm",
    AudioManager.STREAM_NOTIFICATION to "Notification",
    AudioManager.STREAM_SYSTEM to "System",
    AudioManager.STREAM_VOICE_CALL to "Call",
)

private fun audioDeviceType(type: Int): String = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth audio"
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth call audio"
    AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
    AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
    AudioDeviceInfo.TYPE_HDMI -> "HDMI"
    AudioDeviceInfo.TYPE_AUX_LINE -> "AUX"
    AudioDeviceInfo.TYPE_LINE_ANALOG -> "Analog line"
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Digital line"
    AudioDeviceInfo.TYPE_FM -> "FM radio"
    AudioDeviceInfo.TYPE_FM_TUNER -> "FM tuner"
    AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
    AudioDeviceInfo.TYPE_BUS -> "Vehicle bus"
    AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote submix"
    else -> "Type $type"
}

private fun transportName(caps: NetworkCapabilities): String = when {
    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile network"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth tethering"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
    caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB tethering"
    else -> HwItem.UNKNOWN
}

private fun phoneType(v: Int?): String = when (v) {
    TelephonyManager.PHONE_TYPE_NONE -> "None"
    TelephonyManager.PHONE_TYPE_GSM -> "GSM"
    TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
    TelephonyManager.PHONE_TYPE_SIP -> "SIP"
    else -> HwItem.UNKNOWN
}

private fun simState(v: Int?): String = when (v) {
    TelephonyManager.SIM_STATE_ABSENT -> "No SIM"
    TelephonyManager.SIM_STATE_READY -> "Ready"
    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
    TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
    TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
    TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "SIM I/O error"
    TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Restricted"
    else -> HwItem.UNKNOWN
}

/** WifiManager.getConnectionInfo 给的是主机字节序的 int，要按小端拆回点分十进制。 */
private fun intToIp(raw: Int): String? {
    if (raw == 0) return null
    return (0..3).joinToString(".") { ((raw shr (it * 8)) and 0xFF).toString() }
}

/** `BluetoothStatusCodes.FEATURE_SUPPORTED`，API 33 才有。 */
private const val FEATURE_SUPPORTED = 10
