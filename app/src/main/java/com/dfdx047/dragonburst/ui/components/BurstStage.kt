package com.dfdx047.dragonburst.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.dfdx047.dragonburst.ui.theme.DfColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Palco animado do Inicio: um choque de raios de energia visto ao longe, sem personagens.
 * Raio violeta da esquerda (por baixo do logotipo), raio dourado da direita; encontram-se numa
 * esfera branca que vai e vem (o empurra-empurra), solta arcos eletricos, faiscas e ondas de
 * choque. Em baixo, chao rachado a brilhar, mesas no horizonte, pedras a levitar, poeira e
 * brasas; linhas de velocidade fracas no ceu. Tudo em camadas aditivas (BlendMode.Plus).
 *
 * O desenho e um porte linha a linha do prototipo em Python (numpy + Pillow) usado para as
 * previas. O tempo t (0..1, ciclo de 12 s) so e lido na fase de desenho, por isso a animacao
 * nao recompoe nada. Tudo o que so depende do tamanho (rachas, mesas, formas das pedras) fica
 * em cache no drawWithCache. Com as animacoes do sistema desligadas fica um quadro parado.
 *
 * Orcamento por quadro: ~45 paths (2 raios x 5, 1 linhas de velocidade, 2 mesas, 2 rachas,
 * 1 aro da esfera desenhado 2x, 3 de arcos, 16 pedras desenhadas 2x), 40 faiscas + 24 riscos
 * (drawLine), 24 brasas, ~25 gradientes grandes. Cerca de 170 chamadas de desenho.
 */
@Composable
fun BurstStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val time: State<Float> = if (animate) {
        rememberInfiniteTransition(label = "stage").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
            label = "time",
        )
    } else {
        remember { mutableFloatStateOf(STILL_T) }
    }

    Box(modifier) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val stage = StageCache(size.width, size.height)
                    onDrawBehind { drawStage(time.value, stage) }
                },
        )
        content()
    }
}

private const val LOOP_MS = 12000
private const val LOOP_S = 12f
private const val STILL_T = 0.3f
private const val TAU = (2.0 * PI).toFloat()
private val Plus = BlendMode.Plus

// Cores do palco (as da marca vem do DfColors).
private val VioletHot = Color(0xFFC9B6FF)
private val GoldHot = Color(0xFFFFF1B8)
private val GoldBeam = Color(0xFFFFB02E)
private val White = Color.White
private val Dust = Color(0xFFB0704A)
private val Flare = Color(0xFFFFE9C0)
private val CoreCream = Color(0xFFFFF2CC)
private val HaloCream = Color(0xFFFFF4DA)
private val RockLit = Color(0xFF6A3E30)
private val RockMid = Color(0xFF2A1820)
private val RockDark = Color(0xFF07050A)
private val ScrimInk = Color(0xFF05020A)

/** Cor com alfa limitado a 0..1 (Color.copy rejeita valores fora). */
private fun Color.a(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

private fun hsh(i: Int, k: Int): Float {
    val x = sin(i * 12.9898 + k * 78.233) * 43758.5453
    return (x - floor(x)).toFloat()
}

private fun fract(x: Float): Float = x - floor(x)

private fun smooth(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Ruido de borda dos raios: ondas que correm para o centro do choque. */
private fun noise1(xd: Float, t: Float, ph: Float): Float =
    0.5f * sin(xd * 0.055f - t * TAU * 22f + ph) +
        0.3f * sin(xd * 0.13f - t * TAU * 37f + 1.7f + ph * 2f) +
        0.2f * sin(xd * 0.29f - t * TAU * 61f + 0.5f + ph * 3f)

/** Linha partida por deslocamento do ponto medio (rachas e arcos). Pares x,y. */
private fun jag(ax: Float, ay: Float, bx: Float, by: Float, levels: Int, rough: Float, seed: Int): FloatArray {
    var pts = floatArrayOf(ax, ay, bx, by)
    for (lv in 0 until levels) {
        val n = pts.size / 2
        val out = FloatArray((2 * n - 1) * 2)
        out[0] = pts[0]
        out[1] = pts[1]
        var o = 2
        for (i in 0 until n - 1) {
            val px = pts[2 * i]
            val py = pts[2 * i + 1]
            val qx = pts[2 * i + 2]
            val qy = pts[2 * i + 3]
            val dx = qx - px
            val dy = qy - py
            val off = (hsh(seed + lv * 17 + i, 60) - 0.5f) * 2f * rough
            out[o++] = (px + qx) / 2f - dy * off
            out[o++] = (py + qy) / 2f + dx * off
            out[o++] = qx
            out[o++] = qy
        }
        pts = out
    }
    return pts
}

private fun Path.addPoly(pts: FloatArray, from: Int = 0, to: Int = pts.size / 2) {
    moveTo(pts[2 * from], pts[2 * from + 1])
    for (i in from + 1 until to) lineTo(pts[2 * i], pts[2 * i + 1])
}

private class Rock(
    val verts: FloatArray,
    val depth: Float,
    val x: Float,
    val top: Float,
    val size: Float,
    val k: Int,
    val ph: Float,
    val spin: Float,
    val a0: Float,
    val sway: Float,
)

/** Tudo o que so depende do tamanho do palco, mais os Path reutilizados a cada quadro. */
private class StageCache(val w: Float, val h: Float) {
    val dp = h / 300f
    val gy = h * 0.71f
    val crackX = w * 0.585f
    val crackY = gy + h * 0.085f
    val cracks = Path()
    val mesas = Path()
    val mesaRim = Path()
    val rocks: List<Rock>

    // Reutilizados (o canvas copia o path quando o grava).
    val pA = Path()
    val pB = Path()
    val pC = Path()
    val arcLav = Path()
    val arcGold = Path()
    val arcCore = Path()

    init {
        // Rachas a sair de baixo do choque, no plano do chao (achatadas em perspetiva).
        for (i in 0 until 7) {
            val ang = 0.25f + (i / 6f) * (PI.toFloat() - 0.5f) + (hsh(i, 40) - 0.5f) * 0.35f
            var ln = (70f + 170f * hsh(i, 41)) * dp
            if (sin(ang) < 0f) ln *= 0.55f
            val pts = jag(
                crackX + cos(ang) * 6f * dp, crackY + sin(ang) * 6f * dp * 0.4f,
                crackX + cos(ang) * ln, crackY + sin(ang) * ln * 0.4f,
                4, 0.13f, i * 37 + 400,
            )
            cracks.addPoly(pts)
            val bi = 6 + (hsh(i, 43) * 5).toInt()
            val bx = pts[2 * bi]
            val by = pts[2 * bi + 1]
            val ba = ang + if (hsh(i, 44) > 0.5f) 0.55f else -0.55f
            val bl = ln * (0.25f + 0.2f * hsh(i, 46))
            cracks.addPoly(jag(bx, by, bx + cos(ba) * bl, by + sin(ba) * bl * 0.4f, 3, 0.15f, i * 37 + 900))
        }
        // Mesas no horizonte.
        val m = ArrayList<Float>()
        m.add(0f); m.add(gy + 1f)
        var x = 0f
        var k = 0
        while (x < w) {
            k++
            x += (14f + 40f * hsh(k, 30)) * dp
            val hh = (4f + 22f * hsh(k, 31).pow(2)) * dp
            if (hsh(k, 32) > 0.55f) {
                val x2 = x + (20f + 30f * hsh(k, 33)) * dp
                m.add(x); m.add(gy - hh)
                m.add(x2); m.add(gy - hh * (0.9f + 0.1f * hsh(k, 34)))
                x = x2
            } else {
                m.add(x); m.add(gy - hh * 0.6f)
            }
        }
        m.add(w); m.add(gy + 1f)
        val mp = m.toFloatArray()
        mesas.addPoly(mp)
        mesas.close()
        mesaRim.addPoly(mp, 1, mp.size / 2 - 1)
        // Pedras: formas normalizadas (raio 1).
        rocks = List(16) { i ->
            val nv = 4 + (hsh(i, 50) * 3).toInt()
            val el = 0.6f + 0.4f * hsh(i, 59)
            val verts = FloatArray(nv * 2)
            for (v in 0 until nv) {
                val a = (v + 0.15f * (hsh(i * 9 + v, 51) - 0.5f) * 2f) / nv * TAU
                val rr = 0.45f + 0.55f * hsh(i * 9 + v, 52)
                verts[2 * v] = cos(a) * rr
                verts[2 * v + 1] = sin(a) * rr * el
            }
            val d = hsh(i, 53)
            val rx = 0.10f + 0.87f * hsh(i, 54)
            Rock(
                verts = verts,
                depth = d,
                x = rx,
                top = if (rx > 0.42f) 0.06f else 0.42f, // as da esquerda param abaixo do logotipo
                size = (5f + 19f * d * d) * dp,
                k = if (d > 0.45f) 1 else 2,
                ph = hsh(i, 55),
                spin = (hsh(i, 56) - 0.5f) * 2.2f,
                a0 = hsh(i, 57) * TAU,
                sway = hsh(i, 58) * TAU,
            )
        }
    }
}

/** Brilho radial; sy != 1 achata-o numa elipse. */
private fun DrawScope.glow(cx: Float, cy: Float, r: Float, vararg stops: Pair<Float, Color>, sy: Float = 1f, blend: BlendMode = Plus) {
    val c = Offset(cx, cy)
    val brush = Brush.radialGradient(*stops, center = c, radius = r)
    if (sy == 1f) {
        drawCircle(brush, r, c, blendMode = blend)
    } else {
        withTransform({ scale(1f, sy, c) }) { drawCircle(brush, r, c, blendMode = blend) }
    }
}

private fun DrawScope.drawStage(t: Float, c: StageCache) {
    val w = c.w
    val h = c.h
    val dp = c.dp
    val gy = c.gy
    val wob = 0.55f * sin(TAU * t * 3f) + 0.3f * sin(TAU * t * 7f + 1.3f) + 0.15f * sin(TAU * t * 13f + 0.4f)
    val cx = w * (0.585f + 0.03f * wob)
    val cy = h * (0.455f + 0.012f * sin(TAU * t * 5f))
    val cc = Offset(cx, cy)
    val rp = fract(t * 4f)
    val flash = exp(-rp * 9f)
    val pulse = 0.5f + 0.5f * sin(TAU * t * 24f)
    val energy = 0.8f + 0.12f * pulse + 0.35f * flash

    // 1 ceu
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF06030C), 0.45f to Color(0xFF110820), 0.71f to Color(0xFF1E0F22), 1f to Color(0xFF06030A),
            startY = 0f, endY = h,
        ),
    )
    // 2 luz ambiente dos raios: violeta a esquerda, dourada a direita
    glow(cx - w * 0.28f, cy, w * 0.62f, 0f to DfColors.Royal.a(0.45f), 1f to DfColors.Royal.a(0f))
    glow(cx + w * 0.25f, cy, w * 0.5f, 0f to DfColors.OrangeDeep.a(0.30f), 1f to DfColors.OrangeDeep.a(0f))
    // 3 linhas de velocidade (um so path; mudam 8x por segundo; nenhuma para o logotipo)
    val ep = (t * LOOP_S * 8f).toInt()
    val sl = c.pA
    sl.reset()
    for (i in 0 until 30) {
        val a = (i + hsh(i, ep % 97 + 3)) / 30f * TAU
        val deg = (a * 180f / PI.toFloat()) % 360f
        if ((deg > 190f && deg < 250f) || hsh(i, ep % 89 + 5) < 0.3f) continue
        val r0 = (0.28f + 0.2f * hsh(i, ep % 83 + 7)) * h
        sl.moveTo(cx + cos(a) * r0, cy + sin(a) * r0)
        sl.lineTo(cx + cos(a) * w, cy + sin(a) * w)
    }
    drawPath(
        sl,
        Brush.radialGradient(0f to White.a(0f), 0.3f to Flare.a(0.16f), 1f to Flare.a(0f), center = cc, radius = w * 0.8f),
        style = Stroke(1.2f * dp),
        blendMode = Plus,
    )

    // 4 mesas com aro de luz do choque, chao, racha a brilhar
    drawPath(c.mesas, Brush.verticalGradient(0f to Color(0xFF2A1424), 1f to Color(0xFF120A14), startY = gy - 30f * dp, endY = gy))
    drawPath(
        c.mesaRim,
        Brush.radialGradient(0f to DfColors.Ember.a(0.55f * energy), 1f to DfColors.Ember.a(0.05f), center = cc, radius = w * 0.45f),
        style = Stroke(1.2f * dp, join = StrokeJoin.Round),
        blendMode = Plus,
    )
    drawRect(
        Brush.verticalGradient(0f to Color(0xFF1C0E18), 0.25f to Color(0xFF0C0710), 1f to Color(0xFF040306), startY = gy, endY = h),
        topLeft = Offset(0f, gy),
        size = Size(w, h - gy),
    )
    drawRect(
        Brush.verticalGradient(
            0f to DfColors.Ember.a(0f), 0.45f to DfColors.Ember.a(0.22f * energy), 1f to DfColors.Ember.a(0f),
            startY = gy - 5f * dp, endY = gy + 7f * dp,
        ),
        topLeft = Offset(0f, gy - 5f * dp),
        size = Size(w, 12f * dp),
        blendMode = Plus,
    )
    val ox = c.crackX + (cx - w * 0.585f) * 0.6f
    val oy = c.crackY
    val crackO = Offset(c.crackX, oy)
    glow(ox, oy, w * 0.5f, 0f to DfColors.Gold.a(0.35f * energy), 0.3f to DfColors.Ember.a(0.16f * energy), 1f to DfColors.Ember.a(0f), sy = 0.17f)
    drawPath(
        c.cracks,
        Brush.radialGradient(
            0f to DfColors.Ember.a(0.45f * energy), 0.45f to DfColors.OrangeDeep.a(0.18f * energy), 1f to DfColors.OrangeDeep.a(0f),
            center = crackO, radius = w * 0.3f,
        ),
        style = Stroke(4f * dp, join = StrokeJoin.Round),
        blendMode = Plus,
    )
    drawPath(
        c.cracks,
        Brush.radialGradient(
            0f to White.a(0.9f), 0.25f to DfColors.Gold.a(0.5f * energy), 0.65f to DfColors.Ember.a(0.15f), 1f to DfColors.Ember.a(0f),
            center = crackO, radius = w * 0.28f,
        ),
        style = Stroke(1.2f * dp, join = StrokeJoin.Round),
        blendMode = Plus,
    )
    // onda de choque no chao (oval)
    val gr = 30f * dp + w * 0.75f * (1f - (1f - rp).pow(2.4f))
    withTransform({ scale(1f, 0.15f, Offset(ox, oy)) }) {
        drawCircle(DfColors.Ember.a(0.13f * (1f - rp).pow(1.5f)), gr, Offset(ox, oy), style = Stroke(14f * dp), blendMode = Plus)
    }
    // poeira a rolar pelo chao para os lados
    for (i in 0 until 6) {
        val p = fract(hsh(i, 70) + t * (1 + i % 2))
        val side = if (i % 2 == 1) -1f else 1f
        val r = (40f + 70f * p) * dp
        val a = sin(p * PI.toFloat()) * 0.16f
        glow(cx + side * (0.06f + 0.42f * p) * w, gy - 2f * dp, r, 0f to Dust.a(a), 1f to Dust.a(0f), sy = 0.35f)
    }

    // 5 pedras de longe (atras dos raios)
    rocks(t, c, cx, cy, near = false)

    // 6 halo do choque
    val hr = h * (0.62f + 0.04f * pulse + 0.18f * flash)
    glow(cx, cy, hr, 0f to HaloCream.a(0.5f), 0.22f to DfColors.Gold.a(0.22f), 0.5f to DfColors.Ember.a(0.08f), 1f to DfColors.Ember.a(0f))

    // 7 raios: violeta da esquerda (passa por baixo do logotipo), dourado da direita
    val push = 0.08f * wob
    beam(c, -30f * dp, h * 0.585f, cx, cy, t, VioletHot, DfColors.Lavender, DfColors.Royal, 1f + push, pulse, 1)
    beam(c, w + 30f * dp, h * 0.335f, cx, cy, t, GoldHot, GoldBeam, DfColors.OrangeDeep, 1f - push, 1f - pulse, 2)

    // 8 onda de choque: anel suave a crescer
    run {
        val r = 30f * dp + w * 0.62f * (1f - (1f - rp).pow(2.5f))
        val a = (1f - rp).pow(1.6f)
        val bw = (10f + 40f * rp) * dp
        val ro = r + 2f * dp
        glow(
            cx, cy, ro,
            0f to GoldHot.a(0f), max(0.001f, (r - bw) / ro) to GoldHot.a(0f), r / ro to GoldHot.a(0.30f * a), 1f to GoldHot.a(0f),
        )
    }

    // 9 esfera do choque
    val rs = 30f * dp * (1f + 0.08f * pulse + 0.35f * flash)
    glow(cx - 0.55f * rs, cy, rs * 2.3f, 0f to DfColors.Lavender.a(0.55f), 0.45f to DfColors.Lavender.a(0.18f), 1f to DfColors.Lavender.a(0f))
    glow(cx + 0.55f * rs, cy, rs * 2.3f, 0f to DfColors.Gold.a(0.55f), 0.45f to DfColors.Gold.a(0.18f), 1f to DfColors.Gold.a(0f))
    glow(
        cx, cy, rs * 1.15f,
        0f to White, 0.55f to White, 0.78f to CoreCream.a(0.85f), 0.92f to DfColors.Gold.a(0.35f), 1f to DfColors.Gold.a(0f),
    )
    val rim = c.pA
    rim.reset()
    for (k in 0 until 36) {
        val a = k / 36f * TAU
        val rr = rs * (1f + 0.07f * sin(a * 5f + t * TAU * 19f) + 0.05f * sin(a * 9f - t * TAU * 31f))
        if (k == 0) rim.moveTo(cx + cos(a) * rr, cy + sin(a) * rr) else rim.lineTo(cx + cos(a) * rr, cy + sin(a) * rr)
    }
    rim.close()
    drawPath(rim, DfColors.Gold.a(0.22f), style = Stroke(7f * dp, join = StrokeJoin.Round), blendMode = Plus)
    drawPath(rim, White.a(0.75f), style = Stroke(1.6f * dp, join = StrokeJoin.Round), blendMode = Plus)

    // 10 arcos eletricos (renovados 9x por segundo)
    val ae = (t * LOOP_S * 9f).toInt()
    c.arcLav.reset()
    c.arcGold.reset()
    c.arcCore.reset()
    for (i in 0 until 6) {
        val sd = i * 101 + (ae % 108) * 7
        if (hsh(sd, 1) < 0.3f) continue
        val a = hsh(sd, 2) * TAU
        val ln = (22f + 48f * hsh(sd, 3)) * dp
        val a2 = a + (hsh(sd, 4) - 0.5f) * 0.9f
        val pts = jag(
            cx + cos(a) * rs * 0.85f, cy + sin(a) * rs * 0.85f,
            cx + cos(a2) * (rs + ln), cy + sin(a2) * (rs + ln),
            4, 0.22f, sd,
        )
        val fx = pts[12]
        val fy = pts[13]
        val fa = a2 + if (hsh(sd, 5) > 0.5f) 0.7f else -0.7f
        val br = jag(fx, fy, fx + cos(fa) * ln * 0.4f, fy + sin(fa) * ln * 0.4f, 3, 0.25f, sd + 50)
        val col = if (cos(a2) < 0f) c.arcLav else c.arcGold
        col.addPoly(pts)
        col.addPoly(br)
        c.arcCore.addPoly(pts)
        c.arcCore.addPoly(br)
    }
    val arcGlow = Stroke(4f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawPath(c.arcLav, DfColors.Lavender.a(0.30f), style = arcGlow, blendMode = Plus)
    drawPath(c.arcGold, DfColors.Gold.a(0.30f), style = arcGlow, blendMode = Plus)
    drawPath(c.arcCore, White.a(0.9f), style = Stroke(1f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = Plus)

    // 11 risco de lente
    val fl = w * 0.48f
    val l0 = Offset(cx - fl, cy)
    val l1 = Offset(cx + fl, cy)
    drawLine(Brush.linearGradient(0f to White.a(0f), 0.5f to White.a(0.7f), 1f to White.a(0f), start = l0, end = l1), l0, l1, 2f * dp, blendMode = Plus)
    drawLine(
        Brush.linearGradient(0f to DfColors.Gold.a(0f), 0.5f to DfColors.Gold.a(0.2f), 1f to DfColors.Gold.a(0f), start = l0, end = l1),
        l0, l1, 9f * dp, blendMode = Plus,
    )
    val fv = h * 0.22f
    val v0 = Offset(cx, cy - fv)
    val v1 = Offset(cx, cy + fv)
    drawLine(Brush.linearGradient(0f to White.a(0f), 0.5f to White.a(0.5f), 1f to White.a(0f), start = v0, end = v1), v0, v1, 1.5f * dp, blendMode = Plus)

    // 12 faiscas atiradas da esfera, com gravidade
    val g = 160f * dp
    for (i in 0 until 40) {
        val k = 10 + (hsh(i, 80) * 8f).toInt()
        val q = hsh(i, 81) + t * k
        val p = fract(q)
        val cyc = floor(q).toInt() % k
        val a = hsh(i * 13 + cyc, 82) * TAU
        val sp = (90f + 230f * hsh(i * 13 + cyc, 83)) * dp
        val dx = cos(a)
        val dy = sin(a)
        val px = cx + dx * (rs + sp * p)
        val py = cy + dy * (rs + sp * p) + g * p * p
        val vx = dx * sp
        val vy = dy * sp + 2f * g * p
        val al = (1f - p).pow(1.3f)
        val col = if (p < 0.25f) White else if (dx > 0f) DfColors.Gold else VioletHot
        drawLine(
            col.a(al), Offset(px - vx * 0.045f, py - vy * 0.045f), Offset(px, py),
            (0.6f + 1.6f * (1f - p)) * dp, cap = StrokeCap.Round, blendMode = Plus,
        )
    }

    // 13 brasas a subir do chao
    for (i in 0 until 24) {
        val k = 1 + (hsh(i, 90) * 3f).toInt()
        val p = fract(hsh(i, 91) + t * k)
        val x = (0.25f + 0.72f * hsh(i, 92)) * w + sin(p * TAU * 2f + hsh(i, 93) * TAU) * 10f * dp
        val y = gy + 10f * dp - p * h * 0.75f
        val a = sin(p * PI.toFloat()) * 0.85f
        val r = (0.8f + 1.4f * hsh(i, 94)) * dp
        val col = if (hsh(i, 95) > 0.4f) DfColors.Ember else DfColors.Gold
        glow(x, y, r * 3f, 0f to col.a(a), 0.3f to col.a(a * 0.55f), 1f to col.a(0f))
    }

    // 14 pedras de perto (a frente dos raios)
    rocks(t, c, cx, cy, near = true)

    // 15 vinheta e sombra suave atras do logotipo
    val vc = Offset(w * 0.55f, h * 0.45f)
    withTransform({ scale(1f, 0.62f, vc) }) {
        drawRect(
            Brush.radialGradient(0f to Color.Black.a(0f), 0.6f to Color.Black.a(0f), 1f to Color.Black.a(0.55f), center = vc, radius = w * 0.62f),
            topLeft = Offset(0f, vc.y - vc.y / 0.62f),
            size = Size(w, h / 0.62f),
        )
    }
    glow(w * 0.12f, h * 0.1f, w * 0.42f, 0f to ScrimInk.a(0.55f), 1f to ScrimInk.a(0f), sy = 0.55f, blend = BlendMode.SrcOver)
}

/** Um raio de energia de S ate ao choque E: brilho turbulento, corpo, espiral, riscos, nucleo. */
private fun DrawScope.beam(
    c: StageCache,
    sx: Float,
    sy: Float,
    ex: Float,
    ey: Float,
    t: Float,
    hot: Color,
    glowCol: Color,
    deep: Color,
    strength: Float,
    pulse: Float,
    seed: Int,
) {
    val dp = c.dp
    var ux = ex - sx
    var uy = ey - sy
    val len = hypot(ux, uy)
    ux /= len
    uy /= len
    val vx = -uy
    val vy = ux
    val width = strength * (1f + 0.07f * pulse)
    val step = 10f * dp
    val n = (len / step).toInt() + 2
    fun flare(x: Float) = 1f + 0.55f * smooth(len - 70f * dp, len, x)
    fun xAt(i: Int) = min(len, i * step)

    fun band(path: Path, hw: Float, amp: Float, phTop: Float, phBot: Float) {
        path.reset()
        for (i in 0 until n) {
            val x = xAt(i)
            val v = -(hw * flare(x) + amp * noise1(x / dp, t, phTop))
            if (i == 0) path.moveTo(sx + ux * x + vx * v, sy + uy * x + vy * v) else path.lineTo(sx + ux * x + vx * v, sy + uy * x + vy * v)
        }
        for (i in n - 1 downTo 0) {
            val x = xAt(i)
            val v = hw * flare(x) + amp * noise1(x / dp, t, phBot)
            path.lineTo(sx + ux * x + vx * v, sy + uy * x + vy * v)
        }
        path.close()
    }

    val mx = sx + ux * len * 0.5f
    val my = sy + uy * len * 0.5f
    fun perp(hw: Float, vararg stops: Pair<Float, Color>) =
        Brush.linearGradient(*stops, start = Offset(mx - vx * hw, my - vy * hw), end = Offset(mx + vx * hw, my + vy * hw))

    val s = seed.toFloat()
    // brilho exterior turbulento
    val hwo = 46f * dp * width
    band(c.pA, hwo, 9f * dp, s, s + 2.1f)
    drawPath(
        c.pA,
        perp(
            hwo * 1.35f,
            0f to deep.a(0f), 0.18f to deep.a(0.22f), 0.38f to glowCol.a(0.30f), 0.5f to glowCol.a(0.38f),
            0.62f to glowCol.a(0.30f), 0.82f to deep.a(0.22f), 1f to deep.a(0f),
        ),
        blendMode = Plus,
    )
    // corpo
    val hwb = 20f * dp * width
    band(c.pA, hwb, 3.5f * dp, s + 1f, s + 3.1f)
    drawPath(
        c.pA,
        perp(
            hwb * 1.15f,
            0f to glowCol.a(0.15f), 0.1f to glowCol.a(0.65f), 0.3f to glowCol.a(0.55f), 0.5f to hot.a(0.65f),
            0.7f to glowCol.a(0.55f), 0.9f to glowCol.a(0.65f), 1f to glowCol.a(0.15f),
        ),
        blendMode = Plus,
    )
    // espiral a correr para o choque
    val kk = TAU / (150f * dp)
    val sstep = 5f * dp
    val n2 = (len / sstep).toInt() + 2
    c.pB.reset()
    for (j in 0 until 2) {
        for (i in 0 until n2) {
            val x = min(len, i * sstep)
            val v = hwb * 1.25f * flare(x) * sin(x * kk - t * TAU * 18f + j * PI.toFloat())
            if (i == 0) c.pB.moveTo(sx + ux * x + vx * v, sy + uy * x + vy * v) else c.pB.lineTo(sx + ux * x + vx * v, sy + uy * x + vy * v)
        }
    }
    drawPath(c.pB, hot.a(0.12f), style = Stroke(5f * dp, join = StrokeJoin.Round), blendMode = Plus)
    // riscos de energia
    for (i in 0 until 12) {
        val sd = seed * 31 + i
        val v = (hsh(sd, 1) - 0.5f) * 1.6f * hwb
        val ln = (24f + 50f * hsh(sd, 2)) * dp
        val k = 5 + (hsh(sd, 3) * 5f).toInt()
        val x = fract(hsh(sd, 4) + t * k) * (len + ln) - ln
        if (x + ln <= 0f) continue
        val x0 = max(0f, x)
        val x1 = min(len, x + ln)
        val a = Offset(sx + ux * x0 + vx * v, sy + uy * x0 + vy * v)
        val b = Offset(sx + ux * x1 + vx * v, sy + uy * x1 + vy * v)
        drawLine(Brush.linearGradient(0f to White.a(0f), 1f to White.a(0.85f), start = a, end = b), a, b, 1.6f * dp, blendMode = Plus)
    }
    // nucleo branco
    val hwc = 6f * dp * width
    band(c.pC, hwc, 1.2f * dp, s + 4f, s + 6f)
    drawPath(
        c.pC,
        perp(hwc * 1.3f, 0f to hot.a(0f), 0.3f to White.a(0.8f), 0.5f to White, 0.7f to White.a(0.8f), 1f to hot.a(0f)),
        blendMode = Plus,
    )
}

/** Pedras a levitar: aro aceso do lado do choque e corpo escuro com gradiente. */
private fun DrawScope.rocks(t: Float, c: StageCache, cx: Float, cy: Float, near: Boolean) {
    val w = c.w
    val h = c.h
    val dp = c.dp
    val path = c.pB
    for (rk in c.rocks) {
        if ((rk.depth > 0.7f) != near) continue
        val p = fract(rk.ph + t * rk.k)
        val fade = smooth(0f, 0.1f, p) * (1f - smooth(0.72f, 1f, p))
        if (fade <= 0.01f) continue
        val y0 = c.gy + (0.01f + 0.10f * rk.depth) * h
        val y1 = rk.top * h
        val y = y0 - (y0 - y1) * p
        val x = rk.x * w + sin(p * TAU + rk.sway) * 6f * dp
        val ang = rk.a0 + rk.spin * p * TAU * 0.25f
        val ca = cos(ang)
        val sa = sin(ang)
        val sz = rk.size
        var ldx = cx - x
        var ldy = cy - y
        val ll = hypot(ldx, ldy) + 1e-6f
        ldx /= ll
        ldy /= ll
        val off = (1.2f + 0.12f * sz / dp) * dp
        path.reset()
        val nv = rk.verts.size / 2
        for (v in 0 until nv) {
            val vx = rk.verts[2 * v]
            val vy = rk.verts[2 * v + 1]
            val px = x + (vx * ca - vy * sa) * sz + ldx * off
            val py = y + (vx * sa + vy * ca) * sz + ldy * off
            if (v == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        val lit = if (x > cx) DfColors.Gold else DfColors.Lavender
        val nearA = 1f - smooth(0f, 0.6f * w, ll)
        drawPath(path, lit.a(fade * (0.45f + 0.5f * nearA)))
        path.translate(Offset(-ldx * off, -ldy * off))
        drawPath(
            path,
            Brush.linearGradient(
                0f to RockLit.a(fade), 0.35f to RockMid.a(fade), 1f to RockDark.a(fade),
                start = Offset(x + ldx * sz, y + ldy * sz),
                end = Offset(x - ldx * sz, y - ldy * sz),
            ),
        )
    }
}

/** Texto com um gradiente por cima (o logotipo). */
fun Modifier.gradientText(brush: Brush): Modifier = this
    .graphicsLayer(alpha = 0.99f)
    .drawWithCache {
        onDrawWithContent {
            drawContent()
            drawRect(brush, blendMode = BlendMode.SrcAtop)
        }
    }
