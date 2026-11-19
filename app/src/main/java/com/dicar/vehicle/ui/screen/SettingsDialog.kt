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
import android.app.Activity
import android.content.Intent
import com.dicar.vehicle.AppLanguage
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
import com.dicar.vehicle.ui.localizedLabel
import androidx.compose.ui.res.stringResource
import com.dicar.vehicle.R
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

/** Settings: data source selection (F42 fallback), refresh rate (F40), and BYDAuto interface detection. */
@Composable
fun SettingsDialog(
    intervalMs: Long,
    sourceMode: SourceMode,
    probe: ProbeUiState,
    twin3dEnabled: Boolean,
    evOnly: Boolean,
    update: UpdateState,
    autoCheckUpdate: Boolean,
    floatingBlocks: Set<FloatingBlock>,
    floatingAlpha: Float,
    onIntervalChange: (Long) -> Unit,
    onSourceModeChange: (SourceMode) -> Unit,
    onTwin3dChange: (Boolean) -> Unit,
    onEvOnlyChange: (Boolean) -> Unit,
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.text_done)) } },
        title = { Text(stringResource(R.string.text_settings)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleSmall)
                listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.language_system),
                    AppLanguage.ENGLISH to stringResource(R.string.language_english),
                    AppLanguage.RUSSIAN to stringResource(R.string.language_russian),
                ).forEach { (language, label) ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = AppLanguage.selected(context) == language,
                            onClick = { (context as? Activity)?.let { AppLanguage.select(it, language) } },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = AppLanguage.selected(context) == language, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().selectable(selected = evOnly, onClick = { onEvOnlyChange(!evOnly) }), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_ev_only), modifier = Modifier.weight(1f))
                    Switch(checked = evOnly, onCheckedChange = null)
                }
                HorizontalDivider()
                Text(stringResource(R.string.text_data_source), style = MaterialTheme.typography.titleSmall)
                SourceMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == sourceMode, onClick = { onSourceModeChange(mode) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == sourceMode, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(mode.localizedLabel())
                    }
                }

                HorizontalDivider()
                Text(stringResource(R.string.fmt_refresh, sliderValue.roundToLong()), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = (it / 100f).roundToLong() * 100f },
                    onValueChangeFinished = { onIntervalChange(sliderValue.roundToLong()) },
                    valueRange = SettingsStore.MIN_INTERVAL_MS.toFloat()..SettingsStore.MAX_INTERVAL_MS.toFloat(),
                    steps = ((SettingsStore.MAX_INTERVAL_MS - SettingsStore.MIN_INTERVAL_MS) / 100 - 1).toInt(),
                )

                HorizontalDivider()
                Text(stringResource(R.string.text_digital_twin), style = MaterialTheme.typography.titleSmall)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = twin3dEnabled, onClick = { onTwin3dChange(!twin3dEnabled) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.text_3d_vehicle_model))
                        Text(
                            stringResource(R.string.desc_3d),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = twin3dEnabled, onCheckedChange = null)
                }

                HorizontalDivider()
                Text(stringResource(R.string.text_floating_overlay), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.text_choose_overlay_content_lower_opacity_blocks_less_of_the_view_changes_apply_immediately),
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
                        Text(block.localizedLabel())
                    }
                }
                var alpha by remember(floatingAlpha) { mutableFloatStateOf(floatingAlpha) }
                Text(stringResource(R.string.fmt_opacity, (alpha * 100).roundToLong()), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = alpha,
                    onValueChange = { alpha = it },
                    onValueChangeFinished = { onFloatingAlphaChange(alpha) },
                    valueRange = SettingsStore.MIN_FLOATING_ALPHA..1f,
                )

                HorizontalDivider()
                Text(stringResource(R.string.text_about), style = MaterialTheme.typography.titleSmall)
                MetricRow(stringResource(R.string.text_current_version), BuildConfig.VERSION_NAME)
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
                }) { Text(stringResource(R.string.fmt_source_code, PROJECT_URL)) }

                HorizontalDivider()
                Text(stringResource(R.string.text_developer_tools), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(R.string.desc_probe),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onRunProbe, enabled = probe != ProbeUiState.Running) {
                    if (probe == ProbeUiState.Running) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.text_probe_bydauto_interfaces))
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.text_close)) } },
        title = { Text(stringResource(R.string.text_bydauto_probe_results)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    probe.filePath?.let { stringResource(R.string.fmt_saved, it) } ?: stringResource(R.string.text_unable_to_save_file_showing_output_below),
                    style = MaterialTheme.typography.bodySmall,
                )
                SelectionContainer {
                    Text(
                        // The dialog shows only a preview; the file contains the full content.
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

/** Open-source project URL, accessible from settings. */
/**
 * Update checks are primarily manual. Automatic checks at startup default to off because
 * they contact GitHub, the app's only external network request.
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
            Text(stringResource(R.string.text_check_for_updates))
        }
        when (state) {
            is UpdateState.UpToDate -> Text(
                stringResource(R.string.text_up_to_date),
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
                stringResource(R.string.fmt_new_version, release.version) + (release.publishedOn?.let { " ($it)" } ?: ""),
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
            // Explain why only a version number is available when the fallback is used.
            if (release.viaFallback) {
                Text(
                    stringResource(R.string.desc_release),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Do not install directly in the app: that requires REQUEST_INSTALL_PACKAGES.
                // Open the release page so the user can download with the head-unit browser.
                TextButton(onClick = { onOpen(release.pageUrl) }) { Text(stringResource(R.string.text_view_release)) }
                release.apkUrl?.let { url ->
                    TextButton(onClick = { onOpen(url) }) { Text(stringResource(R.string.text_download_apk)) }
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
            Text(stringResource(R.string.text_check_on_startup))
            Text(
                stringResource(R.string.desc_update),
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
