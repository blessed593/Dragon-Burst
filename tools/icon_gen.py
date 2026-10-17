#!/usr/bin/env python3
"""Dragon Burst launcher icon generator.

Concept: two ki beams (gold/ember from lower-left, lavender/white from upper-right)
clash in the centre and blow up into a jagged burst star, sprayed perpendicular to
the beams, with speed shards. Original emblem, no characters/logos/orbs.

One shape list drives both the Android vector XML and a Pillow/numpy raster
(used for the legacy mipmaps and the preview), so they always match.
"""
import math, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont

RES = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else "icon_preview.png"

NIGHT, PANEL, ROYAL, LAV = "#160A26", "#2A1442", "#5C348C", "#9678D6"
GOLD, CREAM, EMBER, WHITE = "#FFD63C", "#E8DEC4", "#FF8A1F", "#FFFFFF"

C = 54.0
AX = (math.cos(math.radians(-40)), math.sin(math.radians(-40)))  # beam axis: lower-left -> upper-right
PX = (-AX[1], AX[0])  # perpendicular


def P(a, p):  # point from axis coords (a along beam, p across) to canvas
    return (C + a * AX[0] + p * PX[0], C + a * AX[1] + p * PX[1])


def star(cx, cy, radii, rot=0.0):
    n = len(radii)
    pts = []
    for i, r in enumerate(radii):
        ang = rot + 2 * math.pi * i / n
        pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
    return pts


def circle(cx, cy, r, n=64):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


def beam(sign, w0, w1, start, end, head):
    """Beam along the axis from a=sign*start (off-canvas) to a=sign*end (clash side),
    widening from half-width w0 to w1, with a rounded head of radius `head`."""
    pts = []
    steps = 10
    for i in range(steps + 1):
        t = i / steps
        a = start + (end - start) * t
        w = w0 + (w1 - w0) * t
        pts.append(P(sign * a, w))
    # rounded head
    for i in range(1, 12):
        th = math.pi / 2 - math.pi * i / 12
        pts.append(P(sign * (end - w1 * 0.6 * math.cos(th)), w1 * math.sin(th)))
    for i in range(steps, -1, -1):
        t = i / steps
        a = start + (end - start) * t
        w = w0 + (w1 - w0) * t
        pts.append(P(sign * a, -w))
    return pts


# ---------------------------------------------------------------- shapes
# fill: ("solid", color, alpha) | ("radial", cx, cy, r, [(off, color, alpha)]) |
#       ("linear", x1, y1, x2, y2, [(off, color, alpha)])
def build():
    fg = []
    # speed shards (thin wedges radiating, mostly across the beam axis)
    for (ang, r0, r1, w, col, al) in [
        (-40 + 90 + 24, 25, 36, 1.7, CREAM, 0.9),
        (-40 + 90 - 26, 26, 35, 1.4, GOLD, 0.95),
        (-40 - 90 + 26, 25, 36, 1.7, CREAM, 0.9),
        (-40 - 90 - 24, 26, 35, 1.4, LAV, 1.0),
    ]:
        a = math.radians(ang)
        d = (math.cos(a), math.sin(a)); n = (-d[1], d[0])
        p0 = (C + d[0] * r0 + n[0] * w, C + d[1] * r0 + n[1] * w)
        p1 = (C + d[0] * r1, C + d[1] * r1)
        p2 = (C + d[0] * r0 - n[0] * w, C + d[1] * r0 - n[1] * w)
        fg.append(("shard", [p0, p1, p2], ("solid", col, al)))

    # beam A: hero, gold/ember, from lower-left
    a_out = beam(-1, 9.5, 12.5, 62, 8, 0)
    s, e = P(-62, 0), P(-8, 0)
    fg.append(("beamA", a_out, ("linear", s[0], s[1], e[0], e[1], [(0, EMBER, 1), (0.6, EMBER, 1), (1, GOLD, 1)])))
    fg.append(("beamA_core", beam(-1, 4.0, 6.5, 62, 6, 0), ("linear", s[0], s[1], e[0], e[1], [(0, GOLD, 1), (1, "#FFF6C8", 1)])))
    # beam B: rival, royal/lavender, from upper-right
    b_out = beam(1, 9.5, 12.5, 62, 8, 0)
    s, e = P(62, 0), P(8, 0)
    fg.append(("beamB", b_out, ("linear", s[0], s[1], e[0], e[1], [(0, ROYAL, 1), (0.55, LAV, 1), (1, "#C9B8F2", 1)])))
    fg.append(("beamB_core", beam(1, 4.0, 6.5, 62, 6, 0), ("linear", s[0], s[1], e[0], e[1], [(0, LAV, 1), (1, WHITE, 1)])))

    # burst star: spikes long across the axis (spray), short along it
    def spray(n, rlong, rshort, rin, jitter, rot):
        radii = []
        for i in range(n):
            ang = rot + 2 * math.pi * i / n
            if i % 2 == 0:
                rel = ang - math.radians(-40)
                k = abs(math.sin(rel))  # 1 = perpendicular to beams
                r = rshort + (rlong - rshort) * k ** 1.5
                r *= 1 + jitter[(i // 2) % len(jitter)]
            else:
                r = rin
            radii.append(r)
        return star(C, C, radii, rot)

    jit = [0.0, -0.08, 0.06, -0.04, 0.09, -0.1, 0.03, -0.05, 0.07]
    o = spray(18, 27, 14.5, 11, jit, math.radians(-40 + 8))
    fg.append(("burst_edge", [(C + (x - C) * 1.13, C + (y - C) * 1.13) for x, y in o], ("solid", NIGHT, 0.85)))
    fg.append(("burst_outer", o, ("solid", EMBER, 1)))
    fg.append(("burst_mid", spray(18, 20, 11.5, 8.5, jit[::-1], math.radians(-40 + 8 + 8)), ("solid", GOLD, 1)))
    fg.append(("burst_core", circle(C, C, 7.2), ("radial", C, C, 7.2, [(0, WHITE, 1), (0.55, "#FFFBEA", 1), (1, "#FFF0A8", 1)])))

    bg = [("bg", [(0, 0), (108, 0), (108, 108), (0, 108)],
           ("radial", 54, 54, 78, [(0, ROYAL, 1), (0.5, PANEL, 1), (1, NIGHT, 1)]))]
    return fg, bg


def mono_shapes(fg):
    d = {n: (p, f) for n, p, f in fg}
    out = [s for s in fg if s[0] == "shard"]
    out.append(("beamA", beam(-1, 9.5, 12.5, 62, 21, 0), None))
    out.append(("beamB", beam(1, 9.5, 12.5, 62, 21, 0), None))
    # ring: outer burst with the inner burst cut out (evenOdd), then the core
    out.append(("ring", [d["burst_outer"][0], circle(C, C, 7.2)], "evenodd"))
    return out


# ---------------------------------------------------------------- vector xml
def pd(pts):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in pts) + "Z"


def argb(col, al):
    return "#%02X%s" % (round(al * 255), col[1:])


def vec(shapes, comment, mono=False):
    out = ['<?xml version="1.0" encoding="utf-8"?>', f"<!-- {comment} -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           '    xmlns:aapt="http://schemas.android.com/aapt"',
           '    android:width="108dp" android:height="108dp"',
           '    android:viewportWidth="108" android:viewportHeight="108">']
    for name, pts, fill in shapes:
        if mono:
            if fill == "evenodd":
                out.append(f'    <path android:fillColor="#FF000000" android:fillType="evenOdd" android:pathData="{" ".join(pd(r) for r in pts)}" />')
            else:
                out.append(f'    <path android:fillColor="#FF000000" android:pathData="{pd(pts)}" />')
            continue
        if fill[0] == "solid":
            al = "" if fill[2] >= 1 else f' android:fillAlpha="{fill[2]:.2f}"'
            out.append(f'    <path android:fillColor="{fill[1]}"{al} android:pathData="{pd(pts)}" />')
        else:
            out.append(f'    <path android:pathData="{pd(pts)}">')
            out.append('        <aapt:attr name="android:fillColor">')
            if fill[0] == "radial":
                _, cx, cy, r, stops = fill
                out.append(f'            <gradient android:type="radial" android:centerX="{cx:.2f}" android:centerY="{cy:.2f}" android:gradientRadius="{r:.2f}">')
            else:
                _, x1, y1, x2, y2, stops = fill
                out.append(f'            <gradient android:type="linear" android:startX="{x1:.2f}" android:startY="{y1:.2f}" android:endX="{x2:.2f}" android:endY="{y2:.2f}">')
            for off, col, al in stops:
                out.append(f'                <item android:offset="{off}" android:color="{argb(col, al)}" />')
            out.append("            </gradient>")
            out.append("        </aapt:attr>")
            out.append("    </path>")
    out.append("</vector>")
    xml = "\n".join(out) + "\n"
    if "aapt:" not in "\n".join(out[6:]):
        xml = xml.replace('    xmlns:aapt="http://schemas.android.com/aapt"\n', "")
    return xml


# ---------------------------------------------------------------- raster
def hex2(col):
    return np.array([int(col[i:i + 2], 16) for i in (1, 3, 5)], dtype=np.float32)


def ramp(t, stops):
    t = np.clip(t, 0, 1)
    rgb = np.zeros(t.shape + (3,), np.float32); a = np.zeros(t.shape, np.float32)
    offs = [s[0] for s in stops]
    for ch in range(3):
        rgb[..., ch] = np.interp(t, offs, [hex2(s[1])[ch] for s in stops])
    a[:] = np.interp(t, offs, [s[2] for s in stops])
    return rgb, a


def raster(shapes, px, mono=False):
    ss = 4; N = px * ss; k = N / 108.0
    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    xx = (xx + 0.5) / k; yy = (yy + 0.5) / k
    img = np.zeros((N, N, 4), np.float32)
    for name, pts, fill in shapes:
        rings = pts if fill == "evenodd" else [pts]
        m = np.zeros((N, N), np.float32)
        for ring in rings:
            mm = Image.new("L", (N, N), 0)
            ImageDraw.Draw(mm).polygon([(x * k, y * k) for x, y in ring], fill=255)
            mm = np.asarray(mm, np.float32) / 255
            m = np.abs(m - mm)
        if mono:
            rgb = np.zeros((N, N, 3), np.float32) + 255; a = np.ones((N, N), np.float32)
        elif fill[0] == "solid":
            rgb = np.broadcast_to(hex2(fill[1]), (N, N, 3)); a = np.full((N, N), fill[2], np.float32)
        elif fill[0] == "radial":
            _, cx, cy, r, stops = fill
            rgb, a = ramp(np.hypot(xx - cx, yy - cy) / r, stops)
        else:
            _, x1, y1, x2, y2, stops = fill
            dx, dy = x2 - x1, y2 - y1
            rgb, a = ramp(((xx - x1) * dx + (yy - y1) * dy) / (dx * dx + dy * dy), stops)
        sa = (a * m)[..., None]
        img[..., :3] = rgb * sa + img[..., :3] * (1 - sa)
        img[..., 3:] = sa + img[..., 3:] * (1 - sa)
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8) if False else
                          np.dstack([np.clip(img[..., :3], 0, 255), img[..., 3:] * 255]).astype(np.uint8), "RGBA")
    return out.resize((px, px), Image.LANCZOS)


def composite(fg, bg, px):
    b = raster(bg, px); f = raster(fg, px)
    return Image.alpha_composite(b, f)


def mask(img, kind):
    px = img.size[0]; s = 4
    m = Image.new("L", (px * s, px * s), 0); d = ImageDraw.Draw(m)
    if kind == "circle":
        d.ellipse([0, 0, px * s - 1, px * s - 1], fill=255)
    else:  # squircle
        n = 5.0; pts = []
        for i in range(360):
            t = 2 * math.pi * i / 360
            c, si = math.cos(t), math.sin(t)
            x = abs(c) ** (2 / n) * np.sign(c); y = abs(si) ** (2 / n) * np.sign(si)
            pts.append(((x + 1) / 2 * px * s, (y + 1) / 2 * px * s))
        d.polygon(pts, fill=255)
    m = m.resize((px, px), Image.LANCZOS)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0)); out.paste(img, (0, 0), m)
    return out


def crop_visible(img):  # launcher shows the middle 72/108
    px = img.size[0]; a = round(px * 18 / 108)
    return img.crop((a, a, px - a, px - a))


def main():
    fg, bg = build()
    mono = mono_shapes(fg)
    dr = os.path.join(RES, "drawable"); os.makedirs(dr, exist_ok=True)
    open(os.path.join(dr, "ic_launcher_foreground.xml"), "w").write(vec(fg,
        "Icone Dragon Burst (original): dois raios de ki (dourado e lavanda) a chocar e a explodir numa estrela de energia. Gerado por tools/icon_gen.py."))
    open(os.path.join(dr, "ic_launcher_background.xml"), "w").write(vec(bg,
        "Fundo do icone Dragon Burst: violeta real ao centro a escurecer para a noite (cores do tema)."))
    open(os.path.join(dr, "ic_launcher_monochrome.xml"), "w").write(vec(mono,
        "Icone tematico (Android 13+): so a silhueta dos raios e da explosao, o sistema aplica a cor.", mono=True))

    # legacy mipmaps (full 108 canvas cropped to the visible 72, then masked)
    for q, px in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
        d = os.path.join(RES, f"mipmap-{q}"); os.makedirs(d, exist_ok=True)
        full = crop_visible(composite(fg, bg, round(px * 108 / 72)))
        full = full.resize((px, px), Image.LANCZOS)
        mask(full, "squircle").save(os.path.join(d, "ic_launcher.png"))
        mask(full, "circle").save(os.path.join(d, "ic_launcher_round.png"))

    # preview
    big = 360
    full108 = composite(fg, bg, big)
    vis = crop_visible(composite(fg, bg, round(big * 108 / 72))).resize((big, big), Image.LANCZOS)
    tiles = [("108dp canvas", full108), ("circle", mask(vis, "circle")), ("squircle", mask(vis, "squircle"))]
    mono_img = crop_visible(raster(mono, round(big * 108 / 72), mono=True)).resize((big, big), Image.LANCZOS)
    mono_bg = Image.new("RGBA", (big, big), (40, 60, 90, 255)); mono_t = Image.new("RGBA", (big, big), (200, 220, 255, 255))
    mono_bg.paste(mono_t, (0, 0), mono_img.split()[3]); tiles.append(("themed", mask(mono_bg, "circle")))
    W = 16 + len(tiles) * (big + 16) + 200
    sheet = Image.new("RGBA", (W, big + 60), (235, 235, 240, 255))
    dd = ImageDraw.Draw(sheet)
    for i, (lab, t) in enumerate(tiles):
        x = 16 + i * (big + 16); sheet.alpha_composite(t, (x, 16)); dd.text((x, big + 26), lab, fill=(0, 0, 0, 255))
    # safe zone guide on canvas tile
    s0 = 16 + big * (54 - 33) / 108; s1 = 16 + big * (54 + 33) / 108
    dd.ellipse([s0, s0, s1, s1], outline=(255, 255, 255, 140))
    x = 16 + len(tiles) * (big + 16)
    for j, px in enumerate([48, 96]):
        sm = mask(crop_visible(composite(fg, bg, round(px * 108 / 72))).resize((px, px), Image.LANCZOS), "circle")
        sheet.alpha_composite(sm, (x + 20, 16 + j * 120))
        dd.text((x + 20, 16 + j * 120 + px + 4), f"{px}px", fill=(0, 0, 0, 255))
    sheet.convert("RGB").save(PREVIEW)


if __name__ == "__main__":
    main()
