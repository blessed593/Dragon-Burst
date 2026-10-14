#!/usr/bin/env bash
# Aplica os patches Android:
#   patches/NNNN-*.patch       ao SDK  (external/burstlimit/thirdparty/rexglue-sdk)
#   patches/game/NNNN-*.patch  ao jogo (external/burstlimit)
#
# 0000-0048 sao os patches do Dragon Fury (DBZ3-Android) refeitos sobre o SDK do
# Burst Limit (iExplosiveRage/rexglue-sdk, branch burstlimit); 0049+ sao do Dragon
# Burst. Pode ser executado sempre que houver patches novos: os que ja estao
# aplicados sao saltados.
#
# Uso (a partir da raiz do repositorio, depois de tools/setup-external.sh):
#   bash tools/apply-patches.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BL="$ROOT/external/burstlimit"
SDK="$BL/thirdparty/rexglue-sdk"

if [ ! -d "$SDK/src" ]; then
  echo "ERRO: SDK nao encontrado em $SDK. Corre primeiro: bash tools/setup-external.sh" >&2
  exit 1
fi

if [ ! -f "$SDK/thirdparty/sdl3/CMakeLists.txt" ]; then
  echo "ERRO: submodulos do SDK em falta. Corre: bash tools/setup-external.sh" >&2
  exit 1
fi

# apply_patches <pasta alvo> <patches...>
# Regista os patches ja aplicados em <alvo>/.dragonburst-patches. Necessario
# porque um patch posterior pode mexer nas mesmas linhas de um anterior e o
# teste "--reverse" deixa de o reconhecer.
apply_patches() {
  local TARGET="$1"
  shift
  local STAMP="$TARGET/.dragonburst-patches"
  touch "$STAMP"
  (
    cd "$TARGET"
    for PATCH in "$@"; do
      [ -f "$PATCH" ] || continue
      NAME="$(basename "$PATCH")"
      if grep -qxF "$NAME" "$STAMP"; then
        echo "ja aplicado: $NAME"
        continue
      fi
      if patch -p1 --dry-run --reverse --force --silent < "$PATCH" > /dev/null 2>&1; then
        echo "$NAME" >> "$STAMP"
        echo "ja aplicado: $NAME"
        continue
      fi
      TEST="$(patch -p1 --dry-run --forward < "$PATCH" 2>&1 || true)"
      if ! patch -p1 --dry-run --forward --silent < "$PATCH" > /dev/null 2>&1; then
        FILES="$(grep -c '^checking file' <<< "$TEST" || true)"
        APPLIED="$(grep -c 'previously applied' <<< "$TEST" || true)"
        if [ "$FILES" -gt 0 ] && [ "$FILES" = "$APPLIED" ]; then
          echo "$NAME" >> "$STAMP"
          echo "ja aplicado: $NAME"
          continue
        fi
        echo "ERRO: $NAME nao aplica de forma limpa. Saida do teste:" >&2
        echo "$TEST" >&2
        exit 1
      fi
      patch -p1 --forward --silent --no-backup-if-mismatch < "$PATCH"
      echo "$NAME" >> "$STAMP"
      echo "aplicado:    $NAME"
    done
  )
}

echo "== SDK (external/burstlimit/thirdparty/rexglue-sdk)"
apply_patches "$SDK" "$ROOT"/patches/[0-9][0-9][0-9][0-9]-*.patch
echo "== Jogo (external/burstlimit)"
apply_patches "$BL" "$ROOT"/patches/game/[0-9][0-9][0-9][0-9]-*.patch
echo "Patches aplicados."
