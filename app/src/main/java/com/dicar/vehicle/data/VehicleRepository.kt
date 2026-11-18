package com.dicar.vehicle.data

import android.content.Context
import com.dicar.vehicle.R
import android.os.SystemClock
import android.util.Log
import com.dicar.vehicle.data.model.CommandResult
import com.dicar.vehicle.data.model.DataSourceType
import com.dicar.vehicle.data.model.VehicleCommand
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.data.model.withDerivedValues
import com.dicar.vehicle.data.source.VehicleDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Shared data entry point: select a source (BYDAuto then DiPlus in auto mode), poll [state],
 * and process controls with optimistic updates, debouncing and readback confirmation.
 * Failures are reported through [events]. Internal state is confined to [dispatcher].
 */
class VehicleRepository(
    private val context: Context,
    private val settings: SettingsStore,
    sources: List<VehicleDataSource>,
) {
    private val sources = sources.associateBy { it.type }

    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _state = MutableStateFlow(VehicleState())
    val state: StateFlow<VehicleState> = _state.asStateFlow()

    /** Command keys awaiting dispatch or readback, used by the UI to show synchronization. */
    private val _pendingKeys = MutableStateFlow<Set<String>>(emptySet())
    val pendingKeys: StateFlow<Set<String>> = _pendingKeys.asStateFlow()

    /** One-time messages such as control failures, shown in a Snackbar. */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    // ------------------------------------------------------------------
    // Polling lifecycle: the foreground service and UI hold references; polling stops when all release.
    // ------------------------------------------------------------------

    private var clients = 0
    private var pollJob: Job? = null

    @Synchronized
    fun acquire() {
        clients++
        if (pollJob?.isActive != true) {
            pollJob = scope.launch { pollLoop() }
        }
    }

    @Synchronized
    fun release() {
        clients = (clients - 1).coerceAtLeast(0)
        if (clients == 0) {
            pollJob?.cancel()
            pollJob = null
        }
    }

    private suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            val start = SystemClock.elapsedRealtime()
            pollOnce()
            val elapsed = SystemClock.elapsedRealtime() - start
            delay((settings.refreshIntervalMs.value - elapsed).coerceAtLeast(MIN_GAP_MS))
        }
    }

    private suspend fun pollOnce() {
        val source = resolveSource()
        val now = System.currentTimeMillis()
        val raw = if (source == null) {
            VehicleState(error = noSourceMessage(), timestamp = now)
        } else {
            try {
                source.read().withDerivedValues().withLatchedSignals().copy(source = source.type, timestamp = now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "read failed on ${source.type}", e)
                invalidateSource()
                VehicleState(source = source.type, error = context.getString(R.string.error_read, e.message ?: e.javaClass.simpleName), timestamp = now)
            }
        }
        _state.value = reconcilePending(raw)
    }

    // ------------------------------------------------------------------
    // Source selection
    // ------------------------------------------------------------------

    private var active: VehicleDataSource? = null
    private var resolvedFor: SourceMode? = null
    private var lastProbeAt = 0L

    private suspend fun resolveSource(): VehicleDataSource? {
        val mode = settings.sourceMode.value
        val now = SystemClock.elapsedRealtime()
        if (mode == resolvedFor && (active != null || now - lastProbeAt < REPROBE_INTERVAL_MS)) {
            return active
        }
        if (mode != resolvedFor) {
            active?.release()
        }
        resolvedFor = mode
        lastProbeAt = now
        val previous = active
        active = candidatesFor(mode).firstOrNull { source ->
            try {
                source.isAvailable()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "probe failed on ${source.type}", e)
                false
            }
        }
        Log.i(TAG, "mode=$mode -> source=${active?.type}")
        if (active?.type != previous?.type) sticky.clear() // Previously sent values are not reliable after a source change.
        return active
    }

    private fun candidatesFor(mode: SourceMode): List<VehicleDataSource> = when (mode) {
        SourceMode.AUTO -> listOf(DataSourceType.BYD_AUTO, DataSourceType.DI_PLUS)
        SourceMode.BYD_AUTO -> listOf(DataSourceType.BYD_AUTO)
        SourceMode.DI_PLUS -> listOf(DataSourceType.DI_PLUS)
        SourceMode.MOCK -> listOf(DataSourceType.MOCK)
    }.mapNotNull { sources[it] }

    /** On source read failure, re-probe on the next poll (auto mode may select the fallback). */
    private fun invalidateSource() {
        active = null
        lastProbeAt = 0L
    }

    // ------------------------------------------------------------------
    // Latch flashing indicators briefly after an on sample; one-second polling may otherwise
    // capture the off half-cycle and make the UI flicker.
    // ------------------------------------------------------------------

    private class SignalLatch(private val holdMs: Long) {
        private var lastTrueAt = 0L

        fun apply(raw: Boolean?): Boolean? {
            val now = SystemClock.elapsedRealtime()
            if (raw == true) {
                lastTrueAt = now
                return true
            }
            if (now - lastTrueAt < holdMs) return true
            return raw
        }
    }

    private val turnLeftLatch = SignalLatch(TURN_SIGNAL_HOLD_MS)
    private val turnRightLatch = SignalLatch(TURN_SIGNAL_HOLD_MS)

    private fun VehicleState.withLatchedSignals() = copy(
        turnLeft = turnLeftLatch.apply(turnLeft),
        turnRight = turnRightLatch.apply(turnRight),
    )

    private fun noSourceMessage(): String = when (settings.sourceMode.value) {
        SourceMode.AUTO -> context.getString(R.string.source_auto_missing)
        SourceMode.BYD_AUTO -> context.getString(R.string.source_byd_missing)
        SourceMode.DI_PLUS -> context.getString(R.string.source_diplus_missing)
        SourceMode.MOCK -> context.getString(R.string.source_mock_missing)
    }

    // ------------------------------------------------------------------
    // Controls: optimistic updates, debouncing and readback confirmation
    // ------------------------------------------------------------------

    private class Pending(val command: VehicleCommand) {
        var sent = false
        var deadline = Long.MAX_VALUE
    }

    private val pending = LinkedHashMap<String, Pending>()
    private val sticky = LinkedHashMap<String, VehicleCommand>()
    private val debounceJobs = HashMap<String, Job>()

    fun send(command: VehicleCommand) = send { command }

    /**
     * Build a command from current state including unconfirmed optimistic values.
     * [build] runs on the repository thread in tap order, so repeated increments accumulate
     * even when an HTTP read blocks the thread. Return null when no command is needed.
     */
    fun send(build: (VehicleState) -> VehicleCommand?) {
        scope.launch { build(_state.value)?.let(::enqueue) }
    }

    private fun enqueue(command: VehicleCommand) {
        // Keep sticky values: pending overrides them, and a failed new command falls back to the last sent value.
        pending[command.key] = Pending(command)
        _state.update { command.applyTo(it) }
        publishPendingKeys()
        debounceJobs.remove(command.key)?.cancel()
        debounceJobs[command.key] = scope.launch {
            delay(DEBOUNCE_MS)
            dispatch(command)
        }
    }

    private suspend fun dispatch(command: VehicleCommand) {
        val source = resolveSource()
        val result = if (source == null) {
            CommandResult.Unsupported(context.getString(R.string.source_unavailable))
        } else {
            try {
                source.execute(command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "execute failed: $command", e)
                CommandResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        Log.i(TAG, "execute $command -> $result")

        // A newer command with the same key replaced this one; let it handle the result.
        val entry = pending[command.key]?.takeIf { it.command == command } ?: return

        when (result) {
            CommandResult.Sent -> {
                if (command.expected == null) {
                    finish(command.key, context.getString(R.string.command_sent, context.commandLabel(command)))
                } else {
                    entry.sent = true
                    entry.deadline = SystemClock.elapsedRealtime() + CONFIRM_TIMEOUT_MS
                    delay(READBACK_DELAY_MS)
                    pollOnce()
                }
            }
            is CommandResult.Unsupported -> finish(command.key, context.getString(R.string.command_unsupported, context.commandLabel(command), result.reason))
            is CommandResult.Failed -> finish(command.key, context.getString(R.string.command_failed, context.commandLabel(command), result.reason))
        }
    }

    /** Confirm or expire pending commands using the poll; unconfirmed commands retain optimistic values. */
    private fun reconcilePending(raw: VehicleState): VehicleState {
        if (pending.isEmpty() && sticky.isEmpty()) return raw
        var merged = raw

        // For fields without readback, retain the last sent value until the source reports it.
        // Otherwise operations such as cycling seat levels would restart from N/A each time.
        sticky.values.removeAll { it.readBack(raw) != null }
        sticky.values.forEach { merged = it.applyTo(merged) }

        val now = SystemClock.elapsedRealtime()
        val iterator = pending.values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val command = entry.command
            if (!entry.sent) {
                merged = command.applyTo(merged) // Still inside the debounce window
                continue
            }
            val actual = command.readBack(raw)
            when {
                actual == command.expected -> iterator.remove()
                actual == null -> {
                    iterator.remove()
                    sticky[command.key] = command
                    merged = command.applyTo(merged)
                    _events.tryEmit(context.getString(R.string.command_unconfirmed, context.commandLabel(command)))
                }
                now > entry.deadline -> {
                    iterator.remove()
                    _events.tryEmit(context.getString(R.string.command_unchanged, context.commandLabel(command)))
                }
                else -> merged = command.applyTo(merged)
            }
        }
        publishPendingKeys()
        return merged
    }

    private fun finish(key: String, message: String) {
        pending.remove(key)
        publishPendingKeys()
        _events.tryEmit(message)
    }

    private fun publishPendingKeys() {
        _pendingKeys.value = pending.keys.toSet()
    }

    companion object {
        private const val TAG = "VehicleRepository"
        private const val MIN_GAP_MS = 50L
        private const val REPROBE_INTERVAL_MS = 10_000L
        private const val DEBOUNCE_MS = 350L
        private const val READBACK_DELAY_MS = 400L
        private const val CONFIRM_TIMEOUT_MS = 3_000L
        private const val TURN_SIGNAL_HOLD_MS = 2_500L
    }
}
