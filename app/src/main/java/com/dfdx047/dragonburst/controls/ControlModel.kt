package com.dfdx047.dragonburst.controls

import com.dfdx047.dragonburst.R
import com.dfdx047.dragonburst.data.AppPaths
import org.json.JSONArray
import org.json.JSONObject

/**
 * Entradas do comando virtual. FORWARD/BACKWARD sao relativas ao lutador: o
 * HelperEngine traduz para esquerda/direita conforme o lado em que o P1 esta.
 */
enum class PadInput {
    A, B, X, Y, LB, RB, LT, RT, BACK, START, L3, R3,
    UP, DOWN, LEFT, RIGHT, FORWARD, BACKWARD,
}

/** Um passo de uma sequencia: estas entradas durante [frames] ticks de 60 Hz. */
data class MacroStep(val inputs: Set<PadInput>, val frames: Int)

/** O que um botao no ecra faz. */
sealed class ControlAction {
    /** Carrega os botoes do comando enquanto o dedo esta em cima (1 ou varios = combinacao). */
    data class Press(val inputs: Set<PadInput>) : ControlAction()

    /**
     * Toca a sequencia ao carregar. Com [holdLast], o ultimo passo fica
     * premido enquanto o dedo estiver em cima (ex.: carregar ki).
     */
    data class Sequence(val steps: List<MacroStep>, val holdLast: Boolean) : ControlAction()
}

enum class ElementKind { BUTTON, STICK, DPAD }

/** Icone desenhado no botao (estilo Dragon Burst). */
enum class ControlIcon(val drawable: Int) {
    PUNCH(R.drawable.ic_btn_punch),
    KICK(R.drawable.ic_btn_kick),
    KI(R.drawable.ic_btn_ki),
    GUARD(R.drawable.ic_btn_guard),
    CHARGE(R.drawable.ic_btn_charge),
    GRAB(R.drawable.ic_btn_grab),
    TRANSFORM(R.drawable.ic_btn_transform),
    HYPER(R.drawable.ic_btn_hyper),
    COUNTER(R.drawable.ic_btn_counter),
    SWAP(R.drawable.ic_btn_swap),
    START(R.drawable.ic_btn_start),
    BACK(R.drawable.ic_btn_back),
    MACRO(R.drawable.ic_btn_macro),
    // Burst Limit
    SMASH(R.drawable.ic_btn_smash),
    AURA(R.drawable.ic_btn_aura),
    BLOW(R.drawable.ic_btn_blow),
    PURSUIT(R.drawable.ic_btn_pursuit),
    CAMERA(R.drawable.ic_btn_camera),
    SUPER(R.drawable.ic_btn_super),
    ULTIMATE(R.drawable.ic_btn_ultimate),
    DASH(R.drawable.ic_btn_dash),
}

/**
 * Um elemento do layout. Posicao = centro, em fracao da largura/altura do
 * ecra (em paisagem), tamanho = diametro em dp.
 */
data class ControlElement(
    val id: String,
    val kind: ElementKind,
    val action: ControlAction,
    val icon: ControlIcon,
    val label: String,
    val x: Float,
    val y: Float,
    val sizeDp: Float,
    val opacity: Float = 1f,
    val visible: Boolean = true,
)

/**
 * [menu] = layout fora da luta (A/B/X/Y com o simbolo do comando); [map] = o do voo livre
 * da camara livre / modo foto (comando completo: analogico, A/B/X/Y, LB/RB/LT/RT, Start e
 * Back; tambem tem [menu] = true). O outro e o da luta. O GameOverlay troca sozinho.
 */
data class ControlLayout(
    val elements: List<ControlElement>,
    val version: Int = GameActions.LAYOUT_VERSION,
    val menu: Boolean = false,
    val map: Boolean = false,
) {
    val kind: LayoutKind get() = when {
        map -> LayoutKind.MAP
        menu -> LayoutKind.MENU
        else -> LayoutKind.GAMEPLAY
    }
}

enum class LayoutKind { GAMEPLAY, MENU, MAP }

/**
 * Acoes do Burst Limit (Xbox 360, manual e FAQs do GameFAQs): X ataque (rush), Y smash
 * (segurar = carregado), B ki blast (segurar = carregado), A defesa; LB Aura Spark, LT
 * golpe que lanca (blow-away), RT perseguicao / defesa final (segurar = postura), RB
 * transformar. L3 carrega ki (opcao ki_charge do Burst Limit Recompiled).
 *
 * Comandos com direcao (relativa ao adversario, "frente" = na direcao dele):
 *  - especial 1: frente + B; especial 2: tras + B; golpe final: cima + B (ki cheio);
 *  - dash: direcao + A (frente + A tambem e o vanish dodge, no momento do golpe);
 *  - agarrar: X + Y; smash pesado (parte a defesa): RT + Y; mega crush: RT + B;
 *  - provocar: Y + A.
 * Os botoes de ajuda mandam a direcao e o botao juntos (a direcao 2 ticks antes), por isso
 * nao e preciso segurar o analogico.
 */
object GameActions {
    /**
     * Versao do layout da luta. 2 juntava os botoes de comando por omissao; 3 tira-os outra
     * vez (pedido do usuario: tela limpa, os especiais fazem-se bem a mao) - ficam so no
     * "Adicionar" do editor.
     */
    const val LAYOUT_VERSION = 3

    val RUSH = setOf(PadInput.X)
    val SMASH = setOf(PadInput.Y)
    val KI_BLAST = setOf(PadInput.B)
    val GUARD = setOf(PadInput.A)

    /** Direcao primeiro e depois direcao + botao (o jogo le a direcao no tick do botao). */
    fun directional(dir: PadInput, button: PadInput) = ControlAction.Sequence(
        listOf(MacroStep(setOf(dir), 2), MacroStep(setOf(dir, button), 6)),
        holdLast = true,
    )

    val SUPER_1 = directional(PadInput.FORWARD, PadInput.B)
    val SUPER_2 = directional(PadInput.BACKWARD, PadInput.B)
    val ULTIMATE = directional(PadInput.UP, PadInput.B)
    val DASH = directional(PadInput.FORWARD, PadInput.A)
    val THROW = ControlAction.Press(setOf(PadInput.X, PadInput.Y))
    val HEAVY_SMASH = ControlAction.Press(setOf(PadInput.RT, PadInput.Y))
    val MEGA_CRUSH = ControlAction.Press(setOf(PadInput.RT, PadInput.B))

    /** Ids dos botoes de ajuda, para o menu "Adicionar" do editor. */
    val HELPERS: List<Triple<String, ControlAction, ControlIcon>> = listOf(
        Triple("charge", ControlAction.Press(setOf(PadInput.L3)), ControlIcon.CHARGE),
        Triple("aura", ControlAction.Press(setOf(PadInput.LB)), ControlIcon.AURA),
        Triple("transform", ControlAction.Press(setOf(PadInput.RB)), ControlIcon.TRANSFORM),
        Triple("blow", ControlAction.Press(setOf(PadInput.LT)), ControlIcon.BLOW),
        Triple("pursuit", ControlAction.Press(setOf(PadInput.RT)), ControlIcon.PURSUIT),
        Triple("menu", ControlAction.Press(setOf(PadInput.BACK, PadInput.START)), ControlIcon.MACRO),
        Triple("super1", SUPER_1, ControlIcon.SUPER),
        Triple("super2", SUPER_2, ControlIcon.SUPER),
        Triple("ultimate", ULTIMATE, ControlIcon.ULTIMATE),
        Triple("dash", DASH, ControlIcon.DASH),
        Triple("throw", THROW, ControlIcon.GRAB),
        Triple("heavy", HEAVY_SMASH, ControlIcon.SMASH),
        Triple("megacrush", MEGA_CRUSH, ControlIcon.KI),
    )

    /**
     * Botoes de comando (cada um ja leva a direcao). Nao entram no layout por omissao; sao as
     * posicoes que o editor usa e as que a versao 2 punha (para os tirar dos layouts gravados
     * que ninguem mexeu).
     */
    val COMMAND_BUTTONS: List<ControlElement> = listOf(
        ControlElement("super1", ElementKind.BUTTON, SUPER_1, ControlIcon.SUPER, "▶B", 0.30f, 0.40f, 52f),
        ControlElement("super2", ElementKind.BUTTON, SUPER_2, ControlIcon.SUPER, "◀B", 0.05f, 0.36f, 48f),
        ControlElement("ultimate", ElementKind.BUTTON, ULTIMATE, ControlIcon.ULTIMATE, "▲B", 0.17f, 0.27f, 52f),
        ControlElement("dash", ElementKind.BUTTON, DASH, ControlIcon.DASH, "▶A", 0.33f, 0.62f, 46f),
        ControlElement("throw", ElementKind.BUTTON, THROW, ControlIcon.GRAB, "X+Y", 0.62f, 0.88f, 46f),
    )

    fun defaultLayout(): ControlLayout {
        val cx = 0.875f
        val cy = 0.68f
        return ControlLayout(
            listOf(
                ControlElement("stick", ElementKind.STICK, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("dpad", ElementKind.DPAD, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("rush", ElementKind.BUTTON, ControlAction.Press(RUSH), ControlIcon.PUNCH, "X", cx - 0.075f, cy, 62f),
                ControlElement("smash", ElementKind.BUTTON, ControlAction.Press(SMASH), ControlIcon.SMASH, "Y", cx, cy - 0.18f, 62f),
                ControlElement("ki", ElementKind.BUTTON, ControlAction.Press(KI_BLAST), ControlIcon.KI, "B", cx + 0.075f, cy, 62f),
                ControlElement("guard", ElementKind.BUTTON, ControlAction.Press(GUARD), ControlIcon.GUARD, "A", cx, cy + 0.18f, 62f),
                // Ombros e gatilhos num arco a volta dos A/B/X/Y (o polegar direito chega a
                // todos sem cobrir a cena); L3 (carregar ki) em baixo, so com a opcao ligada.
                ControlElement("aura", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.LB)), ControlIcon.AURA, "LB", 0.705f, 0.70f, 48f),
                ControlElement("blow", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.LT)), ControlIcon.BLOW, "LT", 0.725f, 0.45f, 48f),
                ControlElement("transform", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.RB)), ControlIcon.TRANSFORM, "RB", 0.80f, 0.29f, 48f),
                ControlElement("pursuit", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.RT)), ControlIcon.PURSUIT, "RT", 0.945f, 0.30f, 48f),
                ControlElement("charge", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.L3)), ControlIcon.CHARGE, "L3", 0.72f, 0.93f, 44f),
                ControlElement("back", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.BACK, "BACK", 0.33f, 0.09f, 42f),
                ControlElement("start", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.START)), ControlIcon.START, "START", 0.67f, 0.09f, 42f),
            ),
        )
    }

    /**
     * Menus do jogo: confirma-se com A, volta-se com B; X e Y mudam de cor/pagina em
     * varios ecras, LB/RB escolhem a forma inicial na selecao (start_forms) e o R2
     * abre a pagina das personagens extra. Back + Start abre o menu do Burst Limit.
     */
    fun defaultMenuLayout(): ControlLayout {
        fun pad(id: String, input: PadInput, x: Float, y: Float, size: Float) =
            ControlElement(id, ElementKind.BUTTON, ControlAction.Press(setOf(input)), ControlIcon.MACRO,
                classicLabel(input, false), x, y, size)
        val cx = 0.875f
        val cy = 0.68f
        return ControlLayout(
            listOf(
                ControlElement("stick", ElementKind.STICK, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("dpad", ElementKind.DPAD, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                pad("m_x", PadInput.X, cx - 0.075f, cy, 58f),
                pad("m_y", PadInput.Y, cx, cy - 0.18f, 58f),
                pad("m_b", PadInput.B, cx + 0.075f, cy, 58f),
                pad("m_a", PadInput.A, cx, cy + 0.18f, 66f),
                pad("m_lb", PadInput.LB, 0.70f, 0.40f, 46f),
                pad("m_rb", PadInput.RB, 0.95f, 0.40f, 46f),
                pad("m_rt", PadInput.RT, 0.95f, 0.22f, 42f),
                ControlElement("m_back", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.BACK, "BACK", 0.33f, 0.09f, 42f),
                ControlElement("m_start", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.START)), ControlIcon.START, "START", 0.67f, 0.09f, 42f),
            ),
            menu = true,
        )
    }

    /**
     * Camara livre / modo foto do Burst Limit (free_camera): analogico esquerdo move,
     * direito olha, LB/RB desce/sobe, LT/RT mais lento/rapido, D-pad zoom e inclinacao,
     * A esconde o HUD, X congela, Y repoe, B sai. Comando completo, com os dois
     * analogicos (o D-pad vem escondido; mostra-se no editor).
     */
    fun defaultMapLayout(): ControlLayout {
        fun pad(id: String, input: PadInput, x: Float, y: Float, size: Float) =
            ControlElement(id, ElementKind.BUTTON, ControlAction.Press(setOf(input)), ControlIcon.MACRO,
                classicLabel(input, false), x, y, size)
        val cx = 0.875f
        val cy = 0.68f
        return ControlLayout(
            listOf(
                ControlElement("stick", ElementKind.STICK, ControlAction.Press(emptySet()), ControlIcon.MACRO, "", 0.15f, 0.66f, 150f),
                ControlElement("dpad", ElementKind.DPAD, ControlAction.Press(emptySet()), ControlIcon.MACRO, "D-pad", 0.15f, 0.66f, 150f,
                    visible = false),
                pad("u_x", PadInput.X, cx - 0.075f, cy, 60f),
                pad("u_y", PadInput.Y, cx, cy - 0.18f, 60f),
                pad("u_b", PadInput.B, cx + 0.075f, cy, 60f),
                pad("u_a", PadInput.A, cx, cy + 0.18f, 60f),
                pad("u_lb", PadInput.LB, 0.70f, 0.46f, 50f),
                pad("u_lt", PadInput.LT, 0.72f, 0.26f, 50f),
                pad("u_rb", PadInput.RB, 0.95f, 0.40f, 50f),
                pad("u_rt", PadInput.RT, 0.85f, 0.26f, 50f),
                ControlElement("u_back", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.BACK)), ControlIcon.BACK, "BACK", 0.33f, 0.09f, 42f),
                ControlElement("u_start", ElementKind.BUTTON, ControlAction.Press(setOf(PadInput.START)), ControlIcon.START, "START", 0.67f, 0.09f, 42f),
            ),
            menu = true,
            map = true,
        )
    }

    fun defaultLayout(kind: LayoutKind): ControlLayout = when (kind) {
        LayoutKind.MENU -> defaultMenuLayout()
        LayoutKind.MAP -> defaultMapLayout()
        LayoutKind.GAMEPLAY -> defaultLayout()
    }

    /** Rotulo do comando classico (Xbox / PlayStation) para uma entrada. */
    fun classicLabel(input: PadInput, playstation: Boolean): String = when (input) {
        PadInput.A -> if (playstation) "✕" else "A"
        PadInput.B -> if (playstation) "○" else "B"
        PadInput.X -> if (playstation) "□" else "X"
        PadInput.Y -> if (playstation) "△" else "Y"
        PadInput.LB -> if (playstation) "L1" else "LB"
        PadInput.RB -> if (playstation) "R1" else "RB"
        PadInput.LT -> if (playstation) "L2" else "LT"
        PadInput.RT -> if (playstation) "R2" else "RT"
        PadInput.BACK -> if (playstation) "SELECT" else "BACK"
        PadInput.START -> "START"
        PadInput.L3 -> "L3"
        PadInput.R3 -> "R3"
        PadInput.UP -> "↑"
        PadInput.DOWN -> "↓"
        PadInput.LEFT -> "←"
        PadInput.RIGHT -> "→"
        PadInput.FORWARD -> "▶"
        PadInput.BACKWARD -> "◀"
    }
}

/** Gravacao do layout em JSON (pasta interna da app). */
object ControlLayoutStore {

    private fun file(kind: LayoutKind) = when (kind) {
        LayoutKind.MENU -> AppPaths.controlsMenuFile
        LayoutKind.MAP -> AppPaths.controlsMapFile
        LayoutKind.GAMEPLAY -> AppPaths.controlsFile
    }

    fun load(kind: LayoutKind = LayoutKind.GAMEPLAY): ControlLayout {
        val f = file(kind)
        if (!f.isFile) return GameActions.defaultLayout(kind)
        return try {
            val saved = fromJson(JSONObject(f.readText())).copy(menu = kind != LayoutKind.GAMEPLAY, map = kind == LayoutKind.MAP)
            // Layout gravado na versao 2: tira os botoes de comando que a 2 juntou sozinha e
            // que continuam onde ela os pos (os que o usuario mexeu ficam).
            if (kind == LayoutKind.GAMEPLAY && saved.version < GameActions.LAYOUT_VERSION) {
                val auto = GameActions.COMMAND_BUTTONS.associateBy { it.id }
                saved.copy(
                    elements = saved.elements.filterNot { e ->
                        val d = auto[e.id]
                        d != null && kotlin.math.abs(d.x - e.x) < 0.002f && kotlin.math.abs(d.y - e.y) < 0.002f &&
                            d.sizeDp == e.sizeDp
                    },
                    version = GameActions.LAYOUT_VERSION,
                )
            } else {
                saved
            }
        } catch (e: Exception) {
            GameActions.defaultLayout(kind)
        }
    }

    fun save(layout: ControlLayout) {
        val f = file(layout.kind)
        f.parentFile?.mkdirs()
        val tmp = java.io.File(f.parentFile, f.name + ".tmp")
        tmp.writeText(toJson(layout).toString(2))
        if (!tmp.renameTo(f)) {
            f.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun reset() {
        AppPaths.controlsFile.delete()
        AppPaths.controlsMenuFile.delete()
        AppPaths.controlsMapFile.delete()
    }

    private fun inputsToJson(s: Set<PadInput>) = JSONArray().apply { s.forEach { put(it.name) } }
    private fun inputsFromJson(a: JSONArray?): Set<PadInput> =
        if (a == null) emptySet() else (0 until a.length()).mapNotNull { i ->
            runCatching { PadInput.valueOf(a.getString(i)) }.getOrNull()
        }.toSet()

    private fun actionToJson(a: ControlAction): JSONObject = when (a) {
        is ControlAction.Press -> JSONObject().put("type", "press").put("inputs", inputsToJson(a.inputs))
        is ControlAction.Sequence -> JSONObject().put("type", "sequence").put("holdLast", a.holdLast)
            .put("steps", JSONArray().apply {
                a.steps.forEach { put(JSONObject().put("inputs", inputsToJson(it.inputs)).put("frames", it.frames)) }
            })
    }

    private fun actionFromJson(o: JSONObject): ControlAction = when (o.optString("type")) {
        "sequence" -> {
            val steps = o.optJSONArray("steps") ?: JSONArray()
            ControlAction.Sequence(
                (0 until steps.length()).map { i ->
                    val s = steps.getJSONObject(i)
                    MacroStep(inputsFromJson(s.optJSONArray("inputs")), s.optInt("frames", 2).coerceIn(1, 600))
                },
                o.optBoolean("holdLast", false),
            )
        }
        else -> ControlAction.Press(inputsFromJson(o.optJSONArray("inputs")))
    }

    fun toJson(layout: ControlLayout): JSONObject = JSONObject().put("version", layout.version).put(
        "elements",
        JSONArray().apply {
            layout.elements.forEach { e ->
                put(
                    JSONObject()
                        .put("id", e.id).put("kind", e.kind.name).put("action", actionToJson(e.action))
                        .put("icon", e.icon.name).put("label", e.label)
                        .put("x", e.x.toDouble()).put("y", e.y.toDouble()).put("size", e.sizeDp.toDouble())
                        .put("opacity", e.opacity.toDouble()).put("visible", e.visible),
                )
            }
        },
    )

    fun fromJson(o: JSONObject): ControlLayout {
        val arr = o.getJSONArray("elements")
        val list = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            ControlElement(
                id = e.getString("id"),
                kind = runCatching { ElementKind.valueOf(e.getString("kind")) }.getOrDefault(ElementKind.BUTTON),
                action = actionFromJson(e.optJSONObject("action") ?: JSONObject()),
                icon = runCatching { ControlIcon.valueOf(e.optString("icon")) }.getOrDefault(ControlIcon.MACRO),
                label = e.optString("label"),
                x = e.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f),
                y = e.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f),
                sizeDp = e.optDouble("size", 60.0).toFloat().coerceIn(28f, 260f),
                opacity = e.optDouble("opacity", 1.0).toFloat().coerceIn(0.05f, 1f),
                visible = e.optBoolean("visible", true),
            )
        }
        return ControlLayout(list, o.optInt("version", 1))
    }
}
