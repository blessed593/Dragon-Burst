#!/usr/bin/env bash
# Gera o codigo recompilado (external/burstlimit/generated/default) a partir do
# default.xex USA do jogo. Corre no PC Linux (a ferramenta rexglue e um programa do
# PC): compila so o alvo burstlimit_codegen do projeto original, numa pasta propria.
#
# Precisa de: clang/clang++, GCC 14, cmake, ninja e dependencias do SDK.
#
# Uso:
#   bash tools/codegen.sh /caminho/para/BurstLimit.iso
#   bash tools/codegen.sh /caminho/para/default.xex
#
# Sem argumento usa external/burstlimit/game_data_root/default.xex se existir.

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
  echo "Uso: bash tools/codegen.sh /caminho/para/BurstLimit.iso (ou default.xex)" >&2
  exit 1
fi

# Se nao for um XEX2, extrair default.xex da ISO.
EXTRACTED=0

if [ "$(head -c 4 "$XEX" | tr -d '\000')" != "XEX2" ]; then
  mkdir -p "$BL/game_data_root"

  python3 "$ROOT/tools/xiso_extract.py" \
    "$XEX" default.xex "$XEX_DST.tmp"

  mv "$XEX_DST.tmp" "$XEX_DST"
  XEX="$XEX_DST"
  EXTRACTED=1
fi

# Validar que o executavel pertence a versao USA/NTSC-U esperada.
SHA="$(sha1sum "$XEX" | cut -d' ' -f1)"

if [ "$SHA" != "$XEX_SHA1" ]; then
  echo "ERRO: este default.xex nao e o USA." >&2
  echo "SHA-1 encontrado: $SHA" >&2
  echo "SHA-1 esperado:   $XEX_SHA1" >&2
  echo "O Burst Limit Recompiled requer a versao USA (NTSC-U)." >&2

  if [ "$EXTRACTED" = 1 ]; then
    rm -f "$XEX_DST"
  fi

  exit 1
fi

mkdir -p "$BL/game_data_root"

if [ "$(realpath "$XEX")" != "$(realpath -m "$XEX_DST")" ]; then
  cp "$XEX" "$XEX_DST"
fi

# O ReXGlue exige Clang. Tentamos usar a libstdc++ do GCC 14
# para disponibilizar std::expected com C++23.
CC_BIN="${CC:-clang}"
CXX_BIN="${CXX:-clang++}"

CXX_EXTRA_FLAGS="-stdlib=libstdc++ -std=c++23"

echo "===== Compiladores do codegen ====="
"$CC_BIN" --version
"$CXX_BIN" --version

# Limpar a configuracao anterior para impedir que o CMake reutilize
# um compilador ou flags antigos.
rm -rf "$BUILD"

# Configurar o gerador para Linux x86-64.
cmake -S "$BL" -B "$BUILD" -G Ninja \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_COMPILER="$CC_BIN" \
  -DCMAKE_CXX_COMPILER="$CXX_BIN" \
  -DCMAKE_C_FLAGS="-march=x86-64-v2" \
  -DCMAKE_CXX_FLAGS="-march=x86-64-v2 $CXX_EXTRA_FLAGS" \
  -DREXGLUE_ENABLE_TRACY=OFF \
  -DREXGLUE_BUILD_TESTS=OFF \
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5 \
  -DREXSDK_DIR="$BL/thirdparty/rexglue-sdk"

echo "===== Compilando burstlimit_codegen ====="

cmake --build "$BUILD" \
  --target burstlimit_codegen \
  --parallel 2

# Verificar se o gerador criou os arquivos esperados.
if [ ! -f "$BL/generated/default/sources.cmake" ]; then
  echo "ERRO: o codegen nao gerou generated/default/sources.cmake." >&2
  exit 1
fi

COUNT="$(grep -c '\.cpp' "$BL/generated/default/sources.cmake" || true)"

echo
echo "Codigo recompilado pronto: $COUNT ficheiros em external/burstlimit/generated/default."
echo "Proximo passo: ./gradlew assembleRelease"
