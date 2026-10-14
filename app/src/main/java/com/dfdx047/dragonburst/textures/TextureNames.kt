package com.dfdx047.dragonburst.textures

import java.io.File
import java.io.RandomAccessFile

/**
 * Regras das texturas de substituicao do Burst Limit (rex replacement.cpp do SDK):
 *
 *  - pasta plana <texture_folder>/replace/ (sem subpastas);
 *  - nome = 16 digitos hexadecimais (o hash do conteudo original), o resto do nome
 *    e ignorado: <hash16>.dds, <hash16>.png ou <hash16>_<qualquer coisa>.dds;
 *  - PNG (qualquer tipo, vira RGBA8) ou DDS BC1/BC2/BC3/BC7 ou 32 bpp RGBA/BGRA;
 *  - qualquer tamanho (os mipmaps sao feitos ao carregar).
 */
object TextureNames {
    private val NAME = Regex("""^([0-9A-Fa-f]{16}).*\.(png|dds)$""", RegexOption.IGNORE_CASE)

    data class Parsed(val hash: String, val ext: String)

    enum class Kind { PNG, DDS_BC1, DDS_BC2, DDS_BC3, DDS_BC7, DDS_RGBA32, DDS_UNSUPPORTED, UNKNOWN }

    data class ImageInfo(val width: Int, val height: Int, val kind: Kind)

    fun isImage(f: File): Boolean = isImageName(f.name)

    fun isImageName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext == "png" || ext == "dds"
    }

    fun parse(fileName: String): Parsed? {
        val m = NAME.matchEntire(fileName) ?: return null
        return Parsed(m.groupValues[1].lowercase(), m.groupValues[2].lowercase())
    }

    /** Le so o cabecalho (PNG IHDR ou DDS) para saber tamanho e formato. */
    fun readImageInfo(f: File): ImageInfo? = try {
        RandomAccessFile(f, "r").use { raf ->
            val head = ByteArray(148)
            val n = raf.read(head)
            if (n >= 24 && head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte() && head[3] == 'G'.code.toByte()) {
                ImageInfo(be(head, 16), be(head, 20), Kind.PNG)
            } else if (n >= 128 && head[0] == 'D'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == 'S'.code.toByte()) {
                val h = le(head, 12)
                val w = le(head, 16)
                // DDS_PIXELFORMAT comeca em 76: flags em 80, fourCC em 84, bits em 88,
                // mascaras R/G/B/A em 92/96/100/104. Cabecalho DX10 (BC7) em 128.
                val pfFlags = le(head, 80)
                val fourCC = String(head, 84, 4, Charsets.US_ASCII)
                val bits = le(head, 88)
                val kind = when {
                    pfFlags and 0x4 != 0 -> when (fourCC) {
                        "DXT1" -> Kind.DDS_BC1
                        "DXT2", "DXT3" -> Kind.DDS_BC2
                        "DXT4", "DXT5" -> Kind.DDS_BC3
                        "DX10" -> if (n >= 132 && le(head, 128) in setOf(97, 98, 99)) Kind.DDS_BC7 else Kind.DDS_UNSUPPORTED
                        else -> Kind.DDS_UNSUPPORTED
                    }
                    pfFlags and 0x40 != 0 && bits == 32 && le(head, 96) == 0x0000FF00 -> Kind.DDS_RGBA32
                    else -> Kind.DDS_UNSUPPORTED
                }
                ImageInfo(w, h, kind)
            } else {
                ImageInfo(0, 0, Kind.UNKNOWN)
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun be(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or
            ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun le(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)
}
