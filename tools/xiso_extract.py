#!/usr/bin/env python3
"""Extrai um arquivo da raiz de uma imagem de disco de Xbox 360 (.iso, XDVDFS).

Uso: python3 tools/xiso_extract.py <imagem.iso> <nome no disco> <destino>
Ex.: python3 tools/xiso_extract.py BurstLimit.iso default.xex /tmp/default.xex

Mesmo leitor do launcher (GameFiles.kt, classe Xdvdfs): procura o descritor
"MICROSOFT*XBOX*MEDIA" no setor 32 a partir dos offsets conhecidos (XGD1/2/3) e
percorre a arvore da pasta raiz.
"""
import struct
import sys

SECTOR = 2048
MAGIC = b"MICROSOFT*XBOX*MEDIA"
BASES = (0, 0xFB20 * SECTOR, 0x20600 * SECTOR, 0x2080000 * SECTOR, 0xFD90000 * SECTOR)


def find_volume(f):
    for base in BASES:
        f.seek(base + 32 * SECTOR)
        head = f.read(28)
        if head[:20] == MAGIC:
            root_sector, root_size = struct.unpack_from("<II", head, 20)
            return base, root_sector, root_size
    return None


def find_entry(f, base, dir_sector, dir_size, wanted):
    """Percorre a arvore toda (sem confiar na ordem dos nomes)."""
    f.seek(base + dir_sector * SECTOR)
    table = f.read(dir_size)
    wanted = wanted.lower()
    stack, seen = [0], set()
    while stack:
        offset = stack.pop()
        if offset in seen or offset + 14 > len(table):
            continue
        seen.add(offset)
        left, right, sector, length, attr, name_len = struct.unpack_from("<HHIIBB", table, offset)
        if left == 0xFFFF and right == 0xFFFF:
            continue
        name = table[offset + 14:offset + 14 + name_len].decode("ascii", "replace")
        if name.lower() == wanted:
            return sector, length, attr
        for nxt in (left, right):
            if nxt:
                stack.append(nxt * 4)
    return None


def main():
    if len(sys.argv) != 4:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    iso, wanted, dest = sys.argv[1:]
    with open(iso, "rb") as f:
        vol = find_volume(f)
        if not vol:
            print(f"ERRO: {iso} nao e uma imagem de disco de Xbox 360 (XDVDFS).", file=sys.stderr)
            return 1
        base, root_sector, root_size = vol
        entry = find_entry(f, base, root_sector, root_size, wanted)
        if not entry or entry[2] & 0x10:
            print(f"ERRO: {wanted} nao esta na raiz do disco.", file=sys.stderr)
            return 1
        sector, length, _ = entry
        f.seek(base + sector * SECTOR)
        with open(dest, "wb") as out:
            left = length
            while left:
                chunk = f.read(min(left, 1 << 20))
                if not chunk:
                    print("ERRO: imagem cortada.", file=sys.stderr)
                    return 1
                out.write(chunk)
                left -= len(chunk)
    print(f"{wanted}: {length} bytes -> {dest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
