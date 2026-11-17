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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.dicar.vehicle.data.hardware.filterSections
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
    val afterQuery = remember(all, query) { filterSections(all, query) }
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
        LiveChip(if (blocked) "CPU (this app)" else "CPU", HwFormat.percent(load), loadColor(load))
        LiveChip("Memory", HwFormat.percent(memPercent), MaterialTheme.colorScheme.secondary)
        LiveChip("Memory usage", HwFormat.usage(live?.memUsed, live?.memTotal))
        LiveChip("GPU", HwFormat.hz(live?.gpuHz))
        LiveChip("Max temp", HwFormat.celsius(hottest), tempColor(hottest))
        LiveChip("Uptime", HwFormat.duration(live?.uptimeMs))
    }
}

@Composable
private fun LiveChip(label: String, value: String, color: androidx.compose.ui.graphics.Color? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
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
        fun cpuTile(modifier: Modifier) = DataCard("Live CPU", modifier) {
            UsageTile(
                label = if (blocked) "App usage" else "Total usage",
                headline = HwFormat.percent(if (blocked) live?.appLoad else live?.totalLoad),
                caption = buildString {
                    append("${cores.count { it.online }} / ${cores.size} cores online")
                    if (blocked) append(" · system blocks /proc/stat; global CPU usage unavailable")
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
        fun memTile(modifier: Modifier) = DataCard("Live memory", modifier) {
            UsageTile(
                label = "Memory used",
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
            HwRow("Available", HwFormat.bytes(live?.memAvailable))
            HwRow("Page cache", HwFormat.bytes(live?.memCached))
            HwRow("Swap used", HwFormat.bytes(
                live?.let { s ->
                    val t = s.swapTotal ?: return@let null
                    val f = s.swapFree ?: return@let null
                    t - f
                }
            ))
            HwRow("App heap", HwFormat.usage(live?.javaHeapUsed, live?.javaHeapMax))
            HwRow("App native memory", HwFormat.bytes(live?.nativeHeapUsed))
            HwRow("App file handles", live?.openFds?.toString() ?: HwItem.UNKNOWN)
            if (live?.lowMemory == true) {
                HwRow("Warning", "System reports low memory")
            }
        }

        @Composable
        fun miscTile(modifier: Modifier) = DataCard("Graphics · temperature · power", modifier) {
            HwRow("GPU frequency", HwFormat.hz(live?.gpuHz))
            HwRow("Thermal status", live?.thermalStatus ?: HwItem.UNKNOWN)
            UsageTile(
                label = "Highest temperature",
                headline = HwFormat.celsius(hottest),
                caption = live?.thermal?.maxByOrNull { it.celsius ?: -999f }?.type ?: "No readable temperature sensor",
                // Use 100°C as full scale; a head-unit SoC at that temperature is likely throttling.
                fraction = hottest?.div(100f),
                color = tempColor(hottest),
            )
            HorizontalDivider()
            val battery = live?.battery
            if (battery?.present == true) {
                HwRow("Battery level", HwFormat.percent(battery.levelPercent))
                HwRow("Status", battery.status)
                HwRow("Voltage", battery.voltageMv?.let { "$it mV" } ?: HwItem.UNKNOWN)
                HwRow("Battery temperature", HwFormat.celsius(battery.temperature))
            } else {
                // Head units commonly use the vehicle's 12V supply without their own battery.
                HwRow("Battery", "No internal battery (vehicle-powered)")
                HwRow("Power supply", battery?.plugged ?: HwItem.UNKNOWN)
            }
            HorizontalDivider()
            HwRow("Uptime", HwFormat.duration(live?.uptimeMs))
            HwRow("Deep sleep", HwFormat.duration(live?.let { it.uptimeMs - it.awakeMs }))
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
                label = { Text("Search models, properties and values") },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Clear") }
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onRefresh, enabled = !loading) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Rescan")
            }
            OutlinedButton(onClick = onExport, enabled = sections.isNotEmpty()) { Text("Export") }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val total = sections.sumOf { it.itemCount }
            CategoryChip("All ($total)", category == null) { onCategory(null) }
            sections.forEach { s ->
                CategoryChip(
                    "${s.category.glyph} ${s.category.label}（${s.itemCount}）",
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
        title = "${section.category.glyph} ${section.category.label}",
        trailing = { CountBadge(section.itemCount) },
    ) {
        section.blocks.forEachIndexed { index, block ->
            if (index > 0) HorizontalDivider()
            block.subtitle?.let { HwSubtitle(it) }
            // Show all search matches; otherwise truncate long lists such as system properties
            // to prevent one card from dominating the list.
            val cap = if (filtering) Int.MAX_VALUE else MAX_ROWS
            block.items.take(cap).forEach { HwRow(it.label, it.value) }
            if (block.items.size > cap) {
                HwRow("…", "${block.items.size - cap} more items; use search or export to view")
            }
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Scanning hardware…", style = MaterialTheme.typography.bodyMedium)
            Text(
                "The initial scan enumerates codecs and system properties and may take a few seconds",
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
            if (query.isBlank()) "No hardware information collected" else "No results for \"$query\"",
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
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Hardware report exported") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    state.filePath?.let { "Saved: $it" } ?: "File write failed; showing report below",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Redact device identifiers", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Hide serial numbers, MAC/IP addresses, Android IDs and other identifiers." +
                                "Enabled by default for reports that may be shared.",
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
