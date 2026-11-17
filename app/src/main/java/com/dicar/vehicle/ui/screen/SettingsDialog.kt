package com.dicar.vehicle.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.Checkbox
import androidx.compose.ui.platform.LocalContext
import com.dicar.vehicle.data.FloatingBlock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import com.dicar.vehicle.BuildConfig
import com.dicar.vehicle.data.update.UpdateState
import com.dicar.vehicle.ui.components.MetricRow
import com.dicar.vehicle.data.SettingsStore
import com.dicar.vehicle.data.SourceMode
import com.dicar.vehicle.ui.ProbeUiState
import kotlin.math.roundToLong

/** 设置：数据源切换（F42 降级）、刷新频率（F40）、BYDAuto 接口探测。 */
@Composable
fun SettingsDialog(
    intervalMs: Long,
    sourceMode: SourceMode,
    probe: ProbeUiState,
    twin3dEnabled: Boolean,
    update: UpdateState,
    autoCheckUpdate: Boolean,
    floatingBlocks: Set<FloatingBlock>,
    floatingAlpha: Float,
    onIntervalChange: (Long) -> Unit,
    onSourceModeChange: (SourceMode) -> Unit,
    onTwin3dChange: (Boolean) -> Unit,
    onCheckUpdate: () -> Unit,
    onAutoCheckUpdateChange: (Boolean) -> Unit,
    onToggleFloatingBlock: (FloatingBlock) -> Unit,
    onFloatingAlphaChange: (Float) -> Unit,
    onRunProbe: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var sliderValue by remember(intervalMs) { mutableFloatStateOf(intervalMs.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Settings") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Data source", style = MaterialTheme.typography.titleSmall)
                SourceMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == sourceMode, onClick = { onSourceModeChange(mode) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == sourceMode, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(mode.label)
                    }
                }

                HorizontalDivider()
                Text("Refresh interval: ${sliderValue.roundToLong()} ms", style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = (it / 100f).roundToLong() * 100f },
                    onValueChangeFinished = { onIntervalChange(sliderValue.roundToLong()) },
                    valueRange = SettingsStore.MIN_INTERVAL_MS.toFloat()..SettingsStore.MAX_INTERVAL_MS.toFloat(),
                    steps = ((SettingsStore.MAX_INTERVAL_MS - SettingsStore.MIN_INTERVAL_MS) / 100 - 1).toInt(),
                )

                HorizontalDivider()
                Text("Digital twin", style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = twin3dEnabled, onClick = { onTwin3dChange(!twin3dEnabled) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("3D vehicle model")
                        Text(
                            "Drag to rotate and pinch to zoom. Continuous GPU usage may slow navigation;" +
                                "when disabled, the default 2D top-down view is used.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = twin3dEnabled, onCheckedChange = null)
                }

                HorizontalDivider()
                Text("Floating overlay", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Choose overlay content. Lower opacity blocks less of the view; changes apply immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FloatingBlock.entries.forEach { block ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = block in floatingBlocks, onClick = { onToggleFloatingBlock(block) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = block in floatingBlocks, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text(block.label)
                    }
                }
                var alpha by remember(floatingAlpha) { mutableFloatStateOf(floatingAlpha) }
                Text("Opacity: ${(alpha * 100).roundToLong()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    onValueChangeFinished = { onFloatingAlphaChange(alpha) },
                    valueRange = SettingsStore.MIN_FLOATING_ALPHA..1f,
                )

                HorizontalDivider()
                Text("About", style = MaterialTheme.typography.titleSmall)
                MetricRow("Current version", BuildConfig.VERSION_NAME)
                UpdateSection(
                    state = update,
                    autoCheck = autoCheckUpdate,
                    onCheck = onCheckUpdate,
                    onAutoCheckChange = onAutoCheckUpdateChange,
                    onOpen = { url ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                )
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_URL))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }) { Text("Source code: $PROJECT_URL") }

                HorizontalDivider()
                Text("Developer tools", style = MaterialTheme.typography.titleSmall)
                Text(
                    "List BYDAuto*Device methods and constants and read no-argument getters;" +
                        "save the report for use with adb pull and BydApiMap.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRunProbe, enabled = probe != ProbeUiState.Running) {
                    if (probe == ProbeUiState.Running) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Probe BYDAuto interfaces")
                }
            }
        },
    )
}

@Composable
fun ProbeResultDialog(probe: ProbeUiState, onDismiss: () -> Unit) {
    if (probe !is ProbeUiState.Done) return
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("BYDAuto probe results") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    probe.filePath?.let { "Saved: $it" } ?: "Unable to save file; showing output below",
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer {
                    Text(
                        // 弹窗只显示前一部分，完整内容看文件
                        probe.report.take(20_000),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
    )
}

/** 项目开源地址，设置页里可以直接点开。 */
/**
 * 更新检查。手动点按为主，「启动时自动检查」默认关——检查会访问 GitHub，
 * 是全 App 唯一一处外部网络请求，不该在用户没表态时默默发出去。
 */
@Composable
private fun UpdateSection(
    state: UpdateState,
    autoCheck: Boolean,
    onCheck: () -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onCheck, enabled = state != UpdateState.Checking) {
            if (state == UpdateState.Checking) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("Check for updates")
        }
        when (state) {
            is UpdateState.UpToDate -> Text(
                "Up to date",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is UpdateState.Failed -> Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Unit
        }
    }

    if (state is UpdateState.Available) {
        val release = state.release
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "New version ${release.version}" + (release.publishedOn?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (release.notes.isNotBlank()) {
                Text(
                    release.notes.lineSequence().take(UPDATE_NOTE_LINES).joinToString("\n").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // 走了降级通道时要说明白为什么只有版本号，否则会被当成「这版没写说明」
            if (release.viaFallback) {
                Text(
                    "The GitHub API is unavailable. Only the version number could be retrieved; open the release page for notes and downloads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 不在 App 内直接下载安装：那需要 REQUEST_INSTALL_PACKAGES 权限，
                // 对一个自用工具来说不值当。跳到发布页用车机浏览器下载即可。
                TextButton(onClick = { onOpen(release.pageUrl) }) { Text("View release") }
                release.apkUrl?.let { url ->
                    TextButton(onClick = { onOpen(url) }) { Text("Download APK") }
                }
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = autoCheck, onClick = { onAutoCheckChange(!autoCheck) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Check on startup")
            Text(
                "The update check is the app's only external network request. It sends one anonymous request" +
                    "without any vehicle data. When disabled, it connects only when checked manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = autoCheck, onCheckedChange = null)
    }
}

private const val UPDATE_NOTE_LINES = 6

const val PROJECT_URL = "https://github.com/IlyaKochetov/dicar2"
