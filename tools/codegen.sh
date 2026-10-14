#!/usr/bin/env bash
# Gera o codigo recompilado (external/burstlimit/generated/default) a partir do
# default.xex USA do jogo. Corre no PC Linux (a ferramenta rexglue e um programa do
# PC): compila so o alvo burstlimit_codegen do projeto original, numa pasta propria.
#
# Precisa de: clang/clang++ (20+), cmake (3.25+), ninja, e os pacotes de
# desenvolvimento que o SDK pede no Linux (x11-xcb e wayland-client, por pkg-config).
#
# Uso (a partir da raiz do repositorio, depois de setup-external.sh e apply-patches.sh):
#   bash tools/codegen.sh /caminho/para/BurstLimit.iso     (o .iso do disco USA)
#   bash tools/codegen.sh /caminho/para/default.xex        (ou o executavel ja extraido)
# Sem argumento usa external/burstlimit/game_data_root/default.xex se ja existir.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=versions.env
source "$ROOT/tools/versions.env"
BL="$ROOT/external/burstlimit"
BUILD="$ROOT/external/build-codegen"
XEX_DST="$BL/game_data_root/default.xex"

if [ ! -f "$BL/burstlimit_manifest.toml" ]; then
  echo "ERRO: external/burstlimit em falta. Corre: bash tools/setup-external.sh" >&2
  exit 1
fi

XEX="${1:-$XEX_DST}"
if [ ! -f "$XEX" ]; then
  echo "ERRO: arquivo nao encontrado ($XEX)." >&2
  echo "  Uso: bash tools/codegen.sh /caminho/para/BurstLimit.iso  (ou .../default.xex)" >&2
  exit 1
fi
# Um .iso (ou qualquer arquivo que nao seja um XEX2): extrair o default.xex da raiz.
EXTRACTED=0
if [ "$(head -c 4 "$XEX" | tr -d '\000')" != "XEX2" ]; then
  mkdir -p "$BL/game_data_root"
  python3 "$ROOT/tools/xiso_extract.py" "$XEX" default.xex "$XEX_DST.tmp"
  mv "$XEX_DST.tmp" "$XEX_DST"
  XEX="$XEX_DST"
  EXTRACTED=1
fi
SHA="$(sha1sum "$XEX" | cut -d' ' -f1)"
if [ "$SHA" != "$XEX_SHA1" ]; then
  echo "ERRO: este default.xex nao e o USA (SHA-1 $SHA, esperado $XEX_SHA1)." >&2
  echo "  O Burst Limit Recompiled so funciona com a versao USA (NTSC-U)." >&2
  [ "$EXTRACTED" = 1 ] && rm -f "$XEX_DST"
  exit 1
fi
mkdir -p "$BL/game_data_root"
if [ "$(realpath "$XEX")" != "$(realpath -m "$XEX_DST")" ]; then
  cp "$XEX" "$XEX_DST"
fi

CC_BIN="${CC:-clang}"
CXX_BIN="${CXX:-clang++}"
if [ ! -f "$BUILD/build.ninja" ]; then
  cmake -S "$BL" -B "$BUILD" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_COMPILER="$CC_BIN" -DCMAKE_CXX_COMPILER="$CXX_BIN" \
    -DREXGLUE_ENABLE_TRACY=OFF -DREXGLUE_BUILD_TESTS=OFF \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
    -DREXSDK_DIR="$BL/thirdparty/rexglue-sdk"
fi
cmake --build "$BUILD" --target burstlimit_codegen

if [ ! -f "$BL/generated/default/sources.cmake" ]; then
  echo "ERRO: o codegen nao gerou generated/default/sources.cmake." >&2
  exit 1
fi
COUNT="$(grep -c '\.cpp' "$BL/generated/default/sources.cmake" || true)"
echo
echo "Codigo recompilado pronto: $COUNT ficheiros em external/burstlimit/generated/default."
echo "Proximo passo: ./gradlew assembleRelease"
