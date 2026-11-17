package com.dicar.vehicle.data.hardware

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.os.Build
import java.util.UUID

/**
 * Codecs and DRM.
 *
 * List hardware codecs first and separate encoding from decoding; hardware resolution support
 * is relevant to playback performance on the head unit.
 */
fun collectCodecs(): HwSection {
    val codecs = runCatching {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList()
    }.getOrNull().orEmpty()

    val video = codecs.filter { it.supportedTypes.any { t -> t.startsWith("video/") } }
    val audio = codecs.filter { it.supportedTypes.any { t -> t.startsWith("audio/") } }

    return section(HwCategory.CODEC) {
        block {
            item("Codec count", "${codecs.size} codecs")
            item("Video", "${video.count { !it.isEncoder }} decoders / ${video.count { it.isEncoder }} encoders")
            item("Audio", "${audio.count { !it.isEncoder }} decoders / ${audio.count { it.isEncoder }} encoders")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                item("Hardware accelerated", "${codecs.count { it.isHardwareAccelerated }} codecs")
                item("Software only", "${codecs.count { it.isSoftwareOnly }} codecs")
            }
        }

        codecBlock("Video decoding", video.filter { !it.isEncoder })
        codecBlock("Video encoding", video.filter { it.isEncoder })
        codecBlock("Audio decoding", audio.filter { !it.isEncoder })
        codecBlock("Audio encoding", audio.filter { it.isEncoder })

        block("Digital rights (DRM)") {
            val widevine = drmProps(WIDEVINE)
            item("Widevine", if (widevine.isEmpty()) "Unsupported" else "Supported")
            widevine.forEach { (k, v) -> item("  $k", v) }
            item("PlayReady", if (drmProps(PLAYREADY).isEmpty()) "Unsupported" else "Supported")
            item("ClearKey", if (drmProps(CLEARKEY).isEmpty()) "Unsupported" else "Supported")
        }
    }
}

private fun SectionScope.codecBlock(title: String, list: List<MediaCodecInfo>) {
    if (list.isEmpty()) return
    block(title) {
        // List hardware codecs first to make hardware decoding capabilities easy to find.
        list.sortedWith(compareByDescending<MediaCodecInfo> { hardware(it) }.thenBy { it.name })
            .forEach { info -> item(info.name, codecSummary(info)) }
    }
}

private fun hardware(info: MediaCodecInfo): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.isHardwareAccelerated
    // Before Android 10, identify AOSP software codecs by OMX.google.* / c2.android.* names.
    else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")

private fun codecSummary(info: MediaCodecInfo): String {
    val type = info.supportedTypes.firstOrNull() ?: return HwItem.UNKNOWN
    val caps = runCatching { info.getCapabilitiesForType(type) }.getOrNull()
    val parts = mutableListOf(if (hardware(info)) "Hardware" else "Software")
    parts += type.substringAfter('/')

    caps?.videoCapabilities?.let { v ->
        runCatching {
            val w = v.supportedWidths.upper
            val h = v.supportedHeights.upper
            parts += "Up to $w×$h"
            parts += "${v.getSupportedFrameRatesFor(w, h).upper.toInt()} fps"
            // Avoid integer division producing zero for low-bitrate codecs such as H.263.
            parts += HwFormat.kbps(v.bitrateRange.upper / 1000)
        }
    }
    caps?.audioCapabilities?.let { a ->
        runCatching {
            a.supportedSampleRateRanges.lastOrNull()?.let { parts += "Up to ${it.upper} Hz" }
            parts += "${a.maxInputChannelCount} channels"
        }
    }
    caps?.profileLevels?.size?.takeIf { it > 0 }?.let { parts += "$it profiles" }
    runCatching { caps?.maxSupportedInstances }.getOrNull()?.takeIf { it > 0 }?.let { parts += "$it concurrent" }
    return parts.joinToString(" · ")
}

/** Read DRM scheme properties; return an empty map if the scheme constructor fails. */
private fun drmProps(uuid: UUID): Map<String, String> {
    if (!runCatching { MediaDrm.isCryptoSchemeSupported(uuid) }.getOrDefault(false)) return emptyMap()
    var drm: MediaDrm? = null
    return try {
        drm = MediaDrm(uuid)
        buildMap {
            DRM_PROPERTIES.forEach { (key, label) ->
                runCatching { drm.getPropertyString(key) }
                    .getOrNull()?.takeIf { it.isNotBlank() }
                    ?.let { put(label, it) }
            }
            runCatching { drm.connectedHdcpLevel }.getOrNull()?.let { put("HDCP level", hdcpName(it)) }
            runCatching { drm.maxHdcpLevel }.getOrNull()?.let { put("Maximum HDCP", hdcpName(it)) }
            runCatching { drm.maxSessionCount }.getOrNull()?.takeIf { it > 0 }
                ?.let { put("Maximum sessions", it.toString()) }
        }
    } catch (e: Throwable) {
        emptyMap()
    } finally {
        runCatching { drm?.close() }
    }
}

private fun hdcpName(level: Int): String = when (level) {
    MediaDrm.HDCP_NONE -> "None"
    MediaDrm.HDCP_NO_DIGITAL_OUTPUT -> "No digital output"
    MediaDrm.HDCP_V1 -> "v1"
    MediaDrm.HDCP_V2 -> "v2"
    MediaDrm.HDCP_V2_1 -> "v2.1"
    MediaDrm.HDCP_V2_2 -> "v2.2"
    MediaDrm.HDCP_V2_3 -> "v2.3"
    else -> HwItem.UNKNOWN
}

private val DRM_PROPERTIES = listOf(
    // L1 uses the TEE for the protected pipeline; L3 uses software and may have playback quality restrictions.
    "securityLevel" to "Security level",
    "version" to "Version",
    "vendor" to "Vendor",
    "description" to "Description",
    "algorithms" to "Algorithms",
    "systemId" to "System ID",
    "maxNumberOfSessions" to "Maximum sessions",
)

private val WIDEVINE = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
private val PLAYREADY = UUID(-0x65fb0f8667bfbd7aL, -0x546d19a41f77a06bL)
private val CLEARKEY = UUID(0x1077efecc0b24d02L, -0x531cc3e1ad1d04b5L)
