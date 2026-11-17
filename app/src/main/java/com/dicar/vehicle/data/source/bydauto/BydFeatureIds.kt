package com.dicar.vehicle.data.source.bydauto

import android.util.Log

/**
 * Resolve BydApiMap FID symbols (such as "Statistic.STATISTIC_ELEC_PERCENTAGE") to values in this vehicle's firmware.
 *
 * BYDAutoFeatureIds has device-specific nested classes (Ac / Statistic / Speed ...),
 * often with copies on the root class. Search both; values vary by platform and require runtime resolution.
 */
class BydFeatureIds {

    private val cache = HashMap<String, Int?>()

    @Synchronized
    fun resolve(symbol: String): Int? {
        if (cache.containsKey(symbol)) return cache[symbol]
        val value = lookup(symbol)
        cache[symbol] = value
        if (value == null) Log.i(TAG, "fid symbol not found: $symbol")
        return value
    }

    private fun lookup(symbol: String): Int? {
        val nested = symbol.substringBefore('.', missingDelimiterValue = "")
        val field = symbol.substringAfter('.')
        if (nested.isNotEmpty()) {
            staticInt("${BydApiMap.FEATURE_IDS_CLASS}\$$nested", field)?.let { return it }
        }
        return staticInt(BydApiMap.FEATURE_IDS_CLASS, field)
    }

    private fun staticInt(className: String, field: String): Int? = try {
        (Class.forName(className).getField(field).get(null) as? Number)?.toInt()
    } catch (e: Throwable) {
        null
    }

    private companion object {
        const val TAG = "BydFeatureIds"
    }
}
