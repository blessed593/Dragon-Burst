package com.dfdx047.dragonburst.net

import androidx.annotation.Keep
import com.dfdx047.dragonburst.BuildConfig
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP(S) para o codigo nativo do jogo (app/src/main/cpp/android_http.cpp): o que no
 * PC e o WinHTTP do ecra inicial e do online do Burst Limit. Chamado de threads do
 * jogo (nunca da thread principal do Android).
 */
@Keep
object NativeHttp {
    private const val USER_AGENT = "DragonBurst/" + BuildConfig.VERSION_NAME
    private const val MAX_BODY = 64 * 1024 * 1024

    /** Corpo da resposta (2xx) ou null. Redirecionamentos entre hosts seguidos (ate 5). */
    @JvmStatic
    @Keep
    fun get(url: String, timeoutMs: Int): ByteArray? {
        var current = url
        repeat(6) {
            val c = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs.coerceAtLeast(1000)
                readTimeout = timeoutMs.coerceAtLeast(1000)
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    current = c.getHeaderField("Location") ?: return null
                    return@repeat
                }
                if (code !in 200..299) return null
                val out = ByteArrayOutputStream()
                c.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BODY) return null
                    }
                }
                return out.toByteArray()
            } catch (e: Exception) {
                return null
            } finally {
                c.disconnect()
            }
        }
        return null
    }
}
