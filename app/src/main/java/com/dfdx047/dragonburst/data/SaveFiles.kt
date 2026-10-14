package com.dfdx047.dragonburst.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/**
 * A pasta saves/ do Burst Limit Recompiled (burstlimit_saves.cpp): o menu do jogo
 * (Back + Start > SAVE) exporta o save para saves/BurstLimit-save-<hora>.zip e importa
 * um .zip (ou pasta do Xenia) que esteja la. No Android essa pasta fica dentro da app,
 * por isso o launcher copia os .zip para Download/ e traz .zip de fora para la.
 */
object SaveFiles {
    data class SaveZip(val file: File, val size: Long, val modified: Long)

    fun list(): List<SaveZip> =
        AppPaths.savesDir.listFiles { f -> f.isFile && f.extension.equals("zip", true) }
            ?.map { SaveZip(it, it.length(), it.lastModified()) }
            ?.sortedByDescending { it.modified }
            ?: emptyList()

    /** Copia para Download/ e devolve o nome com que ficou. */
    fun exportToDownloads(context: Context, save: SaveZip): String {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, save.file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("sem acesso a Download/")
        resolver.openOutputStream(uri)?.use { out -> save.file.inputStream().use { it.copyTo(out) } }
            ?: error("sem acesso a Download/")
        return save.file.name
    }

    /** Copia um .zip escolhido pelo utilizador para saves/ (o jogo importa-o pelo menu). */
    fun addFromUri(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val display = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "save.zip"
        val base = display.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "save" }
        AppPaths.savesDir.mkdirs()
        var dst = File(AppPaths.savesDir, "$base.zip")
        var i = 2
        while (dst.exists()) dst = File(AppPaths.savesDir, "$base-${i++}.zip")
        val tmp = File(AppPaths.savesDir, dst.name + ".tmp")
        resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            ?: error("nao foi possivel ler $display")
        // Um .zip comeca por "PK".
        val head = ByteArray(2)
        val n = tmp.inputStream().use { it.read(head) }
        if (n < 2 || head[0] != 'P'.code.toByte() || head[1] != 'K'.code.toByte()) {
            tmp.delete()
            throw IllegalArgumentException(display)
        }
        if (!tmp.renameTo(dst)) {
            tmp.delete()
            error("nao foi possivel gravar ${dst.name}")
        }
        return dst.name
    }

    fun delete(save: SaveZip): Boolean = save.file.delete()
}
