package com.dfdx047.dragonburst.textures

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dfdx047.dragonburst.data.AppPaths
import java.io.File
import java.util.zip.ZipInputStream

/** Resultado da verificacao de uma imagem de um pack. */
enum class TextureIssue {
    OK,
    BAD_NAME,          // nome sem os 16 digitos do hash: o jogo ignora
    UNREADABLE,        // cabecalho ilegivel / ficheiro corrompido
    DDS_FORMAT,        // DDS num formato que o runtime nao descodifica
    DUPLICATE,         // o mesmo hash duas vezes no pack (so uma e usada)
}

data class TextureFileReport(val file: File, val issue: TextureIssue, val detail: String = "")

/**
 * Um pack = textures/packs/<nome>/replace/ (a mesma estrutura do textures/ do PC).
 * O pack ativo e o texture_folder do burstlimit.toml (GameConfig.texturePackName):
 * so um de cada vez, como no PC, sem copiar gigabytes de um lado para o outro.
 */
data class TexturePackInfo(
    val dir: File,
    val name: String,
    val fileCount: Int,
    val sizeBytes: Long,
) {
    val replaceDir: File get() = File(dir, "replace")
}

data class TexturePackReport(
    val pack: TexturePackInfo,
    val files: List<TextureFileReport>,
    /** Memoria aproximada depois de descodificado (RGBA8 + mipmaps). */
    val decodedBytes: Long,
) {
    val okCount get() = files.count { it.issue == TextureIssue.OK }
    val warnCount get() = files.count { it.issue == TextureIssue.DUPLICATE }
    val errorCount get() = files.count { it.issue != TextureIssue.OK && it.issue != TextureIssue.DUPLICATE }
}

/** Linha do log do runtime sobre texturas (lida depois de jogar). */
data class RuntimeTextureLine(val level: Level, val text: String) {
    enum class Level { INFO, WARN, ERROR }
}

class TextureInstallException(val reason: Reason, detail: String = "") : Exception(detail) {
    enum class Reason { NOT_ZIP, EMPTY, IO }
}

object TexturePacks {
    const val USER_PACK = "Dragon Burst"

    /** texture_replace_ram_mb do runtime (3072 por omissao): acima disso descodifica ao usar. */
    const val RUNTIME_CACHE_BUDGET = 3072L * 1024 * 1024

    val packsRoot: File get() = File(AppPaths.texturesDir, "packs").also { it.mkdirs() }

    fun packDir(name: String) = File(packsRoot, name)

    fun list(): List<TexturePackInfo> =
        packsRoot.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }.orEmpty()
            .sortedBy { it.name.lowercase() }
            .map { d ->
                val files = File(d, "replace").listFiles()?.filter { it.isFile && TextureNames.isImage(it) }.orEmpty()
                TexturePackInfo(d, d.name, files.size, files.sumOf { it.length() })
            }

    fun validate(pack: TexturePackInfo, onProgress: (Int, Int) -> Unit = { _, _ -> }): TexturePackReport {
        val images = pack.replaceDir.listFiles()?.filter { it.isFile && TextureNames.isImage(it) }.orEmpty()
            .sortedBy { it.name }
        val seen = HashMap<String, String>()
        val list = ArrayList<TextureFileReport>()
        var bytes = 0L
        images.forEachIndexed { i, f ->
            if (i % 50 == 0) onProgress(i, images.size)
            val parsed = TextureNames.parse(f.name)
            if (parsed == null) {
                list += TextureFileReport(f, TextureIssue.BAD_NAME)
                return@forEachIndexed
            }
            val info = TextureNames.readImageInfo(f)
            if (info == null || info.kind == TextureNames.Kind.UNKNOWN || info.width <= 0 || info.height <= 0) {
                list += TextureFileReport(f, TextureIssue.UNREADABLE)
                return@forEachIndexed
            }
            if (info.kind == TextureNames.Kind.DDS_UNSUPPORTED) {
                list += TextureFileReport(f, TextureIssue.DDS_FORMAT)
                return@forEachIndexed
            }
            val first = seen[parsed.hash]
            if (first != null) {
                list += TextureFileReport(f, TextureIssue.DUPLICATE, first)
                return@forEachIndexed
            }
            seen[parsed.hash] = f.name
            bytes += info.width.toLong() * info.height * 4 * 4 / 3
            list += TextureFileReport(f, TextureIssue.OK, "${info.width}x${info.height}")
        }
        onProgress(images.size, images.size)
        return TexturePackReport(pack, list, bytes)
    }

    fun delete(pack: TexturePackInfo): Boolean = pack.dir.deleteRecursively()

    private fun queryName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun sanitize(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().trimStart('.').ifEmpty { "pack" }

    /**
     * Instala um .zip de pack: todas as imagens com nome de hash, de qualquer pasta do
     * zip, vao para packs/<nome>/replace/ (a pasta do runtime e plana). Devolve o nome
     * do pack e quantas imagens entraram.
     */
    fun installZip(context: Context, uri: Uri, onProgress: (Long) -> Unit = {}): Pair<String, Int> {
        val display = queryName(context, uri) ?: "pack.zip"
        var name = sanitize(display.substringBeforeLast('.'))
        var i = 2
        while (packDir(name).exists()) name = "${sanitize(display.substringBeforeLast('.'))}_${i++}"
        val replace = File(packDir(name), "replace").also { it.mkdirs() }
        var count = 0
        var written = 0L
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw TextureInstallException(TextureInstallException.Reason.IO, display)
            ZipInputStream(stream.buffered()).use { zip ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val fileName = entry.name.replace('\\', '/').substringAfterLast('/')
                    if (!TextureNames.isImageName(fileName) || TextureNames.parse(fileName) == null) continue
                    File(replace, fileName).outputStream().use { out ->
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            written += n
                        }
                    }
                    count++
                    if (count % 20 == 0) onProgress(written)
                }
            }
        } catch (e: TextureInstallException) {
            packDir(name).deleteRecursively()
            throw e
        } catch (e: java.util.zip.ZipException) {
            packDir(name).deleteRecursively()
            throw TextureInstallException(TextureInstallException.Reason.NOT_ZIP, e.message ?: "")
        } catch (e: Exception) {
            packDir(name).deleteRecursively()
            throw TextureInstallException(TextureInstallException.Reason.IO, e.message ?: e.toString())
        }
        if (count == 0) {
            packDir(name).deleteRecursively()
            throw TextureInstallException(TextureInstallException.Reason.EMPTY, display)
        }
        return name to count
    }

    /**
     * Copia imagens soltas (PNG/DDS com nome de hash) para o pack do usuario.
     * Devolve (copiadas, recusadas com o motivo).
     */
    fun importImages(context: Context, uris: List<Uri>): Pair<Int, List<Pair<String, TextureIssue>>> {
        val dest = File(packDir(USER_PACK), "replace").also { it.mkdirs() }
        var ok = 0
        val refused = ArrayList<Pair<String, TextureIssue>>()
        for (uri in uris) {
            val name = queryName(context, uri) ?: continue
            if (TextureNames.parse(name) == null) {
                refused += name to TextureIssue.BAD_NAME
                continue
            }
            val target = File(dest, name)
            val tmp = File(dest, "$name.part")
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                refused += name to TextureIssue.UNREADABLE
                continue
            }
            input.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
            val info = TextureNames.readImageInfo(tmp)
            val issue = when {
                info == null || info.kind == TextureNames.Kind.UNKNOWN -> TextureIssue.UNREADABLE
                info.kind == TextureNames.Kind.DDS_UNSUPPORTED -> TextureIssue.DDS_FORMAT
                else -> TextureIssue.OK
            }
            if (issue != TextureIssue.OK) {
                tmp.delete()
                refused += name to issue
                continue
            }
            target.delete()
            tmp.renameTo(target)
            ok++
        }
        return ok to refused
    }

    /** Linhas do log da ultima sessao sobre texturas de substituicao. */
    fun runtimeLog(): List<RuntimeTextureLine> {
        val log = AppPaths.lastRunLog
        if (!log.isFile) return emptyList()
        return log.useLines { lines ->
            lines.filter { l ->
                l.contains("TextureReplacement") || l.contains("Texture cache: a texture pack") ||
                    l.contains("Button icons")
            }.map { l ->
                val level = when {
                    l.contains("[error]", true) || l.contains(" E ") || l.contains("failed", true) ->
                        RuntimeTextureLine.Level.ERROR
                    l.contains("[warn", true) || l.contains("skipp", true) || l.contains("unsupported", true) ->
                        RuntimeTextureLine.Level.WARN
                    else -> RuntimeTextureLine.Level.INFO
                }
                RuntimeTextureLine(level, l.substringAfter("] ").take(240))
            }.take(200).toList()
        }
    }
}
