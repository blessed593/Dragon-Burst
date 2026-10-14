#!/usr/bin/env python3
"""Extrai para PNG as texturas que os draws de uma captura .dfc leem (o conteudo esta
na memoria do guest gravada na captura), para ver onde a imagem se perde.

Formatos: k_8_8_8_8, k_DXT1, k_DXT2_3, k_DXT4_5 (tiled ou linear, com a troca de
endian da Xenos). Os resolves que o jogo le como textura (render targets copiados)
saem como 8_8_8_8 1280x720 etc. - com "Atalho dos efeitos: modo rapido" ligado eles
nao vao para a memoria do guest e saem a zero.

Uso:
  python3 tools/dfc_textures.py captura.dfc --out pasta [--frame 0] [--draws 200-260]
  python3 tools/dfc_textures.py captura.dfc --out pasta --addr 0DE59000
  python3 tools/dfc_textures.py captura.dfc --out pasta --resolves
    (capturas com "resolve_readback=1", patch 0058: a imagem que CADA resolve do 1.o frame
     escreveu, lida de volta da GPU - ve-se em que passe a imagem se perde)
  python3 tools/dfc_textures.py captura.dfc --out pasta --eventos [--addr 0DE59000]
    (patch 0061: linha do tempo do 1.o frame - resolves, cargas e invalidacoes de texturas
     e os draws que as leem)
Precisa do OptXbox (pasta irma ../optxbox ou OPTXBOX=/caminho).
"""
import argparse
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
for cand in (os.environ.get("OPTXBOX", ""), os.path.join(HERE, "..", "..", "optxbox"),
             os.path.join(HERE, "..", "..", "Optxbox"), os.path.expanduser("~/Optxbox"),
             os.path.expanduser("~/optxbox")):
    if cand and os.path.isfile(os.path.join(cand, "optxbox", "dfc.py")):
        sys.path.insert(0, cand)
        break
from optxbox.dfc import Capture, REGS  # noqa: E402

# formato (6 bits do fetch) -> (nome, bytes por bloco, texels por lado do bloco)
FORMATS = {
    6: ("8_8_8_8", 4, 1),
    18: ("DXT1", 8, 4),
    19: ("DXT2_3", 16, 4),
    20: ("DXT4_5", 16, 4),
}


def fetch_constants(regs, slot):
    base = REGS["SHADER_CONSTANT_FETCH_00_0"] + slot * 6
    return regs[base:base + 6]


def tiled_offset(x, y, pitch, bpb_log2):
    """Endereco tiled 2D da Xenos (x, y e pitch em blocos; resultado em bytes)."""
    pitch = (pitch + 31) & ~31
    macro = ((x >> 5) + (y >> 5) * (pitch >> 5)) << (bpb_log2 + 7)
    micro = ((x & 7) + ((y & 0xE) << 2)) << bpb_log2
    offset = macro + ((micro & ~0xF) << 1) + (micro & 0xF) + ((y & 1) << 4)
    return (((offset & ~0x1FF) << 3) + ((y & 16) << 7) + ((offset & 0x1C0) << 2) +
            (((((y & 8) >> 2) + (x >> 3)) & 3) << 6) + (offset & 0x3F))


def swap_block(raw, endian):
    if endian == 1:  # 8in16
        return bytes(b for i in range(0, len(raw), 2) for b in (raw[i + 1], raw[i]))
    if endian == 2:  # 8in32
        return b"".join(raw[i:i + 4][::-1] for i in range(0, len(raw), 4))
    if endian == 3:  # 16in32
        return b"".join(raw[i + 2:i + 4] + raw[i:i + 2] for i in range(0, len(raw), 4))
    return raw


def rgb565(v):
    r, g, b = (v >> 11) & 31, (v >> 5) & 63, v & 31
    return ((r << 3) | (r >> 2), (g << 2) | (g >> 4), (b << 3) | (b >> 2))


def dxt_color(block, out, x0, y0, w, opaque4):
    c0, c1, bits = struct.unpack_from("<HHI", block, 0)
    p0, p1 = rgb565(c0), rgb565(c1)
    if c0 > c1 or opaque4:
        p2 = tuple((2 * a + b) // 3 for a, b in zip(p0, p1))
        p3 = tuple((a + 2 * b) // 3 for a, b in zip(p0, p1))
        pal = [p0 + (255,), p1 + (255,), p2 + (255,), p3 + (255,)]
    else:
        p2 = tuple((a + b) // 2 for a, b in zip(p0, p1))
        pal = [p0 + (255,), p1 + (255,), p2 + (255,), (0, 0, 0, 0)]
    for i in range(16):
        out[(y0 + i // 4) * w + x0 + i % 4] = list(pal[(bits >> (2 * i)) & 3])


def decode(data_at, w, h, fmt, pitch, tiled, endian):
    name, bpb, bs = FORMATS[fmt]
    bpb_log2 = {4: 2, 8: 3, 16: 4}[bpb]
    bw, bh = (w + bs - 1) // bs, (h + bs - 1) // bs
    pitch_blocks = max(pitch // bs, bw)
    W, H = bw * bs, bh * bs
    out = [[0, 0, 0, 255] for _ in range(W * H)]
    missing = 0
    for by in range(bh):
        for bx in range(bw):
            off = tiled_offset(bx, by, pitch_blocks, bpb_log2) if tiled else (by * pitch_blocks + bx) * bpb
            raw = data_at(off, bpb)
            if raw is None:
                missing += 1
                continue
            raw = swap_block(raw, endian)
            if name == "8_8_8_8":
                # k_8_8_8_8 do guest: ARGB em big-endian -> depois da troca 8in32 fica BGRA
                b, g, r, a = raw
                out[by * W + bx] = [r, g, b, a]
            elif name == "DXT1":
                dxt_color(raw, out, bx * 4, by * 4, W, False)
            else:
                dxt_color(raw[8:], out, bx * 4, by * 4, W, True)
                if name == "DXT2_3":
                    alpha = struct.unpack_from("<Q", raw, 0)[0]
                    for i in range(16):
                        out[(by * 4 + i // 4) * W + bx * 4 + i % 4][3] = ((alpha >> (4 * i)) & 15) * 17
                else:
                    a0, a1 = raw[0], raw[1]
                    bits = int.from_bytes(raw[2:8], "little")
                    if a0 > a1:
                        pal = [a0, a1] + [((6 - k) * a0 + (k + 1) * a1) // 7 for k in range(6)]
                    else:
                        pal = [a0, a1] + [((4 - k) * a0 + (k + 1) * a1) // 5 for k in range(4)] + [0, 255]
                    for i in range(16):
                        out[(by * 4 + i // 4) * W + bx * 4 + i % 4][3] = pal[(bits >> (3 * i)) & 7]
    rows = [bytes(v for px in out[y * W:y * W + w] for v in px) for y in range(h)]
    return rows, missing, bw * bh


def write_png(path, rows, w, h, keep_alpha):
    if not keep_alpha:
        rows = [bytes(v if (i % 4) != 3 else 255 for i, v in enumerate(r)) for r in rows]
    raw = b"".join(b"\x00" + r for r in rows)

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", zlib.compress(raw, 6)))
        f.write(chunk(b"IEND", b""))


# Registos usados pelos resolves (indices do register file da Xenos).
REG_RB_COPY_CONTROL = 0x2318
REG_RB_COPY_DEST_BASE = 0x2319
REG_RB_COPY_DEST_PITCH = 0x231A
REG_RB_COPY_DEST_INFO = 0x231B
REG_PA_SC_WINDOW_SCISSOR_TL = 0x2081
REG_PA_SC_WINDOW_SCISSOR_BR = 0x2082
COPY_FORMATS = {6: "8_8_8_8", 36: "32_FLOAT", 7: "2_10_10_10", 22: "24_8"}


def tiled_table(w, h, pitch):
    """Offsets (bytes, 32 bpp) de cada texel de uma imagem tiled, linha a linha."""
    pitch = max(pitch, w)
    return [[tiled_offset(x, y, pitch, 2) for x in range(w)] for y in range(h)]


def dump_resolves(path, out_dir):
    regs = [0] * 0x10000
    pending = None
    draw = -1
    frame = 0
    index = 0
    tables = {}
    for typ, body in Capture.records(path):
        if typ == 1:
            n = struct.unpack_from("<I", body)[0]
            for i, v in struct.iter_unpack("<II", body[4:4 + n * 8]):
                if i < len(regs):
                    regs[i] = v
        elif typ == 2:
            draw = struct.unpack_from("<IIIIIIQQI", body)[8]
        elif typ == 3:
            cc = regs[REG_RB_COPY_CONTROL]
            info = regs[REG_RB_COPY_DEST_INFO]
            pitch_reg = regs[REG_RB_COPY_DEST_PITCH]
            tl, br = regs[REG_PA_SC_WINDOW_SCISSOR_TL], regs[REG_PA_SC_WINDOW_SCISSOR_BR]
            pending = {
                "draw": draw, "frame": frame, "src": cc & 7,
                "clear": ((cc >> 8) & 1, (cc >> 9) & 1),
                "base": regs[REG_RB_COPY_DEST_BASE] & 0x1FFFFFFF,
                "pitch": pitch_reg & 0x3FFF, "height": (pitch_reg >> 16) & 0x3FFF,
                "endian": info & 7, "fmt": (info >> 7) & 0x3F, "swap": (info >> 24) & 1,
                "w": (br & 0x7FFF) - (tl & 0x7FFF), "h": ((br >> 16) & 0x7FFF) - ((tl >> 16) & 0x7FFF),
            }
        elif typ == 4:
            frame += 1
        elif typ == 8:
            addr, n = struct.unpack_from("<II", body)
            data = body[8:8 + n]
            r = pending or {}
            pending = None
            if not r:
                continue
            w, h = max(r["w"], 0), max(r["h"], 0)
            if not w or not h or r["fmt"] not in COPY_FORMATS:
                print("r%02d d%d %08X formato %d %dx%d: nao descodificado" % (
                    index, r["draw"], r["base"], r["fmt"], w, h))
                index += 1
                continue
            shift = addr - r["base"]
            key = (w, h, r["pitch"])
            if key not in tables:
                tables[key] = tiled_table(w, h, r["pitch"])
            table = tables[key]
            words = []
            for y in range(h):
                row = []
                for off in table[y]:
                    o = off - shift
                    raw = data[o:o + 4] if 0 <= o and o + 4 <= len(data) else b"\0\0\0\0"
                    row.append(swap_block(raw, r["endian"]))
                words.append(row)
            is_depth = r["src"] == 4
            kind = COPY_FORMATS[r["fmt"]]
            rows = []
            nonblack = 0
            total = [0, 0, 0]
            if is_depth or kind in ("32_FLOAT", "24_8"):
                vals = []
                for row in words:
                    for raw in row:
                        if kind == "32_FLOAT" and not is_depth:
                            vals.append(struct.unpack("<f", raw)[0])
                        else:
                            vals.append(struct.unpack("<I", raw)[0] >> 8)
                finite = [v for v in vals if v == v and abs(v) != float("inf")]
                lo, hi = (min(finite), max(finite)) if finite else (0, 1)
                span = (hi - lo) or 1
                k = 0
                for y in range(h):
                    line = bytearray()
                    for x in range(w):
                        v = vals[k]
                        k += 1
                        g = int(255 * (v - lo) / span) if v == v and abs(v) != float("inf") else 255
                        line += bytes((g, g, g, 255))
                        nonblack += g > 0
                        total[0] += g
                    rows.append(bytes(line))
                stats = "min %.6g max %.6g" % (lo, hi)
            else:
                for row in words:
                    line = bytearray()
                    for raw in row:
                        b, g, rr, a = raw
                        line += bytes((rr, g, b, 255))
                        nonblack += (rr | g | b) > 0
                        total[0] += rr
                        total[1] += g
                        total[2] += b
                    rows.append(bytes(line))
                stats = "media RGB %d,%d,%d" % tuple(t // (w * h) for t in total)
            src = "prof" if is_depth else "cor%d" % r["src"]
            name = "r%02d_f%d_d%04d_%08X_%dx%d_%s_%s.png" % (
                index, r["frame"], r["draw"], r["base"], w, h, kind, src)
            write_png(os.path.join(out_dir, name), rows, w, h, False)
            print("%s  %s  nao-preto %.0f%%  endian=%d swap=%d limpa=%d/%d" % (
                name, stats, 100.0 * nonblack / (w * h), r["endian"], r["swap"],
                r["clear"][0], r["clear"][1]))
            index += 1
    if index == 0:
        print("Sem dados de resolves nesta captura (precisa do patch 0058: resolve_readback=1).")


def dump_events(path, wanted):
    regs = [0] * 0x10000
    frame = 0
    draw = -1
    for typ, body in Capture.records(path):
        if frame > 0:
            break
        if typ == 1:
            n = struct.unpack_from("<I", body)[0]
            for i, v in struct.iter_unpack("<II", body[4:4 + n * 8]):
                if i < len(regs):
                    regs[i] = v
        elif typ == 2:
            draw = struct.unpack_from("<IIIIIIQQI", body)[8]
            ps = struct.unpack_from("<Q", body, 32)[0]
            reads = []
            for slot in range(32):
                d = fetch_constants(regs, slot)
                if d[0] & 3 != 2:
                    continue
                addr = ((d[1] >> 12) & 0xFFFFF) << 12
                if not wanted or addr in wanted:
                    reads.append("tf%d=%08X" % (slot, addr))
            if reads:
                print("d%04d ps=%016X le %s" % (draw, ps, " ".join(reads)))
        elif typ == 3:
            dest = regs[REG_RB_COPY_DEST_BASE] & 0x1FFFFFFF
            if not wanted or dest in wanted:
                print("   resolve -> %08X (depois do d%d)" % (dest, draw))
        elif typ == 4:
            frame += 1
        elif typ == 10:
            di, line, ok = struct.unpack_from("<3I", body)
            print("      d%04d terminou na linha %d de IssueDraw (%s)" % (di, line, "ok" if ok else "falhou"))
        elif typ == 9:
            kind, base, w, h, fmt, flags = struct.unpack_from("<6I", body)
            if not wanted or base in wanted:
                print("   %s %08X %dx%d fmt=%d flags=%X" % (
                    "CARGA" if kind == 1 else "INVALIDA", base, w, h, fmt, flags))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("capture")
    ap.add_argument("--out", required=True)
    ap.add_argument("--frame", type=int, default=0)
    ap.add_argument("--draws", default="")
    ap.add_argument("--addr", action="append", default=[])
    ap.add_argument("--alpha", action="store_true", help="manter o alfa (por omissao opaco)")
    ap.add_argument("--resolves", action="store_true",
                    help="a imagem escrita por cada resolve (capturas com resolve_readback=1)")
    ap.add_argument("--eventos", action="store_true",
                    help="linha do tempo de resolves/cargas/invalidacoes (patch 0061)")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    if a.resolves:
        dump_resolves(a.capture, a.out)
        return
    if a.eventos:
        dump_events(a.capture, {int(x, 16) for x in a.addr})
        return
    lo, hi = 0, 1 << 30
    if a.draws:
        p = a.draws.split("-")
        lo, hi = int(p[0]), int(p[-1])
    wanted = {int(x, 16) for x in a.addr}

    # Releitura dos registos para ter os fetch constants completos (pitch, endian).
    cap = Capture(a.capture)
    regs = [0] * 0x10000
    seen = {}
    frame = 0
    for typ, body in Capture.records(a.capture):
        if typ == 1:
            n = struct.unpack_from("<I", body)[0]
            for i, v in struct.iter_unpack("<II", body[4:4 + n * 8]):
                if i < len(regs):
                    regs[i] = v
        elif typ == 2:
            di = struct.unpack_from("<IIIIIIQQI", body)[8]
            if frame != a.frame or not (lo <= di <= hi):
                continue
            for slot in range(32):
                d = fetch_constants(regs, slot)
                if d[0] & 3 != 2:
                    continue
                fmt = d[1] & 0x3F
                addr = ((d[1] >> 12) & 0xFFFFF) << 12
                if wanted and addr not in wanted:
                    continue
                w = (d[2] & 0x1FFF) + 1
                h = ((d[2] >> 13) & 0x1FFF) + 1
                pitch = ((d[0] >> 22) & 0x1FF) * 32
                tiled = (d[0] >> 31) & 1
                endian = (d[1] >> 6) & 3
                key = (addr, w, h, fmt)
                if key not in seen:
                    seen[key] = (di, pitch, tiled, endian)
        elif typ == 4:
            frame += 1
    for (addr, w, h, fmt), (di, pitch, tiled, endian) in sorted(seen.items(), key=lambda kv: kv[1][0]):
        if fmt not in FORMATS:
            print("%08X %dx%d formato %d: nao suportado (draw %d)" % (addr, w, h, fmt, di))
            continue
        data_at = lambda off, n, base=addr: cap.memory.read(base + off, n)  # noqa: E731
        rows, missing, total = decode(data_at, w, h, fmt, pitch or w, tiled, endian)
        name = "d%04d_%08X_%dx%d_%s.png" % (di, addr, w, h, FORMATS[fmt][0])
        write_png(os.path.join(a.out, name), rows, w, h, a.alpha)
        print("%s  pitch=%d tiled=%d endian=%d  blocos sem memoria: %d de %d" %
              (name, pitch, tiled, endian, missing, total))


if __name__ == "__main__":
    main()
