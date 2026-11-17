package com.dicar.vehicle.data.source.bydauto

import com.dicar.vehicle.data.source.diplus.DiPlusDataSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Vehicle status labels must be English. DiPlus parameter names and control
 * commands are intentionally kept in Chinese because they are wire-protocol data.
 */
class EnglishVehicleStatusTest {
    private val chinese = Regex("[\\u3400-\\u9FFF]")

    @Test
    fun bydAutoModeLabelsAreEnglish() {
        assertEquals("HEV", BydApiMap.WORK_MODE_LABELS[3])
        assertEquals("Eco", BydApiMap.DRIVE_MODE_LABELS[1])
        assertEquals("Sport", BydApiMap.DRIVE_MODE_LABELS[2])
        assertFalse(BydApiMap.WORK_MODE_LABELS.values.any { chinese.containsMatchIn(it) })
        assertFalse(BydApiMap.DRIVE_MODE_LABELS.values.any { chinese.containsMatchIn(it) })
    }

    @Test
    fun chargeStateLabelsAreEnglish() {
        assertEquals("Charging", BydApiMap.CHARGE_STATE_LABELS[1])
        assertEquals("Not charging", BydApiMap.CHARGE_STATE_LABELS[15])
        assertFalse(BydApiMap.CHARGE_STATE_LABELS.values.any { chinese.containsMatchIn(it) })
        assertFalse(BydApiMap.CHARGE_GUN_LABELS.values.any { chinese.containsMatchIn(it) })
    }

    @Test
    fun diPlusDisplayLabelsAreEnglishWithoutChangingProtocol() {
        assertEquals("Sport", DiPlusDataSource.DRIVE_MODE_LABELS[2])
        assertEquals("Charging", DiPlusDataSource.CHARGE_STATE_LABELS[2])
        assertFalse(DiPlusDataSource.WORK_MODE_LABELS.values.any { chinese.containsMatchIn(it) })
        assertFalse(DiPlusDataSource.DRIVE_MODE_LABELS.values.any { chinese.containsMatchIn(it) })
        assertFalse(DiPlusDataSource.CHARGE_STATE_LABELS.values.any { chinese.containsMatchIn(it) })
        // Chinese DiPlus field identifiers must remain unchanged for vehicle I/O.
        assertEquals(true, DiPlusDataSource.TEMPLATE.startsWith("Speed:{车速}|"))
    }
}
