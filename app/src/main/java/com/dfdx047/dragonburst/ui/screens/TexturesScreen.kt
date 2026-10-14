package com.dfdx047.dragonburst.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonburst.R
import com.dfdx047.dragonburst.textures.RuntimeTextureLine
import com.dfdx047.dragonburst.textures.TextureIssue
import com.dfdx047.dragonburst.textures.TexturePackInfo
import com.dfdx047.dragonburst.textures.TexturePackReport
import com.dfdx047.dragonburst.textures.TexturePacks
import com.dfdx047.dragonburst.ui.LauncherViewModel
import com.dfdx047.dragonburst.ui.components.Level
import com.dfdx047.dragonburst.ui.components.Pill
import com.dfdx047.dragonburst.ui.components.SectionCard
import com.dfdx047.dragonburst.ui.components.SettingSwitch
import com.dfdx047.dragonburst.ui.components.StatusLine
import com.dfdx047.dragonburst.ui.components.formatBytes
import com.dfdx047.dragonburst.ui.theme.DfColors

/**
 * Packs de texturas HD do Burst Limit (pasta textures/replace do PC). Um pack de cada
 * vez, como no PC: o escolhido aqui vira o texture_folder do burstlimit.toml.
 */
@Composable
fun TexturesScreen(vm: LauncherViewModel) {
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.installTexturePackZip(uri)
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        vm.importTextureImages(uris)
    }
    var toDelete by remember { mutableStateOf<TexturePackInfo?>(null) }
    val packs = vm.texturePacks
    val active = vm.config.texturePackName
    val report = vm.textureReport

    LaunchedEffect(active, packs.size) {
        if (active.isNotEmpty() && report?.pack?.name != active && vm.busy == null) vm.validateTextures()
        vm.refreshRuntimeLog()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(stringResource(R.string.textures_title), Icons.Filled.Texture, subtitle = stringResource(R.string.textures_subtitle)) {
                SettingSwitch(
                    title = stringResource(R.string.textures_enabled),
                    subtitle = stringResource(R.string.textures_enabled_sub),
                    checked = vm.config.texturePack,
                ) { on -> vm.updateConfig { it.copy(texturePack = on) } }
                SettingSwitch(
                    title = stringResource(R.string.textures_preload),
                    subtitle = stringResource(R.string.textures_preload_sub),
                    checked = vm.config.texturePreload,
                    enabled = vm.config.texturePack,
                ) { on -> vm.updateConfig { it.copy(texturePreload = on) } }
                SettingSwitch(
                    title = stringResource(R.string.textures_dump),
                    subtitle = stringResource(R.string.textures_dump_sub),
                    checked = vm.config.textureDump,
                ) { on -> vm.updateConfig { it.copy(textureDump = on) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                        enabled = vm.busy == null,
                    ) {
                        Icon(Icons.Filled.FileOpen, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.textures_install_zip))
                    }
                    OutlinedButton(
                        onClick = { pickImages.launch(arrayOf("image/png", "image/x-dds", "image/vnd-ms.dds", "application/octet-stream")) },
                        enabled = vm.busy == null,
                    ) {
                        Icon(Icons.Filled.Image, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.textures_add_images))
                    }
                }
                OutlinedButton(onClick = { vm.validateTextures() }, enabled = vm.busy == null && active.isNotEmpty()) {
                    Icon(Icons.Filled.Checklist, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.textures_validate))
                }
            }
        }
        item {
            SectionCard(stringResource(R.string.textures_rules_title), Icons.Filled.Checklist) {
                Text(stringResource(R.string.textures_rules), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (packs.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.textures_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(packs, key = { it.name }) { pack ->
            PackCard(
                pack,
                selected = pack.name == active,
                report = report?.takeIf { it.pack.name == pack.name },
                onSelect = { vm.selectTexturePack(pack.name) },
                onDelete = { toDelete = pack },
            )
        }
        if (report != null && report.pack.name == active) {
            item {
                val over = report.decodedBytes > TexturePacks.RUNTIME_CACHE_BUDGET
                StatusLine(
                    stringResource(if (over) R.string.textures_ram_over else R.string.textures_ram, formatBytes(report.decodedBytes)),
                    if (over) Level.WARN else Level.INFO,
                )
            }
        }
        item {
            RuntimeLogCard(vm.runtimeTextureLog)
        }
    }

    toDelete?.let { pack ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(stringResource(R.string.mods_delete_title)) },
            text = { Text(stringResource(R.string.mods_delete_text, pack.name)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteTexturePack(pack)
                    toDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun issueText(i: TextureIssue): String = stringResource(
    when (i) {
        TextureIssue.OK -> R.string.tex_issue_ok
        TextureIssue.BAD_NAME -> R.string.tex_issue_bad_name
        TextureIssue.UNREADABLE -> R.string.tex_issue_unreadable
        TextureIssue.DDS_FORMAT -> R.string.tex_issue_dds
        TextureIssue.DUPLICATE -> R.string.tex_issue_duplicate
    },
)

@Composable
private fun PackCard(
    pack: TexturePackInfo,
    selected: Boolean,
    report: TexturePackReport?,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onSelect)
                Column(Modifier.weight(1f)) {
                    Text(pack.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(stringResource(R.string.mod_files, pack.fileCount), formatBytes(pack.sizeBytes)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
            if (report != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(stringResource(R.string.textures_ok_count, report.okCount), DfColors.Ok)
                    if (report.warnCount > 0) Pill(stringResource(R.string.textures_warn_count, report.warnCount), DfColors.Warn)
                    if (report.errorCount > 0) Pill(stringResource(R.string.textures_error_count, report.errorCount), DfColors.Error)
                }
                val status = when {
                    report.okCount == 0 -> stringResource(R.string.textures_status_none) to Level.ERROR
                    report.errorCount > 0 -> stringResource(R.string.textures_status_partial) to Level.WARN
                    else -> stringResource(R.string.textures_status_ok) to Level.OK
                }
                StatusLine(status.first, status.second)
                val issues = report.files.filter { it.issue != TextureIssue.OK }
                if (issues.isNotEmpty()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(stringResource(if (expanded) R.string.hide_details else R.string.show_details, issues.size))
                    }
                    if (expanded) {
                        issues.take(100).forEach { f ->
                            val lvl = if (f.issue == TextureIssue.DUPLICATE) Level.WARN else Level.ERROR
                            val extra = if (f.detail.isNotEmpty()) " (${f.detail})" else ""
                            StatusLine("${f.file.name}: ${issueText(f.issue)}$extra", lvl)
                        }
                        if (issues.size > 100) Text("+${issues.size - 100}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (selected) {
                Text(stringResource(R.string.textures_not_validated), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RuntimeLogCard(lines: List<RuntimeTextureLine>) {
    SectionCard(
        stringResource(R.string.textures_log_title),
        Icons.Filled.ReceiptLong,
        subtitle = stringResource(R.string.textures_log_subtitle),
    ) {
        if (lines.isEmpty()) {
            Text(stringResource(R.string.textures_log_empty), style = MaterialTheme.typography.bodySmall)
        } else {
            lines.take(60).forEach { l ->
                StatusLine(
                    l.text,
                    when (l.level) {
                        RuntimeTextureLine.Level.INFO -> Level.INFO
                        RuntimeTextureLine.Level.WARN -> Level.WARN
                        RuntimeTextureLine.Level.ERROR -> Level.ERROR
                    },
                )
            }
        }
    }
}
