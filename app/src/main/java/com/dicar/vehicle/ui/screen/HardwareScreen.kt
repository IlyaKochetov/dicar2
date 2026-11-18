package com.dicar.vehicle.ui.screen

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.dicar.vehicle.ui.hardwareText
import com.dicar.vehicle.ui.filterLocalizedSections
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.dicar.vehicle.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dicar.vehicle.data.hardware.HwCategory
import com.dicar.vehicle.data.hardware.HwFormat
import com.dicar.vehicle.data.hardware.HwItem
import com.dicar.vehicle.data.hardware.HwSection
import com.dicar.vehicle.data.hardware.LiveStats
import com.dicar.vehicle.data.hardware.batterySection
import com.dicar.vehicle.data.hardware.thermalSection
import com.dicar.vehicle.ui.HardwareExportState
import com.dicar.vehicle.ui.MainViewModel
import com.dicar.vehicle.ui.components.CoreRow
import com.dicar.vehicle.ui.components.CountBadge
import com.dicar.vehicle.ui.components.DataCard
import com.dicar.vehicle.ui.components.HwRow
import com.dicar.vehicle.ui.components.HwSubtitle
import com.dicar.vehicle.ui.components.ThinBar
import com.dicar.vehicle.ui.components.UsageTile
import com.dicar.vehicle.ui.components.loadColor
import com.dicar.vehicle.ui.components.tempColor

/**
 * Hardware information screen.
 *
 * Live metrics (utilization, frequency, temperature, memory) refresh every second.
 * The static inventory is collected once and provides search and category filtering.
 */
@Composable
fun HardwareScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val sections by viewModel.hardware.collectAsStateWithLifecycle()
    val loading by viewModel.hardwareLoading.collectAsStateWithLifecycle()
    val export by viewModel.hardwareExport.collectAsStateWithLifecycle()
    // The cold flow stops sampling when this screen is left.
    val liveFlow = remember { viewModel.liveStats() }
    val live by liveFlow.collectAsStateWithLifecycle(initialValue = null)

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<HwCategory?>(null) }

    LaunchedEffect(Unit) { viewModel.loadHardware() }

    // Temperature and battery cards are rebuilt from live samples; unchanged static categories
    // retain their objects and do not cause the whole list to recompose each second.
    val all = remember(sections, live) {
        if (sections.isEmpty()) emptyList()
        else {
            val derived = live?.let { listOf(thermalSection(it), batterySection(it.battery)) }.orEmpty()
            (sections + derived).sortedBy { it.category.ordinal }
        }
    }
    // Keyword-filtered results feed both category counts and the list; category filtering applies only to the list.
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val afterQuery = remember(all, query, configuration) { filterLocalizedSections(all, query, context) }
    val visible = remember(afterQuery, category) {
        afterQuery.filter { category == null || it.category == category }
    }
    // Clear a selected category excluded by the search results to avoid a permanently empty view.
    LaunchedEffect(afterQuery) {
        if (category != null && afterQuery.none { it.category == category }) category = null
    }

    Column(modifier.fillMaxSize()) {
        // Keep key readings in a narrow fixed strip; scroll detailed live cards with the list
        // so they do not crowd out the inventory on a 1200-pixel-high head unit.
        LiveStrip(live)
        Toolbar(
            query = query,
            onQuery = { query = it },
            category = category,
            onCategory = { category = it },
            sections = afterQuery,
            loading = loading,
            onRefresh = { viewModel.loadHardware(force = true) },
            onExport = { viewModel.exportHardware(all) },
        )
        when {
            loading && sections.isEmpty() -> Loading()
            visible.isEmpty() -> Empty(query)
            else -> SectionGrid(
                sections = visible,
                filtering = query.isNotBlank(),
                // Hide the live panel when searching or filtering; it is not a search result.
                live = live.takeIf { query.isBlank() && category == null },
            )
        }
    }

    HardwareExportDialog(export, viewModel::setExportRedact, viewModel::dismissHardwareExport)
}

// ---------------------------------------------------------------------------
// 实时面板
// ---------------------------------------------------------------------------

/** Persistent strip with key readings in one row. */
@Composable
private fun LiveStrip(live: LiveStats?) {
    val blocked = live != null && !live.systemLoadAvailable
    val load = if (blocked) live?.appLoad else live?.totalLoad
    val memPercent = live?.let { s ->
        val used = s.memUsed ?: return@let null
        val total = s.memTotal?.takeIf { it > 0 } ?: return@let null
        used.toFloat() / total * 100f
    }
    val hottest = live?.thermal?.mapNotNull { it.celsius }?.maxOrNull()

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveChip(if (blocked) stringResource(R.string.text_cpu_this_app) else "CPU", HwFormat.percent(load), loadColor(load))
        LiveChip(stringResource(R.string.text_memory), HwFormat.percent(memPercent), MaterialTheme.colorScheme.secondary)
        LiveChip(stringResource(R.string.text_memory_usage), HwFormat.usage(live?.memUsed, live?.memTotal))
        LiveChip("GPU", HwFormat.hz(live?.gpuHz))
        LiveChip(stringResource(R.string.text_max_temp), HwFormat.celsius(hottest), tempColor(hottest))
        LiveChip(stringResource(R.string.text_uptime), HwFormat.duration(live?.uptimeMs))
    }
}

@Composable
private fun LiveChip(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            hardwareText(value),
            style = MaterialTheme.typography.titleMedium,
            color = when {
                value == HwItem.UNKNOWN -> MaterialTheme.colorScheme.outline
                color != null -> color
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun LivePanel(live: LiveStats?) {
    BoxWithConstraints(Modifier.padding(bottom = 4.dp)) {
        val wide = maxWidth > 900.dp
        val cores = live?.cores.orEmpty()
        val hottest = live?.thermal?.mapNotNull { it.celsius }?.maxOrNull()

        // Android 11 restricts /proc/stat for ordinary apps, so global utilization can be unavailable.
        // Fall back to this app's utilization from /proc/self/stat and explain why.
        val blocked = live != null && !live.systemLoadAvailable

        @Composable
        fun cpuTile(modifier: Modifier) = DataCard(stringResource(R.string.text_live_cpu), modifier) {
            UsageTile(
                label = if (blocked) stringResource(R.string.text_app_usage) else stringResource(R.string.text_total_usage),
                headline = HwFormat.percent(if (blocked) live?.appLoad else live?.totalLoad),
                caption = buildString {
                    append(pluralStringResource(R.plurals.fmt_cores, cores.size, cores.count { it.online }, cores.size))
                    if (blocked) append(stringResource(R.string.msg_cpu_blocked))
                },
                fraction = (if (blocked) live?.appLoad else live?.totalLoad)?.div(100f),
            )
            if (cores.isNotEmpty()) {
                HorizontalDivider()
                // Keep all cores visible, even in a long list, to inspect big/little scheduling.
                cores.forEach { CoreRow(it) }
            }
        }

        @Composable
        fun memTile(modifier: Modifier) = DataCard(stringResource(R.string.text_live_memory), modifier) {
            UsageTile(
                label = stringResource(R.string.text_memory_used),
                headline = HwFormat.percent(
                    live?.let { s ->
                        val used = s.memUsed ?: return@let null
                        val total = s.memTotal ?: return@let null
                        used.toFloat() / total * 100f
                    }
                ),
                caption = HwFormat.usage(live?.memUsed, live?.memTotal),
                fraction = live?.let { s ->
                    val used = s.memUsed ?: return@let null
                    val total = s.memTotal?.takeIf { it > 0 } ?: return@let null
                    used.toFloat() / total
                },
                color = MaterialTheme.colorScheme.secondary,
            )
            HwRow(stringResource(R.string.text_available), HwFormat.bytes(live?.memAvailable))
            HwRow(stringResource(R.string.text_page_cache), HwFormat.bytes(live?.memCached))
            HwRow(stringResource(R.string.text_swap_used), HwFormat.bytes(
                live?.let { s ->
                    val t = s.swapTotal ?: return@let null
                    val f = s.swapFree ?: return@let null
                    t - f
                }
            ))
            HwRow(stringResource(R.string.text_app_heap), HwFormat.usage(live?.javaHeapUsed, live?.javaHeapMax))
            HwRow(stringResource(R.string.text_app_native_memory), HwFormat.bytes(live?.nativeHeapUsed))
            HwRow(stringResource(R.string.text_app_file_handles), live?.openFds?.toString() ?: HwItem.UNKNOWN)
            if (live?.lowMemory == true) {
                HwRow(stringResource(R.string.text_warning), stringResource(R.string.text_system_reports_low_memory))
            }
        }

        @Composable
        fun miscTile(modifier: Modifier) = DataCard(stringResource(R.string.text_graphics_temperature_power), modifier) {
            HwRow(stringResource(R.string.text_gpu_frequency), HwFormat.hz(live?.gpuHz))
            HwRow(stringResource(R.string.text_thermal_status), live?.thermalStatus ?: HwItem.UNKNOWN)
            UsageTile(
                label = stringResource(R.string.text_highest_temperature),
                headline = HwFormat.celsius(hottest),
                caption = live?.thermal?.maxByOrNull { it.celsius ?: -999f }?.type ?: stringResource(R.string.text_no_readable_temperature_sensor),
                // Use 100°C as full scale; a head-unit SoC at that temperature is likely throttling.
                fraction = hottest?.div(100f),
                color = tempColor(hottest),
            )
            HorizontalDivider()
            val battery = live?.battery
            if (battery?.present == true) {
                HwRow(stringResource(R.string.text_battery_level), HwFormat.percent(battery.levelPercent))
                HwRow(stringResource(R.string.text_status), battery.status)
                HwRow(stringResource(R.string.text_voltage), battery.voltageMv?.let { "$it mV" } ?: HwItem.UNKNOWN)
                HwRow(stringResource(R.string.text_battery_temperature), HwFormat.celsius(battery.temperature))
            } else {
                // Head units commonly use the vehicle's 12V supply without their own battery.
                HwRow(stringResource(R.string.text_battery), stringResource(R.string.text_no_internal_battery_vehicle_powered))
                HwRow(stringResource(R.string.text_power_supply), battery?.plugged ?: HwItem.UNKNOWN)
            }
            HorizontalDivider()
            HwRow(stringResource(R.string.text_uptime), HwFormat.duration(live?.uptimeMs))
            HwRow(stringResource(R.string.text_deep_sleep), HwFormat.duration(live?.let { it.uptimeMs - it.awakeMs }))
        }

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                cpuTile(Modifier.weight(1.3f))
                memTile(Modifier.weight(1f))
                miscTile(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cpuTile(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    memTile(Modifier.weight(1f))
                    miscTile(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Toolbar
// ---------------------------------------------------------------------------

@Composable
private fun Toolbar(
    query: String,
    onQuery: (String) -> Unit,
    category: HwCategory?,
    onCategory: (HwCategory?) -> Unit,
    sections: List<HwSection>,
    loading: Boolean,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                label = { Text(stringResource(R.string.text_search_models_properties_and_values)) },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text(stringResource(R.string.text_clear)) }
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.text_rescan))
            }
            OutlinedButton(onClick = onExport, enabled = sections.isNotEmpty()) { Text(stringResource(R.string.text_export)) }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val total = sections.sumOf { it.itemCount }
            CategoryChip(stringResource(R.string.fmt_all, total), category == null) { onCategory(null) }
            sections.forEach { s ->
                CategoryChip(
                    "${s.category.glyph} ${hardwareText(s.category.label)}（${s.itemCount}）",
                    category == s.category,
                ) { onCategory(if (category == s.category) null else s.category) }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        // Use accent blue for selection; green is reserved for battery level.
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

// ---------------------------------------------------------------------------
// 清单
// ---------------------------------------------------------------------------

@Composable
private fun SectionGrid(sections: List<HwSection>, filtering: Boolean, live: LiveStats?) {
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 400.dp),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalItemSpacing = 12.dp,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (live != null) {
            item(key = "live", span = StaggeredGridItemSpan.FullLine) { LivePanel(live) }
        }
        items(sections, key = { it.category.name }) { s -> SectionCard(s, filtering) }
    }
}

@Composable
private fun SectionCard(section: HwSection, filtering: Boolean) {
    DataCard(
        title = "${section.category.glyph} ${hardwareText(section.category.label)}",
        trailing = { CountBadge(section.itemCount) },
    ) {
        section.blocks.forEachIndexed { index, block ->
            if (index > 0) HorizontalDivider()
            block.subtitle?.let { HwSubtitle(hardwareText(it)) }
            // Show all search matches; otherwise truncate long lists such as system properties
            // to prevent one card from dominating the list.
            val cap = if (filtering) Int.MAX_VALUE else MAX_ROWS
            block.items.take(cap).forEach { HwRow(hardwareText(it.label), it.value) }
            if (block.items.size > cap) {
                HwRow("…", pluralStringResource(R.plurals.fmt_more, block.items.size - cap, block.items.size - cap))
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.text_scanning_hardware), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.text_the_initial_scan_enumerates_codecs_and_system_properties_and_may_take_a_few_seconds),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Empty(query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            if (query.isBlank()) stringResource(R.string.text_no_hardware_information_collected) else stringResource(R.string.fmt_no_results, query),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Export
// ---------------------------------------------------------------------------

@Composable
private fun HardwareExportDialog(
    state: HardwareExportState,
    onRedactChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (state !is HardwareExportState.Done) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.text_close)) } },
        title = { Text(stringResource(R.string.text_hardware_report_exported)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    state.filePath?.let { stringResource(R.string.fmt_saved, it) } ?: stringResource(R.string.text_file_write_failed_showing_report_below),
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.text_redact_device_identifiers), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.desc_redact),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = state.redact, onCheckedChange = onRedactChange)
                }
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        state.text.take(MAX_PREVIEW_CHARS),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
    )
}

/** Maximum rows per section without a search; system properties can number in the thousands. */
private const val MAX_ROWS = 40

/** Preview limit in the dialog; the file contains the full content. */
private const val MAX_PREVIEW_CHARS = 20_000
