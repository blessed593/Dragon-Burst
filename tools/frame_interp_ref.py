#!/usr/bin/env python3
"""Modelo de referencia (numpy) da interpolacao de frames do presenter Vulkan (patch 0092,
"120 FPS (interpolado)"). Faz as MESMAS contas que os compute shaders de
src/ui/vulkan/vulkan_presenter.cpp (dbz3_frame_interp), para validar o algoritmo fora do
aparelho e afinar os limiares:

  1. luma a 1/4 (Q) e 1/16 (S) da imagem do jogo;
  2. movimento grosso em S: busca simetrica +-3 texels (prev(c - v/2) contra cur(c + v/2)),
     janela 3x3, e meio texel de refinamento;
  3. movimento por bloco de 8x8 pixeis em Q: candidatos = vetores S (o do bloco e 4 em cruz) + zero,
     refinamento +-1 e +-0,5 texel Q, janela 4x4; blocos com erro alto contam para o corte de cena;
  4. por pixel: pixel igual nos dois frames -> o atual (HUD e fundo parado); corte de cena -> o
     atual; senao o melhor de {vetor interpolado, vetor do bloco, zero} pelo erro de cor entre
     as duas amostras; erro alto -> mistura simples dos dois frames (oclusao).

As capturas .dfc so trazem os resolves lidos de volta do 1.o frame, por isso nao ha pares de
frames consecutivos reais. Os pares sao feitos de UM frame real: a imagem final, a mesma sem
HUD (resolve antes do HUD) e a profundidade linear separam fundo, personagens e HUD; o fundo
(com os buracos dos personagens preenchidos) e cada personagem andam com movimentos diferentes e
o HUD fica parado. Assim ha oclusoes reais e uma referencia exata em t = 0,5.

Uso:
  python3 tools/frame_interp_ref.py --dir pasta_dos_resolves [--dir2 outra] --out previa.png
  (pasta = saida de tools/dfc_textures.py captura.dfc --out pasta --resolves)
"""
import argparse
import glob
import os
import sys

import numpy as np
from PIL import Image

LUMA = np.array([0.299, 0.587, 0.114], np.float32)
# Q e S guardam (Y, (B - Y) / 2, (R - Y) / 2): o amarelo do Goku e o branco do ceu quase nao
# diferem em luma, a cor separa-os (custa so ALU, as amostras sao as mesmas em rgba16f).
YUV = np.array([[0.299, -0.1495, 0.3505], [0.587, -0.2935, -0.2935], [0.114, 0.443, -0.057]],
               np.float32)
R_COARSE = 3             # busca grossa: +-3 texels S (= +-48 px a 720p; o refinamento soma +-6)
COARSE_CANDS = 5         # vetores S candidatos por bloco: o do bloco e os 4 vizinhos em cruz
LAMBDA_COARSE = 0.002    # penalizacao por texel S de |v|1 (prefere vetores pequenos)
LAMBDA_REFINE = 0.0005   # idem por texel Q
T_CUT_BLOCK = 0.15       # erro medio de luma de um bloco que conta como "nao encontrado"
CUT_FRACTION = 0.5       # mais de metade dos blocos assim = corte de cena
EPS_STATIC = 1.5 / 255   # pixel igual nos dois frames (max por canal)
T_LO, T_HI = 0.03, 0.10  # erro de cor por pixel: abaixo de T_LO movimento, acima de T_HI recurso
K_COHERENCE = 0.004      # penalizacao por pixel de vetor incoerente nas amostras (cor 0..1)
COHERENCE_CAP = 32.0     # ... ate 32 px (0,128)
COHERENT_PX = 4.0        # lado coerente: vetor do bloco da amostra a menos de 4 px deste


# ------------------------------------------------------------------ amostragem como no Vulkan

def sample(img, x, y):
    """Filtro linear com clamp-to-edge; x, y em pixeis com centros em i + 0,5."""
    h, w = img.shape[:2]
    fx, fy = x - 0.5, y - 0.5
    x0, y0 = np.floor(fx), np.floor(fy)
    ax, ay = (fx - x0)[..., None], (fy - y0)[..., None]
    x0 = x0.astype(np.int64)
    y0 = y0.astype(np.int64)
    xa, xb = np.clip(x0, 0, w - 1), np.clip(x0 + 1, 0, w - 1)
    ya, yb = np.clip(y0, 0, h - 1), np.clip(y0 + 1, 0, h - 1)
    im = img if img.ndim == 3 else img[..., None]
    r = ((im[ya, xa] * (1 - ax) + im[ya, xb] * ax) * (1 - ay) +
         (im[yb, xa] * (1 - ax) + im[yb, xb] * ax) * ay)
    return r if img.ndim == 3 else r[..., 0]


def fetch(img, ix, iy):
    h, w = img.shape[:2]
    return img[np.clip(iy, 0, h - 1), np.clip(ix, 0, w - 1)]


# ------------------------------------------------------------------ passes (iguais aos shaders)

def scale_k(w):
    return max(1, (w + 640) // 1280)


def downsample(img):
    """Passe 1: luma Q (1/4k) por 2k x 2k amostras bilineares, e S = media 4x4 de Q."""
    h, w = img.shape[:2]
    k = scale_k(w)
    d = 4 * k
    wq, hq = -(-w // d), -(-h // d)
    ws, hs = -(-wq // 4), -(-hq // 4)
    # Q numa grelha alinhada a 4 (os grupos 8x8 do shader calculam texels Q fora da imagem com
    # o clamp do sampler e usam-nos na media de S).
    pq_w, pq_h = ws * 4, hs * 4
    qy, qx = np.mgrid[0:pq_h, 0:pq_w].astype(np.float32)
    acc = np.zeros((pq_h, pq_w, 3), np.float32)
    for a in range(2 * k):
        for b in range(2 * k):
            rgb = sample(img, qx * d + 1 + 2 * a, qy * d + 1 + 2 * b)
            acc += rgb @ YUV
    qpad = acc / (4 * k * k)
    s = qpad.reshape(hs, 4, ws, 4, 3).mean(axis=(1, 3))
    # Q e S ficam em rgba8 (U e V + 0,5): o filtro linear de 32 bits e a taxa cheia nas GPUs moveis.
    return q8(qpad[:hq, :wq]), q8(s), k


YUV_OFFSET = np.array([0.0, 0.5, 0.5], np.float32)


def q8(a):
    return np.round(np.clip(a + YUV_OFFSET, 0, 1) * 255) / 255 - YUV_OFFSET


def yuv_diff(a, b):
    d = np.abs(a - b)
    return d[..., 0] + d[..., 1] + d[..., 2]


def motion_coarse(sp, sc, k):
    hs, ws = sp.shape[:2]
    cy, cx = np.mgrid[0:hs, 0:ws].astype(np.float32) + 0.5
    offs = [(dx, dy) for dy in (-1, 0, 1) for dx in (-1, 0, 1)]

    def cost(vx, vy):
        c = np.zeros_like(cx)
        for dx, dy in offs:
            a = sample(sp, cx + dx - vx * 0.5, cy + dy - vy * 0.5)
            b = sample(sc, cx + dx + vx * 0.5, cy + dy + vy * 0.5)
            c += yuv_diff(a, b)
        return c / 9 + LAMBDA_COARSE * (np.abs(vx) + np.abs(vy))

    best = np.full(cx.shape, np.inf, np.float32)
    bvx = np.zeros_like(cx)
    bvy = np.zeros_like(cx)
    for vy in range(-R_COARSE, R_COARSE + 1):
        for vx in range(-R_COARSE, R_COARSE + 1):
            c = cost(np.full_like(cx, vx), np.full_like(cx, vy))
            m = c < best
            best = np.where(m, c, best)
            bvx = np.where(m, vx, bvx)
            bvy = np.where(m, vy, bvy)
    cvx, cvy = bvx.copy(), bvy.copy()
    for dy in (-0.5, 0.0, 0.5):
        for dx in (-0.5, 0.0, 0.5):
            if dx == 0 and dy == 0:
                continue
            c = cost(cvx + dx, cvy + dy)
            m = c < best
            best = np.where(m, c, best)
            bvx = np.where(m, cvx + dx, bvx)
            bvy = np.where(m, cvy + dy, bvy)
    scale = 16 * k  # texel S -> pixeis
    return np.stack([bvx * scale, bvy * scale], -1), best


def motion_refine(qp, qc, vs, k):
    hq, wq = qp.shape[:2]
    wb, hb = -(-wq // 2), -(-hq // 2)
    by, bx = np.mgrid[0:hb, 0:wb]
    cx = (2 * bx + 1).astype(np.float32)
    cy = (2 * by + 1).astype(np.float32)
    taps = (-1.5, -0.5, 0.5, 1.5)
    q2px = 4 * k

    def cost(vx, vy):
        c = np.zeros_like(cx)
        for oy in taps:
            for ox in taps:
                a = sample(qp, cx + ox - vx * 0.5, cy + oy - vy * 0.5)
                b = sample(qc, cx + ox + vx * 0.5, cy + oy + vy * 0.5)
                c += yuv_diff(a, b)
        return c / 16

    def total(vx, vy, raw):
        return raw + LAMBDA_REFINE * (np.abs(vx) + np.abs(vy))

    best = np.full(cx.shape, np.inf, np.float32)
    braw = best.copy()
    bvx = np.zeros_like(cx)
    bvy = np.zeros_like(cx)

    def consider(vx, vy):
        nonlocal best, braw, bvx, bvy
        raw = cost(vx, vy)
        t = total(vx, vy, raw)
        m = t < best
        best = np.where(m, t, best)
        braw = np.where(m, raw, braw)
        bvx = np.where(m, vx, bvx)
        bvy = np.where(m, vy, bvy)

    consider(np.zeros_like(cx), np.zeros_like(cx))
    sx, sy = bx // 2, by // 2
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            if COARSE_CANDS == 5 and dx != 0 and dy != 0:
                continue
            v = fetch(vs, sx + dx, sy + dy) / q2px
            consider(v[..., 0].astype(np.float32), v[..., 1].astype(np.float32))
    for step in (1.0, 0.5):
        cvx, cvy = bvx.copy(), bvy.copy()
        for dy in (-step, 0.0, step):
            for dx in (-step, 0.0, step):
                if dx == 0 and dy == 0:
                    continue
                consider(cvx + dx, cvy + dy)
    bad = braw > T_CUT_BLOCK
    return np.stack([bvx * q2px, bvy * q2px], -1), braw, int(bad.sum()), bad.size


def warp(prev, cur, v8, cut, k, quality):
    h, w = prev.shape[:2]
    py, px = np.mgrid[0:h, 0:w]
    p0 = prev
    c0 = cur
    static = np.abs(p0 - c0).max(-1) <= EPS_STATIC
    blend0 = 0.5 * (p0 + c0)
    if cut:
        return c0.copy(), np.zeros((h, w), bool), static
    if quality == 0:
        out = np.where(static[..., None], c0, blend0)
        return out, ~static, static
    fx, fy = px + 0.5, py + 0.5
    bsz = 8 * k
    # vetor interpolado (bilinear dos blocos, centros em (b + 0,5) * 8k)
    vb = sample(v8, fx / bsz, fy / bsz)
    ib_x, ib_y = (px // bsz), (py // bsz)
    cands = [vb, fetch(v8, ib_x, ib_y)]
    if quality >= 2:
        for dx, dy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            cands.append(fetch(v8, ib_x + dx, ib_y + dy))

    def block_vec(x, y):
        return fetch(v8, np.floor(x / bsz).astype(np.int64), np.floor(y / bsz).astype(np.int64))

    # candidato zero: sem penalizacao de coerencia (fundo parado, HUD)
    best_s = np.abs(p0 - c0).mean(-1)
    best_e = best_s.copy()
    best_p, best_c = p0, c0
    best_okp = np.ones((h, w), bool)
    best_okc = np.ones((h, w), bool)
    for v in cands:
        vx, vy = v[..., 0], v[..., 1]
        sxp, syp = fx - vx * 0.5, fy - vy * 0.5
        sxc, syc = fx + vx * 0.5, fy + vy * 0.5
        pw = sample(prev, sxp, syp)
        cw = sample(cur, sxc, syc)
        e = np.abs(pw - cw).mean(-1)
        # Coerencia: o que se amostra em cada frame deve mexer-se com este vetor (o vetor do
        # bloco onde a amostra cai). Ao lado de um objeto liso as duas amostras podem cair no
        # objeto com erro baixo; sem isto o objeto "pinta" o fundo que ja deixou.
        dp = np.abs(block_vec(sxp, syp) - v).sum(-1)
        dc = np.abs(block_vec(sxc, syc) - v).sum(-1)
        score = e + K_COHERENCE * np.minimum(dp + dc, COHERENCE_CAP)
        m = score < best_s
        best_s = np.where(m, score, best_s)
        best_e = np.where(m, e, best_e)
        best_p = np.where(m[..., None], pw, best_p)
        best_c = np.where(m[..., None], cw, best_c)
        best_okp = np.where(m, dp <= COHERENT_PX, best_okp)
        best_okc = np.where(m, dc <= COHERENT_PX, best_okc)
    avg = 0.5 * (best_p + best_c)
    # Oclusao: as duas amostras discordam. Se so um lado e coerente com o vetor, fica esse lado
    # (zona a descobrir: o atual; zona a tapar: o anterior); senao mistura dos dois frames.
    one = np.where(best_okc[..., None], best_c, best_p)
    side = (best_okp != best_okc)[..., None]
    fallback = np.where(side, one, blend0)
    wgt = np.clip((best_e - T_LO) / (T_HI - T_LO), 0.0, 1.0)[..., None]
    out = (1 - wgt) * avg + wgt * fallback
    out = np.where(static[..., None], c0, out)
    return out, (wgt[..., 0] > 0.5) & ~static, static


def interpolate(prev, cur, quality=1):
    qp, sp, k = downsample(prev)
    qc, sc, _ = downsample(cur)
    stats = {"k": k, "q": qp.shape, "s": sp.shape}
    if quality == 0:
        out, fb, st = warp(prev, cur, None, False, k, 0)
        stats.update(cut=False, bad_blocks=0, blocks=0, fallback=float(fb.mean()),
                     static=float(st.mean()))
        return out, stats
    vs, _ = motion_coarse(sp, sc, k)
    v8, _, bad, total = motion_refine(qp, qc, vs, k)
    cut = bad > CUT_FRACTION * total
    out, fb, st = warp(prev, cur, v8, cut, k, quality)
    stats.update(cut=bool(cut), bad_blocks=bad, blocks=total, fallback=float(fb.mean()),
                 static=float(st.mean()), v8=v8)
    return out, stats


# ------------------------------------------------------------------ pares sinteticos

def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.float32) / 255.0


def fill_holes(img, known):
    """Push-pull: preenche os pixeis desconhecidos com medias dos conhecidos a volta."""
    levels = [(img * known[..., None], known.astype(np.float32))]
    while min(levels[-1][1].shape) > 2:
        c, wgt = levels[-1]
        h, w = wgt.shape
        h2, w2 = h // 2 * 2, w // 2 * 2
        c = c[:h2, :w2].reshape(h2 // 2, 2, w2 // 2, 2, 3).sum(axis=(1, 3))
        wgt = wgt[:h2, :w2].reshape(h2 // 2, 2, w2 // 2, 2).sum(axis=(1, 3))
        levels.append((c, wgt))
    filled = None
    for c, wgt in reversed(levels):
        cur = c / np.maximum(wgt, 1e-6)[..., None]
        if filled is not None:
            from scipy import ndimage
            zy, zx = cur.shape[0] / filled.shape[0], cur.shape[1] / filled.shape[1]
            up = ndimage.zoom(filled, (zy, zx, 1), order=1, mode="nearest")
            up = up[:cur.shape[0], :cur.shape[1]]
            cur = np.where((wgt > 0)[..., None], cur, up)
        filled = cur
    return np.where(known[..., None], img, filled)


def find_dir_images(d):
    def pick(pattern, last=True):
        fs = sorted(glob.glob(os.path.join(d, pattern)))
        return (fs[-1] if last else fs[0]) if fs else None
    final = pick("*_0C000000_1280x720_*cor0.png") or pick("*_0E1F1000_1280x720_*cor0.png")
    nohud = pick("*_0DE59000_1280x720_*cor0.png")
    depth = pick("*_0F451000_1280x720_32_FLOAT*.png")
    return final, nohud, depth


def build_scene(d):
    from scipy import ndimage
    final, nohud, depth = find_dir_images(d)
    if not (final and nohud and depth):
        raise SystemExit("%s: faltam a imagem final, a sem HUD ou a profundidade" % d)
    f, n = load(final), load(nohud)
    dep = load(depth)[..., 0]
    # Personagens: bem mais perto que o que esta a volta na mesma linha (mediana larga), para nao
    # apanhar o chao perto da camara; fora componentes enormes ou que tocam a borda.
    around = ndimage.median_filter(dep[::4, ::4], size=(1, 61), mode="nearest")
    around = np.repeat(np.repeat(around, 4, 0), 4, 1)[:dep.shape[0], :dep.shape[1]]
    chars = (dep < 0.03) | (dep < 0.55 * around)
    chars = ndimage.binary_opening(chars, iterations=2)
    lab, count = ndimage.label(chars)
    keep = np.zeros(count + 1, bool)
    for i, sl in enumerate(ndimage.find_objects(lab), start=1):
        comp = lab[sl] == i
        touches = sl[0].start == 0 or sl[1].start == 0 or sl[0].stop == chars.shape[0] or \
            sl[1].stop == chars.shape[1]
        keep[i] = comp.sum() < 0.15 * chars.size and not touches and comp.sum() > 400
    chars = keep[lab]
    near = ndimage.binary_dilation(chars, iterations=8)
    hud = (np.abs(n - f).max(-1) > 24 / 255) & ~near
    hud = ndimage.binary_dilation(hud, iterations=1) & ~near
    scene = np.where(hud[..., None], n, f)
    plate = fill_holes(scene, ~ndimage.binary_dilation(chars, iterations=3))
    labels, count = ndimage.label(ndimage.binary_dilation(chars, iterations=2))
    sizes = ndimage.sum(np.ones_like(labels), labels, range(1, count + 1))
    order = np.argsort(sizes)[::-1][:2]
    layers = []
    for i in order:
        m = (labels == i + 1).astype(np.float32)
        m = ndimage.gaussian_filter(m, 0.7)
        layers.append(m)
    return {"scene": scene, "plate": plate, "layers": layers, "hud": hud, "final": f}


def render(sc, t, vb, vchars):
    h, w = sc["scene"].shape[:2]
    py, px = np.mgrid[0:h, 0:w].astype(np.float32) + 0.5
    out = sample(sc["plate"], px - t * vb[0], py - t * vb[1])
    for m, v in zip(sc["layers"], vchars):
        a = sample(m, px - t * v[0], py - t * v[1])[..., None]
        col = sample(sc["scene"], px - t * v[0], py - t * v[1])
        out = out * (1 - a) + col * a
    out = np.where(sc["hud"][..., None], sc["final"], out)
    return np.round(out * 255) / 255  # o jogo entrega 8 bits


def psnr(a, b, mask=None):
    d = (a - b) ** 2
    mse = float(d[mask].mean() if mask is not None else d.mean())
    return 99.0 if mse <= 1e-12 else 10 * np.log10(1.0 / mse)


def to_img(a):
    return Image.fromarray(np.clip(a * 255 + 0.5, 0, 255).astype(np.uint8))


def label(im, text):
    from PIL import ImageDraw
    dr = ImageDraw.Draw(im)
    dr.rectangle((0, 0, 8 + 7 * len(text), 16), fill=(0, 0, 0))
    dr.text((4, 2), text, fill=(255, 255, 255))
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", action="append", required=True,
                    help="pasta com os resolves de uma captura (dfc_textures.py --resolves)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--quality", type=int, default=1)
    a = ap.parse_args()
    cases = [("normal", (-10, 2), [(16, -6), (-12, 4)]),
             ("rapido", (-24, 6), [(36, -14), (-30, 10)])]
    rows = []
    scenes = [build_scene(d) for d in a.dir]
    for si, sc in enumerate(scenes):
        for name, vb, vc in cases:
            prev, gt, cur = render(sc, 0.0, vb, vc), render(sc, 0.5, vb, vc), render(sc, 1.0, vb, vc)
            gen, st = interpolate(prev, cur, a.quality)
            blend = 0.5 * (prev + cur)
            mv = np.abs(prev - cur).max(-1) > EPS_STATIC
            line = ("cena %d %-6s zona em movimento (%.0f%%): gerado %.2f dB | mistura simples %.2f dB | "
                    "repetir %.2f dB || blocos ruins %d/%d corte=%s recurso %.1f%% parados %.1f%%" % (
                        si, name, 100 * mv.mean(), psnr(gen, gt, mv), psnr(blend, gt, mv), psnr(cur, gt, mv),
                        st["bad_blocks"],
                        st["blocks"], st["cut"], 100 * st["fallback"], 100 * st["static"]))
            print(line)
            if name == "rapido":
                rows.append((si, name, prev, gen, cur, gt, blend, sc))
    if len(scenes) >= 2:
        prev, cur = render(scenes[0], 0.0, (0, 0), [(0, 0), (0, 0)]), render(scenes[1], 0.0, (0, 0), [(0, 0), (0, 0)])
        gen, st = interpolate(prev, cur, a.quality)
        print("corte de cena (cena 0 -> cena 1): corte=%s, blocos ruins %d/%d, gerado == atual: %s" % (
            st["cut"], st["bad_blocks"], st["blocks"], bool(np.abs(gen - cur).max() < 1e-6)))
    # previa: por linha prev | gerado | cur, e por baixo referencia | mistura simples | erro x4
    tiles = []
    for si, name, prev, gen, cur, gt, blend, _ in rows[:2]:
        w3 = 640
        top = [label(to_img(prev).resize((w3, 360)), "anterior (real)"),
               label(to_img(gen).resize((w3, 360)), "gerado t=0,5"),
               label(to_img(cur).resize((w3, 360)), "atual (real)")]
        err = np.abs(gen - gt).max(-1) * 4
        bot = [label(to_img(gt).resize((w3, 360)), "referencia t=0,5"),
               label(to_img(blend).resize((w3, 360)), "so mistura (sem movimento)"),
               label(to_img(np.repeat(err[..., None], 3, -1)).resize((w3, 360)), "erro do gerado x4")]
        tiles.append(top)
        tiles.append(bot)
    sheet = Image.new("RGB", (640 * 3, 360 * len(tiles)))
    for r, row in enumerate(tiles):
        for c, im in enumerate(row):
            sheet.paste(im, (c * 640, r * 360))
    sheet.save(a.out)
    # recorte a 1:1 da zona dos personagens (o detalhe nao se ve a 50%)
    si, name, prev, gen, cur, gt, blend, sc = rows[0]
    ys, xs = np.nonzero(sum(sc["layers"]) > 0.5)
    cx = int(np.clip((xs.min() + xs.max()) // 2, 320, prev.shape[1] - 320))
    cy = int(np.clip((ys.min() + ys.max()) // 2, 180, prev.shape[0] - 180))
    box = (cx - 320, cy - 180, cx + 320, cy + 180)
    crop = Image.new("RGB", (640 * 3, 360))
    for c, im in enumerate((prev, gen, cur)):
        crop.paste(to_img(im).crop(box), (c * 640, 0))
    crop.save(os.path.splitext(a.out)[0] + "_detalhe.png")
    print("previa:", a.out)


if __name__ == "__main__":
    sys.exit(main())
