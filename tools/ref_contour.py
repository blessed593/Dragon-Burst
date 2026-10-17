#!/usr/bin/env python3
"""Referencia offline do passo 3b do render nativo (patch 0080): a mascara do contorno dos
lutadores do Burst Limit (PS ABBFE80C449D5F12) recalculada em numpy com as mesmas contas do
compute (render_target_cache.cpp, dbz3_native_contour) e comparada com o resolve que o jogo
fez (capturas com resolve_readback=1, registo 8).

Entradas de cada draw ABBF do 1.o frame: o ID (canal lido como .z de tf0, 0E589000) e a
profundidade linear (tf1, 0F451000; a captura nao le de volta resolves 32_FLOAT, por isso e
recalculada da profundidade 24_8 0E921000 com A/(A - d*B), as constantes do PS D9872B5A...).

Uso:
  python3 tools/ref_contour.py captura.dfc [outra.dfc ...] [--glsl]
    --glsl: aresta como no compute ("algum vizinho com outro ID", valido com c254.w = 0)
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
PS_CONTOUR = 0xABBFE80C449D5F12
PS_LINEAR_DEPTH = 0xD9872B5AFB5E0BEC


def tiled_offsets(w, h, pitch, bpb_log2=2):
    pitch = (max(pitch, w) + 31) & ~31
    y, x = np.mgrid[0:h, 0:w].astype(np.int64)
    macro = ((x >> 5) + (y >> 5) * (pitch >> 5)) << (bpb_log2 + 7)
    micro = ((x & 7) + ((y & 0xE) << 2)) << bpb_log2
    offset = macro + ((micro & ~0xF) << 1) + (micro & 0xF) + ((y & 1) << 4)
    return (((offset & ~0x1FF) << 3) + ((y & 16) << 7) + ((offset & 0x1C0) << 2) +
            (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 0x3F))


def load(path):
    """Resolves (com a imagem lida de volta, bytes na ordem dos canais da textura) e os
    registos de cada draw dos PS que interessam."""
    regs = np.zeros(0x10000, np.uint32)
    pending = None
    draw = -1
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
            if t[7] in (PS_CONTOUR, PS_LINEAR_DEPTH) and (regs[0x2208] & 7) != 6:
                snaps[draw] = (regs.copy(), t)
        elif typ == 3:
            info, pr = int(regs[0x231B]), int(regs[0x231A])
            tl, br = int(regs[0x2081]), int(regs[0x2082])
            pending = dict(draw=draw, base=int(regs[0x2319]) & 0x1FFFFFFF, pitch=pr & 0x3FFF,
                           endian=info & 7, swap=(info >> 24) & 1,
                           w=(br & 0x7FFF) - (tl & 0x7FFF), h=((br >> 16) & 0x7FFF) - ((tl >> 16) & 0x7FFF))
            resolves.append(pending)
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


def contour(id_byte, lin, colors, ids, scale, swap, glsl):
    """PS ABBF...: bytes do resolve (HxWx4, ordem da memoria) a partir do canal do ID (uint8)
    e da profundidade linear (float32). tf0 tem borda = aresta, tf1 borda preta (0)."""
    h, w = lin.shape
    idv = np.floor(id_byte.astype(f32) / f32(255) * f32(scale)).astype(f32)
    ys, xs = np.mgrid[0:h, 0:w]

    def at(a, dx, dy, border):
        x2, y2 = xs + dx, ys + dy
        if border is None:
            return a[np.clip(y2, 0, h - 1), np.clip(x2, 0, w - 1)]
        inside = (x2 >= 0) & (x2 < w) & (y2 >= 0) & (y2 < h)
        out = np.full_like(a, border)
        out[inside] = a[y2[inside], x2[inside]]
        return out

    dl, dr, du, dd = (at(lin, -1, 0, f32(0)), at(lin, 1, 0, f32(0)),
                      at(lin, 0, -1, f32(0)), at(lin, 0, 1, f32(0)))
    il, ir, iu, idn = at(idv, -1, 0, None), at(idv, 1, 0, None), at(idv, 0, -1, None), at(idv, 0, 1, None)
    near = np.where(dr > dl, il, ir)
    m2 = np.minimum(dl, dr)
    near = np.where(m2 > du, iu, near)
    near = np.where(np.minimum(m2, du) > dd, idn, near)
    if glsl:
        edge = (il != ir) | (il != iu) | (il != idn)
    else:
        z = f32(ids[3])
        edge = ((il - near) != z) | ((ir - near) != z) | ((iu - near) != z) | ((idn - near) != z)
    col = np.zeros((h, w, 4), f32)
    for j in range(3):
        col = np.where((near == f32(ids[j]))[..., None], np.array(colors[j], f32), col)
    out = col * edge[..., None].astype(f32)
    u8 = np.floor(np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8)
    return (u8[..., [2, 1, 0, 3]] if swap else u8), edge


def main():
    glsl = "--glsl" in sys.argv
    paths = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not paths:
        print(__doc__)
        return 1
    total_bad = 0
    for path in paths:
        resolves, snaps = load(path)
        ld_draws = sorted(d for d, (_, t) in snaps.items() if t[7] == PS_LINEAR_DEPTH)
        for d in sorted(k for k, (_, t) in snaps.items() if t[7] == PS_CONTOUR):
            regs = snaps[d][0]
            after = [r for r in resolves if r["draw"] > d]
            if not after or "img" not in after[0]:
                continue
            before = [r for r in resolves if r["draw"] < d and "img" in r]
            t0 = [int(v) for v in regs[0x4800:0x4806]]
            id_base = ((t0[1] >> 12) & 0xFFFFF) << 12
            ids_src = [r for r in before if r["base"] == id_base]
            depth = [r for r in before if r["base"] == 0x0E921000]
            lds = [k for k in ld_draws if k < d]
            if not ids_src or not depth or not lds:
                continue
            a, b = (f32(v) for v in psc(snaps[lds[-1]][0], 255)[:2])
            d24 = (depth[-1]["img"].view("<u4")[..., 0] >> 8).astype(np.float64)
            lin = (a / (a - (d24 / 16777215.0).astype(f32) * b)).astype(f32)
            channel = (((t0[3] >> 1) & 0xFFF) >> 6) & 7
            colors = [psc(regs, n) for n in range(3)]
            ids, scale = psc(regs, 254), psc(regs, 255)[0]
            ref, edge = contour(ids_src[-1]["img"][..., channel], lin, colors, ids, scale,
                                after[0]["swap"], glsl)
            diff = np.abs(ref.astype(int) - after[0]["img"].astype(int))
            bad = int((diff.max(-1) > 0).sum())
            total_bad += bad
            print("%s draw %d -> %08X: arestas %d (%.2f%%), erro max %d medio %.6f, pixeis "
                  "diferentes %d de %d" % (os.path.basename(path), d, after[0]["base"],
                                           edge.sum(), 100 * edge.mean(), diff.max(), diff.mean(),
                                           bad, diff.shape[0] * diff.shape[1]))
    return 1 if total_bad else 0


if __name__ == "__main__":
    sys.exit(main())
