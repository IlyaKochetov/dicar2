package com.dicar.vehicle.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.platform.LocalContext
import com.dicar.vehicle.service.FloatingWindowService
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.localizedVehicleValue
import com.dicar.vehicle.ui.localizedLabel
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dicar.vehicle.data.model.VehicleState
import com.dicar.vehicle.ui.MainViewModel
import com.dicar.vehicle.ui.components.AcPanel
import com.dicar.vehicle.ui.components.BatteryCard
import com.dicar.vehicle.ui.components.BodyCard
import com.dicar.vehicle.ui.components.GearBadge
import com.dicar.vehicle.ui.components.MiscCard
import com.dicar.vehicle.ui.components.PowerCard
import com.dicar.vehicle.ui.components.StatusDot
import com.dicar.vehicle.util.Format
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Dashboard views. The twin view is the default. */
enum class MainTab(val label: String, val glyph: String) {
    TWIN("Twin", "⬢"),
    CARDS("Cards", "▦"),
    HARDWARE("Hardware", "▣"),
}

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending by viewModel.pendingKeys.collectAsStateWithLifecycle()
    val intervalMs by viewModel.refreshIntervalMs.collectAsStateWithLifecycle()
    val sourceMode by viewModel.sourceMode.collectAsStateWithLifecycle()
    val probe by viewModel.probe.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val floatingOn by FloatingWindowService.isRunning.collectAsStateWithLifecycle()
    val floatingBlocks by viewModel.floatingBlocks.collectAsStateWithLifecycle()
    val floatingAlpha by viewModel.floatingAlpha.collectAsStateWithLifecycle()
    val twin3d by viewModel.twin3dEnabled.collectAsStateWithLifecycle()
    val update by viewModel.update.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(MainTab.TWIN) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val toggleFloating = {
            when {
                floatingOn -> FloatingWindowService.stop(context)
                FloatingWindowService.canDrawOverlay(context) -> FloatingWindowService.start(context)
                else -> context.startActivity(FloatingWindowService.overlayPermissionIntent(context))
            }
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth > 700.dp
            Row(Modifier.fillMaxSize()) {
                // Use side navigation on landscape head units and top segments on smaller or portrait screens.
                if (wide) {
                    SideRail(
                        tab = tab,
                        onTab = { tab = it },
                        floatingOn = floatingOn,
                        onToggleFloating = toggleFloating,
                        onSettings = { showSettings = true },
                    )
                }
                Column(Modifier.fillMaxSize()) {
                    StatusStrip(
                        state = state,
                        intervalMs = intervalMs,
                        compact = wide,
                        tab = tab,
                        onTab = { tab = it },
                        floatingOn = floatingOn,
                        onToggleFloating = toggleFloating,
                        onSettings = { showSettings = true },
                    )
                    state.error?.let { ErrorBanner(it) }
                    when (tab) {
                        MainTab.TWIN -> TwinPane(state, history, Modifier.fillMaxSize(), use3d = twin3d)
                        MainTab.CARDS -> CardsPane(state, pending, viewModel)
                        MainTab.HARDWARE -> HardwareScreen(viewModel, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            intervalMs = intervalMs,
            sourceMode = sourceMode,
            probe = probe,
            twin3dEnabled = twin3d,
            update = update,
            autoCheckUpdate = autoCheckUpdate,
            floatingBlocks = floatingBlocks,
            floatingAlpha = floatingAlpha,
            onIntervalChange = viewModel::setRefreshInterval,
            onSourceModeChange = viewModel::setSourceMode,
            onTwin3dChange = viewModel::setTwin3dEnabled,
            onCheckUpdate = viewModel::checkUpdate,
            onAutoCheckUpdateChange = viewModel::setAutoCheckUpdate,
            onToggleFloatingBlock = viewModel::toggleFloatingBlock,
            onFloatingAlphaChange = viewModel::setFloatingAlpha,
            onRunProbe = viewModel::runProbe,
            onDismiss = { showSettings = false },
        )
    }
    ProbeResultDialog(probe, onDismiss = viewModel::dismissProbe)
}

@Composable
private fun CardsPane(state: VehicleState, pending: Set<String>, viewModel: MainViewModel) {
    // Adaptive cards: about three columns at 1920 landscape width, one on portrait or phones.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 400.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "power") { PowerCard(state) }
        item(key = "battery") { BatteryCard(state) }
        item(key = "ac") {
            AcPanel(state, pending, actions = viewModel, tempStep = state.acTempStep)
        }
        item(key = "body") { BodyCard(state) }
        item(key = "misc") { MiscCard(state) }
    }
}

/** Left navigation for landscape head units, with large touch targets. */
@Composable
private fun SideRail(
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    floatingOn: Boolean,
    onToggleFloating: () -> Unit,
    onSettings: () -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Text(
                "DiCar",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        },
    ) {
        // Use accent blue for selection; Material's green secondaryContainer is reserved for battery level.
        val railColors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        )
        MainTab.entries.forEach { item ->
            NavigationRailItem(
                selected = tab == item,
                onClick = { onTab(item) },
                icon = { Text(item.glyph, fontSize = 20.sp) },
                label = { Text(item.localizedLabel()) },
                colors = railColors,
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = floatingOn,
            onClick = onToggleFloating,
            icon = { Text("◳", fontSize = 20.sp) },
            label = { Text(stringResource(R.string.text_overlay)) },
            colors = railColors,
        )
        NavigationRailItem(
            selected = false,
            onClick = onSettings,
            icon = { Text("⚙", fontSize = 20.sp) },
            label = { Text(stringResource(R.string.text_settings)) },
            colors = railColors,
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Top bar: vehicle state on the left; data source, outside temperature and time on the right.
 * When [compact] is true, navigation already appears on the left and is not repeated here.
 */
@Composable
private fun StatusStrip(
    state: VehicleState,
    intervalMs: Long,
    compact: Boolean,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    floatingOn: Boolean,
    onToggleFloating: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!compact) {
            SingleChoiceSegmentedButtonRow {
                MainTab.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = tab == item,
                        onClick = { onTab(item) },
                        shape = SegmentedButtonDefaults.itemShape(index, MainTab.entries.size),
                        // Use accent blue for selection and reserve green for battery level.
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) { Text(item.localizedLabel(), maxLines = 1) }
                }
            }
        }
        GearBadge(state.gear)
        val (powerText, powerColor) = powertrainStatus(state)
        StatusDot(powerText, powerColor)
        Text(
            modeSummary(state),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SourceChip(state, intervalMs)
        Text(
            stringResource(R.string.fmt_outside, Format.num(state.outsideTemp, 0, stringResource(R.string.unit_celsius))),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            rememberClock(),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
        if (compact) return@Row
        TextButton(onClick = onToggleFloating) {
            Text(if (floatingOn) stringResource(R.string.text_close_overlay) else stringResource(R.string.text_overlay), maxLines = 1)
        }
        TextButton(onClick = onSettings) { Text("⚙", maxLines = 1) }
    }
}

/** Top-bar power state: prefer engine data, fall back to motor data, otherwise show N/A. */
@Composable
private fun powertrainStatus(state: VehicleState): Pair<String, androidx.compose.ui.graphics.Color> {
    val scheme = MaterialTheme.colorScheme
    val motor = state.displayRpm
    return when {
        state.engineRpm != null && state.engineRpm > ENGINE_IDLE_RPM -> stringResource(R.string.text_engine_running) to scheme.primary
        state.engineRpm != null -> stringResource(R.string.text_engine_stopped) to scheme.outline
        motor != null && kotlin.math.abs(motor) > MOTOR_IDLE_RPM -> stringResource(R.string.text_motor_driving) to scheme.secondary
        motor != null -> stringResource(R.string.text_motor_idle) to scheme.outline
        else -> stringResource(R.string.fmt_powertrain, Format.NA) to scheme.outline
    }
}

/** Mode string such as HEV · Eco; show N/A if neither value is available. */
@Composable
private fun modeSummary(state: VehicleState): String =
    listOfNotNull(localizedVehicleValue(state.workMode), localizedVehicleValue(state.driveMode))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifEmpty { Format.NA }

/** Clock refreshed every 20 seconds; minute precision needs no faster refresh. */
@Composable
private fun rememberClock(): String {
    var text by remember { mutableStateOf(nowHhMm()) }
    LaunchedEffect(Unit) {
        while (true) {
            text = nowHhMm()
            delay(20_000)
        }
    }
    return text
}

private fun nowHhMm(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun SourceChip(state: VehicleState, intervalMs: Long) {
    val ok = state.source != null && state.error == null
    Text(
        text = stringResource(R.string.source_interval, state.source?.localizedLabel() ?: stringResource(R.string.text_no_data_source), intervalMs),
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        color = if (ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
        modifier = Modifier
            .background(
                if (ok) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(50),
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

private const val ENGINE_IDLE_RPM = 300
private const val MOTOR_IDLE_RPM = 50

@Composable
private fun ErrorBanner(message: String) {
    Text(
        text = stringResource(R.string.fmt_unavailable, message),
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}
