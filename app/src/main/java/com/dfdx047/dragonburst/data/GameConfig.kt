package com.dfdx047.dragonburst.data

import java.io.File

/**
 * Opcoes do jogo, guardadas no burstlimit.toml que o runtime le no arranque
 * (rex::cvar::LoadConfig) - o mesmo ficheiro do PC, com os mesmos nomes de cvar do
 * Burst Limit Recompiled (README, "Settings"). O launcher escreve-o antes de abrir o
 * jogo; as mudancas feitas no menu do jogo (Back + Start) ficam no mesmo ficheiro e
 * o launcher le-as ao abrir.
 *
 * So entram opcoes que funcionam no Vulkan/Android (docs/COMPATIBILIDADE.md). Ficam
 * de fora: DLSS, FSR 4/3.1.5 da cena, FSR 2/3 do presenter, janela/ecra inteiro,
 * Discord, teclas do teclado e o ecra inicial do PC (o launcher faz esse papel).
 */
data class GameConfig(
    // Desempenho / imagem
    val resolutionScale: Int = 1,             // draw_resolution_scale_x/y 1-3 (720p x N)
    val presentEffect: String = "bilinear",   // present_effect bilinear|cas|fsr
    val fsrQuality: String = "auto",          // present_fsr_quality_mode auto|quality|balanced|performance|ultra_performance
    val fsrSharpness: Double = 0.2,           // present_fsr_sharpness_reduction 0-2 (0 = mais nitido)
    val casSharpness: Double = 0.0,           // present_cas_additional_sharpness 0-1
    val fxaa: String = "none",                // swap_post_effect none|fxaa|fxaa_extreme
    val anisotropic: Int = -1,                // anisotropic_override -1 (jogo) ou 1-5 (1x-16x)
    val frameRate: Int = 60,                  // frame_rate "30" (original), "60" ou "120"
    val frameskip: Boolean = false,           // dbz3_frameskip: jogo a 60, desenha 1 em 2 (patch 0023)
    val showFps: Boolean = false,             // debug_overlay
    val letterbox: Boolean = true,            // present_letterbox (16:9 com barras / esticar)
    // Efeitos do jogo (burstlimit_patches.cpp: desligados por omissao no PC)
    val depthOfField: Boolean = false,        // depth_of_field
    val glowBlur: Boolean = false,            // glow_blur
    val motionBlur: Boolean = false,          // motion_blur
    val softFilter: Boolean = false,          // soft_filter
    // Jogo
    val kiCharge: Boolean = false,            // ki_charge (segurar L3)
    val kiChargeRate: Int = 16,               // ki_charge_rate 10/16/25
    val vibration: Boolean = true,            // vibration
    val deadzone: Double = 0.0,               // left/right_stick_deadzone_percentage 0-0.5
    val buttonIcons: String = "auto",         // button_icons auto|xbox|playstation|switch
    val startForms: Boolean = true,           // start_forms (RB/LB na selecao)
    val storyCostumes: Boolean = true,        // story_costumes
    val extraStages: Boolean = true,          // extra_stages
    val extraCharacters: Boolean = true,      // extra_characters
    val modsEnabled: Boolean = true,          // mods_enabled
    val onlineBranding: Boolean = true,       // online_branding
    val menuSounds: Boolean = true,           // menu_sounds
    val texturePack: Boolean = false,         // texture_replace_enabled
    val texturePreload: Boolean = true,       // texture_replace_preload
    val textureDump: Boolean = false,         // texture_dump_enabled (<texture_folder>/dump)
    val texturePackName: String = "",         // texture_folder = textures/packs/<nome> (vazio = textures/)
    // Online
    val onlineName: String = "",              // online_name (max 15, ASCII)
    val onlineInputDelay: String = "4",       // online_input_delay 2-8 ou off
    // Depuracao / otimizacoes da GPU (patches do Dragon Fury, docs/ESTADO.md)
    val verboseLog: Boolean = false,          // log_level = "debug" (senao "info")
    val perfLogging: Boolean = false,         // dbz3_perf_logging + logs/gpu_stats.log
    // Ligadas: com o 0064 (o preto era o exp_adjust lido da word errada, nao estas
    // otimizacoes) a luta sai certa com elas e vai de 12-16 para 41-57 fps no RP5
    // (logs de 2026-10-15 08:05 / 08:10).
    val directResolve: Int = 1,               // dbz3_direct_resolve_texture 0/1
    val directResolveFast: Boolean = true,    // dbz3_direct_resolve_skip_memory
    val rtDirect: Boolean = true,             // dbz3_rt_direct (render targets sem transferencias)
    val specShaders: Boolean = true,          // dbz3_spec_texture_signs + dbz3_tex_implicit_lod
    val clearTo1x: Boolean = true,            // dbz3_clear_to_1x (Clear() do XDK direto em 1x)
    val nativePresent: Boolean = true,        // dbz3_bl_native_present (render nativo: imagem final direta)
    val nativeLinearDepth: Boolean = true,    // dbz3_bl_native_linear_depth (render nativo: profundidade linear por compute)
    val nativeIntoTexture: Boolean = true,    // dbz3_render_into_texture (passo 4: passes direto na textura)
    val nativePostLite: Boolean = true,       // dbz3_resolve_dedupe + dbz3_skip_null_draws + dbz3_bl_native_post_lite (passo 3) + dbz3_bl_native_contour/_downsample/_blur (3b)
) {
    fun toToml(): Map<String, Any> = linkedMapOf(
        "gpu_plugin" to "xenos",
        "gpu_allow_invalid_fetch_constants" to true,
        // O launcher do APP substitui o ecra inicial do PC.
        "start_screen" to false,
        "log_file" to AppPaths.lastRunLog.absolutePath,
        "draw_resolution_scale_x" to resolutionScale.coerceIn(1, MAX_RESOLUTION_SCALE),
        "draw_resolution_scale_y" to resolutionScale.coerceIn(1, MAX_RESOLUTION_SCALE),
        "present_effect" to presentEffect,
        "present_fsr_quality_mode" to fsrQuality,
        "present_fsr_sharpness_reduction" to fsrSharpness.coerceIn(0.0, 2.0),
        "present_cas_additional_sharpness" to casSharpness.coerceIn(0.0, 1.0),
        "swap_post_effect" to fxaa,
        "anisotropic_override" to anisotropic.coerceIn(-1, 5),
        "frame_rate" to frameRate.toString(),
        // Com o jogo a 60, desenha so metade dos frames (GPUs fracas, patch 0023).
        "dbz3_frameskip" to if (frameskip && frameRate == 60) 1 else 0,
        "debug_overlay" to showFps,
        "present_letterbox" to letterbox,
        "depth_of_field" to depthOfField,
        "glow_blur" to glowBlur,
        "motion_blur" to motionBlur,
        "soft_filter" to softFilter,
        "ki_charge" to kiCharge,
        "ki_charge_rate" to kiChargeRate.coerceIn(1, 100),
        "vibration" to vibration,
        "left_stick_deadzone_percentage" to deadzone.coerceIn(0.0, 1.0),
        "right_stick_deadzone_percentage" to deadzone.coerceIn(0.0, 1.0),
        "button_icons" to buttonIcons,
        "start_forms" to startForms,
        "story_costumes" to storyCostumes,
        "extra_stages" to extraStages,
        "extra_characters" to extraCharacters,
        "mods_enabled" to modsEnabled,
        "online_branding" to onlineBranding,
        "menu_sounds" to menuSounds,
        "texture_replace_enabled" to texturePack,
        "texture_replace_preload" to texturePreload,
        "texture_dump_enabled" to textureDump,
        "texture_folder" to if (texturePackName.isEmpty()) "" else
            java.io.File(AppPaths.texturesDir, "packs/$texturePackName").absolutePath,
        "online_name" to onlineName.filter { it.code in 32..126 }.take(15),
        "online_input_delay" to onlineInputDelay,
        "log_level" to if (verboseLog) "debug" else "info",
        "dbz3_perf_logging" to perfLogging,
        "dbz3_direct_resolve_texture" to directResolve.coerceIn(0, 1),
        "dbz3_direct_resolve_skip_memory" to directResolveFast,
        "dbz3_rt_direct" to rtDirect,
        // Mali: com os restauros ignorados o Goku (P1) sumia da imagem no autoteste de 16/10
        // 14:06; so nos Adreno ficam ligados ate o autoteste confirmar qual parte e.
        "dbz3_rt_elide_restores" to com.dfdx047.dragonburst.drivers.DriverRepository.isAdreno(),
        "dbz3_spec_texture_signs" to specShaders,
        "dbz3_tex_implicit_lod" to specShaders,
        // Transferencias profundidade -> cor do modo direto: fazem falta ao desfoque (autoteste
        // de 16/10 22:17 RP5 e 22:19 RG Cube); custam ~3 ms no RP5 e ~8 ms no Mali.
        "dbz3_rt_keep_dc" to true,
        "dbz3_clear_to_1x" to clearTo1x,
        "dbz3_bl_native_present" to nativePresent,
        "dbz3_bl_native_linear_depth" to nativeLinearDepth,
        "dbz3_resolve_dedupe" to nativePostLite,
        "dbz3_skip_null_draws" to nativePostLite,
        "dbz3_bl_native_post_lite" to nativePostLite,
        "dbz3_discard_full_overwrite" to nativePostLite,
        "dbz3_bl_native_contour" to nativePostLite,  // passo 3b (0080): contorno por compute
        "dbz3_bl_native_downsample" to nativePostLite,  // passo 3b (0083): reducao 640 por compute
        "dbz3_bl_native_blur" to nativePostLite,  // passo 3b (0084): os dois desfoques 640 por compute
        "dbz3_render_into_texture" to nativeIntoTexture,
        // Memoria do jogo num buffer normal (sem sparse binding): o RB2-Android e o
        // Skate3-Mobile desligam-no nos Adreno; menos um caminho com bugs de driver.
        "vulkan_sparse_shared_memory" to false,
        // O preto da luta era o tradutor SPIR-V a ler o exp_adjust da word 4 (LOD bias) em vez
        // da word 3 (patch 0064); o contorno do LOD bias volta a desligado.
        "dbz3_ignore_fetch_lod_bias" to false,
    )

    companion object {
        val EFFECTS = listOf("bilinear", "cas", "fsr")
        val FSR_QUALITY = listOf("auto", "quality", "balanced", "performance", "ultra_performance")
        val FXAA = listOf("none", "fxaa", "fxaa_extreme")
        val BUTTON_ICONS = listOf("auto", "xbox", "playstation", "switch")
        val INPUT_DELAYS = listOf("2", "3", "4", "5", "6", "8", "off")
        const val MAX_RESOLUTION_SCALE = 3

        fun load(file: File = AppPaths.userToml): GameConfig {
            val m = try {
                TomlLite.read(file)
            } catch (e: TomlLite.ParseError) {
                emptyMap()
            }
            val d = GameConfig()
            fun str(k: String, def: String) = (m[k] as? TomlLite.Value.Str)?.v ?: def
            fun bool(k: String, def: Boolean) = (m[k] as? TomlLite.Value.Bool)?.v ?: def
            fun int(k: String, def: Int) = when (val v = m[k]) {
                is TomlLite.Value.Num -> v.v.toInt()
                is TomlLite.Value.Dbl -> v.v.toInt()
                is TomlLite.Value.Str -> v.v.toIntOrNull() ?: def
                else -> def
            }
            fun dbl(k: String, def: Double) = when (val v = m[k]) {
                is TomlLite.Value.Num -> v.v.toDouble()
                is TomlLite.Value.Dbl -> v.v
                else -> def
            }
            return GameConfig(
                resolutionScale = int("draw_resolution_scale_x", d.resolutionScale).coerceIn(1, MAX_RESOLUTION_SCALE),
                // fsr2/fsr3/dlss vindos do PC caem para o FSR 1, que funciona no Vulkan.
                presentEffect = str("present_effect", d.presentEffect).let { if (it in EFFECTS) it else "fsr" },
                fsrQuality = str("present_fsr_quality_mode", d.fsrQuality).let { if (it in FSR_QUALITY) it else "auto" },
                fsrSharpness = dbl("present_fsr_sharpness_reduction", d.fsrSharpness),
                casSharpness = dbl("present_cas_additional_sharpness", d.casSharpness),
                fxaa = str("swap_post_effect", d.fxaa).let { if (it in FXAA) it else "none" },
                anisotropic = int("anisotropic_override", d.anisotropic),
                // 30 (original), 60 ou 120 (ecras de 120 Hz); 144/unlocked do PC caem para 120.
                frameRate = int("frame_rate", d.frameRate).let {
                    when {
                        it in 1..30 -> 30
                        it >= 100 -> 120
                        else -> 60
                    }
                },
                frameskip = int("dbz3_frameskip", 0) > 0,
                showFps = bool("debug_overlay", d.showFps),
                letterbox = bool("present_letterbox", d.letterbox),
                depthOfField = bool("depth_of_field", d.depthOfField),
                glowBlur = bool("glow_blur", d.glowBlur),
                motionBlur = bool("motion_blur", d.motionBlur),
                softFilter = bool("soft_filter", d.softFilter),
                kiCharge = bool("ki_charge", d.kiCharge),
                kiChargeRate = int("ki_charge_rate", d.kiChargeRate),
                vibration = bool("vibration", d.vibration),
                deadzone = dbl("left_stick_deadzone_percentage", d.deadzone).coerceIn(0.0, 0.5),
                buttonIcons = str("button_icons", d.buttonIcons).let { if (it in BUTTON_ICONS) it else "auto" },
                startForms = bool("start_forms", d.startForms),
                storyCostumes = bool("story_costumes", d.storyCostumes),
                extraStages = bool("extra_stages", d.extraStages),
                extraCharacters = bool("extra_characters", d.extraCharacters),
                modsEnabled = bool("mods_enabled", d.modsEnabled),
                onlineBranding = bool("online_branding", d.onlineBranding),
                menuSounds = bool("menu_sounds", d.menuSounds),
                texturePack = bool("texture_replace_enabled", d.texturePack),
                texturePreload = bool("texture_replace_preload", d.texturePreload),
                textureDump = bool("texture_dump_enabled", d.textureDump),
                texturePackName = str("texture_folder", "").let { folder ->
                    val packs = java.io.File(AppPaths.texturesDir, "packs").absolutePath + "/"
                    if (folder.startsWith(packs)) folder.removePrefix(packs).trimEnd('/') else ""
                },
                onlineName = str("online_name", d.onlineName),
                onlineInputDelay = str("online_input_delay", d.onlineInputDelay).let { if (it in INPUT_DELAYS) it else "4" },
                verboseLog = str("log_level", "info").let { it == "debug" || it == "trace" },
                perfLogging = bool("dbz3_perf_logging", d.perfLogging),
                directResolve = if (int("dbz3_direct_resolve_texture", d.directResolve) == 0) 0 else 1,
                directResolveFast = bool("dbz3_direct_resolve_skip_memory", d.directResolveFast),
                rtDirect = bool("dbz3_rt_direct", d.rtDirect),
                specShaders = bool("dbz3_spec_texture_signs", d.specShaders),
                clearTo1x = bool("dbz3_clear_to_1x", d.clearTo1x),
                nativePresent = bool("dbz3_bl_native_present", d.nativePresent),
                nativeLinearDepth = bool("dbz3_bl_native_linear_depth", d.nativeLinearDepth),
                nativePostLite = bool("dbz3_resolve_dedupe", d.nativePostLite),
                nativeIntoTexture = bool("dbz3_render_into_texture", d.nativeIntoTexture),
            )
        }

        fun save(config: GameConfig, file: File = AppPaths.userToml) {
            TomlLite.merge(file, config.toToml())
        }
    }
}

/**
 * Predefinicoes graficas pelo peso na GPU (regra fixa dos ports, guia 9.1): Leve para Mali e
 * GPUs de entrada, Equilibrado para Adreno 6xx (SD865), Alto para Adreno 7xx (8 Gen 1/2),
 * Ultra para 8 Gen 3 / 8 Elite.
 */
enum class GraphicsPreset { LIGHT, BALANCED, HIGH, ULTRA }

fun GameConfig.withPreset(p: GraphicsPreset): GameConfig {
    val fps60 = if (frameRate == 30) 60 else frameRate
    return when (p) {
        GraphicsPreset.LIGHT -> copy(
            resolutionScale = 1, frameRate = 30, frameskip = false, presentEffect = "bilinear",
            depthOfField = false, glowBlur = false, motionBlur = false, softFilter = false,
            fxaa = "none", anisotropic = -1,
        )
        GraphicsPreset.BALANCED -> copy(
            resolutionScale = 1, frameRate = fps60, depthOfField = true, glowBlur = true,
            motionBlur = false, softFilter = false, fxaa = "none", anisotropic = 3,
        )
        GraphicsPreset.HIGH -> copy(
            resolutionScale = 2, frameRate = fps60, depthOfField = true, glowBlur = true,
            motionBlur = true, softFilter = false, fxaa = "fxaa", anisotropic = 4,
        )
        GraphicsPreset.ULTRA -> copy(
            resolutionScale = 3, frameRate = fps60, depthOfField = true, glowBlur = true,
            motionBlur = true, softFilter = false, fxaa = "fxaa", anisotropic = 5,
        )
    }
}

/** A predefinicao que corresponde as opcoes atuais, ou null (personalizado). */
fun GameConfig.matchingPreset(): GraphicsPreset? = GraphicsPreset.entries.firstOrNull { p ->
    val t = withPreset(p)
    t.resolutionScale == resolutionScale && t.depthOfField == depthOfField && t.glowBlur == glowBlur &&
        t.motionBlur == motionBlur && t.softFilter == softFilter && t.fxaa == fxaa &&
        t.anisotropic == anisotropic && (p != GraphicsPreset.LIGHT || frameRate == 30) &&
        (p == GraphicsPreset.LIGHT || frameRate != 30)
}

/** Predefinicao recomendada para o SoC deste aparelho. */
fun recommendedPreset(): GraphicsPreset {
    val soc = (if (android.os.Build.VERSION.SDK_INT >= 31) android.os.Build.SOC_MODEL else "") +
        " " + android.os.Build.HARDWARE + " " + android.os.Build.BOARD
    val s = soc.uppercase(java.util.Locale.ROOT)
    return when {
        listOf("SM8750", "SM8650", "SM8735", "SM8845", "SM8850").any { it in s } -> GraphicsPreset.ULTRA
        listOf("SM8550", "SM8475", "SM8450", "SM8635", "SM7675").any { it in s } -> GraphicsPreset.HIGH
        listOf("SM8350", "SM8250", "SM8150", "SM7", "SM6", "KONA", "LAHAINA").any { it in s } -> GraphicsPreset.BALANCED
        else -> GraphicsPreset.LIGHT
    }
}

