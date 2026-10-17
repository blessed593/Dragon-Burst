#!/usr/bin/env python3
"""Referencia offline do passo 3b do render nativo (patches 0083+): a cadeia do brilho do
contorno do Burst Limit recalculada em numpy com as mesmas contas do compute
(render_target_cache.cpp, dbz3_native_post, kSource) e comparada com o resolve que o jogo fez
(capturas com resolve_readback=1, registo 8).

Passes (as entradas sao os resolves lidos de volta antes do draw, a saida o resolve seguinte):
  reducao 640 (PS B0133EF80147AD50): 0F0B9000 1280x720 -> 0FF19000 640x360
  desfoque 640 (PS 97906B6915CE1A5E): VS 66B048B2F9FEDDB4 vertical 0FF19000 -> 10009000,
                                      VS 4212920CA5E1B3A8 horizontal 10009000 -> 100F9000

A conversao 8 bits -> float da textura e a do aparelho: no Adreno t * (1/255) em fp32, no Mali
t / 255 (--conv mul|div; por omissao pelo aparelho.txt da captura). No aparelho o compute le a
textura com texelFetch, por isso usa a conversao do proprio aparelho, como o draw original.

Uso:
  python3 tools/ref_glow.py captura.dfc [outra.dfc ...] [--conv mul|div]
Precisa de numpy e do OptXbox (pasta irma ../optxbox ou OPTXBOX=/caminho).
"""
import os
import struct
import sys

import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
for cand in (os.environ.get("OPTXBOX", ""), os.path.join(HERE, "..", "..", "optxbox"),
             os.path.expanduser("~/optxbox")):
    if cand and os.path.isfile(os.path.join(cand, "optxbox", "dfc.py")):
        sys.path.insert(0, cand)
        break
from optxbox.dfc import Capture  # noqa: E402

f32 = np.float32
PS_DOWNSAMPLE = 0xB0133EF80147AD50
PS_BLUR = 0x97906B6915CE1A5E
VS_BLUR_V = 0x66B048B2F9FEDDB4
PASSES = {PS_DOWNSAMPLE: "reducao 640", PS_BLUR: "desfoque 640"}


def tiled_offsets(w, h, pitch, bpb_log2=2):
    pitch = (max(pitch, w) + 31) & ~31
    y, x = np.mgrid[0:h, 0:w].astype(np.int64)
    macro = ((x >> 5) + (y >> 5) * (pitch >> 5)) << (bpb_log2 + 7)
    micro = ((x & 7) + ((y & 0xE) << 2)) << bpb_log2
    offset = macro + ((micro & ~0xF) << 1) + (micro & 0xF) + ((y & 1) << 4)
    return (((offset & ~0x1FF) << 3) + ((y & 16) << 7) + ((offset & 0x1C0) << 2) +
            (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 0x3F))


def load(path):
    """Resolves do 1.o frame (com a imagem lida de volta, bytes na ordem dos canais da
    textura) e os registos de cada draw dos PS que interessam."""
    regs = np.zeros(0x10000, np.uint32)
    pending = None
    draw = -1
    frame = 0
    resolves, snaps = [], {}
    for typ, body in Capture.records(path):
        if typ == 1:
            n = struct.unpack_from("<I", body)[0]
            pairs = np.frombuffer(body[4:4 + n * 8], dtype="<u4").reshape(-1, 2)
            ok = pairs[:, 0] < len(regs)
            regs[pairs[ok, 0]] = pairs[ok, 1]
        elif typ == 2:
            t = struct.unpack_from("<IIIIIIQQI", body)
            draw = t[8]
            if t[7] in PASSES and (regs[0x2208] & 7) != 6 and frame == 0:
                snaps[draw] = (regs.copy(), t)
        elif typ == 3:
            info, pr = int(regs[0x231B]), int(regs[0x231A])
            tl, br = int(regs[0x2081]), int(regs[0x2082])
            pending = dict(draw=draw, frame=frame, base=int(regs[0x2319]) & 0x1FFFFFFF,
                           pitch=pr & 0x3FFF, endian=info & 7, swap=(info >> 24) & 1,
                           w=(br & 0x7FFF) - (tl & 0x7FFF),
                           h=((br >> 16) & 0x7FFF) - ((tl >> 16) & 0x7FFF))
            resolves.append(pending)
        elif typ == 4:
            frame += 1
        elif typ == 8 and pending:
            addr, n = struct.unpack_from("<II", body)
            data = np.frombuffer(body[8:8 + n], dtype=np.uint8)
            r = pending
            off = tiled_offsets(r["w"], r["h"], r["pitch"]) - (addr - r["base"])
            ok = (off >= 0) & (off + 4 <= len(data))
            offc = np.where(ok, off, 0)
            raw = np.stack([data[offc + k] for k in range(4)], -1)
            raw = {1: raw[..., [1, 0, 3, 2]], 2: raw[..., ::-1], 3: raw[..., [2, 3, 0, 1]]}.get(r["endian"], raw)
            r["img"] = np.where(ok[..., None], raw, 0).astype(np.uint8)
    return resolves, snaps


def fval(regs, index):
    return struct.unpack("<f", struct.pack("<I", int(regs[index])))[0]


def psc(regs, n):
    return [fval(regs, 0x4000 + (256 + n) * 4 + k) for k in range(4)]


def fetch(regs, slot):
    return [int(v) for v in regs[0x4800 + slot * 6:0x4806 + slot * 6]]


class Tex:
    """T0/T1 do compute: texelFetch com borda = aresta, conversao do aparelho, swizzle BGRA."""

    def __init__(self, img, conv, bgra):
        t = img.astype(f32)
        t = (t * f32(1 / 255)).astype(f32) if conv == "mul" else (t / f32(255)).astype(f32)
        self.t = t[..., [2, 1, 0, 3]] if bgra else t
        self.h, self.w = img.shape[:2]

    def at(self, xs, ys):
        """xs: indices de coluna (1D, largura do alvo), ys: de linha (1D)."""
        return self.t[np.clip(ys, 0, self.h - 1)][:, np.clip(xs, 0, self.w - 1)]


def pack(v, out_swap):
    """Pack do compute: floor(clamp(v) * 255 + 0,5) em fp32, troca R/B do resolve."""
    s = (np.clip(v, 0, 1).astype(f32) * f32(255) + f32(0.5)).astype(f32)
    u = np.floor(s).astype(np.int64)
    return u[..., [2, 1, 0, 3]] if out_swap else u


def box(a, b, c, d):
    top = ((a + b).astype(f32) * f32(0.5)).astype(f32)
    bottom = ((c + d).astype(f32) * f32(0.5)).astype(f32)
    return ((top + bottom).astype(f32) * f32(0.5)).astype(f32)


def downsample(t0, k0, k1, w, h):
    """KIND 0: oC0 = caixa(0,0) * c0 + max(caixa(0,0), (1,0), (0,1), (1,1)) * c1."""
    xs, ys = 2 * np.arange(w), 2 * np.arange(h)
    t = [[t0.at(xs + dx, ys + dy) for dx in range(3)] for dy in range(3)]  # t[dy][dx]
    b00 = box(t[0][0], t[0][1], t[1][0], t[1][1])
    b10 = box(t[0][1], t[0][2], t[1][1], t[1][2])
    b01 = box(t[1][0], t[1][1], t[2][0], t[2][1])
    b11 = box(t[1][1], t[1][2], t[2][1], t[2][2])
    m = np.maximum(np.maximum(np.maximum(b00, b10), b01), b11)
    return ((b00 * np.array(k0, f32)).astype(f32) + (m * np.array(k1, f32)).astype(f32)).astype(f32)


def name_of(path):
    parts = os.path.abspath(path).split(os.sep)
    return parts[-4] if len(parts) >= 4 and parts[-2] == "capture" else os.path.basename(path)


def blur(t0, vertical, k0, k1, k2, w, h):
    """KIND 1: c0*c255.z * (T0 + (T+1 + T-1)*w1 + ... + (T+5 + T-5)*w5), mad a mad."""
    xs, ys = np.arange(w), np.arange(h)

    def tap(k):
        return t0.at(xs, ys + k) if vertical else t0.at(xs + k, ys)

    r = tap(0)
    weights = [k0[0], k0[1], k0[2], k0[3], k1[0]]
    for k in range(1, 6):
        pair = (tap(k) + tap(-k)).astype(f32)
        r = (r + (pair * f32(weights[k - 1])).astype(f32)).astype(f32)
    return (np.array(k2, f32) * r).astype(f32)


def device_conv(path):
    info = os.path.join(os.path.dirname(path), "..", "..", "aparelho.txt")
    try:
        with open(info, errors="replace") as f:
            return "mul" if "Adreno: true" in f.read() else "div"
    except OSError:
        return "mul"


def main():
    args = sys.argv[1:]
    conv_opt = None
    if "--conv" in args:
        i = args.index("--conv")
        conv_opt = args[i + 1]
        del args[i:i + 2]
    paths = [a for a in args if not a.startswith("--")]
    if not paths:
        print(__doc__)
        return 1
    total_bad = 0
    for path in paths:
        conv = conv_opt or device_conv(path)
        resolves, snaps = load(path)
        for d in sorted(snaps):
            regs, t = snaps[d]
            after = [r for r in resolves if r["draw"] > d]
            if not after or "img" not in after[0]:
                continue
            out = after[0]
            before = [r for r in resolves if r["draw"] < d and "img" in r and r["frame"] == out["frame"]]
            f0 = fetch(regs, 0)
            base0 = f0[1] & 0xFFFFF000
            bgra0 = ((f0[3] >> 1) & 0xFFF) == 0x60A
            src0 = [r for r in before if r["base"] == base0]
            if not src0:
                continue
            if t[7] == PS_DOWNSAMPLE:
                tex0 = Tex(src0[-1]["img"], conv, bgra0)
                v = downsample(tex0, psc(regs, 0), psc(regs, 1), out["w"], out["h"])
            elif t[7] == PS_BLUR:
                c254, c255 = psc(regs, 254), psc(regs, 255)
                k2 = (np.array(psc(regs, 0), f32) * f32(c255[2])).astype(f32)
                tex0 = Tex(src0[-1]["img"], conv, bgra0)
                v = blur(tex0, t[6] == VS_BLUR_V, (c255[1], c254[0], c254[1], c255[0]), (c255[3],),
                         k2, out["w"], out["h"])
            ref = pack(v, out["swap"])
            diff = np.abs(ref - out["img"].astype(np.int64))
            bad = int((diff.max(-1) > 0).sum())
            # O draw nao correu nessa build (a saida e o conteudo anterior do render target).
            if bad and any(r["img"].shape == out["img"].shape and (r["img"] == out["img"]).all()
                           for r in before[-6:]):
                print("%s draw %d (%s): saltado, a saida e igual a um resolve anterior (o draw "
                      "nao correu nessa build)" % (name_of(path), d, PASSES[t[7]]))
                continue
            total_bad += bad
            x = np.clip(v, 0, 1) * 255
            ties = int((np.abs(x - np.floor(x) - 0.5) < 1e-4).sum())
            print("%s draw %d (%s) %08X -> %08X [%s]: erro max %d medio %.6f, pixeis diferentes "
                  "%d de %d (%d nao nulos, %d empates .5)" % (
                      name_of(path), d, PASSES[t[7]], base0, out["base"], conv,
                      diff.max(), diff.mean(), bad, diff.shape[0] * diff.shape[1],
                      int((out["img"].max(-1) > 0).sum()), ties))
    return 1 if total_bad else 0


if __name__ == "__main__":
    sys.exit(main())
