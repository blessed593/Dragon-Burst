#!/usr/bin/env python3
"""Referencia offline do render nativo abaixo de 1x (patch 0091, draw_resolution_percent).

Abaixo de 1x (0089) o jogo desenha no canto superior esquerdo dos render targets com a
percentagem p do tamanho e as texturas feitas de dados resolvidos tem DownscaleCeil(tamanho, p).
O pixel do host i tem o centro em i + 0,5 = f * (s + 0,5), f = p / 100, s = a posicao no ecra do
guest (D3D9: centros dos pixeis em inteiros; o Xenia soma meio pixel e o 0089 escala o viewport
com ele). Um quad de ecra inteiro com UV = UV0 + a * s (texeis do guest) amostra a textura do
host, de largura W_h, em X = (UV0 + a * s + k) / W_g * W_h texeis (k = desvio do VS em texeis do
guest). Com W_h = f * W_g exato:  X = a * (i + 0,5) + f * (UV0 - a / 2) + f * k,  e os UV do BL
tem UV0 = a / 2 (reducao 1 e 2, desfoque/composicao/contorno 0,5 e 1): X = a * (i + 0,5) + f * k.

Este script:
  1. "posicoes": para cada passo do render nativo e cada percentagem (100, 75, 67 por omissao)
     calcula as amostras que o draw do jogo faz (indice do texel + peso bilinear, ou texel do
     filtro de ponto) para todos os pixeis do alvo, com os tamanhos reais do host, e compara com
     o que o compute faz (0091: a 1x as contas de sempre; abaixo de 1x, SCALED, desvios k * f).
     Diz em quantos pixeis o texel ou o peso diferem e se os pesos sao exatos (multiplos de 1/16).
  2. com capturas (.dfc com resolve_readback): os passes do brilho (reducao, desfoque,
     composicao) com as entradas reais (a 1x, comparado com o resolve do jogo, como o
     ref_glow.py; a p < 100, o canto da entrada ao tamanho do host como entrada) - o modelo do
     draw do jogo (amostras acima, bilinear em fp64) contra o compute SCALED em fp32.

Uso:
  python3 tools/ref_below1x.py [--percent 75,67] [captura.dfc ...] [--conv mul|div]
"""
import math
import os
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ref_glow as rg  # noqa: E402

f32 = np.float32


def host(n, p):
    """DownscaleCeil do SDK."""
    return (n * p + 99) // 100 if p < 100 else n


# Passos: alvo (guest), fonte (guest), UV0 e a (texeis do guest por pixel do alvo), desvios k
# (texeis do guest, por eixo), filtro. Do mapa dos passes (NATIVE_RENDERER.md 7.x).
PASSES = [
    # nome, alvo, fonte, uv0, a, desvios x, desvios y, bilinear
    ("imagem final direta (passo 1)", 1280, 720, 1280, 720, 0.5, 1.0, [0], [0], True),
    ("profundidade linear (passo 2)", 1280, 720, 1280, 720, 0.5, 1.0, [0], [0], False),
    ("contorno (3b)", 1280, 720, 1280, 720, 0.5, 1.0, [-1, 0, 1], [-1, 0, 1], False),
    ("reducao 640 (3b)", 640, 360, 1280, 720, 1.0, 2.0, [0, 1], [0, 1], True),
    ("desfoque 640 (3b)", 640, 360, 640, 360, 0.5, 1.0, list(range(-5, 6)), [0], True),
    ("composicao, brilho (3b)", 1280, 720, 640, 360, 0.25, 0.5, [0], [0], True),
    ("composicao, cena (3b)", 1280, 720, 1280, 720, 0.5, 1.0, [0], [0], False),
]


def game_coords(p, tw, src_w, uv0, a, k):
    """Coordenada X (texeis do host, centros em n + 0,5) da amostra do draw do jogo para cada
    pixel do alvo no host (0..host(tw)-1), desvio k texeis do guest."""
    f = p / 100.0
    i = np.arange(host(tw, p), dtype=np.float64)
    s = (i + 0.5) / f - 0.5
    return (uv0 + a * s + k) / src_w * host(src_w, p)


def compute_coords(p, tw, src_w, uv0, a, k):
    """O que o compute faz (0091): X = a * (i + 0,5) + f * k nos tamanhos do host."""
    f = p / 100.0
    i = np.arange(host(tw, p), dtype=np.float64)
    return a * (i + 0.5) + f * k


def check_positions(percents):
    print("== posicoes das amostras (draw do jogo vs compute)")
    for p in percents:
        steps_ok = []
        for name, tw, th, sw, sh, uv0, a, kx, ky, bilinear in PASSES:
            bad = 0
            total = 0
            weights_exact = True
            for axis, (t, src, ks) in enumerate(((tw, sw, kx), (th, sh, ky))):
                for k in ks:
                    # (arredondadas a 1e-9: so o erro do fp64 nas contas daqui)
                    g = np.round(game_coords(p, t, src, uv0, a, k), 9)
                    c = np.round(compute_coords(p, t, src, uv0, a, k), 9)
                    if bilinear:
                        gi, ci = np.floor(g - 0.5), np.floor(c - 0.5)
                        gw, cw = (g - 0.5) - gi, (c - 0.5) - ci
                        diff = (gi != ci) | (np.abs(gw - cw) > 1e-9)
                        weights_exact &= bool(np.all(np.abs(cw * 16 - np.round(cw * 16)) < 1e-9))
                    else:
                        # Ponto: o texel e floor(X); o compute usa i + k (ou o centro): igual se
                        # o texel do jogo for o mesmo, e longe da fronteira (> 1e-3 texel).
                        gi = np.floor(g)
                        ci = np.arange(g.size) + k  # o compute le o texel i + k do host
                        near = np.abs(g - np.round(g)) < 1e-3
                        diff = (gi != ci) | near
                    bad += int(diff.sum())
                    total += diff.size
            exact_sizes = all(host(n, p) * 100 == n * p for n in (tw, th, sw, sh))
            verdict = "igual" if bad == 0 and (weights_exact or not bilinear) else "DIFERENTE"
            print("  %3d%%  %-32s tamanhos %s, amostras diferentes %d de %d, pesos %s -> %s" % (
                p, name, "exatos" if exact_sizes else "arredondados", bad, total,
                "exatos" if weights_exact else "nao exatos", verdict))
            steps_ok.append((name, verdict == "igual"))
        print("  %3d%%  ligados: %s" % (p, ", ".join(n for n, ok in steps_ok if ok) or "nenhum"))


# --- Compute SCALED (fp32, as contas do kSource com SCALED=1) -----------------------------

def lerp(a, b, w):
    w = f32(w)
    return ((a * (f32(1) - w)).astype(f32) + (b * w).astype(f32)).astype(f32)


def scaled_downsample(t0, k0, k1, w, h, step):
    xs, ys = np.arange(w), np.arange(h)

    def bil(ox, oy):
        x = (2 * xs + 0.5 + ox).astype(f32)
        y = (2 * ys + 0.5 + oy).astype(f32)
        bx, by = np.floor(x), np.floor(y)
        wx = (x - bx).astype(f32)[None, :, None]
        wy = (y - by).astype(f32)[:, None, None]
        bx, by = bx.astype(np.int64), by.astype(np.int64)
        top = lerp(t0.at(bx, by), t0.at(bx + 1, by), wx)
        bottom = lerp(t0.at(bx, by + 1), t0.at(bx + 1, by + 1), wx)
        return lerp(top, bottom, wy)

    st = f32(step)
    b00, b10, b01, b11 = bil(0, 0), bil(st, 0), bil(0, st), bil(st, st)
    m = np.maximum(np.maximum(np.maximum(b00, b10), b01), b11)
    return ((b00 * np.array(k0, f32)).astype(f32) + (m * np.array(k1, f32)).astype(f32)).astype(f32)


def scaled_blur(t0, vertical, k0, k1, k2, w, h, step):
    xs, ys = np.arange(w), np.arange(h)

    def at(off):
        return t0.at(xs, ys + off) if vertical else t0.at(xs + off, ys)

    def tap(k):
        o = f32(k) * f32(step)
        b = math.floor(o)
        wgt = f32(o - b)
        if wgt == 0:
            return at(b)
        return lerp(at(b), at(b + 1), wgt)

    r = at(0)
    weights = [k0[0], k0[1], k0[2], k0[3], k1[0]]
    for k in range(1, 6):
        pair = (tap(k) + tap(-k)).astype(f32)
        r = (r + (pair * f32(weights[k - 1])).astype(f32)).astype(f32)
    return (np.array(k2, f32) * r).astype(f32)


# --- Modelo do draw do jogo a p% (amostras de game_coords, bilinear em fp64) --------------

def model_sample(tex, xs_coord, ys_coord):
    """Bilinear com borda = aresta nas coordenadas do host (centros em n + 0,5), fp64."""
    t = tex.t.astype(np.float64)
    x, y = xs_coord - 0.5, ys_coord - 0.5
    x0, y0 = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64)
    wx, wy = (x - x0)[None, :, None], (y - y0)[:, None, None]

    def g(xi, yi):
        return t[np.clip(yi, 0, tex.h - 1)][:, np.clip(xi, 0, tex.w - 1)]

    top = g(x0, y0) * (1 - wx) + g(x0 + 1, y0) * wx
    bottom = g(x0, y0 + 1) * (1 - wx) + g(x0 + 1, y0 + 1) * wx
    return (top * (1 - wy) + bottom * wy).astype(f32)


def model_downsample(t0, k0, k1, p):
    def b(kx, ky):
        return model_sample(t0, game_coords(p, 640, 1280, 1.0, 2.0, kx),
                            game_coords(p, 360, 720, 1.0, 2.0, ky))
    b00, b10, b01, b11 = b(0, 0), b(1, 0), b(0, 1), b(1, 1)
    m = np.maximum(np.maximum(np.maximum(b00, b10), b01), b11)
    return ((b00 * np.array(k0, f32)).astype(f32) + (m * np.array(k1, f32)).astype(f32)).astype(f32)


def model_blur(t0, vertical, k0, k1, k2, p):
    def tap(k):
        kx, ky = (0, k) if vertical else (k, 0)
        return model_sample(t0, game_coords(p, 640, 640, 0.5, 1.0, kx),
                            game_coords(p, 360, 360, 0.5, 1.0, ky))
    r = tap(0)
    weights = [k0[0], k0[1], k0[2], k0[3], k1[0]]
    for k in range(1, 6):
        pair = (tap(k) + tap(-k)).astype(f32)
        r = (r + (pair * f32(weights[k - 1])).astype(f32)).astype(f32)
    return (np.array(k2, f32) * r).astype(f32)


def model_composite(t0, t1, k0, p):
    glow = model_sample(t1, game_coords(p, 1280, 640, 0.25, 0.5, 0),
                        game_coords(p, 720, 360, 0.25, 0.5, 0))
    scene = model_sample(t0, game_coords(p, 1280, 1280, 0.5, 1.0, 0),
                         game_coords(p, 720, 720, 0.5, 1.0, 0))
    return ((glow * f32(k0[0])).astype(f32) + (scene * f32(k0[1])).astype(f32)).astype(f32)


def crop(img, w, h):
    return img[:h, :w]


def check_captures(paths, percents, conv_opt):
    print("== passes do brilho com as entradas das capturas")
    for path in paths:
        conv = conv_opt or rg.device_conv(path)
        resolves, snaps = rg.load(path)
        for d in sorted(snaps):
            regs, t = snaps[d]
            after = [r for r in resolves if r["draw"] > d]
            if not after or "img" not in after[0]:
                continue
            out = after[0]
            before = [r for r in resolves if r["draw"] < d and "img" in r and r["frame"] == out["frame"]]
            f0 = rg.fetch(regs, 0)
            base0 = f0[1] & 0xFFFFF000
            bgra0 = ((f0[3] >> 1) & 0xFFF) == 0x60A
            src0 = [r for r in before if r["base"] == base0]
            if not src0:
                continue
            # O draw nao correu nessa build: a saida e um resolve anterior.
            if any(r["img"].shape == out["img"].shape and (r["img"] == out["img"]).all()
                   for r in before[-6:]):
                continue
            img0 = src0[-1]["img"]
            src1 = None
            if t[7] == rg.PS_COMPOSITE:
                f1 = rg.fetch(regs, 1)
                s1 = [r for r in before if r["base"] == f1[1] & 0xFFFFF000]
                if not s1:
                    continue
                src1 = (s1[-1]["img"], ((f1[3] >> 1) & 0xFFF) == 0x60A)
            line = []
            for p in [100] + [q for q in percents if q < 100]:
                f = p / 100.0
                if t[7] == rg.PS_DOWNSAMPLE:
                    tex0 = rg.Tex(crop(img0, host(1280, p), host(720, p)), conv, bgra0)
                    k0, k1 = rg.psc(regs, 0), rg.psc(regs, 1)
                    model = model_downsample(tex0, k0, k1, p)
                    if p == 100:
                        comp = rg.downsample(tex0, k0, k1, 640, 360)
                    else:
                        comp = scaled_downsample(tex0, k0, k1, host(640, p), host(360, p), f)
                elif t[7] == rg.PS_BLUR:
                    c254, c255 = rg.psc(regs, 254), rg.psc(regs, 255)
                    k2 = (np.array(rg.psc(regs, 0), f32) * f32(c255[2])).astype(f32)
                    vertical = t[6] == rg.VS_BLUR_V
                    kk0, kk1 = (c255[1], c254[0], c254[1], c255[0]), (c255[3],)
                    tex0 = rg.Tex(crop(img0, host(640, p), host(360, p)), conv, bgra0)
                    model = model_blur(tex0, vertical, kk0, kk1, k2, p)
                    if p == 100:
                        comp = rg.blur(tex0, vertical, kk0, kk1, k2, 640, 360)
                    else:
                        comp = scaled_blur(tex0, vertical, kk0, kk1, k2, host(640, p), host(360, p), f)
                else:
                    c0 = rg.psc(regs, 0)
                    tex0 = rg.Tex(crop(img0, host(1280, p), host(720, p)), conv, bgra0)
                    tex1 = rg.Tex(crop(src1[0], host(640, p), host(360, p)), conv, src1[1])
                    model = model_composite(tex0, tex1, (c0[0], c0[3]), p)
                    comp = rg.composite(tex0, tex1, (c0[0], c0[3]), host(1280, p), host(720, p))
                a = rg.pack(model, out["swap"])
                b = rg.pack(comp, out["swap"])
                diff = np.abs(a - b)
                text = "%d%%: modelo vs compute erro max %d, %d de %d pixeis" % (
                    p, diff.max(), int((diff.max(-1) > 0).sum()), diff.shape[0] * diff.shape[1])
                if p == 100:
                    g = np.abs(a - out["img"].astype(np.int64))
                    text += ", modelo vs jogo erro max %d, %d pixeis" % (
                        g.max(), int((g.max(-1) > 0).sum()))
                line.append(text)
            print("%s draw %d (%s): %s" % (rg.name_of(path), d, rg.PASSES[t[7]], "; ".join(line)))


def main():
    args = sys.argv[1:]
    percents = [75, 67]
    conv_opt = None
    if "--percent" in args:
        i = args.index("--percent")
        percents = [int(v) for v in args[i + 1].split(",")]
        del args[i:i + 2]
    if "--conv" in args:
        i = args.index("--conv")
        conv_opt = args[i + 1]
        del args[i:i + 2]
    check_positions([100] + percents)
    paths = [a for a in args if not a.startswith("--")]
    if paths:
        check_captures(paths, percents, conv_opt)
    return 0


if __name__ == "__main__":
    sys.exit(main())
