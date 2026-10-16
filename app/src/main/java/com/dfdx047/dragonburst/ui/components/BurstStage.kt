package com.dfdx047.dragonburst.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.dfdx047.dragonburst.ui.theme.DfColors
import kotlin.math.PI
import kotlin.math.sin

/**
 * Palco animado do Inicio: dois lutadores em silhueta (figuras originais, nenhum
 * personagem do jogo) a disparar um contra o outro, com o choque de energia ao centro,
 * aura a pulsar e brasas a subir. Com as animacoes do sistema desligadas fica parado.
 */
@Composable
fun BurstStage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val transition = rememberInfiniteTransition(label = "stage")
    val t = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
            label = "time",
        ).value
    } else {
        0.35f
    }
    val embers = remember { List(46) { Ember(it) } }

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawStage(t, embers)
        }
        content()
    }
}

private class Ember(private val i: Int) {
    private fun rnd(k: Int): Float {
        val x = sin((i * 12.9898f + k * 78.233f).toDouble()) * 43758.5453
        return (x - kotlin.math.floor(x)).toFloat()
    }
    val x = rnd(1)
    val start = rnd(2)
    val speed = 0.6f + rnd(3) * 1.6f
    val size = 1.2f + rnd(4) * 2.8f
    val sway = rnd(5) * 2f * PI.toFloat()
    val gold = rnd(6) > 0.5f
}

private fun DrawScope.drawStage(t: Float, embers: List<Ember>) {
    val w = size.width
    val h = size.height
    val tau = 2f * PI.toFloat()
    val pulse = 0.5f + 0.5f * sin(t * tau * 6f)

    // Ceu: violeta do menu do jogo, mais escuro em baixo.
    drawRect(Brush.verticalGradient(listOf(DfColors.Royal, DfColors.Panel, DfColors.Night)))

    val ground = h * 0.86f
    val figureH = h * 0.56f
    val centerX = w * 0.5f
    val clashY = ground - figureH * 0.66f

    // Brilho do choque por tras de tudo.
    val glow = (minOf(w, h) * (0.55f + 0.08f * pulse))
    drawCircle(
        Brush.radialGradient(
            listOf(DfColors.Gold.copy(alpha = 0.55f), DfColors.Ember.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(centerX, clashY),
            radius = glow,
        ),
        radius = glow,
        center = Offset(centerX, clashY),
    )

    // Chao: uma linha de luz lavanda.
    drawRect(
        Brush.verticalGradient(
            listOf(Color.Transparent, DfColors.Lavender.copy(alpha = 0.35f), Color.Transparent),
            startY = ground - 6f,
            endY = ground + 10f,
        ),
        topLeft = Offset(0f, ground - 6f),
        size = androidx.compose.ui.geometry.Size(w, 16f),
    )

    // Brasas a subir.
    for (e in embers) {
        val p = ((e.start + t * e.speed) % 1f)
        val y = h * (1f - p)
        val x = w * e.x + sin(p * tau + e.sway) * w * 0.015f
        val a = (1f - p) * 0.8f
        drawCircle(
            (if (e.gold) DfColors.Gold else DfColors.Ember).copy(alpha = a),
            radius = e.size * density,
            center = Offset(x, y),
        )
    }

    val bob = sin(t * tau * 3f) * h * 0.006f
    val leftX = w * 0.22f
    val rightX = w * 0.78f
    val leftHand = fighter(leftX, ground + bob, figureH, 1f, DfColors.Lavender, pulse)
    val rightHand = fighter(rightX, ground - bob, figureH, -1f, DfColors.Ember, pulse)

    // Raios das maos ate ao centro.
    val core = Offset(centerX, clashY)
    beam(leftHand, core, DfColors.Lavender, pulse)
    beam(rightHand, core, DfColors.Gold, pulse)

    // Nucleo e aneis a sair do choque.
    for (k in 0 until 3) {
        val ring = ((t * 4f + k / 3f) % 1f)
        drawCircle(
            Color.White.copy(alpha = (1f - ring) * 0.45f),
            radius = figureH * (0.08f + ring * 0.5f),
            center = core,
            style = Stroke(width = 2.5f * density),
        )
    }
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White, DfColors.Gold, DfColors.Ember.copy(alpha = 0f)),
            center = core,
            radius = figureH * (0.12f + 0.03f * pulse),
        ),
        radius = figureH * (0.12f + 0.03f * pulse),
        center = core,
    )
}

private fun DrawScope.beam(from: Offset, to: Offset, color: Color, pulse: Float) {
    drawLine(color.copy(alpha = 0.35f), from, to, strokeWidth = (14f + 6f * pulse) * density, cap = StrokeCap.Round)
    drawLine(Color.White.copy(alpha = 0.9f), from, to, strokeWidth = (4f + 2f * pulse) * density, cap = StrokeCap.Round)
}

/**
 * Uma silhueta: pose de disparo com os dois bracos para a frente. Coordenadas da figura
 * com origem nos pes, altura 1, x para a frente (dir = 1 olha para a direita). Devolve
 * onde ficam as maos.
 */
private fun DrawScope.fighter(x: Float, feet: Float, height: Float, dir: Float, aura: Color, pulse: Float): Offset {
    fun p(fx: Float, fy: Float) = Offset(x + fx * height * dir, feet + fy * height)
    // Offset e uma value class: nao pode ser vararg.
    fun limb(pts: List<Offset>): Path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    val parts = listOf(
        limb(listOf(p(0.03f, -0.80f), p(-0.04f, -0.47f))) to 0.15f,                     // tronco
        limb(listOf(p(0.04f, -0.76f), p(0.20f, -0.71f), p(0.35f, -0.68f))) to 0.065f,  // braco da frente
        limb(listOf(p(0.00f, -0.74f), p(0.16f, -0.64f), p(0.33f, -0.63f))) to 0.06f,   // braco de tras
        limb(listOf(p(-0.02f, -0.48f), p(0.14f, -0.27f), p(0.22f, -0.01f))) to 0.09f,  // perna da frente
        limb(listOf(p(-0.06f, -0.48f), p(-0.18f, -0.25f), p(-0.31f, -0.01f))) to 0.09f, // perna de tras
    )
    val head = p(0.07f, -0.90f)
    val headR = height * 0.075f
    // Aura: a mesma figura, larga e translucida.
    val auraGrow = height * (0.10f + 0.04f * pulse)
    for ((path, width) in parts) {
        drawPath(
            path,
            aura.copy(alpha = 0.22f + 0.12f * pulse),
            style = Stroke(width = width * height + auraGrow, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
    drawCircle(aura.copy(alpha = 0.22f + 0.12f * pulse), radius = headR + auraGrow / 2f, center = head)
    // Corpo.
    for ((path, width) in parts) {
        drawPath(path, DfColors.Night, style = Stroke(width = width * height, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    drawCircle(DfColors.Night, radius = headR, center = head)
    return p(0.37f, -0.66f)
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
