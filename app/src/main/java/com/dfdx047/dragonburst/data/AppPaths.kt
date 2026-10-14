package com.dfdx047.dragonburst.data

import android.content.Context
import java.io.File

/**
 * Pastas da app. A pasta de dados e a mesma que o GameActivity passa ao runtime
 * em REX_APP_DIR (getExternalFilesDir). No Android o SDK trata-a como a "pasta do
 * executavel" (filesystem_posix.cpp), por isso o layout e o mesmo do PC, ao lado
 * do burstlimit.exe:
 *
 *   <appDir>/game_data_root/    jogo (default.xex + LONG2DATA/)
 *   <appDir>/burstlimit.toml    configuracoes do runtime e do jogo (ver GameConfig)
 *   <appDir>/mods/              mods (uma pasta por mod, mods.toml com os ligados)
 *   <appDir>/textures/replace/  pack de texturas (PNG/DDS)
 *   <appDir>/saves/             saves exportados (.zip)
 *   <appDir>/cvars.txt          overrides manuais para testes (GameActivity)
 *   <appDir>/driver/            driver Vulkan ativo (o GameActivity copia-o para a pasta interna)
 *   <appDir>/drivers/           drivers instalados (um por pasta)
 *   <appDir>/logs/              log da ultima sessao e capturas de frames
 */
object AppPaths {
    lateinit var appDir: File
        private set
    lateinit var internalDir: File
        private set
    lateinit var cacheDir: File
        private set

    fun init(context: Context) {
        appDir = (context.getExternalFilesDir(null) ?: context.filesDir).also { it.mkdirs() }
        internalDir = context.filesDir
        cacheDir = context.cacheDir
    }

    val gameDir: File get() = File(appDir, "game_data_root")
    val xexFile: File get() = File(gameDir, "default.xex")
    val dataDir: File get() = File(gameDir, "LONG2DATA")
    val modsDir: File get() = File(appDir, "mods")
    val texturesDir: File get() = File(appDir, "textures")
    val textureReplaceDir: File get() = File(texturesDir, "replace")
    val savesDir: File get() = File(appDir, "saves")
    val userToml: File get() = File(appDir, "burstlimit.toml")
    val logsDir: File get() = File(appDir, "logs")
    val lastRunLog: File get() = File(logsDir, "dragonburst_last.log")
    val driversDir: File get() = File(appDir, "drivers")
    val activeDriverDir: File get() = File(appDir, "driver")
    val controlsFile: File get() = File(internalDir, "controls/layout_v1.json")
    val controlsMenuFile: File get() = File(internalDir, "controls/layout_menu_v1.json")
    /** Camara livre / modo foto (comando completo). */
    val controlsMapFile: File get() = File(internalDir, "controls/layout_camera_v1.json")
    val updatesDir: File get() = File(cacheDir, "updates")
}
