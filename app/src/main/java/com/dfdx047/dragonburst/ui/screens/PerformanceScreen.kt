package com.dfdx047.dragonburst.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonburst.R
import com.dfdx047.dragonburst.data.GameConfig
import com.dfdx047.dragonburst.data.GraphicsPreset
import com.dfdx047.dragonburst.data.matchingPreset
import com.dfdx047.dragonburst.data.recommendedPreset
import com.dfdx047.dragonburst.data.withPreset
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.remember
import com.dfdx047.dragonburst.ui.LauncherViewModel
import com.dfdx047.dragonburst.ui.components.Level
import com.dfdx047.dragonburst.ui.components.SectionCard
import com.dfdx047.dragonburst.ui.components.SettingDropdown
import com.dfdx047.dragonburst.ui.components.SettingSegmented
import com.dfdx047.dragonburst.ui.components.SettingSlider
import com.dfdx047.dragonburst.ui.components.SettingSwitch
import com.dfdx047.dragonburst.ui.components.StatusLine
import kotlin.math.roundToInt

/**
 * Aba Desempenho: tudo o que pesa na GPU (FPS, resolucao interna, efeitos do jogo,
 * filtros de imagem, proporcao e as otimizacoes do renderizador). Tudo vai para o
 * burstlimit.toml (GameConfig), lido pelo jogo no arranque; o menu do jogo (Back +
 * Start) mexe nas mesmas opcoes.
 */
@Composable
fun PerformanceScreen(vm: LauncherViewModel) {
    val c = vm.config

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusLine(stringResource(R.string.settings_saved_note), Level.INFO)

        // ------------------------------------------------------- predefinicoes
        val recommended = remember { recommendedPreset() }
        val current = c.matchingPreset()
        val presetNames = mapOf(
            GraphicsPreset.LIGHT to stringResource(R.string.preset_light),
            GraphicsPreset.BALANCED to stringResource(R.string.preset_balanced),
            GraphicsPreset.HIGH to stringResource(R.string.preset_high),
            GraphicsPreset.ULTRA to stringResource(R.string.preset_ultra),
        )
        SectionCard(
            stringResource(R.string.preset_title),
            Icons.Filled.Tune,
            subtitle = stringResource(R.string.preset_sub),
        ) {
            SettingSegmented(
                title = stringResource(R.string.preset_choose),
                options = GraphicsPreset.entries.map { it to presetNames.getValue(it) },
                selected = current,
                onSelect = { p -> if (p != null) vm.updateConfig { it.withPreset(p) } },
            )
            StatusLine(
                if (current == null) stringResource(R.string.preset_custom, presetNames.getValue(recommended))
                else stringResource(R.string.preset_recommended, presetNames.getValue(recommended)),
                if (current == recommended) Level.OK else Level.INFO,
            )
            if (current != recommended) {
                OutlinedButton(onClick = { vm.updateConfig { it.withPreset(recommended) } }) {
                    Text(stringResource(R.string.preset_apply_recommended, presetNames.getValue(recommended)))
                }
            }
        }

        // ------------------------------------------------------------ desempenho
        SectionCard(stringResource(R.string.settings_performance), Icons.Filled.Speed) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_api), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("Vulkan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(stringResource(R.string.settings_api_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            // frame_rate: o Burst Limit e um jogo de 30 FPS; o patch do PC leva-o a 60.
            SettingSegmented(
                title = stringResource(R.string.perf_frame_rate),
                subtitle = stringResource(R.string.perf_frame_rate_sub),
                options = listOf(30 to "30 FPS", 60 to "60 FPS", 120 to "120 FPS"),
                selected = c.frameRate,
                onSelect = { v -> vm.updateConfig { it.copy(frameRate = v) } },
            )
            if (c.frameRate == 120) StatusLine(stringResource(R.string.perf_fps_120_note), Level.INFO)
            if (c.frameRate == 60) {
                SettingSwitch(
                    stringResource(R.string.perf_frameskip),
                    stringResource(R.string.perf_frameskip_sub),
                    checked = c.frameskip,
                ) { on -> vm.updateConfig { it.copy(frameskip = on) } }
            }
            SettingSegmented(
                title = stringResource(R.string.settings_resolution),
                subtitle = stringResource(R.string.perf_resolution_sub),
                options = (1..GameConfig.MAX_RESOLUTION_SCALE).map { s -> s to "${s}x (${720 * s}p)" },
                selected = c.resolutionScale,
                onSelect = { v -> vm.updateConfig { it.copy(resolutionScale = v) } },
            )
            if (c.resolutionScale >= 2) StatusLine(stringResource(R.string.perf_resolution_mem), Level.INFO)
            if (c.resolutionScale >= 3) StatusLine(stringResource(R.string.settings_resolution_warn), Level.WARN)
            SettingSwitch(stringResource(R.string.settings_show_fps), checked = c.showFps) { on -> vm.updateConfig { it.copy(showFps = on) } }
        }

        // ------------------------------------------------------- efeitos do jogo
        // Desligados por omissao (como no PC): o jogo salta esses passes (draw overrides).
        SectionCard(
            stringResource(R.string.perf_effects_title),
            Icons.Filled.BlurOn,
            subtitle = stringResource(R.string.perf_effects_sub),
        ) {
            SettingSwitch(stringResource(R.string.fx_dof), stringResource(R.string.fx_dof_sub), checked = c.depthOfField) { on ->
                vm.updateConfig { it.copy(depthOfField = on) }
            }
            SettingSwitch(stringResource(R.string.fx_glow), stringResource(R.string.fx_glow_sub), checked = c.glowBlur) { on ->
                vm.updateConfig { it.copy(glowBlur = on) }
            }
            SettingSwitch(stringResource(R.string.fx_motion_blur), stringResource(R.string.fx_motion_blur_sub), checked = c.motionBlur) { on ->
                vm.updateConfig { it.copy(motionBlur = on) }
            }
            SettingSwitch(stringResource(R.string.fx_soft_filter), stringResource(R.string.fx_soft_filter_sub), checked = c.softFilter) { on ->
                vm.updateConfig { it.copy(softFilter = on) }
            }
        }

        // ---------------------------------------------------------------- imagem
        SectionCard(stringResource(R.string.settings_image), Icons.Filled.Wallpaper) {
            SettingSegmented(
                title = stringResource(R.string.settings_filter),
                subtitle = stringResource(R.string.settings_filter_sub),
                options = listOf("bilinear" to "Bilinear", "cas" to "AMD CAS", "fsr" to "AMD FSR 1"),
                selected = c.presentEffect,
                onSelect = { v -> vm.updateConfig { it.copy(presentEffect = v) } },
            )
            if (c.presentEffect == "fsr") {
                SettingDropdown(
                    title = stringResource(R.string.settings_fsr_mode),
                    subtitle = stringResource(R.string.settings_fsr_mode_sub),
                    options = listOf(
                        "auto" to stringResource(R.string.fsr_native),
                        "quality" to stringResource(R.string.fsr_quality),
                        "balanced" to stringResource(R.string.fsr_balanced),
                        "performance" to stringResource(R.string.fsr_performance),
                        "ultra_performance" to stringResource(R.string.fsr_ultra),
                    ),
                    selected = c.fsrQuality,
                    onSelect = { v -> vm.updateConfig { it.copy(fsrQuality = v) } },
                )
                if (c.fsrQuality != "auto" && c.resolutionScale < 2) {
                    StatusLine(stringResource(R.string.settings_fsr_mode_no_effect), Level.INFO)
                }
                SettingSlider(
                    title = stringResource(R.string.settings_fsr_sharpness),
                    value = (2.0 - c.fsrSharpness).toFloat(),
                    range = 0f..2f,
                    label = { v -> "${(v / 2f * 100).roundToInt()}%" },
                ) { v -> vm.updateConfig { it.copy(fsrSharpness = (2.0 - v).coerceIn(0.0, 2.0)) } }
            }
            if (c.presentEffect == "cas") {
                SettingSlider(
                    title = stringResource(R.string.settings_cas_sharpness),
                    value = c.casSharpness.toFloat(),
                    range = 0f..1f,
                    label = { v -> "${(v * 100).roundToInt()}%" },
                ) { v -> vm.updateConfig { it.copy(casSharpness = v.toDouble()) } }
            }
            HorizontalDivider()
            SettingDropdown(
                title = stringResource(R.string.settings_aa),
                options = listOf(
                    "none" to stringResource(R.string.off),
                    "fxaa" to "FXAA",
                    "fxaa_extreme" to "FXAA Extreme",
                ),
                selected = c.fxaa,
                onSelect = { v -> vm.updateConfig { it.copy(fxaa = v) } },
            )
            SettingDropdown(
                title = stringResource(R.string.settings_aniso),
                options = listOf(-1 to stringResource(R.string.aniso_game), 1 to "1x", 2 to "2x", 3 to "4x", 4 to "8x", 5 to "16x"),
                selected = c.anisotropic,
                onSelect = { v -> vm.updateConfig { it.copy(anisotropic = v) } },
            )
        }

        // ------------------------------------------------------------- proporcao
        SectionCard(stringResource(R.string.settings_aspect), Icons.Filled.PhotoSizeSelectLarge) {
            SettingSegmented(
                title = stringResource(R.string.settings_aspect_mode),
                options = listOf(true to stringResource(R.string.aspect_original), false to stringResource(R.string.aspect_stretch)),
                selected = c.letterbox,
                onSelect = { v -> vm.updateConfig { it.copy(letterbox = v) } },
            )
        }

    }
}
