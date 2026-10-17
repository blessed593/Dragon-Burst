#!/usr/bin/env python3
"""Dragon Burst launcher icon generator (pure Python + numpy + Pillow).

Concept: a white-hot impact seen head-on. A torn shockwave ring with a streaky
interior and a faint lavender pressure wave, rock fragments flung outward with
motion trails, embers and lit dust. In the middle, as the hero element, a custom
drawn (not a font) beveled gold-to-orange metal "DB" monogram with a dark chrome
band, a dark outline with an orange rim, a diagonal "burst" slash and a star
glint. The blast core sits behind the letters as a backlight, so the energy
rays and the lens flare come out from behind the "DB".
Original emblem: no characters, Dragon Balls, kanji or official logos.

Outputs (under app/src/main/res):
  drawable/ic_launcher_background.xml     dark radial gradient (vector)
  drawable-<dpi>/ic_launcher_foreground.png   108 dp, real alpha (glow alpha from
                                              luminance, letters and rocks opaque)
  drawable-<dpi>/ic_launcher_monochrome.png   108 dp, letters silhouette + ring
  mipmap-<dpi>/ic_launcher.png, ic_launcher_round.png   legacy 48 dp
and a preview sheet.

Usage: icon_gen.py [res_dir] [preview.png]
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "app", "src", "main", "res")
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else "icon_preview.png"

W = 1728          # supersampled canvas = 108 dp * 16
U = W / 108.0     # 1 dp in pixels
DENS = [("mdpi", 1.0), ("hdpi", 1.5), ("xhdpi", 2.0), ("xxhdpi", 3.0), ("xxxhdpi", 4.0)]


# ---------------------------------------------------------------- helpers
def P(x, y):
    return (x * U, y * U)


def mask_poly(pts, size=W):
    im = Image.new("L", (size, size), 0)
    ImageDraw.Draw(im).polygon([tuple(p) for p in pts], fill=255)
    return np.asarray(im, np.float32) / 255.0


def mask_lines(polys, width, size=W):
    im = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(im)
    for pl in polys:
        d.line([tuple(p) for p in pl], fill=255, width=max(1, int(width)), joint="curve")
    return np.asarray(im, np.float32) / 255.0


def _box(a, r, axis):
    """box filter of radius r along axis (edge-clamped), via cumulative sums"""
    if r < 1:
        return a
    pad = [(0, 0)] * a.ndim
    pad[axis] = (r + 1, r)
    c = np.cumsum(np.pad(a, pad, mode="edge"), axis=axis, dtype=np.float64)
    n = a.shape[axis]
    hi = np.take(c, np.arange(2 * r + 1, 2 * r + 1 + n), axis=axis)
    lo = np.take(c, np.arange(0, n), axis=axis)
    return ((hi - lo) / (2 * r + 1)).astype(np.float32)


def blur(m, s):
    """approximate gaussian blur (sigma s px): 3 box passes; big sigmas at reduced resolution"""
    if s < 0.3:
        return m
    H, Wd = m.shape
    k = 1
    for cand in (8, 6, 4, 3, 2):
        if s / cand >= 3 and H % cand == 0 and Wd % cand == 0:
            k = cand
            break
    a = m.reshape(H // k, k, Wd // k, k).mean((1, 3)) if k > 1 else m
    sk = s / k
    r = max(1, int(round((math.sqrt(4 * sk * sk + 1) - 1) / 2)))
    for _ in range(3):
        a = _box(_box(a, r, 0), r, 1)
    if k > 1:
        a = np.asarray(Image.fromarray(a.astype(np.float32), "F").resize((Wd, H), Image.BICUBIC), np.float32)
    return a


def col(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (0, 2, 4)], np.float32)


def add(img, m, c, k=1.0):
    img += (m[..., None] * k) * c[None, None, :]


def over(img, m, c):
    m = np.clip(m, 0, 1)[..., None]
    img *= (1 - m)
    img += m * (c if np.ndim(c) == 3 else c[None, None, :])


def fbm(seed, size=W, octaves=5, base=6):
    rng = np.random.default_rng(seed)
    out = np.zeros((size, size), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = base * (2 ** o)
        a = rng.random((n + 1, n + 1)).astype(np.float32)
        out += amp * np.asarray(Image.fromarray(a, "F").resize((size, size), Image.BICUBIC), np.float32)
        tot += amp
        amp *= 0.5
    return np.clip(out / tot, 0, 1)


def tonemap(x):
    return 1 - np.exp(-x * 1.25)


def untonemap(t):
    return -np.log(1 - np.clip(t, 0, 0.999)) / 1.25


def ramp(v, stops):
    v = np.clip(v, 0, 1)
    xs = np.array([s[0] for s in stops], np.float32)
    cs = np.stack([col(s[1]) for s in stops])
    return np.stack([np.interp(v, xs, cs[:, i]) for i in range(3)], -1).astype(np.float32)


def grid(size=W):
    y, x = np.mgrid[0:size, 0:size].astype(np.float32)
    k = size / 108.0
    return (x + 0.5) / k, (y + 0.5) / k


X, Y = grid()
CX, CY = 54.0, 54.0
GOLD, EMBER, HOT, WHITE = col("#FFD63C"), col("#FF8A1F"), col("#FFF2B8"), col("#FFFFFF")
RED, LAV = col("#C0300A"), col("#9678D6")

# ---------------------------------------------------------------- background (shared with the XML)
BG_RADIUS = 76.4  # centre to corner


def _bg_exact(r):
    r0 = r / 72.5
    lin = col("#040207")[None, :] + (np.clip(1 - r0, 0, 1) ** 2)[:, None] * col("#2A1442")[None, :] * 0.6
    vig = np.clip(1 - 0.6 * (r / 64) ** 2.4, 0.15, 1)
    return tonemap(lin * vig[:, None])


BG_OFFS = np.array([0, 0.12, 0.25, 0.4, 0.56, 0.75, 1.0], np.float32)
BG_STOPS = np.round(_bg_exact(BG_OFFS * BG_RADIUS) * 255).astype(int)  # sRGB 0..255


def bg_image(size):
    """exactly what Android draws for ic_launcher_background.xml (piecewise-linear radial)"""
    x, y = grid(size)
    t = np.clip(np.hypot(x - CX, y - CY) / BG_RADIUS, 0, 1)
    return np.stack([np.interp(t, BG_OFFS, BG_STOPS[:, i] / 255.0) for i in range(3)], -1).astype(np.float32)


def bg_xml():
    items = "\n".join(f'                <item android:offset="{o:.2f}" android:color="#FF{c[0]:02X}{c[1]:02X}{c[2]:02X}" />'
                      for o, c in zip(BG_OFFS, BG_STOPS))
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Fundo do icone Dragon Burst: quase preto com um leve violeta no centro. Gerado por tools/icon_gen.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0 L108,0 L108,108 L0,108Z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="54" android:centerY="54" android:gradientRadius="{BG_RADIUS}">
{items}
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


# ---------------------------------------------------------------- "DB" monogram geometry
# letterforms in a 0..30 tall box (before skew/scale/placement)
D_OUT = [(-2.5, -2.5), (13, 0), (20.5, 7), (20.5, 23), (13, 30), (0, 30), (0, 3)]
D_IN = [(6.2, 6), (11, 6), (14.3, 9.3), (14.3, 20.7), (11, 24), (6.2, 24)]
B_OUT = [(0, 0), (15.5, 0), (19.5, 4), (19.5, 11), (16.8, 14.6), (20.5, 18), (20.5, 26.5),
         (17, 30), (3, 30), (-1.5, 33)]
B_IN1 = [(6.2, 5.6), (12.2, 5.6), (13.6, 7), (13.6, 10.4), (12.2, 11.8), (6.2, 11.8)]
B_IN2 = [(6.2, 17.6), (12.8, 17.6), (14.4, 19.2), (14.4, 23), (12.8, 24.6), (6.2, 24.6)]
B_DX = 23.0
SKEW = math.tan(math.radians(15))
LETTER_R = 23.0   # max radius (dp) of the letter shapes from the centre


def _local(pts, dx):
    return [((x + dx) + (15 - y) * SKEW, y) for x, y in pts]


def _fit():
    pts = _local(D_OUT, 0) + _local(B_OUT, B_DX)
    xs, ys = [p[0] for p in pts], [p[1] for p in pts]
    mx, my = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    rmax = max(math.hypot(x - mx, y - my) for x, y in pts)
    sc = LETTER_R / rmax
    return sc, CX - mx * sc, CY - my * sc


SC, OX, OY = _fit()


def place(pts, dx):
    return [P(OX + x * SC, OY + y * SC) for x, y in _local(pts, dx)]


def letters_mask():
    m = mask_poly(place(D_OUT, 0)) - mask_poly(place(D_IN, 0))
    m = np.maximum(m, mask_poly(place(B_OUT, B_DX)) - mask_poly(place(B_IN1, B_DX)) - mask_poly(place(B_IN2, B_DX)))
    m = np.clip(m, 0, 1)
    # diagonal slash cut through both letters (the 'burst')
    a = (OX + (-5.0) * SC, OY + 24.6 * SC)
    b = (OX + 42.6 * SC, OY + 4.9 * SC)
    cut = mask_lines([[P(*a), P(*b)]], 0.95 * SC * U)
    return np.clip(m - cut, 0, 1), cut


def metal(M):
    """beveled gold-to-orange metal shading with a dark chrome band"""
    bw = 1.0 * SC * U
    h = np.clip((blur(M, bw * 0.55) - 0.5) * 2, 0, 1)
    h = h * h * (3 - 2 * h)
    gy, gx = np.gradient(h)
    k = bw * 0.9
    nx, ny, nz = -gx * k, -gy * k, np.ones_like(h)
    ln = np.sqrt(nx * nx + ny * ny + nz * nz)
    nx, ny, nz = nx / ln, ny / ln, nz / ln
    L = np.array([-0.45, -0.7, 0.55]); L /= np.linalg.norm(L)
    diff = np.clip(nx * L[0] + ny * L[1] + nz * L[2], 0, 1)
    spec = np.clip(2 * diff * nz - L[2], 0, 1) ** 30
    ys = np.clip((Y - OY + 2 * SC) / (34 * SC), 0, 1)
    stops = [(0.0, "#FFF4C8"), (0.18, "#FFDC5A"), (0.42, "#FFB21E"), (0.475, "#C2600A"), (0.50, "#4A1404"),
             (0.525, "#FF7A10"), (0.62, "#FFB040"), (0.80, "#E8500E"), (1.0, "#6A1002")]
    c = ramp(ys, stops) * (0.35 + 0.9 * diff)[..., None] + spec[..., None] * 1.1
    edge = np.clip(-(gx + gy) * bw * 0.9, 0, 1) * M * (h < 0.6)
    c += edge[..., None] * HOT * 1.2
    return c


# ---------------------------------------------------------------- scene
def rock(cx, cy, r, rng):
    n = int(rng.integers(4, 7))
    angs = (np.arange(n) + rng.uniform(-0.3, 0.3, n)) * 2 * math.pi / n + rng.uniform(0, 6.3)
    return [(cx + math.cos(a) * r * rng.uniform(0.55, 1.15), cy + math.sin(a) * r * rng.uniform(0.55, 1.15)) for a in angs]


def render():
    """returns (tonemapped RGB at W, opaque mask at W, monochrome mask at W)"""
    rng = np.random.default_rng(23)
    base = untonemap(bg_image(W))
    img = base.copy()
    opaque = np.zeros((W, W), np.float32)
    dx, dy = X - CX, Y - CY
    rr = np.hypot(dx, dy)
    ang = np.arctan2(dy, dx)

    an = np.zeros_like(ang)
    for k in range(2, 48):
        an += np.sin(ang * k + rng.uniform(0, 6.3)) * rng.uniform(0.2, 1.0) / k ** 0.8
    an = (an - an.min()) / (an.max() - an.min())

    # lit smoke/dust around the ring
    smoke = fbm(77, octaves=5, base=5)
    sm = np.clip((smoke - 0.42) * 2.2, 0, 1) * np.exp(-((rr - 33) / 8) ** 2)
    add(img, sm.astype(np.float32), col("#4A1606"), 0.7)

    # main shockwave ring: razor outer edge, streaky inner falloff, torn by angular noise
    RING = 31.0
    R = RING + (an - 0.5) * 1.2
    tear = np.clip((an - 0.3) * 4.0, 0, 1)
    inner = np.where(rr <= R, np.clip((rr - (R - 6)) / 6, 0, 1) ** 2.4, 0).astype(np.float32)
    edge = (np.exp(-((rr - R) / 0.42) ** 2) * (rr <= R + 0.5)).astype(np.float32)
    streak = 0.55 + 0.45 * np.sin(ang * 61 + an * 9) ** 2
    ring = (inner * 0.8 * streak + edge * 1.5) * tear
    gr = blur(ring, 3.5 * U)
    add(img, gr * (rr > R - 2), RED * 0.5 + EMBER * 0.5, 0.8)
    add(img, gr * (rr <= R - 2), RED * 0.3 + EMBER * 0.4, 0.12)
    add(img, blur(ring, 1.1 * U), EMBER, 0.6)
    add(img, ring, EMBER * 0.35 + GOLD * 0.65, 1.0)
    add(img, edge * tear, WHITE, 0.9)
    mono_ring = np.clip((np.exp(-((rr - (R - 0.6)) / 1.0) ** 2) - 0.35) * 3, 0, 1) * (tear > 0.5)

    # faint lavender outer pressure wave
    R2 = 35.8
    e2 = (np.exp(-((rr - R2) / 0.35) ** 2) * np.clip((an - 0.45) * 3, 0, 1)).astype(np.float32)
    add(img, blur(e2, 1.4 * U), LAV, 0.8)
    add(img, e2, HOT, 0.4)

    # energy rays from the core, coming out from behind the letters
    rays = np.zeros((W, W), np.float32)
    for i in range(46):
        a = rng.uniform(0, 2 * math.pi)
        Lr = rng.uniform(16, 29)
        wd = rng.uniform(0.35, 1.1)
        p0 = (CX + math.cos(a + 1.5708) * wd, CY + math.sin(a + 1.5708) * wd)
        p1 = (CX - math.cos(a + 1.5708) * wd, CY - math.sin(a + 1.5708) * wd)
        tip = (CX + math.cos(a) * Lr, CY + math.sin(a) * Lr)
        rays = np.maximum(rays, mask_poly([P(*p0), P(*tip), P(*p1)]) * rng.uniform(0.25, 0.8))
    rays *= np.clip(1 - rr / 29, 0, 1) ** 0.6
    add(img, blur(rays, 1.1 * U), EMBER, 0.3)
    add(img, rays, GOLD, 0.6)

    # core: a dimmed backlight halo behind the letters, plus a horizontal lens flare
    for rad, c, k in [(20, RED, 0.16), (11, EMBER, 0.28), (5.5, GOLD, 0.35), (2.5, WHITE, 0.8)]:
        add(img, np.exp(-((dx / rad) ** 2 + (dy / rad) ** 2)).astype(np.float32), c, k)
    fl = np.exp(-((dx / 30) ** 2 + (dy / 0.45) ** 2)).astype(np.float32)
    add(img, fl, HOT, 0.9)
    add(img, blur(fl, 1.0 * U), EMBER, 0.7)
    fv = np.exp(-((dx / 0.35) ** 2 + (dy / 14) ** 2)).astype(np.float32)
    add(img, fv, HOT, 0.35)

    # debris: rock shards flung outward, kept out at the ring (never over the letters)
    specs = []
    while len(specs) < 16:
        a = rng.uniform(0, 2 * math.pi)
        size = rng.uniform(1.0, 4.2)
        d = rng.uniform(27 + size, 44)
        x, y = CX + math.cos(a) * d, CY + math.sin(a) * d
        if 9 < x < 99 and 9 < y < 99:
            specs.append((a, d, size))
    for a, d, size in sorted(specs, key=lambda s: s[2]):
        x, y = CX + math.cos(a) * d, CY + math.sin(a) * d
        vx, vy = math.cos(a), math.sin(a)
        poly = rock(x, y, size, rng)
        tl = min(size * 3.0, d - size - 26)
        tr = mask_lines([[P(x - vx * size * 0.5, y - vy * size * 0.5), P(x - vx * (size * 0.5 + tl), y - vy * (size * 0.5 + tl))]],
                        size * 0.45 * U)
        add(img, blur(tr, 0.7 * U) * 0.3 * (rr > 25.5), EMBER, 0.8)
        m = mask_poly([P(*p) for p in poly])
        lit = mask_poly([P(px - vx * 0.3, py - vy * 0.3) for px, py in poly])
        rim = np.clip(m - lit, 0, 1)
        gl = np.clip(1 - ((X - x) * vx + (Y - y) * vy + size) / (2 * size), 0, 1) ** 2
        body = col("#120705")[None, None, :] + (gl[..., None] ** 2.5 * col("#8A3410")[None, None, :])
        dof = 0.0 if size < 3.0 else (size - 3.0) * 0.35 * U
        if dof > 0:
            m = blur(m, dof); rim = blur(rim, dof)
        over(img, m, body)
        opaque = np.maximum(opaque, m)
        add(img, rim, GOLD * 0.6 + HOT * 0.4, 1.2)
        add(img, blur(rim, 0.6 * U), EMBER, 0.9)

    # embers
    em = np.zeros((W, W), np.float32)
    for i in range(30):
        a = rng.uniform(0, 2 * math.pi); d = rng.uniform(26, 46)
        x = CX + math.cos(a) * d; y = CY + math.sin(a) * d
        em = np.maximum(em, mask_lines([[P(x, y), P(x + math.cos(a) * 1.5, y + math.sin(a) * 1.5)]], 0.26 * U) * rng.uniform(0.4, 1))
    add(img, blur(em, 0.8 * U), EMBER, 1.0)
    add(img, em, HOT, 0.9)

    # vignette on everything drawn over the background (the background has its own falloff)
    vig = np.clip(1 - 0.6 * (rr / 64) ** 2.4, 0.15, 1)
    img = base + (img - base) * vig[..., None]

    # ---- the hero: metal "DB" monogram over the backlight
    M, cut = letters_mask()
    O = np.clip(blur(M, 1.15 * SC * U) * 3.2, 0, 1)       # dark outline
    add(img, blur(M, 4.5 * U), EMBER, 0.3)
    over(img, O, col("#120402"))
    rimO = np.clip(O - np.clip(blur(M, 0.55 * SC * U) * 2.6, 0, 1), 0, 1)
    add(img, rimO, EMBER, 0.6)
    over(img, M, metal(M))
    opaque = np.maximum(opaque, O)
    # hot light leaking through the slash
    add(img, blur(cut, 0.9 * U) * O, HOT, 1.1)
    add(img, blur(cut, 3 * U) * O, EMBER, 0.5)
    # star glint on the D's top corner
    gx, gy = OX + (-2.5 + 17.5 * SKEW) * SC, OY - 2.5 * SC
    gs = SC / 1.22
    g = (np.exp(-(((X - gx) / (0.45 * gs)) ** 2 + ((Y - gy) / (5.5 * gs)) ** 2))
         + np.exp(-(((X - gx) / (5.5 * gs)) ** 2 + ((Y - gy) / (0.45 * gs)) ** 2))).astype(np.float32)
    add(img, g, WHITE, 1.4)
    add(img, blur(g, 1.3 * U), GOLD, 0.8)

    mono = np.maximum(M, mono_ring.astype(np.float32))
    return tonemap(np.clip(img, 0, None)), opaque, mono


# ---------------------------------------------------------------- outputs
def down(a, px):
    if a.ndim == 2:
        return np.asarray(Image.fromarray(a.astype(np.float32), "F").resize((px, px), Image.LANCZOS), np.float32)
    return np.stack([down(a[..., i], px) for i in range(a.shape[2])], -1)


def to_img(a):
    return Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8))


def foreground(F, O, px):
    """unmix the composite F over the known background into colour + alpha.
    Glow alpha comes from how much brighter than the background a pixel is;
    letters and rocks (opaque mask) stay fully opaque."""
    f, o, b = np.clip(down(F, px), 0, 1), np.clip(down(O, px), 0, 1), bg_image(px)
    a = np.max(np.clip((f - b) / np.maximum(1 - b, 1e-3), 0, 1), -1)
    a = np.maximum(a, o)
    c = (f - (1 - a[..., None]) * b) / np.maximum(a[..., None], 1e-4)
    c = np.where(a[..., None] > 1e-3, c, 0)
    return to_img(np.dstack([np.clip(c, 0, 1), a]))


def composite(F, px):
    return to_img(np.clip(down(F, px), 0, 1)).convert("RGBA")


def shape_mask(px, kind):
    s = 4
    m = Image.new("L", (px * s, px * s), 0)
    d = ImageDraw.Draw(m)
    if kind == "circle":
        d.ellipse([0, 0, px * s - 1, px * s - 1], fill=255)
    else:  # squircle
        n, pts = 5.0, []
        for i in range(720):
            t = 2 * math.pi * i / 720
            c, si = math.cos(t), math.sin(t)
            x = abs(c) ** (2 / n) * np.sign(c); y = abs(si) ** (2 / n) * np.sign(si)
            pts.append(((x + 1) / 2 * px * s, (y + 1) / 2 * px * s))
        d.polygon(pts, fill=255)
    return m.resize((px, px), Image.LANCZOS)


def masked(img, kind):
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    out.paste(img, (0, 0), shape_mask(img.size[0], kind))
    return out


def visible(F, px):
    """what a launcher shows: the middle 72 of the 108 dp canvas, at px"""
    full = composite(F, round(px * 108 / 72))
    a = (full.size[0] - px) // 2
    return full.crop((a, a, a + px, a + px))


def save_res(F, O, Mo):
    dr = os.path.join(RES, "drawable"); os.makedirs(dr, exist_ok=True)
    open(os.path.join(dr, "ic_launcher_background.xml"), "w").write(bg_xml())
    for q, k in DENS:
        px = round(108 * k)
        d = os.path.join(RES, f"drawable-{q}"); os.makedirs(d, exist_ok=True)
        foreground(F, O, px).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
        mono = np.clip(down(Mo, px), 0, 1)
        Image.fromarray(np.dstack([np.full((px, px, 3), 255, np.uint8), (mono * 255 + 0.5).astype(np.uint8)]), "RGBA") \
            .save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
        lp = round(48 * k)
        m = os.path.join(RES, f"mipmap-{q}"); os.makedirs(m, exist_ok=True)
        v = visible(F, lp)
        masked(v, "squircle").save(os.path.join(m, "ic_launcher.png"), optimize=True)
        masked(v, "circle").save(os.path.join(m, "ic_launcher_round.png"), optimize=True)


def font(sz):
    for f in ("/usr/share/fonts/opentype/inter/Inter-SemiBold.otf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"):
        if os.path.exists(f):
            return ImageFont.truetype(f, sz)
    return ImageFont.load_default()


def preview(F, O, Mo):
    """large in the squircle, plus 48 px on light and dark, 96 px, round/squircle masks, themed"""
    BIG, pad = 400, 30
    sheet = Image.new("RGBA", (pad * 4 + BIG + 2 * 300, BIG + 2 * pad + 160), (22, 10, 38, 255))
    d = ImageDraw.Draw(sheet)
    fl, fs = font(22), font(15)
    cream = (232, 222, 196, 255)
    d.text((pad, 14), "Dragon Burst - launcher icon", font=fl, fill=(255, 214, 60, 255))
    sheet.alpha_composite(masked(visible(F, BIG), "squircle"), (pad, 50))
    d.text((pad, 56 + BIG), "squircle mask", font=fs, fill=cream)
    # adaptive layers recombined: fg PNG over the vector background, full 108 dp with safe zone
    x1 = pad * 2 + BIG
    fg = foreground(F, O, 300)
    bgl = to_img(bg_image(300)).convert("RGBA")
    layered = Image.alpha_composite(bgl, fg)
    dd = ImageDraw.Draw(layered)
    for r_dp, colr in [(33, (255, 255, 255, 150)), (36, (120, 200, 255, 150))]:
        s0, s1 = 300 * (54 - r_dp) / 108, 300 * (54 + r_dp) / 108
        dd.ellipse([s0, s0, s1, s1], outline=colr)
    sheet.alpha_composite(layered, (x1, 50))
    d.text((x1, 356), "108 dp layers (fg PNG + bg), 66/72 dp", font=fs, fill=cream)
    x2 = x1 + 300 + pad
    sheet.alpha_composite(masked(visible(F, 300), "circle"), (x2, 50))
    d.text((x2, 356), "round mask", font=fs, fill=cream)
    # bottom row
    y0 = 400 + 2 * pad + 40
    d.rounded_rectangle((pad, y0 - 14, pad + 210, y0 + 76), 14, fill=(236, 233, 228, 255))
    for i, k in enumerate(("squircle", "circle")):
        sheet.alpha_composite(masked(visible(F, 48), k), (pad + 30 + i * 90, y0 + 7))
    d.text((pad, y0 + 84), "48 px on light", font=fs, fill=cream)
    xb = pad + 240
    d.rounded_rectangle((xb, y0 - 14, xb + 210, y0 + 76), 14, fill=(12, 12, 14, 255))
    for i, k in enumerate(("squircle", "circle")):
        sheet.alpha_composite(masked(visible(F, 48), k), (xb + 30 + i * 90, y0 + 7))
    d.text((xb, y0 + 84), "48 px on dark", font=fs, fill=cream)
    xc = xb + 240
    sheet.alpha_composite(masked(visible(F, 96), "squircle"), (xc, y0 - 16))
    sheet.alpha_composite(masked(visible(F, 96), "circle"), (xc + 116, y0 - 16))
    d.text((xc, y0 + 84), "96 px", font=fs, fill=cream)
    xd = xc + 250
    mono = np.clip(down(Mo, round(96 * 108 / 72)), 0, 1)
    a = (mono.shape[0] - 96) // 2
    mono = mono[a:a + 96, a:a + 96]
    th = np.dstack([np.full((96, 96), v) for v in (0.13, 0.20, 0.30)]) * (1 - mono[..., None]) + \
        np.dstack([np.full((96, 96), v) for v in (0.80, 0.86, 0.98)]) * mono[..., None]
    sheet.alpha_composite(masked(to_img(th).convert("RGBA"), "circle"), (xd, y0 - 16))
    d.text((xd, y0 + 84), "themed (mono)", font=fs, fill=cream)
    sheet.convert("RGB").save(PREVIEW)


def main():
    F, O, Mo = render()
    save_res(F, O, Mo)
    preview(F, O, Mo)
    print("letter scale %.3f -> %s" % (SC, PREVIEW))


if __name__ == "__main__":
    main()
