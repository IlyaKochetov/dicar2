package com.dicar.vehicle.data.source

import com.dicar.vehicle.data.model.CommandResult
import com.dicar.vehicle.data.model.DataSourceType
import com.dicar.vehicle.data.model.VehicleCommand
import com.dicar.vehicle.data.model.VehicleState

/**
 * Common data source interface. Implementation contract:
 * - The repository calls methods serially on one IO thread; blocking reflection or HTTP is allowed without switching threads.
 * - Set individual unavailable fields to null rather than throwing.
 * - If the entire source fails (class loading or connection failure), [read] throws so the repository can re-probe and switch in automatic mode.
 * - The repository fills source and timestamp on the state returned by [read].
 */
interface VehicleDataSource {
    val type: DataSourceType

    /** Probe availability in the current environment; may be called repeatedly. */
    suspend fun isAvailable(): Boolean

    suspend fun read(): VehicleState

    suspend fun execute(command: VehicleCommand): CommandResult

    fun release() {}
}
