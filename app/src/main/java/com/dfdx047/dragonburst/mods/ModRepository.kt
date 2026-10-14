package com.dfdx047.dragonburst.mods

import android.content.Context
import android.net.Uri
import com.dfdx047.dragonburst.data.AppPaths
import com.dfdx047.dragonburst.data.TomlLite
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Mods no formato do Burst Limit Recompiled (src/burstlimit_mods.cpp), lidos e
 * escritos em Kotlin. O runtime so precisa das pastas certas:
 *
 *  - um mod = uma pasta mods/<nome>/ com ficheiros com o nome dos ficheiros do
 *    LONG2DATA_US.CPK (ex. BCGOK002.NUX, ou PAC/CMN/CH/BCGOK/BCGOK002.NUX quando o
 *    nome nao chega); mod.toml opcional (name, author, version, description);
 *  - ligados = lista em mods/mods.toml: enabled = ["Pasta_1", "Pasta_2"];
 *  - dois mods com o mesmo ficheiro: ganha o que vem depois por ordem alfabetica;
 *  - personagem extra = mod com slot.toml (burstlimit_slots.cpp), na 2a pagina da
 *    selecao (R2 / L2); so muda depois de reiniciar o jogo;
 *  - modelos de PS3 (NDP3 / NTP3) sao convertidos pelo jogo ao carregar.
 */
enum class ModType { CHARACTER, MODEL, PS3_MODEL, AUDIO, UI, OTHER }

sealed class ModProblem {
    /** Pasta vazia: nao faz nada. */
    object Empty : ModProblem()
    /** O conteudo esta dentro de uma pasta a mais: o jogo nao o ve. Tem correcao automatica. */
    data class Nested(val inner: String) : ModProblem()
    /** Um caminho do slot.toml nao existe na pasta do mod. */
    data class MissingFile(val path: String) : ModProblem()
    /** O .toml do mod nao se le. */
    data class BadToml(val file: String, val message: String) : ModProblem()
    /** O mesmo ficheiro do jogo tambem e trocado por outro mod que ganha. */
    data class Overridden(val path: String, val winner: String) : ModProblem()
}

data class ModInfo(
    val dir: File,
    val folderName: String,
    val displayName: String,
    val description: String,
    val author: String,
    val version: String,
    val type: ModType,
    val enabled: Boolean,
    val fileCount: Int,
    val sizeBytes: Long,
    val problems: List<ModProblem>,
) {
    val hasErrors: Boolean
        get() = problems.any { it !is ModProblem.Overridden }
}

class ModInstallException(val reason: Reason, detail: String = "") : Exception(detail) {
    enum class Reason { NOT_ZIP, EMPTY, UNSAFE_PATH, IO }
}

object ModRepository {
    private const val LIST_FILE = "mods.toml"
    private const val INFO_FILE = "mod.toml"
    private const val SLOT_FILE = "slot.toml"
    private const val MAX_ZIP_ENTRY = 4L * 1024 * 1024 * 1024 // 4 GB

    private val modsRoot: File get() = AppPaths.modsDir.also { it.mkdirs() }

    private fun isModFolder(f: File) = f.isDirectory && !f.name.startsWith(".")

    /** Pastas ligadas (mods/mods.toml), pela ordem do ficheiro. */
    fun enabledList(): List<String> {
        val f = File(modsRoot, LIST_FILE)
        val raw = try {
            (TomlLite.read(f)["enabled"] as? TomlLite.Value.Raw)?.v
        } catch (e: TomlLite.ParseError) {
            null
        } ?: return emptyList()
        // enabled = ["A", "B"] - guardado como texto pelo TomlLite.
        return Regex(""""((?:[^"\\]|\\.)*)"""").findAll(raw).map {
            it.groupValues[1].replace("\\\"", "\"").replace("\\\\", "\\")
        }.toList()
    }

    private fun writeEnabledList(folders: List<String>) {
        val f = File(modsRoot, LIST_FILE)
        val tmp = File(modsRoot, "$LIST_FILE.tmp")
        val items = folders.joinToString(", ") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
        tmp.writeText(
            "# Mods that are switched on: folder names in this folder. The Mods entry of the\n" +
                "# main menu (and the Dragon Burst launcher) writes this file.\n" +
                "# When two mods replace the same file, the one later in alphabetical order wins.\n" +
                "enabled = [$items]\n",
        )
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Todas as pastas de mods. */
    fun listAll(): List<ModInfo> {
        val enabled = enabledList().toSet()
        val dirs = modsRoot.listFiles()?.filter(::isModFolder).orEmpty().sortedBy { it.name }
        return withConflicts(dirs.map { readMod(it, it.name in enabled) })
    }

    fun readMod(dir: File, enabled: Boolean): ModInfo {
        val info = readToml(File(dir, INFO_FILE))
        val files = dir.walkTopDown().filter { it.isFile }.toList()
        val gameFiles = files.filter { it.name != INFO_FILE && it.name != SLOT_FILE }
        val exts = gameFiles.map { it.extension.lowercase() }.toSet()
        val type = when {
            File(dir, SLOT_FILE).isFile -> ModType.CHARACTER
            gameFiles.any { isPs3Model(it) } -> ModType.PS3_MODEL
            exts.any { it == "nux" || it == "amb" } -> ModType.MODEL
            exts.any { it in setOf("csb", "acb", "awb", "adx", "aax", "hca") } -> ModType.AUDIO
            exts.any { it in setOf("nut", "ama", "nfx", "ntxr") } -> ModType.UI
            else -> ModType.OTHER
        }
        return ModInfo(
            dir = dir,
            folderName = dir.name,
            displayName = info["name"].orEmpty().ifEmpty { dir.name.replace('_', ' ') },
            description = info["description"].orEmpty(),
            author = info["author"].orEmpty(),
            version = info["version"].orEmpty(),
            type = type,
            enabled = enabled,
            fileCount = gameFiles.size,
            sizeBytes = files.sumOf { it.length() },
            problems = checkProblems(dir, gameFiles),
        )
    }

    /** Modelo/textura de PS3: assinatura NDP3 ou NTP3 no inicio do ficheiro. */
    private fun isPs3Model(f: File): Boolean = try {
        f.inputStream().use { i ->
            val b = ByteArray(4)
            i.read(b) == 4 && String(b, Charsets.US_ASCII).let { it == "NDP3" || it == "NTP3" }
        }
    } catch (e: Exception) {
        false
    }

    private fun readToml(f: File): Map<String, String> {
        if (!f.isFile) return emptyMap()
        return try {
            TomlLite.read(f).mapNotNull { (k, v) -> (v as? TomlLite.Value.Str)?.let { k.lowercase() to it.v } }.toMap()
        } catch (e: TomlLite.ParseError) {
            emptyMap()
        }
    }

    private fun checkProblems(dir: File, files: List<File>): List<ModProblem> {
        val out = ArrayList<ModProblem>()
        if (files.isEmpty()) return listOf(ModProblem.Empty)
        // Pasta a mais: so uma subpasta com o conteudo real e nada solto na raiz.
        val children = dir.listFiles().orEmpty()
        if (children.size == 1 && children[0].isDirectory && !hasModContent(dir) && hasModContent(children[0]) &&
            !children[0].name.equals("PAC", true)
        ) {
            out += ModProblem.Nested(children[0].name)
        }
        for (name in listOf(INFO_FILE, SLOT_FILE)) {
            val f = File(dir, name)
            if (!f.isFile) continue
            try {
                val values = TomlLite.read(f)
                if (name == SLOT_FILE) {
                    for (v in values.values) {
                        val s = (v as? TomlLite.Value.Str)?.v ?: continue
                        if (looksLikeRelativePath(s) && !File(dir, s.replace('\\', '/')).exists()) {
                            out += ModProblem.MissingFile(s)
                        }
                    }
                }
            } catch (e: TomlLite.ParseError) {
                out += ModProblem.BadToml(name, "linha ${e.line}: ${e.message}")
            }
        }
        return out
    }

    private fun looksLikeRelativePath(s: String): Boolean {
        if (!s.contains('/') && !s.contains('\\')) return false
        val ext = s.substringAfterLast('.', "")
        return ext.isNotEmpty() && ext.length <= 4 && !s.startsWith("http")
    }

    /** Um ficheiro do jogo (nome com extensao) ou os .toml do mod na raiz. */
    private fun hasModContent(d: File): Boolean {
        if (File(d, INFO_FILE).isFile || File(d, SLOT_FILE).isFile || File(d, "PAC").isDirectory) return true
        return d.listFiles().orEmpty().any { it.isFile && it.extension.isNotEmpty() }
    }

    /** Chave do ficheiro do jogo que um ficheiro do mod substitui (o nome, sem pasta). */
    private fun overrideKey(rel: String): String? {
        val name = rel.replace('\\', '/').substringAfterLast('/')
        if (name == INFO_FILE || name == SLOT_FILE || name.startsWith(".")) return null
        return name.uppercase()
    }

    private fun withConflicts(mods: List<ModInfo>): List<ModInfo> {
        val owner = HashMap<String, String>()
        val extra = HashMap<String, MutableList<ModProblem>>()
        // Como o runtime: o mod que vem depois por ordem alfabetica ganha.
        for (m in mods.filter { it.enabled }.sortedByDescending { it.folderName }) {
            val keys = m.dir.walkTopDown().filter { it.isFile }
                .mapNotNull { overrideKey(it.relativeTo(m.dir).path) }.toSet()
            for (k in keys) {
                val winner = owner[k]
                if (winner == null) owner[k] = m.folderName
                else extra.getOrPut(m.folderName) { mutableListOf() } += ModProblem.Overridden(k, winner)
            }
        }
        return mods.map { m ->
            val more = extra[m.folderName] ?: return@map m
            // Um aviso por mod chega (pode haver centenas de entradas).
            m.copy(problems = m.problems + more.first())
        }
    }

    fun setEnabled(mod: ModInfo, enabled: Boolean) = setEnabled(mod.folderName, enabled)

    fun setEnabled(folder: String, enabled: Boolean) {
        val list = enabledList().toMutableList()
        list.remove(folder)
        if (enabled) list += folder
        writeEnabledList(list.filter { File(modsRoot, it).isDirectory }.distinct().sorted())
    }

    fun delete(mod: ModInfo): Boolean {
        setEnabled(mod.folderName, false)
        return mod.dir.deleteRecursively()
    }

    /** Correcao do problema "pasta a mais": sobe o conteudo um nivel, sem sobrescrever. */
    fun fixNested(mod: ModInfo): Boolean {
        val inner = mod.problems.filterIsInstance<ModProblem.Nested>().firstOrNull() ?: return false
        val innerDir = File(mod.dir, inner.inner)
        val kids = innerDir.listFiles().orEmpty()
        if (kids.any { File(mod.dir, it.name).exists() && it.name != inner.inner }) return false
        val tmp = File(mod.dir, ".nested_tmp")
        if (!innerDir.renameTo(tmp)) return false
        tmp.listFiles().orEmpty().forEach { it.renameTo(File(mod.dir, it.name)) }
        return tmp.delete()
    }

    /**
     * Instala um .zip escolhido pelo usuario: extrai, desembrulha uma pasta unica na
     * raiz e da um nome sem colisao. Um zip com mods/ na raiz instala cada subpasta
     * como um mod (substitui as que ja existirem). Os mods instalados ficam ligados.
     */
    fun installZip(context: Context, uri: Uri, displayName: String, onProgress: (Long) -> Unit = {}): List<String> {
        val stem = sanitize(displayName.substringBeforeLast('.').ifBlank { "mod" })
        val staging = File(modsRoot, ".install_$stem")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            var written = 0L
            var entries = 0
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw ModInstallException(ModInstallException.Reason.IO, "sem acesso ao ficheiro")
            ZipInputStream(stream.buffered()).use { zip ->
                val canonicalRoot = staging.canonicalPath + File.separator
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entries++
                    val target = File(staging, entry.name.replace('\\', '/'))
                    // Zip-slip: nada pode sair da pasta de destino.
                    if (!target.canonicalPath.startsWith(canonicalRoot) && target.canonicalPath != staging.canonicalPath) {
                        throw ModInstallException(ModInstallException.Reason.UNSAFE_PATH, entry.name)
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        var size = 0L
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            size += n
                            if (size > MAX_ZIP_ENTRY) {
                                throw ModInstallException(ModInstallException.Reason.IO, "${entry.name}: demasiado grande")
                            }
                            out.write(buf, 0, n)
                            written += n
                        }
                    }
                    onProgress(written)
                }
            }
            if (entries == 0) throw ModInstallException(ModInstallException.Reason.NOT_ZIP)

            var root = staging
            // Desembrulha "Mod/Mod/..." enquanto houver so uma pasta e nada solto.
            while (true) {
                val kids = root.listFiles().orEmpty()
                if (kids.size == 1 && kids[0].isDirectory && !hasModContent(root) &&
                    !kids[0].name.equals("mods", true) && !kids[0].name.equals("PAC", true)
                ) root = kids[0] else break
            }
            val kids = root.listFiles().orEmpty()
            if (kids.isEmpty()) throw ModInstallException(ModInstallException.Reason.EMPTY)

            val modsFolder = kids.firstOrNull { it.isDirectory && it.name.equals("mods", true) }
            val installed = ArrayList<String>()
            if (modsFolder != null && kids.size <= 3 && !hasModContent(root)) {
                for (sub in modsFolder.listFiles().orEmpty().filter { it.isDirectory }) {
                    val dest = File(modsRoot, sub.name)
                    dest.deleteRecursively()
                    moveDir(sub, dest)
                    installed += sub.name
                }
                if (installed.isEmpty()) throw ModInstallException(ModInstallException.Reason.EMPTY)
            } else {
                val name = uniqueName(stem)
                moveDir(root, File(modsRoot, name))
                installed += name
            }
            installed.forEach { setEnabled(it, true) }
            return installed
        } catch (e: ModInstallException) {
            throw e
        } catch (e: java.util.zip.ZipException) {
            throw ModInstallException(ModInstallException.Reason.NOT_ZIP, e.message ?: "")
        } catch (e: Exception) {
            throw ModInstallException(ModInstallException.Reason.IO, e.message ?: e.toString())
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun moveDir(from: File, to: File) {
        if (!from.renameTo(to)) {
            from.copyRecursively(to, overwrite = true)
            from.deleteRecursively()
        }
    }

    fun uniqueName(base: String): String {
        var name = base
        var i = 2
        while (File(modsRoot, name).exists()) name = "${base}_${i++}"
        return name
    }

    /** Nome de pasta seguro (o runtime e o mods.toml usam o nome da pasta). */
    fun sanitize(s: String): String =
        s.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_').trimStart('.').ifEmpty { "mod" }
}
