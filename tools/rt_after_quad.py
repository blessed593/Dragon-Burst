#!/usr/bin/env python3
"""Regra do 0087/0090: antes de saltar um quad de ecra inteiro (passo 3b), ver nas capturas se
algum draw seguinte CARREGA o render target dele (mistura, cobertura parcial) antes de o alvo
ser sobrescrito por inteiro (Clear de ecra inteiro ou quad opaco de ecra inteiro).

Para cada draw com o PS dado, segue os draws seguintes com a mesma base de cor no EDRAM
(os resolves nao contam) ate ao primeiro que sobrescreve tudo; qualquer outro draw pelo meio
que nao seja opaco e de ecra inteiro no seu proprio alvo conta como "carrega".

Uso:
  python3 tools/rt_after_quad.py [--ps F59256F3F90DE9FF] captura.dfc [outra.dfc ...]
Sem capturas: todas as /tmp/claude-0/s/**/*.dfc (uma por nome).
"""
import glob
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
for cand in (os.environ.get("OPTXBOX", ""), os.path.join(HERE, "..", "..", "optxbox"),
             os.path.expanduser("~/optxbox")):
    if cand and os.path.isfile(os.path.join(cand, "optxbox", "dfc.py")):
        sys.path.insert(0, cand)
        break
from optxbox.dfc import Capture  # noqa: E402

PS_CLEAR = 0x2E372EA28CC404B7  # Clear() do XDK do Burst Limit (retangulo em pixeis)


def full_screen(d):
    """Sobrescreve o proprio alvo todo (cor opaca, 4 canais)?"""
    if not d.verts:
        return False
    if d.ps == PS_CLEAR and d.prim_name == 'rect_list' and d.mask == 0xFFFF:
        xs = [v[0][0] for v in d.verts]
        ys = [v[0][1] for v in d.verts]
        return min(xs) <= 0 and min(ys) <= 0 and max(xs) >= d.rt.pitch - 1
    if not d.opaque or (d.mask & 0xF) != 0xF or d.prim_name not in ('quad_list', 'rect_list'):
        return False
    p = [v[0] for v in d.verts]
    return (min(x[0] for x in p) <= -0.5 and max(x[0] for x in p) >= 0.5 and
            min(x[1] for x in p) <= -0.5 and max(x[1] for x in p) >= 0.5)


def main():
    args = sys.argv[1:]
    ps = 0xF59256F3F90DE9FF
    if '--ps' in args:
        i = args.index('--ps')
        ps = int(args[i + 1], 16)
        del args[i:i + 2]
    if not args:
        seen = {}
        for p in sorted(glob.glob('/tmp/claude-0/s/**/*.dfc', recursive=True)):
            seen.setdefault(os.path.basename(p), p)
        args = list(seen.values())
    bad = 0
    total = 0
    for path in args:
        cap = Capture(path)
        for fr in cap.complete_frames():
            ev = fr.events
            for i, e in enumerate(ev):
                if e.kind != 'draw' or e.ps != ps:
                    continue
                total += 1
                verdict = 'fim do frame'
                for f in ev[i + 1:]:
                    if f.kind == 'swap':
                        break
                    if f.kind != 'draw' or f.rt.cbase != e.rt.cbase:
                        continue
                    if full_screen(f):
                        if f.rt.pitch * (2 if f.rt.msaa == 2 else 1) == e.rt.pitch:
                            verdict = 'sobrescrito por %016X (%s)' % (f.ps, f.rt.short())
                            break
                        continue  # outro alvo, mesmo EDRAM: opaco de ecra inteiro
                    verdict = 'CARREGADO por draw %d %016X (%s)' % (f.n, f.ps, f.rt.short())
                    bad += 1
                    break
                print('%s frame %d draw %d: %s' % (os.path.basename(path), fr.index, e.n, verdict))
    print('%d quads, %d carregados depois' % (total, bad))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
