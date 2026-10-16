package com.dfdx047.dragonburst.ui.screens

import android.os.Build
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardType
import com.dfdx047.dragonburst.data.LanMode
import com.dfdx047.dragonburst.game.GameActivity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonburst.R
import com.dfdx047.dragonburst.data.AppLanguage
import com.dfdx047.dragonburst.data.DarkMode
import com.dfdx047.dragonburst.data.ThemeMode
import com.dfdx047.dragonburst.ui.LauncherViewModel
import com.dfdx047.dragonburst.ui.components.Level
import com.dfdx047.dragonburst.ui.components.SectionCard
import com.dfdx047.dragonburst.ui.components.SettingDropdown
import com.dfdx047.dragonburst.ui.components.SettingSegmented
import com.dfdx047.dragonburst.ui.components.SettingSwitch
import com.dfdx047.dragonburst.ui.components.StatusLine

/**
 * Aba Jogo: as opcoes do Burst Limit Recompiled (as mesmas do menu do jogo), os saves e
 * o online.
 */
@Composable
fun GameScreen(vm: LauncherViewModel) {
    val c = vm.config
    val context = LocalContext.current
    val pickSave = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.addSave(uri)
    }
    // O jogo pode ter exportado um save desde a ultima vez.
    LaunchedEffect(Unit) { vm.refreshSaves() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.settings_saved_note), Level.INFO)

        // ------------------------------------------------------------------ jogo
        // As novidades do Burst Limit Recompiled (README do projeto do PC).
        SectionCard(stringResource(R.string.settings_game), Icons.Filled.SportsEsports) {
            SettingSwitch(
                stringResource(R.string.settings_ki_charge),
                stringResource(R.string.settings_ki_charge_sub),
                checked = c.kiCharge,
            ) { on -> vm.updateConfig { it.copy(kiCharge = on) } }
            if (c.kiCharge) {
                SettingSegmented(
                    title = stringResource(R.string.settings_ki_charge_rate),
                    options = listOf(
                        10 to stringResource(R.string.speed_slow),
                        16 to stringResource(R.string.speed_normal),
                        25 to stringResource(R.string.speed_fast),
                    ),
                    selected = c.kiChargeRate,
                    onSelect = { v -> vm.updateConfig { it.copy(kiChargeRate = v) } },
                )
            }
            SettingSwitch(
                stringResource(R.string.settings_form_select),
                stringResource(R.string.settings_form_select_sub),
                checked = c.startForms,
            ) { on -> vm.updateConfig { it.copy(startForms = on) } }
            SettingSwitch(
                stringResource(R.string.settings_story_costumes),
                stringResource(R.string.settings_story_costumes_sub),
                checked = c.storyCostumes,
            ) { on -> vm.updateConfig { it.copy(storyCostumes = on) } }
            SettingSwitch(
                stringResource(R.string.settings_extra_stages),
                stringResource(R.string.settings_extra_stages_sub),
                checked = c.extraStages,
            ) { on -> vm.updateConfig { it.copy(extraStages = on) } }
            SettingSwitch(
                stringResource(R.string.settings_extra_characters),
                stringResource(R.string.settings_extra_characters_sub),
                checked = c.extraCharacters,
            ) { on -> vm.updateConfig { it.copy(extraCharacters = on) } }
            SettingDropdown(
                title = stringResource(R.string.settings_button_icons),
                subtitle = stringResource(R.string.settings_button_icons_sub),
                options = listOf(
                    "auto" to stringResource(R.string.icons_auto),
                    "xbox" to "Xbox",
                    "playstation" to "PlayStation",
                    "switch" to "Nintendo Switch",
                ),
                selected = c.buttonIcons,
                onSelect = { v -> vm.updateConfig { it.copy(buttonIcons = v) } },
            )
            SettingSwitch(
                stringResource(R.string.settings_menu_sounds),
                stringResource(R.string.settings_menu_sounds_sub),
                checked = c.menuSounds,
            ) { on -> vm.updateConfig { it.copy(menuSounds = on) } }
            SettingSwitch(
                stringResource(R.string.settings_online_branding),
                stringResource(R.string.settings_online_branding_sub),
                checked = c.onlineBranding,
            ) { on -> vm.updateConfig { it.copy(onlineBranding = on) } }
        }

        // ----------------------------------------------------------------- saves
        // Exportar/importar save do menu do jogo (Back + Start > SAVE): os .zip ficam
        // em saves/, dentro da app; daqui saem para Download/ e entram de fora.
        SectionCard(stringResource(R.string.saves_title), Icons.Filled.Save, subtitle = stringResource(R.string.saves_subtitle)) {
            Text(stringResource(R.string.saves_help), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { pickSave.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }) {
                Text(stringResource(R.string.saves_add))
            }
            if (vm.saves.isEmpty()) {
                Text(stringResource(R.string.saves_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            vm.saves.forEach { save ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(save.file.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.exportSave(save) }) {
                        Icon(Icons.Filled.Download, stringResource(R.string.saves_to_downloads))
                    }
                    IconButton(onClick = { vm.deleteSave(save) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                    }
                }
            }
        }

        // ---------------------------------------------------------------- online
        SectionCard(stringResource(R.string.settings_online), Icons.Filled.Public, subtitle = stringResource(R.string.settings_online_sub)) {
            OutlinedTextField(
                value = c.onlineName,
                onValueChange = { v -> vm.updateConfig { it.copy(onlineName = v.filter { ch -> ch.code in 32..126 }.take(15)) } },
                label = { Text(stringResource(R.string.settings_online_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SettingDropdown(
                title = stringResource(R.string.settings_input_delay),
                subtitle = stringResource(R.string.settings_input_delay_sub),
                options = listOf("2", "3", "4", "5", "6", "8").map { it to stringResource(R.string.frames_n, it.toInt()) } +
                    ("off" to stringResource(R.string.input_delay_game)),
                selected = c.onlineInputDelay,
                onSelect = { v -> vm.updateConfig { it.copy(onlineInputDelay = v) } },
            )
            StatusLine(stringResource(R.string.settings_online_status), Level.WARN)
            HorizontalDivider()
            // Rede local (metodo "Radmin VPN / LAN" do PC): UDP direto entre os IPs.
            SettingSegmented(
                title = stringResource(R.string.lan_title),
                subtitle = stringResource(R.string.lan_sub),
                options = listOf(
                    LanMode.OFF to stringResource(R.string.off),
                    LanMode.HOST to stringResource(R.string.lan_host),
                    LanMode.JOIN to stringResource(R.string.lan_join),
                ),
                selected = vm.lanMode,
                onSelect = vm::setLanMode,
            )
            if (vm.lanMode != LanMode.OFF) {
                val ownIp = remember(vm.lanMode) { GameActivity.localIpv4() }
                StatusLine(
                    if (ownIp != null) stringResource(R.string.lan_own_ip, ownIp) else stringResource(R.string.lan_no_ip),
                    if (ownIp != null) Level.INFO else Level.ERROR,
                )
            }
            if (vm.lanMode == LanMode.JOIN) {
                OutlinedTextField(
                    value = vm.lanHostIp,
                    onValueChange = vm::setLanHostIp,
                    label = { Text(stringResource(R.string.lan_host_ip)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
