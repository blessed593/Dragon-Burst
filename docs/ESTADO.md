# Estado do Dragon Burst

Port Android do [DBZ Burst Limit Recompiled](https://github.com/iExplosiveRage/DBZ-Burst-Limit-Recompiled)
(iExplosiveRage), feito sobre a infraestrutura do Dragon Fury (DBZ3-Android). Aparelhos de teste:
**Retroid Pocket 5** (Snapdragon 865 / Adreno 650) e **Anbernic RG Cube** (Unisoc T770 / Mali-G57).

O que cada recurso do PC vira no Android: [COMPATIBILIDADE.md](COMPATIBILIDADE.md).

## Fase atual: 0 → 1 (base pronta, primeiro build no PC)

Feito nesta base:

- Pipeline reprodutível: `tools/versions.env` fixa o commit do BL (v5.2, `bb60bc1`) e do SDK dele
  (`465f9ef`, branch `burstlimit` do fork do iExplosiveRage, base ReXGlue v0.10).
- **Patches do SDK** (`patches/`, aplicados em `external/burstlimit/thirdparty/rexglue-sdk`):
  - `0000`–`0048`: os 49 patches do Dragon Fury refeitos sobre o SDK do BL (o BL mexe em muitos
    arquivos do SDK: as zonas que conflitavam foram resolvidas à mão; o `0000` traz a telemetria base
    `dbz3:` que o DBZ3 tinha no overlay do jogo).
  - `0049-vulkan-texture-replacement`: o envio das texturas de substituição no Vulkan (no PC só o D3D12
    tinha). Faz os mipmaps quando faltam, copia por um buffer de staging e solta-o no próximo submit.
  - `0050-vulkan-draw-overrides`: os `draw_overrides` do BL no Vulkan (ligar/desligar profundidade de
    campo, glow, motion blur, filtro suave; esconder o HUD da câmera livre).
  - `0051-posix-online-sockets`: o XNet (sockets do jogo) compila e funciona em POSIX.
  - `0052-android-overlay-fonts`: fontes do sistema Android para o menu do jogo / overlay.
  - `0053-cvar-gpu-debug-log-draws`: a cvar `gpu_debug_log_draws` só era definida no D3D12, mas o cache de
    texturas comum usa-a (link falhava fora do Windows).
- **Patches do jogo** (`patches/game/`, aplicados em `external/burstlimit`):
  - `0001-android-portability`: caminhos (pasta da app = "pasta do exe"), HTTP por JNI
    (`android_http.cpp` → `NativeHttp.kt`), sons de menu pelo SDL3, saves, instalador/tela inicial e
    Discord desligados, itens só de PC escondidos do menu do jogo.
  - `0002-android-pause-point`: ponto de pausa usado pelo launcher (menus de toque com o jogo parado).
  - `0003-android-libcxx-fixes`: diferenças do libc++ do NDK (ex.: `file_time_type` em `__int128`).
- Verificado aqui (Linux x86-64): os 55 patches aplicam limpos num clone novo e o resultado é idêntico
  ao branch de trabalho; o SDK com os patches compila no host (os únicos erros são do `std::expected`
  do libstdc++ do host nas ferramentas `rexglue`/codegen, que no PC com clang 20 + libc++ não aparecem);
  `vulkan/texture_cache.cpp`, `vulkan/command_processor.cpp`, `xsocket.cpp`, `xam_net.cpp` e
  `overlay_text.cpp` (com `REX_PLATFORM_ANDROID`) compilam. Os fontes do jogo passaram por
  verificação de sintaxe com as flags do Android e com os headers do libc++ 19 (o do NDK r28):
  jogo, SDK e JNI sem erros além dos que só existem fora do Android (APIs do bionic).
- No PC do dfdx047 (CachyOS, clang 23): `codegen.sh` com o `.iso` Redump funcionou (90 arquivos
  gerados); o primeiro build NDK parou num `to_string` ambíguo, corrigido no `game/0003`.
- **Não verificado aqui** (sem NDK/Android SDK neste ambiente): o build Gradle/NDK completo e o
  Kotlin. A revisão estática do Kotlin não achou erros pendentes, mas o primeiro build no PC é que
  confirma.
- **Launcher** adaptado ao BL: importação por `.iso` (XDVDFS) ou pasta, verificação do `default.xex`
  USA (SHA-1), todas as opções do BL que funcionam no Vulkan, mods no formato do BL, packs de texturas
  (vários, um ativo), extração de texturas, saves (Download ⇄ `saves/`), online em rede local,
  controles de toque com os comandos do BL (Rush X, Smash Y, Ki blast B, Guarda A, Arremesso LT,
  Aura Spark LB, Perseguição RT, Transformar RB, Ki L3) e layout próprio da câmera livre.

## Como compilar (PC Linux)

Precisa de: Android Studio ou SDK de linha de comando com o **NDK 28.2.13676358**, JDK 17,
clang 20+, cmake 3.25+, ninja, e os pacotes de desenvolvimento que o SDK pede no Linux para a
ferramenta de codegen (x11-xcb e wayland-client, por pkg-config). A cópia do jogo: o `default.xex`
da versão **USA** (SHA-1 `aec598f88cf51181fc377b148e0b1ad30db4485c`).

```bash
git clone https://github.com/dfdx047/Dragon-Burst.git && cd Dragon-Burst
bash tools/setup-external.sh                 # clona o BL na versão fixada + submódulos usados
bash tools/apply-patches.sh                  # patches do SDK e do jogo (pode repetir)
bash tools/codegen.sh ~/Jogos/BurstLimit.iso  # o .iso USA (ou o default.xex); gera generated/default
# CMake: usa o do sistema (PATH) automaticamente; ou cmake.dir=... no local.properties
./gradlew assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. Instalar nos aparelhos ligados por adb:
`bash tools/install.sh` (todos), `bash tools/install.sh rp5` ou `bash tools/install.sh cube`. `-Pdf.uiOnly=true` compila só o launcher (rápido,
sem o jogo).

No aparelho: abrir o Dragon Burst → **Importar .iso** (ou pasta com `default.xex` + `LONG2DATA/`) →
JOGAR.

## Próximos passos

1. **Rodada 1 do OptXbox nos dois aparelhos** (`docs/GUIA_OPTXBOX.md`): instalar, importar, jogar
   menus + uma luta de Versus com "Diagnóstico de desempenho" ligado, exportar os logs (aba Sobre) e
   analisar com `--prefix "dbz3: " --adb-pattern "DragonBurst-logs-*.zip"`. Objetivo: o jogo abre,
   chega aos menus e entra em luta; anotar FPS/GPU ms por aparelho.
2. Corrigir o que a rodada 1 mostrar (crashes de arranque, formatos de textura, áudio, entrada).
3. **Mali (RG Cube)**: medir 30 FPS / 1x; se não bastar, portar `draw_resolution_percent` (resolução
   abaixo de 720p) para o Vulkan.
4. **Mapa D3D do Burst Limit** (`tools/d3d_map.py`, `docs/GUIA_PORT_XBOX360_ANDROID.md`): achar o
   endereço da espera do ring buffer do D3D do BL para aplicar o atalho de espera do Dragon Fury.
5. **Online pela internet**: cliente WebSocket do lobby por JNI (o ICE/libjuice já compila);
   depois Ranked/placares. Testar o online em rede local entre os dois aparelhos antes.
6. Packs de texturas e mods reais nos aparelhos (RAM com packs grandes no RG Cube).

## Atualizar o Burst Limit Recompiled

1. Mudar `BL_COMMIT` (e `SDK_COMMIT`, se o submódulo mudou) em `tools/versions.env`.
2. Num clone limpo: `setup-external.sh` → `apply-patches.sh`. O que não aplicar: refazer o patch no
   repositório de trabalho do SDK (cherry-pick dos commits dos patches sobre o novo `burstlimit`) e
   exportar de novo (`git diff --full-index -M c~1 c` por commit, nome = assunto do commit).
3. `codegen.sh` de novo (o código gerado depende da versão do BL), build, rodada do OptXbox.

## Diferenças de nomes em relação ao Dragon Fury

- Prefixo de log `dbz3:` e cvars `dbz3_*` mantidos de propósito: são o contrato do OptXbox
  (`GUIA_OPTXBOX.md`) e dos patches 0000–0048.
- Config do runtime: `burstlimit.toml` (o mesmo nome do PC). Logs exportados:
  `DragonBurst-logs-<data>.zip` (com `aparelho.txt`, `cvars.txt`, `logcat.txt`, `logs/`,
  `burstlimit.toml`).
- String de versão online: `dragonburst-android` (salas só entre aparelhos com o mesmo APK).
