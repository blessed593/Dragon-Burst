#!/usr/bin/env python3
"""Extrai funcoes do codigo recompilado (external/burstlimit/generated/default) para
um ficheiro de texto: cada funcao com as instrucoes PowerPC em comentario. Usado para
analisar pilhas do vigia (logs/watchdog.txt) sem enviar o projeto todo.

Uso (a partir da raiz do repositorio):
  python3 tools/extract_funcs.py sub_820E7920 sub_822A2E50 ... > funcs.txt
  python3 tools/extract_funcs.py --from-watchdog watchdog.txt > funcs.txt
    (todas as sub_XXXXXXXX das threads do jogo no despejo)
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GEN = os.path.join(ROOT, "external", "burstlimit", "generated", "default")


def names_from_watchdog(path):
    names = []
    with open(path, encoding="utf-8", errors="replace") as f:
        for line in f:
            for m in re.finditer(r"\b(?:__imp__)?(sub_[0-9A-Fa-f]{8})\b", line):
                if m.group(1) not in names:
                    names.append(m.group(1))
    return names


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    if args[0] == "--from-watchdog":
        names = names_from_watchdog(args[1])
    else:
        names = [a for a in args if re.fullmatch(r"sub_[0-9A-Fa-f]{8}", a)]
    if not os.path.isdir(GEN):
        print(f"ERRO: {GEN} nao existe (corre tools/codegen.sh).", file=sys.stderr)
        return 1
    wanted = {n.upper().replace("SUB_", "sub_") for n in names}
    found = {}
    # Inicio de uma funcao: uma linha que define sub_XXXXXXXX (ou __imp__sub_XXXXXXXX)
    # com "(" e termina em "{"; o fim e a primeira linha "}" na coluna 0.
    start_re = re.compile(r"^[^/\n]*\b(?:__imp__)?(sub_[0-9A-Fa-f]{8})\b[^;\n]*\{\s*$")
    for name in sorted(os.listdir(GEN)):
        if not name.endswith(".cpp"):
            continue
        path = os.path.join(GEN, name)
        with open(path, encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
        i = 0
        while i < len(lines):
            m = start_re.match(lines[i])
            if m:
                fn = "sub_" + m.group(1)[4:].upper()
                if fn in wanted and fn not in found:
                    j = i
                    while j < len(lines) and lines[j].rstrip("\n") != "}":
                        j += 1
                    found[fn] = (name, "".join(lines[i:j + 1]))
                    i = j
            i += 1
    for fn in names:
        key = "sub_" + fn[4:].upper()
        if key in found:
            src, body = found[key]
            print(f"// ===== {key} ({src}) =====")
            print(body)
        else:
            print(f"// ===== {key}: nao encontrada =====\n")
    print(f"// {len(found)} de {len(wanted)} funcoes encontradas", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
