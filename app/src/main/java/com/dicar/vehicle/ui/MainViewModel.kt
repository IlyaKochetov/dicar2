package com.dicar.vehicle.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dicar.vehicle.appContainer
import com.dicar.vehicle.data.FloatingBlock
import com.dicar.vehicle.data.SourceMode
import com.dicar.vehicle.data.model.AcCycleMode
import com.dicar.vehicle.data.model.AcWindMode
import com.dicar.vehicle.data.model.GlassZone
import com.dicar.vehicle.data.model.QuickAction
import com.dicar.vehicle.data.model.VehicleCommand
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.data.model.Zone
import com.dicar.vehicle.data.hardware.HardwareMonitor
import com.dicar.vehicle.data.hardware.HwSection
import com.dicar.vehicle.data.hardware.LiveStats
import com.dicar.vehicle.data.update.UpdateState
import com.dicar.vehicle.ui.components.AcActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ProbeUiState {
    data object Idle : ProbeUiState
    data object Running : ProbeUiState
    data class Done(val filePath: String?, val report: String) : ProbeUiState
}

sealed interface HardwareExportState {
    data object Idle : HardwareExportState
    data class Done(val filePath: String?, val text: String, val redact: Boolean) : HardwareExportState
}

class MainViewModel(app: Application) : AndroidViewModel(app), AcActions {

    private val container = app.appContainer
    private val repository = container.repository
    private val settings = container.settings

    val state = repository.state
    val pendingKeys = repository.pendingKeys
    val events = repository.events
    val refreshIntervalMs = settings.refreshIntervalMs
    val sourceMode = settings.sourceMode

    private val _probe = MutableStateFlow<ProbeUiState>(ProbeUiState.Idle)
    val probe: StateFlow<ProbeUiState> = _probe.asStateFlow()

    /** Rolling chart samples, appended once per poll. */
    data class HistorySample(
        val speed: Float?,
        val power: Float?,
        /** Primary RPM (engine when available, otherwise motor) for single-series charts. */
        val rpm: Float?,
        val soc: Float?,
        val electricRange: Float?,
        /** Engine RPM: hybrid vehicles provide a value only when the engine is active. */
        val engineRpm: Float?,
        /** Motor RPM (larger absolute value of front and rear), stored separately from [engineRpm]
         *  to avoid duplicating the primary RPM line on hybrids. */
        val motorRpm: Float?,
    ) {
        companion object {
            /** Main screen and floating window maintain separate chart buffers using this shared sampling logic. */
            fun of(s: VehicleState) = HistorySample(
                speed = s.speed,
                power = s.batteryPower ?: s.power,
                rpm = s.displayRpm?.toFloat(),
                soc = s.soc,
                electricRange = s.remainRangeElec?.toFloat(),
                engineRpm = s.engineRpm?.toFloat(),
                motorRpm = listOfNotNull(s.motorRpmFront, s.motorRpmRear)
                    .maxByOrNull { kotlin.math.abs(it) }?.toFloat(),
            )
        }
    }

    private val _history = MutableStateFlow<List<HistorySample>>(emptyList())
    val history: StateFlow<List<HistorySample>> = _history.asStateFlow()

    init {
        // The UI also holds a polling reference so the dashboard receives data if the foreground service cannot start.
        repository.acquire()

        viewModelScope.launch {
            var lastTimestamp = 0L
            state.collect { s ->
                // Sample only on polls; optimistic updates must not add false chart points.
                if (s.timestamp == lastTimestamp) return@collect
                lastTimestamp = s.timestamp
                _history.update { (it + HistorySample.of(s)).takeLast(HISTORY_SIZE) }
            }
        }

        checkUpdateOnStartIfEnabled()
    }

    override fun onCleared() {
        repository.release()
    }

    // ---------------- Climate / seat controls ----------------
    // Calculate increment and toggle targets on the repository thread from the latest state, including optimistic values.
    // Accumulate consecutive taps in order, then debounce them into one command.

    override fun toggleAc() = repository.send { s -> VehicleCommand.AcPower(s.acOn != true) }

    override fun toggleAcAuto() = repository.send { s -> VehicleCommand.AcAuto(s.acAuto != true) }

    override fun adjustTemp(zone: Zone, delta: Float) = repository.send { s ->
        val current = (if (zone == Zone.DRIVER) s.acTempDriver else s.acTempPassenger) ?: DEFAULT_TEMP
        val target = (current + delta).coerceIn(MIN_TEMP, MAX_TEMP)
        if (target != current) VehicleCommand.AcTemperature(zone, target) else null
    }

    override fun adjustFan(delta: Int) = repository.send { s ->
        val current = s.acFanLevel ?: 0
        val target = (current + delta).coerceIn(MIN_FAN, MAX_FAN)
        if (target != current) VehicleCommand.AcFanLevel(target) else null
    }

    override fun setWindMode(mode: AcWindMode) = repository.send(VehicleCommand.AcWind(mode))

    override fun toggleCycle() = repository.send { s ->
        VehicleCommand.AcCycle(if (s.acCycle == AcCycleMode.INNER) AcCycleMode.OUTER else AcCycleMode.INNER)
    }

    /** Seat heating/ventilation cycles off → 1 → maximum → off; the source determines the maximum. */
    override fun cycleSeatHeat(zone: Zone) = repository.send { s ->
        val current = if (zone == Zone.DRIVER) s.seatHeatDriver else s.seatHeatPassenger
        VehicleCommand.SeatHeat(zone, nextSeatLevel(current, s.seatMaxLevel))
    }

    override fun cycleSeatVent(zone: Zone) = repository.send { s ->
        val current = if (zone == Zone.DRIVER) s.seatVentDriver else s.seatVentPassenger
        VehicleCommand.SeatVent(zone, nextSeatLevel(current, s.seatMaxLevel))
    }

    private fun nextSeatLevel(current: Int?, max: Int): Int {
        val level = current ?: 0
        return if (level >= max) 0 else level + 1
    }

    override fun quick(action: QuickAction) = repository.send(VehicleCommand.Quick(action))

    override fun glass(zone: GlassZone, open: Boolean) = repository.send(VehicleCommand.Glass(zone, open))

    // ---------------- Settings ----------------

    fun setRefreshInterval(ms: Long) = settings.setRefreshInterval(ms)

    fun setSourceMode(mode: SourceMode) = settings.setSourceMode(mode)

    val evOnly = settings.evOnly
    fun setEvOnly(enabled: Boolean) = settings.setEvOnly(enabled)

    val twin3dEnabled = settings.twin3dEnabled

    fun setTwin3dEnabled(enabled: Boolean) = settings.setTwin3dEnabled(enabled)

    // ---------------- Update checks ----------------

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val autoCheckUpdate = settings.autoCheckUpdate

    fun setAutoCheckUpdate(enabled: Boolean) {
        settings.setAutoCheckUpdate(enabled)
        // Check immediately when enabled rather than waiting for the next launch.
        if (enabled && _update.value is UpdateState.Idle) checkUpdate()
    }

    fun checkUpdate() {
        if (_update.value == UpdateState.Checking) return
        _update.value = UpdateState.Checking
        viewModelScope.launch {
            _update.value = container.updateChecker.check()
        }
    }

    /** 启动时的自动检查。只在Settings里显式打开过才会联网。 */
    private fun checkUpdateOnStartIfEnabled() {
        if (settings.autoCheckUpdate.value) checkUpdate()
    }

    val floatingBlocks = settings.floatingBlocks
    val floatingAlpha = settings.floatingAlpha

    fun toggleFloatingBlock(block: FloatingBlock) = settings.toggleFloatingBlock(block)

    fun setFloatingAlpha(value: Float) = settings.setFloatingAlpha(value)

    // ---------------- Hardware information ----------------

    private val _hardware = MutableStateFlow<List<HwSection>>(emptyList())
    val hardware: StateFlow<List<HwSection>> = _hardware.asStateFlow()

    private val _hardwareLoading = MutableStateFlow(false)
    val hardwareLoading: StateFlow<Boolean> = _hardwareLoading.asStateFlow()

    private val _hardwareExport = MutableStateFlow<HardwareExportState>(HardwareExportState.Idle)
    val hardwareExport: StateFlow<HardwareExportState> = _hardwareExport.asStateFlow()

    /** Raw data used to rerender when the identifier-redaction setting changes. */
    private var lastExported: List<HwSection> = emptyList()

    /**
     * Live metrics at one sample per second.
     *
     * A cold flow samples only while the hardware screen is subscribed and stops on exit.
     * These readings traverse dozens of /sys nodes, so sampling an unseen screen is wasteful.
     */
    fun liveStats(): Flow<LiveStats> = flow {
        while (true) {
            emit(withContext(Dispatchers.IO) { container.hardwareMonitor.sample() })
            delay(HardwareMonitor.SAMPLE_INTERVAL_MS)
        }
    }

    /** Collect static information once; [force] refreshes it, for example after inserting a USB drive. */
    fun loadHardware(force: Boolean = false) {
        if (_hardwareLoading.value) return
        if (!force && _hardware.value.isNotEmpty()) return
        _hardwareLoading.value = true
        viewModelScope.launch {
            _hardware.value = withContext(Dispatchers.IO) { container.hardwareInspector.collectStatic() }
            _hardwareLoading.value = false
        }
    }

    fun exportHardware(sections: List<HwSection>) {
        if (sections.isEmpty()) return
        lastExported = sections
        writeExport(redact = true)
    }

    fun setExportRedact(redact: Boolean) = writeExport(redact)

    private fun writeExport(redact: Boolean) {
        val sections = lastExported.ifEmpty { return }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                container.hardwareInspector.export(sections, redact)
            }
            _hardwareExport.value = HardwareExportState.Done(result.file?.absolutePath, result.text, redact)
        }
    }

    fun dismissHardwareExport() {
        _hardwareExport.value = HardwareExportState.Idle
    }

    fun runProbe() {
        if (_probe.value == ProbeUiState.Running) return
        _probe.value = ProbeUiState.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { container.probe.run() }
            _probe.value = ProbeUiState.Done(result.file?.absolutePath, result.report)
        }
    }

    fun dismissProbe() {
        _probe.value = ProbeUiState.Idle
    }

    companion object {
        const val MIN_TEMP = 17f
        const val MAX_TEMP = 33f
        private const val DEFAULT_TEMP = 22f
        const val MIN_FAN = 1
        const val MAX_FAN = 7
        /** Number of chart samples retained (one per second, about three minutes). */
        const val HISTORY_SIZE = 180
    }
}
