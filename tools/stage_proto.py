#!/usr/bin/env python3
"""Prototype of BurstStage (Compose Canvas) in numpy+Pillow.

Previews: stage_proto.py frames OUT.png [ui] | one T [ui] | gif N

Every primitive here maps 1:1 to a Compose DrawScope call:
  rect(brush)          -> drawRect(brush)
  circle(c, r, brush)  -> drawCircle(brush, r, c)        (sy != 1 -> withTransform scale)
  fill(poly, brush)    -> drawPath(path, brush)
  stroke(polys, w, b)  -> drawPath(path, brush, style = Stroke(w))
  ring(c, r, w, b)     -> drawCircle(brush, r, c, style = Stroke(w))
mode 'plus' -> BlendMode.Plus, 'over' -> SrcOver. Each draw clamps to [0,1] like 8-bit.
"""
import math, sys
import numpy as np
from PIL import Image, ImageDraw

TAU = 2 * math.pi


def hx(c, a=1.0):
    c = c.lstrip('#')
    return (int(c[0:2], 16) / 255, int(c[2:4], 16) / 255, int(c[4:6], 16) / 255, a)


NIGHT = '160A26'; PANEL = '2A1442'; ROYAL = '5C348C'; LAV = '9678D6'
GOLD = 'FFD63C'; CREAM = 'E8DEC4'; EMBER = 'FF8A1F'; ODEEP = 'E0560B'
VIOLET_HOT = 'C9B6FF'   # light lavender core tint
GOLD_HOT = 'FFF1B8'


def C(h, a=1.0):
    return hx(h, a)


def fract(x):
    return x - math.floor(x)


def hsh(i, k):
    x = math.sin(i * 12.9898 + k * 78.233) * 43758.5453
    return x - math.floor(x)


# ------------------------------------------------------------------ brushes
class Solid:
    def __init__(s, col): s.col = col

    def eval(s, X, Y):
        return np.array(s.col[:3], np.float32), np.float32(s.col[3])


def _interp(stops, f):
    pos = np.array([p for p, _ in stops], np.float32)
    cols = np.array([c for _, c in stops], np.float32)
    f = np.clip(f, 0, 1)
    out = np.empty(f.shape + (4,), np.float32)
    for ch in range(4):
        out[..., ch] = np.interp(f, pos, cols[:, ch])
    return out[..., :3], out[..., 3]


class Linear:
    def __init__(s, p0, p1, stops): s.p0, s.p1, s.stops = p0, p1, stops

    def eval(s, X, Y):
        dx, dy = s.p1[0] - s.p0[0], s.p1[1] - s.p0[1]
        L2 = dx * dx + dy * dy + 1e-9
        f = ((X - s.p0[0]) * dx + (Y - s.p0[1]) * dy) / L2
        return _interp(s.stops, f)


class Radial:
    def __init__(s, c, r, stops, sy=1.0): s.c, s.r, s.stops, s.sy = c, r, stops, sy

    def eval(s, X, Y):
        d = np.sqrt((X - s.c[0]) ** 2 + ((Y - s.c[1]) / s.sy) ** 2) / s.r
        return _interp(s.stops, d)


# ------------------------------------------------------------------ canvas
class Cv:
    def __init__(s, W, H):
        s.W, s.H = W, H
        s.img = np.zeros((H, W, 3), np.float32)
        s.ops = 0

    def _draw(s, mask_fn, bbox, brush, mode):
        s.ops += 1
        x0, y0, x1, y1 = bbox
        x0 = max(0, int(math.floor(x0)) - 2); y0 = max(0, int(math.floor(y0)) - 2)
        x1 = min(s.W, int(math.ceil(x1)) + 2); y1 = min(s.H, int(math.ceil(y1)) + 2)
        if x1 <= x0 or y1 <= y0:
            return
        im = Image.new('L', (x1 - x0, y1 - y0), 0)
        mask_fn(ImageDraw.Draw(im), x0, y0)
        cov = np.asarray(im, np.float32) / 255.0
        if not cov.any():
            return
        Y, X = np.mgrid[y0:y1, x0:x1].astype(np.float32)
        X += 0.5; Y += 0.5
        rgb, a = brush.eval(X, Y)
        A = (cov * a)[..., None]
        reg = s.img[y0:y1, x0:x1]
        if mode == 'plus':
            reg += rgb * A
        else:
            reg *= (1 - A)
            reg += rgb * A
        np.clip(reg, 0, 1, out=reg)

    def rect(s, brush, x0=0, y0=0, x1=None, y1=None, mode='over'):
        x1 = s.W if x1 is None else x1; y1 = s.H if y1 is None else y1
        s._draw(lambda d, ox, oy: d.rectangle([x0 - ox, y0 - oy, x1 - ox, y1 - oy], fill=255),
                (x0, y0, x1, y1), brush, mode)

    def circle(s, c, r, brush, mode='over', sy=1.0):
        cx, cy = c; ry = r * sy
        s._draw(lambda d, ox, oy: d.ellipse([cx - r - ox, cy - ry - oy, cx + r - ox, cy + ry - oy], fill=255),
                (cx - r, cy - ry, cx + r, cy + ry), brush, mode)

    def ring(s, c, r, w, brush, mode='over', sy=1.0):
        cx, cy = c
        ro = r + w / 2; ri = max(0, r - w / 2)

        def m(d, ox, oy):
            d.ellipse([cx - ro - ox, cy - ro * sy - oy, cx + ro - ox, cy + ro * sy - oy], fill=255)
            if ri > 0:
                d.ellipse([cx - ri - ox, cy - ri * sy - oy, cx + ri - ox, cy + ri * sy - oy], fill=0)
        s._draw(m, (cx - ro, cy - ro * sy, cx + ro, cy + ro * sy), brush, mode)

    def fill(s, poly, brush, mode='over'):
        xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
        s._draw(lambda d, ox, oy: d.polygon([(x - ox, y - oy) for x, y in poly], fill=255),
                (min(xs), min(ys), max(xs), max(ys)), brush, mode)

    def stroke(s, polys, w, brush, mode='over', cap=True):
        """polys: list of polylines -> one Path, one draw."""
        allx = [p[0] for pl in polys for p in pl]; ally = [p[1] for pl in polys for p in pl]
        if not allx:
            return
        r = w / 2

        def m(d, ox, oy):
            for pl in polys:
                pts = [(x - ox, y - oy) for x, y in pl]
                d.line(pts, fill=255, width=max(1, int(round(w))), joint='curve')
                if cap and w > 2:
                    for (x, y) in (pts[0], pts[-1]):
                        d.ellipse([x - r, y - r, x + r, y + r], fill=255)
        s._draw(m, (min(allx) - r, min(ally) - r, max(allx) + r, max(ally) + r), brush, mode)

    def line(s, a, b, w, brush, mode='over', cap=True):
        s.stroke([[a, b]], w, brush, mode, cap)


# ------------------------------------------------------------------ scene data (cached per size)
def smooth(e0, e1, x):
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


class Scene:
    """Things that depend only on the size: drawWithCache / remember(size)."""

    def __init__(s, w, h):
        s.w, s.h = w, h
        s.dp = h / 300.0
        dp = s.dp
        s.gy = h * 0.71
        # ground cracks: polylines radiating on the ground plane from under the clash
        s.cracks = []
        ox = w * 0.585; oy = s.gy + h * 0.085
        s.crackO = (ox, oy)
        for i in range(7):
            ang = 0.25 + (i / 6.0) * (math.pi - 0.5) + (hsh(i, 40) - 0.5) * 0.35
            ln = (70 + 170 * hsh(i, 41)) * dp
            if math.sin(ang) < 0:
                ln *= 0.55   # cracks going away from the viewer are short (perspective)
            p0 = (ox + math.cos(ang) * 6 * dp, oy + math.sin(ang) * 6 * dp * 0.4)
            p1 = (ox + math.cos(ang) * ln, oy + math.sin(ang) * ln * 0.4)
            pts = jag(p0, p1, 4, 0.13, i * 37 + 400)
            s.cracks.append(pts)
            # one branch off the middle
            b0 = pts[6 + int(hsh(i, 43) * 5)]
            ba = ang + (0.55 if hsh(i, 44) > 0.5 else -0.55)
            bl = ln * (0.25 + 0.2 * hsh(i, 46))
            s.cracks.append(jag(b0, (b0[0] + math.cos(ba) * bl, b0[1] + math.sin(ba) * bl * 0.4), 3, 0.15, i * 37 + 900))
        # distant mesas on the horizon (one path)
        m = [(0, s.gy + 1)]
        x = 0.0; k = 0
        while x < w:
            k += 1
            x += (14 + 40 * hsh(k, 30)) * dp
            hh = (4 + 22 * hsh(k, 31) ** 2) * dp
            if hsh(k, 32) > 0.55:      # flat-topped butte
                m += [(x, s.gy - hh), (x + (20 + 30 * hsh(k, 33)) * dp, s.gy - hh * (0.9 + 0.1 * hsh(k, 34)))]
                x = m[-1][0]
            else:
                m.append((x, s.gy - hh * 0.6))
        m.append((w, s.gy + 1))
        s.mesas = m
        # rock shapes, normalised (radius 1)
        s.rocks = []
        for i in range(16):
            nv = 4 + int(hsh(i, 50) * 3)
            verts = []
            for k in range(nv):
                a = (k + 0.15 * (hsh(i * 9 + k, 51) - 0.5) * 2) / nv * TAU
                rr = 0.45 + 0.55 * hsh(i * 9 + k, 52)
                el = 0.6 + 0.4 * hsh(i, 59)
                verts.append((math.cos(a) * rr, math.sin(a) * rr * el))
            d = hsh(i, 53)                       # depth 0 far .. 1 near
            x = 0.10 + 0.87 * hsh(i, 54)
            top = 0.06 if x > 0.42 else 0.42      # rocks on the left stop below the logo
            s.rocks.append(dict(verts=verts, depth=d, x=x, top=top, size=(5 + 19 * d * d) * dp,
                                k=1 if d > 0.45 else 2, ph=hsh(i, 55), spin=(hsh(i, 56) - 0.5) * 2.2,
                                a0=hsh(i, 57) * TAU, sway=hsh(i, 58) * TAU))


# ------------------------------------------------------------------ frame
def noise1(xd, t, ph):
    return (0.5 * math.sin(xd * 0.055 - t * TAU * 22 + ph)
            + 0.3 * math.sin(xd * 0.13 - t * TAU * 37 + 1.7 + ph * 2)
            + 0.2 * math.sin(xd * 0.29 - t * TAU * 61 + 0.5 + ph * 3))


def draw_beam(cv, sc, S, E, t, hot, glow, deep, strength, pulse, seed):
    dp = sc.dp
    ux, uy = E[0] - S[0], E[1] - S[1]
    L = math.hypot(ux, uy); ux /= L; uy /= L
    vx, vy = -uy, ux

    def P(x, v):
        return (S[0] + ux * x + vx * v, S[1] + uy * x + vy * v)

    flare = lambda x: 1 + 0.55 * smooth(L - 70 * dp, L, x)
    width = strength * (1 + 0.07 * pulse)
    step = 10 * dp
    xs = [min(L, i * step) for i in range(int(L / step) + 2)]

    def band(hw, amp, ph):
        top = [P(x, -(hw * flare(x) + amp * noise1(x / dp, t, ph))) for x in xs]
        bot = [P(x, (hw * flare(x) + amp * noise1(x / dp, t, ph + 2.1))) for x in reversed(xs)]
        return top + bot

    M = P(L * 0.5, 0)

    def perp(hw, stops):
        return Linear((M[0] - vx * hw, M[1] - vy * hw), (M[0] + vx * hw, M[1] + vy * hw), stops)

    # outer turbulent glow
    hwo = 46 * dp * width
    cv.fill(band(hwo, 9 * dp, seed), perp(hwo * 1.35, [
        (0, C(deep, 0)), (0.18, C(deep, 0.22)), (0.38, C(glow, 0.30)), (0.5, C(glow, 0.38)),
        (0.62, C(glow, 0.30)), (0.82, C(deep, 0.22)), (1, C(deep, 0))]), 'plus')
    # body
    hwb = 20 * dp * width
    cv.fill(band(hwb, 3.5 * dp, seed + 1), perp(hwb * 1.15, [
        (0, C(glow, 0.15)), (0.1, C(glow, 0.65)), (0.3, C(glow, 0.55)), (0.5, C(hot, 0.65)),
        (0.7, C(glow, 0.55)), (0.9, C(glow, 0.65)), (1, C(glow, 0.15))]), 'plus')
    # spiral strands, flowing toward the clash
    kk = TAU / (150 * dp)
    sxs = [min(L, i * 5 * dp) for i in range(int(L / (5 * dp)) + 2)]
    for j in range(2):
        pts = [P(x, hwb * 1.25 * flare(x) * math.sin(x * kk - t * TAU * 18 + j * math.pi)) for x in sxs]
        cv.stroke([pts], 5 * dp, Solid(C(hot, 0.12)), 'plus', cap=False)
    # streaks
    for i in range(12):
        v = (hsh(seed * 31 + i, 1) - 0.5) * 1.6 * hwb
        ln = (24 + 50 * hsh(seed * 31 + i, 2)) * dp
        k = 5 + int(hsh(seed * 31 + i, 3) * 5)
        x = fract(hsh(seed * 31 + i, 4) + t * k) * (L + ln) - ln
        a, b = P(max(0, x), v), P(min(L, x + ln), v)
        if x + ln <= 0:
            continue
        cv.line(a, b, 1.6 * dp, Linear(a, b, [(0, C('FFFFFF', 0)), (1, C('FFFFFF', 0.85))]), 'plus', cap=False)
    # white core
    hwc = 6 * dp * width
    core = ([P(x, -(hwc * flare(x) + 1.2 * dp * noise1(x / dp, t, seed + 4))) for x in xs]
            + [P(x, hwc * flare(x) + 1.2 * dp * noise1(x / dp, t, seed + 6)) for x in reversed(xs)])
    cv.fill(core, perp(hwc * 1.3, [(0, C(hot, 0)), (0.3, C('FFFFFF', 0.8)), (0.5, C('FFFFFF', 1)),
                                   (0.7, C('FFFFFF', 0.8)), (1, C(hot, 0))]), 'plus')


def jag(a, b, levels, rough, seed):
    pts = [a, b]
    for lv in range(levels):
        out = [pts[0]]
        for i in range(len(pts) - 1):
            p, q = pts[i], pts[i + 1]
            mx, my = (p[0] + q[0]) / 2, (p[1] + q[1]) / 2
            dx, dy = q[0] - p[0], q[1] - p[1]
            off = (hsh(seed + lv * 17 + i, 60) - 0.5) * 2 * rough
            out += [(mx - dy * off, my + dx * off), q]
        pts = out
    return pts


def frame(sc, t, W, H):
    cv = Cv(W, H)
    w, h, dp = sc.w, sc.h, sc.dp
    P_LOOP = 12.0
    wob = 0.55 * math.sin(TAU * t * 3) + 0.3 * math.sin(TAU * t * 7 + 1.3) + 0.15 * math.sin(TAU * t * 13 + 0.4)
    cx = w * (0.585 + 0.03 * wob)
    cy = h * (0.455 + 0.012 * math.sin(TAU * t * 5))
    Cc = (cx, cy)
    rp = fract(t * 4)
    flash = math.exp(-rp * 9)
    pulse = 0.5 + 0.5 * math.sin(TAU * t * 24)
    energy = 0.8 + 0.12 * pulse + 0.35 * flash
    gy = sc.gy

    # 1 sky
    cv.rect(Linear((0, 0), (0, h), [(0, C('06030C')), (0.45, C('110820')), (0.71, C('1E0F22')), (1, C('06030A'))]))
    # 2 ambient beam light, violet left / gold right
    cv.circle((cx - w * 0.28, cy), w * 0.62, Radial((cx - w * 0.28, cy), w * 0.62, [(0, C(ROYAL, 0.45)), (1, C(ROYAL, 0))]), 'plus')
    cv.circle((cx + w * 0.25, cy), w * 0.5, Radial((cx + w * 0.25, cy), w * 0.5, [(0, C(ODEEP, 0.30)), (1, C(ODEEP, 0))]), 'plus')
    # 3 speed lines (one path)
    ep = int(t * P_LOOP * 8)
    lines = []
    for i in range(30):
        a = (i + hsh(i, ep % 97 + 3)) / 30 * TAU
        deg = math.degrees(a) % 360
        if 190 < deg < 250 or hsh(i, ep % 89 + 5) < 0.3:
            continue
        r0 = (0.28 + 0.2 * hsh(i, ep % 83 + 7)) * h
        lines.append([(cx + math.cos(a) * r0, cy + math.sin(a) * r0), (cx + math.cos(a) * w, cy + math.sin(a) * w)])
    cv.stroke(lines, 1.2 * dp, Radial(Cc, w * 0.8, [(0, C('FFFFFF', 0)), (0.3, C('FFE9C0', 0.16)), (1, C('FFE9C0', 0))]), 'plus', cap=False)

    # 4 mesas, rim lit by the clash
    cv.fill(sc.mesas, Linear((0, gy - 30 * dp), (0, gy), [(0, C('2A1424')), (1, C('120A14'))]))
    cv.stroke([sc.mesas[1:-1]], 1.2 * dp, Radial(Cc, w * 0.45, [(0, C(EMBER, 0.55 * energy)), (1, C(EMBER, 0.05))]), 'plus', cap=False)
    # 4 ground
    cv.rect(Linear((0, gy), (0, h), [(0, C('1C0E18')), (0.25, C('0C0710')), (1, C('040306'))]), 0, gy, w, h)
    cv.rect(Linear((0, gy - 5 * dp), (0, gy + 7 * dp), [(0, C(EMBER, 0)), (0.45, C(EMBER, 0.22 * energy)), (1, C(EMBER, 0))]), 0, gy - 5 * dp, w, gy + 7 * dp, 'plus')
    O = sc.crackO
    Ox = O[0] + (cx - w * 0.585) * 0.6
    cv.circle((Ox, O[1]), w * 0.5, Radial((Ox, O[1]), w * 0.5, [(0, C(GOLD, 0.35 * energy)), (0.3, C(EMBER, 0.16 * energy)), (1, C(EMBER, 0))], sy=0.17), 'plus', sy=0.17)
    cv.stroke(sc.cracks, 4 * dp, Radial(O, w * 0.3, [(0, C(EMBER, 0.45 * energy)), (0.45, C(ODEEP, 0.18 * energy)), (1, C(ODEEP, 0))]), 'plus', cap=False)
    cv.stroke(sc.cracks, 1.2 * dp, Radial(O, w * 0.28, [(0, C('FFFFFF', 0.9)), (0.25, C(GOLD, 0.5 * energy)), (0.65, C(EMBER, 0.15)), (1, C(EMBER, 0))]), 'plus', cap=False)
    # ground shockwave (oval)
    gr = 30 * dp + w * 0.75 * (1 - (1 - rp) ** 2.4)
    cv.ring((Ox, O[1]), gr, 14 * dp, Solid(C(EMBER, 0.13 * (1 - rp) ** 1.5)), 'plus', sy=0.15)
    # dust clouds rolling outward along the ground
    for i in range(6):
        p = fract(hsh(i, 70) + t * (1 + i % 2))
        side = -1 if i % 2 else 1
        dx = side * (0.06 + 0.42 * p) * w
        r = (40 + 70 * p) * dp
        a = math.sin(p * math.pi) * 0.16
        c = (cx + dx, gy - 2 * dp)
        cv.circle(c, r, Radial(c, r, [(0, C('B0704A', a)), (1, C('B0704A', 0))], sy=0.35), 'plus', sy=0.35)

    # 5 rocks (far ones behind the beams)
    def rocks(near):
        for i, rk in enumerate(sc.rocks):
            if (rk['depth'] > 0.7) != near:
                continue
            p = fract(rk['ph'] + t * rk['k'])
            y0 = gy + (0.01 + 0.10 * rk['depth']) * h
            y1 = rk['top'] * h
            y = y0 - (y0 - y1) * p
            x = rk['x'] * w + math.sin(p * TAU + rk['sway']) * 6 * dp
            fade = smooth(0, 0.1, p) * (1 - smooth(0.72, 1, p))
            if fade <= 0.01:
                continue
            ang = rk['a0'] + rk['spin'] * p * TAU * 0.25
            ca, sa = math.cos(ang), math.sin(ang)
            sz = rk['size']
            poly = [(x + (vx * ca - vy * sa) * sz, y + (vx * sa + vy * ca) * sz) for vx, vy in rk['verts']]
            ldx, ldy = cx - x, cy - y
            ll = math.hypot(ldx, ldy) + 1e-6
            ldx /= ll; ldy /= ll
            lit = GOLD if x > cx else LAV
            off = (1.2 + 0.12 * sz / dp) * dp
            near_a = 1 - smooth(0.0, 0.6 * w, ll)
            cv.fill([(px + ldx * off, py + ldy * off) for px, py in poly], Solid(C(lit, fade * (0.45 + 0.5 * near_a))), 'over')
            shade = 0.35 + 0.65 * rk['depth']
            cv.fill(poly, Linear((x + ldx * sz, y + ldy * sz), (x - ldx * sz, y - ldy * sz),
                                 [(0, C('6A3E30', fade)), (0.35, C('2A1820', fade)), (1, C('07050A', fade))]), 'over')

    rocks(False)

    # 6 halo
    hr = h * (0.62 + 0.04 * pulse + 0.18 * flash)
    cv.circle(Cc, hr, Radial(Cc, hr, [(0, C('FFF4DA', 0.5)), (0.22, C(GOLD, 0.22)), (0.5, C(EMBER, 0.08)), (1, C(EMBER, 0))]), 'plus')

    # 7 beams: violet from the left (under the logo), gold from the right
    push = 0.08 * wob
    draw_beam(cv, sc, (-30 * dp, h * 0.585), Cc, t, VIOLET_HOT, LAV, ROYAL, 1.0 + push, pulse, 1)
    draw_beam(cv, sc, (w + 30 * dp, h * 0.335), Cc, t, GOLD_HOT, 'FFB02E', ODEEP, 1.0 - push, 1 - pulse, 2)

    # 8 shockwave rings
    for j, (dl, strength) in enumerate(((0.0, 1.0),)):
        p = rp - dl
        if p < 0:
            p += 1
        r = 30 * dp + w * 0.62 * (1 - (1 - p) ** 2.5)
        a = (1 - p) ** 1.6 * strength
        bw = (10 + 40 * p) * dp
        ro = r + 2 * dp
        cv.circle(Cc, ro, Radial(Cc, ro, [(0, C(GOLD_HOT, 0)), (max(0, (r - bw) / ro), C(GOLD_HOT, 0)),
                                          (r / ro, C(GOLD_HOT, 0.30 * a)), (1, C(GOLD_HOT, 0))]), 'plus')

    # 9 clash sphere
    R = 30 * dp * (1 + 0.08 * pulse + 0.35 * flash)
    for off, col in ((-0.55, LAV), (0.55, GOLD)):
        c = (cx + off * R, cy)
        cv.circle(c, R * 2.3, Radial(c, R * 2.3, [(0, C(col, 0.55)), (0.45, C(col, 0.18)), (1, C(col, 0))]), 'plus')
    cv.circle(Cc, R * 1.15, Radial(Cc, R * 1.15, [(0, C('FFFFFF', 1)), (0.55, C('FFFFFF', 1)), (0.78, C('FFF2CC', 0.85)), (0.92, C(GOLD, 0.35)), (1, C(GOLD, 0))]), 'plus')
    rim = []
    for k in range(37):
        a = k / 36 * TAU
        rr = R * (1.0 + 0.07 * math.sin(a * 5 + t * TAU * 19) + 0.05 * math.sin(a * 9 - t * TAU * 31))
        rim.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    cv.stroke([rim], 7 * dp, Solid(C(GOLD, 0.22)), 'plus', cap=False)
    cv.stroke([rim], 1.6 * dp, Solid(C('FFFFFF', 0.75)), 'plus', cap=False)

    # 10 lightning arcs
    ae = int(t * P_LOOP * 9)
    for i in range(6):
        sd = i * 101 + (ae % 108) * 7
        if hsh(sd, 1) < 0.3:
            continue
        a = hsh(sd, 2) * TAU
        ln = (22 + 48 * hsh(sd, 3)) * dp
        a0 = (cx + math.cos(a) * R * 0.85, cy + math.sin(a) * R * 0.85)
        a2 = a + (hsh(sd, 4) - 0.5) * 0.9
        a1 = (cx + math.cos(a2) * (R + ln), cy + math.sin(a2) * (R + ln))
        pts = jag(a0, a1, 4, 0.22, sd)
        fk = pts[6]
        fa = a2 + (0.7 if hsh(sd, 5) > 0.5 else -0.7)
        br = jag(fk, (fk[0] + math.cos(fa) * ln * 0.4, fk[1] + math.sin(fa) * ln * 0.4), 3, 0.25, sd + 50)
        col = LAV if math.cos(a2) < 0 else GOLD
        cv.stroke([pts, br], 4 * dp, Solid(C(col, 0.30)), 'plus')
        cv.stroke([pts, br], 1.0 * dp, Solid(C('FFFFFF', 0.9)), 'plus')

    # 11 lens flare
    fl = w * 0.48
    cv.line((cx - fl, cy), (cx + fl, cy), 2 * dp, Linear((cx - fl, cy), (cx + fl, cy), [(0, C('FFFFFF', 0)), (0.5, C('FFFFFF', 0.7)), (1, C('FFFFFF', 0))]), 'plus', cap=False)
    cv.line((cx - fl, cy), (cx + fl, cy), 9 * dp, Linear((cx - fl, cy), (cx + fl, cy), [(0, C(GOLD, 0)), (0.5, C(GOLD, 0.2)), (1, C(GOLD, 0))]), 'plus', cap=False)
    fv = h * 0.22
    cv.line((cx, cy - fv), (cx, cy + fv), 1.5 * dp, Linear((cx, cy - fv), (cx, cy + fv), [(0, C('FFFFFF', 0)), (0.5, C('FFFFFF', 0.5)), (1, C('FFFFFF', 0))]), 'plus', cap=False)

    # 12 sparks
    for i in range(40):
        k = 10 + int(hsh(i, 80) * 8)
        q = hsh(i, 81) + t * k
        p = fract(q)
        cyc = int(math.floor(q)) % k
        a = hsh(i * 13 + cyc, 82) * TAU
        sp = (90 + 230 * hsh(i * 13 + cyc, 83)) * dp
        g = 160 * dp
        dx, dy = math.cos(a), math.sin(a)
        px = cx + dx * (R + sp * p)
        py = cy + dy * (R + sp * p) + g * p * p
        vx, vy = dx * sp, dy * sp + 2 * g * p
        tl = 0.045
        q0 = (px - vx * tl, py - vy * tl)
        al = (1 - p) ** 1.3
        col = 'FFFFFF' if p < 0.25 else (GOLD if dx > 0 else VIOLET_HOT)
        cv.line(q0, (px, py), (0.6 + 1.6 * (1 - p)) * dp, Solid(C(col, al)), 'plus', cap=True)

    # 13 embers rising from the ground
    for i in range(24):
        k = 1 + int(hsh(i, 90) * 3)
        p = fract(hsh(i, 91) + t * k)
        x = (0.25 + 0.72 * hsh(i, 92)) * w + math.sin(p * TAU * 2 + hsh(i, 93) * TAU) * 10 * dp
        y = gy + 10 * dp - p * h * 0.75
        a = math.sin(p * math.pi) * 0.85
        r = (0.8 + 1.4 * hsh(i, 94)) * dp
        col = EMBER if hsh(i, 95) > 0.4 else GOLD
        cv.circle((x, y), r * 3, Radial((x, y), r * 3, [(0, C(col, a)), (0.3, C(col, a * 0.55)), (1, C(col, 0))]), 'plus')

    rocks(True)

    # 14 vignette + logo scrim
    vc = (w * 0.55, h * 0.45)
    cv.rect(Radial(vc, w * 0.62, [(0, C('000000', 0)), (0.6, C('000000', 0)), (1, C('000000', 0.55))], sy=0.62))
    lc = (w * 0.12, h * 0.1)
    cv.circle(lc, w * 0.42, Radial(lc, w * 0.42, [(0, C('05020A', 0.55)), (1, C('05020A', 0))], sy=0.55), sy=0.55)
    return cv


def overlay_ui(img, dp):
    """Rough HomeScreen overlay to judge readability (not part of the stage)."""
    from PIL import ImageFont
    d = ImageDraw.Draw(img, 'RGBA')
    W, H = img.size
    # bottom scrim 84 dp
    top = H - 84 * dp
    for y in range(int(top), H):
        a = int(255 * 0.92 * (y - top) / (H - top))
        d.line([(0, y), (W, y)], fill=(0x16, 0x0A, 0x26, a))
    try:
        f = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', int(34 * dp))
        f2 = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', int(13 * dp))
    except Exception:
        f = f2 = None
    d.text((20 * dp + 2, 20 * dp + 4 * dp / 2), 'DRAGON BURST', font=f, fill=(0x16, 0x0A, 0x26, 200))
    d.text((20 * dp, 20 * dp), 'DRAGON BURST', font=f, fill=(0xFF, 0xC0, 0x30, 255))
    d.text((20 * dp, 64 * dp), 'Dragon Ball Z: Burst Limit', font=f2, fill=(0xE8, 0xDE, 0xC4, 255))
    d.rounded_rectangle([16 * dp, H - 56 * dp, 150 * dp, H - 28 * dp], 14 * dp, fill=(0xFF, 0xD6, 0x3C, 60), outline=(0xFF, 0xD6, 0x3C, 255))
    d.rounded_rectangle([W - 196 * dp, H - 70 * dp, W - 16 * dp, H - 14 * dp], 16 * dp, fill=(0xFF, 0xD6, 0x3C, 255))
    d.text((W - 140 * dp, H - 52 * dp), 'JOGAR', font=f2, fill=(0x2A, 0x14, 0x42, 255))
    return img


def render(t, W, H, ui=False):
    sc = SC.get((W, H)) or SC.setdefault((W, H), Scene(W, H))
    cv = frame(sc, t, W, H)
    im = Image.fromarray((np.clip(cv.img, 0, 1) * 255 + 0.5).astype(np.uint8))
    if ui:
        im = overlay_ui(im, sc.dp)
    return im, cv.ops


SC = {}

if __name__ == '__main__':
    mode = sys.argv[1] if len(sys.argv) > 1 else 'frames'
    W, H = 1332, 600   # 666 x 300 dp at 2x (1920x1080 handheld, ~2.75 density)
    if mode == 'frames':
        ims = []
        for t in (0.02, 0.27, 0.55, 0.81):
            im, ops = render(t, W, H, ui='ui' in sys.argv)
            ims.append(im.resize((W // 2, H // 2), Image.LANCZOS))
            print('t', t, 'draw ops', ops)
        sheet = Image.new('RGB', (W // 2 * 2 + 12, H // 2 * 2 + 12), (20, 20, 20))
        for k, im in enumerate(ims):
            sheet.paste(im, ((k % 2) * (W // 2 + 12), (k // 2) * (H // 2 + 12)))
        sheet.save(sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2] == 'ui' else 'stage_frames.png')
    elif mode == 'one':
        t = float(sys.argv[2])
        im, ops = render(t, W, H, ui='ui' in sys.argv)
        im.save('stage_one.png'); print('ops', ops)
    elif mode == 'gif':
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 144
        fr = []
        for i in range(n):
            im, _ = render(i / n, W, H, ui='ui' in sys.argv)
            fr.append(im.resize((640, int(640 * H / W)), Image.LANCZOS))
        fr[0].save('stage_preview.gif', save_all=True, append_images=fr[1:],
                   duration=int(12000 / n), loop=0, optimize=False)
        print('gif frames', n)
