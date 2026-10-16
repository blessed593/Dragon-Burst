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

/** Aba Configuracoes: o proprio aplicativo (tema, idioma, atualizacoes). */
@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val c = vm.config
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.settings_saved_note), Level.INFO)

        // -------------------------------------------------------------- aplicacao
        SectionCard(stringResource(R.string.settings_app), Icons.Filled.Palette) {
            SettingSegmented(
                title = stringResource(R.string.settings_theme),
                subtitle = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) stringResource(R.string.settings_theme_material_unavailable) else null,
                options = listOf(
                    ThemeMode.DRAGON to "Dragon Burst",
                    ThemeMode.MATERIAL_YOU to "Material You",
                ),
                selected = vm.themeMode,
                onSelect = vm::setTheme,
            )
            SettingSegmented(
                title = stringResource(R.string.settings_dark),
                options = listOf(
                    DarkMode.SYSTEM to stringResource(R.string.dark_system),
                    DarkMode.LIGHT to stringResource(R.string.dark_light),
                    DarkMode.DARK to stringResource(R.string.dark_dark),
                ),
                selected = vm.darkMode,
                onSelect = vm::setDark,
            )
            SettingSegmented(
                title = stringResource(R.string.settings_language),
                subtitle = stringResource(R.string.settings_language_sub),
                options = listOf(
                    AppLanguage.SYSTEM to stringResource(R.string.lang_system),
                    AppLanguage.PORTUGUESE to "Português",
                    AppLanguage.ENGLISH to "English",
                    AppLanguage.SPANISH to "Español",
                ),
                selected = vm.appLanguage,
                onSelect = vm::setLanguage,
            )
            SettingSwitch(
                stringResource(R.string.settings_check_updates),
                checked = vm.checkUpdatesOnStart,
                onChange = vm::setCheckUpdatesOnStart,
            )
        }
    }
}
