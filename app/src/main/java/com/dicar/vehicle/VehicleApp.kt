package com.dicar.vehicle

import android.app.Application
import android.content.Context
import com.dicar.vehicle.data.SettingsStore
import com.dicar.vehicle.data.VehicleRepository
import com.dicar.vehicle.data.hardware.HardwareInspector
import com.dicar.vehicle.data.hardware.HardwareMonitor
import com.dicar.vehicle.data.source.bydauto.BydAutoDataSource
import com.dicar.vehicle.data.source.bydauto.BydAutoProbe
import com.dicar.vehicle.data.source.bydauto.adb.AdbTransport
import com.dicar.vehicle.data.source.diplus.DiPlusDataSource
import com.dicar.vehicle.data.source.mock.MockDataSource
import com.dicar.vehicle.data.update.UpdateChecker

class VehicleApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency injection; the app does not use Hilt or Koin. */
class AppContainer(context: Context) {
    val settings = SettingsStore(context)
    private val adbTransport = AdbTransport(context)
    private val bydAuto = BydAutoDataSource(context, adbTransport)
    val repository = VehicleRepository(
        settings = settings,
        sources = listOf(bydAuto, DiPlusDataSource(), MockDataSource()),
    )
    val probe = BydAutoProbe(context) { bydAuto.acquireAccess() }
    val updateChecker = UpdateChecker(currentVersion = BuildConfig.VERSION_NAME)
    val hardwareInspector = HardwareInspector(context)
    // Stateful: utilization requires the difference between consecutive /proc/stat samples, so share one instance.
    val hardwareMonitor = HardwareMonitor(context)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as VehicleApp).container
