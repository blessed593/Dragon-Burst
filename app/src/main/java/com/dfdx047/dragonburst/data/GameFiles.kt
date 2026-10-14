package com.dfdx047.dragonburst.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Arquivos do jogo (README do Burst Limit Recompiled, "Which version"): so a copia
 * USA (NTSC-U) funciona, porque o codigo foi recompilado a partir deste default.xex.
 */
object BaseRom {
    const val XEX_SIZE = 7_983_104L
    const val XEX_SHA1_US = "aec598f88cf51181fc377b148e0b1ad30db4485c"

    /** Caminho relativo a game_data_root, obrigatorio. */
    data class Entry(val path: String, val required: Boolean, val directory: Boolean = false)

    val FILES = listOf(
        Entry("LONG2DATA/LONG2DATA_US.CPK", true),
        Entry("LONG2DATA/STREAM_US.CPK", true),
        Entry("LONG2DATA/STREAM_JP.CPK", false),
        Entry("LONG2DATA/SFD", true, directory = true),
        Entry("LONG2DATA/SOUND", true, directory = true),
    )
}

enum class FileStatus { OK, MISSING, DIFFERENT, WRONG_SIZE }

data class FileCheck(val name: String, val required: Boolean, val status: FileStatus)

/** Estado da instalacao do jogo na pasta da app. */
data class GameInstall(val checks: List<FileCheck>) {
    val xex: FileCheck? get() = checks.firstOrNull { it.name == "default.xex" }
    /**
     * Bloqueia o JOGAR: obrigatorio em falta ou executavel que nao e o USA. Com outro
     * default.xex o jogo fecha logo ao abrir ("No function registered at ...").
     */
    val playable: Boolean
        get() = checks.none { c ->
            (c.required && c.status == FileStatus.MISSING) ||
                (c.name == "default.xex" && c.status != FileStatus.OK)
        }
    val installed: Boolean get() = xex?.status != FileStatus.MISSING
}

sealed class ImportProgress {
    data class Copying(val file: String, val done: Long, val total: Long) : ImportProgress()
    data class Hashing(val file: String, val done: Long, val total: Long) : ImportProgress()
    data class Reading(val file: String) : ImportProgress()
}

class ImportException(val reason: Reason, val detail: String = "") : Exception(detail) {
    enum class Reason { NO_XEX, NO_DATA_FOLDER, WRONG_REGION, BAD_XEX, NOT_A_DISC, MISSING_REQUIRED, NO_SPACE, IO }
}

object GameFiles {

    private fun sha1(input: InputStream, total: Long, onProgress: (Long, Long) -> Unit): String {
        val md = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(1 shl 20)
        var done = 0L
        input.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                onProgress(done, total)
            }
        }
        return md.digest().joinToString("") { b -> "%02x".format(b) }
    }

    /** Verifica o que esta na pasta da app (o .xex por SHA-1, o resto por presenca). */
    fun check(onProgress: (ImportProgress) -> Unit = {}): GameInstall {
        val list = ArrayList<FileCheck>()
        val xex = AppPaths.xexFile
        list += FileCheck(
            "default.xex", true,
            when {
                !xex.isFile -> FileStatus.MISSING
                xex.length() != BaseRom.XEX_SIZE -> FileStatus.WRONG_SIZE
                else -> {
                    val hash = sha1(xex.inputStream(), xex.length()) { d, t ->
                        onProgress(ImportProgress.Hashing(xex.name, d, t))
                    }
                    if (hash == BaseRom.XEX_SHA1_US) FileStatus.OK else FileStatus.DIFFERENT
                }
            },
        )
        for (e in BaseRom.FILES) {
            val f = File(AppPaths.gameDir, e.path)
            val present = if (e.directory) f.isDirectory && (f.list()?.isNotEmpty() == true) else f.isFile
            list += FileCheck(e.path, e.required, if (present) FileStatus.OK else FileStatus.MISSING)
        }
        return GameInstall(list)
    }

    private fun DocumentFile.child(name: String): DocumentFile? =
        listFiles().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * Importa o jogo de uma pasta ja extraida (SAF): a pasta com o default.xex e a
     * LONG2DATA/, ou uma pasta acima com game_data_root/ dentro. Copia tudo para a
     * pasta da app, o default.xex por ultimo (e ele que marca a instalacao como
     * completa, como no instalador do PC).
     */
    fun importFolder(context: Context, tree: Uri, onProgress: (ImportProgress) -> Unit) {
        val root = DocumentFile.fromTreeUri(context, tree)
            ?: throw ImportException(ImportException.Reason.IO, "pasta invalida")
        val base = root.child("game_data_root")?.takeIf { it.isDirectory } ?: root
        val xexDoc = base.child("default.xex") ?: throw ImportException(ImportException.Reason.NO_XEX)
        val dataDoc = base.child("LONG2DATA")?.takeIf { it.isDirectory }
            ?: throw ImportException(ImportException.Reason.NO_DATA_FOLDER)

        if (xexDoc.length() != BaseRom.XEX_SIZE) {
            throw ImportException(ImportException.Reason.BAD_XEX, xexDoc.name ?: "")
        }
        val resolver = context.contentResolver
        val hash = sha1(resolver.openInputStream(xexDoc.uri)!!, xexDoc.length()) { d, t ->
            onProgress(ImportProgress.Hashing("default.xex", d, t))
        }
        if (hash != BaseRom.XEX_SHA1_US) throw ImportException(ImportException.Reason.WRONG_REGION, hash)

        // Lista recursiva da LONG2DATA (o resto do disco nao e lido pelo jogo).
        val files = ArrayList<Pair<DocumentFile, String>>()
        fun walk(dir: DocumentFile, rel: String) {
            for (c in dir.listFiles()) {
                val name = c.name ?: continue
                if (c.isDirectory) walk(c, "$rel/$name") else files += c to "$rel/$name"
            }
        }
        onProgress(ImportProgress.Reading("LONG2DATA"))
        walk(dataDoc, "LONG2DATA")
        requireFiles(files.map { it.second })

        val total = xexDoc.length() + files.sumOf { it.first.length() }
        ensureSpace(total)
        var done = 0L
        val buf = ByteArray(1 shl 20)
        fun copy(doc: DocumentFile, rel: String) {
            val target = File(AppPaths.gameDir, rel)
            if (target.isFile && target.length() == doc.length() && rel != "default.xex") {
                done += doc.length()
                return
            }
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, target.name + ".part")
            try {
                resolver.openInputStream(doc.uri)!!.use { input ->
                    tmp.outputStream().use { out ->
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - last >= (4L shl 20)) {
                                last = done
                                onProgress(ImportProgress.Copying(rel, done, total))
                            }
                        }
                    }
                }
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) throw ImportException(ImportException.Reason.IO, rel)
            } catch (e: ImportException) {
                tmp.delete()
                throw e
            } catch (e: Exception) {
                tmp.delete()
                throw ImportException(ImportException.Reason.IO, "$rel: ${e.message}")
            }
        }
        AppPaths.xexFile.delete()
        for ((doc, rel) in files) copy(doc, rel)
        copy(xexDoc, "default.xex")
        onProgress(ImportProgress.Copying("", total, total))
    }

    /**
     * Importa direto da imagem do disco (.iso do Xbox 360, XDVDFS), como o instalador
     * do PC (burstlimit_install.cpp): confere o SHA-1 do default.xex antes de copiar
     * e copia todo o disco menos o $SystemUpdate, o default.xex por ultimo.
     */
    fun importIso(context: Context, uri: Uri, onProgress: (ImportProgress) -> Unit) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw ImportException(ImportException.Reason.IO, "iso")
        pfd.use {
            java.io.FileInputStream(it.fileDescriptor).channel.use { ch ->
                onProgress(ImportProgress.Reading("ISO"))
                val disc = Xdvdfs.open(ch) ?: throw ImportException(ImportException.Reason.NOT_A_DISC)
                val xex = disc.files.firstOrNull { f -> f.path.equals("default.xex", true) }
                    ?: throw ImportException(ImportException.Reason.NO_XEX)
                if (xex.size != BaseRom.XEX_SIZE) {
                    throw ImportException(ImportException.Reason.BAD_XEX, xex.size.toString())
                }
                val md = MessageDigest.getInstance("SHA-1")
                disc.read(ch, xex) { b, n -> md.update(b, 0, n) }
                val hash = md.digest().joinToString("") { b -> "%02x".format(b) }
                if (hash != BaseRom.XEX_SHA1_US) throw ImportException(ImportException.Reason.WRONG_REGION, hash)

                val files = disc.files.filter { f ->
                    !f.path.startsWith("\$SystemUpdate", ignoreCase = true) && f !== xex
                }.sortedBy { f -> f.offset } + xex
                requireFiles(files.map { f -> f.path })
                val total = files.sumOf { f -> f.size }
                ensureSpace(total)
                AppPaths.xexFile.delete()
                var done = 0L
                var last = 0L
                for (f in files) {
                    val target = File(AppPaths.gameDir, f.path)
                    if (f !== xex && target.isFile && target.length() == f.size) {
                        done += f.size
                        continue
                    }
                    target.parentFile?.mkdirs()
                    val tmp = File(target.parentFile, target.name + ".part")
                    try {
                        tmp.outputStream().use { out ->
                            disc.read(ch, f) { b, n ->
                                out.write(b, 0, n)
                                done += n
                                if (done - last >= (4L shl 20)) {
                                    last = done
                                    onProgress(ImportProgress.Copying(f.path, done, total))
                                }
                            }
                        }
                        if (target.exists()) target.delete()
                        if (!tmp.renameTo(target)) throw ImportException(ImportException.Reason.IO, f.path)
                    } catch (e: ImportException) {
                        tmp.delete()
                        throw e
                    } catch (e: Exception) {
                        tmp.delete()
                        throw ImportException(ImportException.Reason.IO, "${f.path}: ${e.message}")
                    }
                }
                onProgress(ImportProgress.Copying("", total, total))
            }
        }
    }

    private fun requireFiles(paths: List<String>) {
        val lower = paths.map { it.lowercase() }
        val missing = BaseRom.FILES.filter { e ->
            e.required && lower.none { p ->
                if (e.directory) p.startsWith(e.path.lowercase() + "/") else p == e.path.lowercase()
            }
        }.map { it.path }
        if (missing.isNotEmpty()) {
            throw ImportException(ImportException.Reason.MISSING_REQUIRED, missing.joinToString())
        }
    }

    private fun ensureSpace(total: Long) {
        AppPaths.gameDir.mkdirs()
        val free = AppPaths.gameDir.usableSpace
        if (free < total + (64L shl 20)) {
            throw ImportException(
                ImportException.Reason.NO_SPACE,
                "%.1f GB / %.1f GB".format(total / 1e9, free / 1e9),
            )
        }
    }

    fun installedSizeBytes(): Long =
        AppPaths.gameDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

/**
 * Leitor minimo do sistema de ficheiros dos discos do Xbox 360 (XDVDFS / GDFX), igual
 * ao DiscImageDevice do SDK: o setor 32 da particao do jogo tem "MICROSOFT*XBOX*MEDIA",
 * a raiz e uma arvore binaria de entradas (esquerda/direita em unidades de 4 bytes).
 * Tudo little-endian, setores de 2 KB.
 */
class Xdvdfs private constructor(val files: List<FileEntry>) {

    data class FileEntry(val path: String, val offset: Long, val size: Long)

    fun read(ch: FileChannel, f: FileEntry, sink: (ByteArray, Int) -> Unit) {
        val bytes = ByteArray(1 shl 20)
        val buf = ByteBuffer.wrap(bytes)
        var pos = f.offset
        var left = f.size
        while (left > 0) {
            buf.clear()
            buf.limit(minOf(left, bytes.size.toLong()).toInt())
            val n = ch.read(buf, pos)
            if (n <= 0) throw ImportException(ImportException.Reason.IO, "fim do disco em ${f.path}")
            sink(bytes, n)
            pos += n
            left -= n
        }
    }

    companion object {
        private const val SECTOR = 2048L
        private val OFFSETS = longArrayOf(0x00000000, 0x0000FB20, 0x00020600, 0x02080000, 0x0FD90000)
        private const val MAGIC = "MICROSOFT*XBOX*MEDIA"
        private const val ATTR_DIRECTORY = 0x10

        private fun readAt(ch: FileChannel, pos: Long, size: Int): ByteBuffer? {
            if (pos < 0 || pos + size > ch.size()) return null
            val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
            while (b.hasRemaining()) {
                if (ch.read(b, pos + b.position()) <= 0) return null
            }
            b.flip()
            return b
        }

        fun open(ch: FileChannel): Xdvdfs? {
            var gameOffset = -1L
            for (o in OFFSETS) {
                val m = readAt(ch, o + 32 * SECTOR, 20) ?: continue
                val bytes = ByteArray(20).also { m.get(it) }
                if (String(bytes, Charsets.US_ASCII) == MAGIC) {
                    gameOffset = o
                    break
                }
            }
            if (gameOffset < 0) return null
            val fs = readAt(ch, gameOffset + 32 * SECTOR, 28) ?: return null
            val rootSector = fs.getInt(20).toLong() and 0xFFFFFFFFL
            val rootSize = fs.getInt(24).toLong() and 0xFFFFFFFFL
            if (rootSize < 13 || rootSize > (32L shl 20)) return null
            val out = ArrayList<FileEntry>()
            val ok = readDir(ch, gameOffset, gameOffset + rootSector * SECTOR, rootSize, "", out, 0)
            return if (ok) Xdvdfs(out) else null
        }

        private fun readDir(
            ch: FileChannel, gameOffset: Long, dirOffset: Long, dirSize: Long,
            prefix: String, out: MutableList<FileEntry>, depth: Int,
        ): Boolean {
            if (depth > 32) return false
            val dir = readAt(ch, dirOffset, dirSize.toInt()) ?: return false
            val visited = HashSet<Int>()
            fun entry(ordinal: Int): Boolean {
                val p = ordinal * 4
                if (!visited.add(p) || p + 14 > dir.limit()) return false
                val left = dir.getShort(p).toInt() and 0xFFFF
                val right = dir.getShort(p + 2).toInt() and 0xFFFF
                val sector = dir.getInt(p + 4).toLong() and 0xFFFFFFFFL
                val length = dir.getInt(p + 8).toLong() and 0xFFFFFFFFL
                val attributes = dir.get(p + 12).toInt() and 0xFF
                val nameLength = dir.get(p + 13).toInt() and 0xFF
                if (p + 14 + nameLength > dir.limit()) return false
                if (left != 0 && !entry(left)) return false
                val nameBytes = ByteArray(nameLength)
                for (i in 0 until nameLength) nameBytes[i] = dir.get(p + 14 + i)
                val name = String(nameBytes, Charsets.ISO_8859_1)
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (attributes and ATTR_DIRECTORY != 0) {
                    if (length != 0L &&
                        !readDir(ch, gameOffset, gameOffset + sector * SECTOR, length, path, out, depth + 1)
                    ) return false
                } else {
                    out += FileEntry(path, gameOffset + sector * SECTOR, length)
                }
                return right == 0 || entry(right)
            }
            return entry(0)
        }
    }
}
