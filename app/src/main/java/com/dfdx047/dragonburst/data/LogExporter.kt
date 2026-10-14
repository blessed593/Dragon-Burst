package com.dfdx047.dragonburst.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dfdx047.dragonburst.BuildConfig
import com.dfdx047.dragonburst.drivers.DriverRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Junta num .zip tudo o que ajuda a depurar: logcat da app (o jogo corre no mesmo
 * processo, por isso inclui as linhas `dbz3` do runtime), o log da ultima sessao,
 * o burstlimit.toml, o cvars.txt, o driver ativo e as propriedades do aparelho.
 * Grava em Download/ (visivel no PC por adb ou MTP).
 */
object LogExporter {

    private fun run(vararg cmd: String): String = try {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        out
    } catch (e: Exception) {
        "erro: ${e.message}"
    }

    private fun deviceReport(): String = buildString {
        appendLine("Dragon Burst ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Aparelho: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("SoC: ${DriverRepository.socName()}  Adreno: ${DriverRepository.isAdreno()}")
        appendLine("Hardware: ${Build.HARDWARE}  Board: ${Build.BOARD}")
        appendLine("Driver Vulkan ativo: ${DriverRepository.activeDir() ?: "sistema"}")
        appendLine("Drivers instalados: " + DriverRepository.installed().joinToString { it.name })
        appendLine()
        appendLine("Arquivos do jogo:")
        try {
            GameFiles.check().checks.forEach { appendLine("  ${it.name}: ${it.status}") }
        } catch (e: Exception) {
            appendLine("  erro: ${e.message}")
        }
        appendLine()
        appendLine("Mods:")
        val enabledMods = com.dfdx047.dragonburst.mods.ModRepository.enabledList().toSet()
        AppPaths.modsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }?.forEach { m ->
            appendLine("  ${m.name}${if (m.name in enabledMods) "" else " (desligado)"}")
        }
        appendLine()
        appendLine("Pacote de texturas ativo: " + (com.dfdx047.dragonburst.data.GameConfig.load().texturePackName.ifEmpty { "-" }))
        appendLine()
        appendLine("getprop (graficos):")
        run("getprop").lines().filter { l ->
            listOf("vulkan", "egl", "gfx", "gpu", "opengles", "hwui", "soc", "board.platform", "adreno", "mali")
                .any { l.contains(it, ignoreCase = true) }
        }.forEach { appendLine("  $it") }
    }

    /** Cria o .zip e devolve o nome com que ficou em Download/. */
    fun export(context: Context): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val name = "DragonBurst-logs-$stamp.zip"
        val tmp = File(context.cacheDir, name)
        ZipOutputStream(tmp.outputStream().buffered()).use { zip ->
            fun put(entry: String, text: String) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            fun putFile(entry: String, f: File) {
                if (!f.isFile) return
                zip.putNextEntry(ZipEntry(entry))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            put("aparelho.txt", deviceReport())
            put("logcat.txt", run("logcat", "-d", "-v", "threadtime"))
            AppPaths.logsDir.listFiles()?.forEach { putFile("logs/${it.name}", it) }
            // Capturas de frames (.dfc): so a mais recente, que pode ter dezenas de MB.
            File(AppPaths.logsDir, "capture").listFiles { f -> f.extension == "dfc" }
                ?.maxByOrNull { it.lastModified() }?.let { putFile("logs/capture/${it.name}", it) }
            // Tempos por draw de cada captura (pequenos): todos.
            File(AppPaths.logsDir, "capture").listFiles { f -> f.name.endsWith(".timing.txt") }
                ?.forEach { putFile("logs/capture/${it.name}", it) }
            putFile("burstlimit.toml", AppPaths.userToml)
            putFile("cvars.txt", File(AppPaths.appDir, "cvars.txt"))
            File(AppPaths.appDir, "cache").takeIf { it.isDirectory }?.walkTopDown()
                ?.filter { it.isFile && it.extension == "log" }
                ?.forEach { putFile("cache/${it.name}", it) }
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("sem acesso a Download/")
        resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out) } }
        tmp.delete()
        return name
    }
}
