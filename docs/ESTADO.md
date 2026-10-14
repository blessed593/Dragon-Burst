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
  - `0054-pulse-event-registered-waits`: o `PulseEvent` do runtime POSIX perdia o aviso quando a
    thread estava entre soltar o mutex e esperar (`SignalAndWait`); o Burst Limit usa mutex + evento
    como variavel de condicao e encravava no arranque (o PC usa o Windows, que faz os dois passos de
    uma vez). Agora a espera regista-se antes de soltar o mutex e o pulso acorda quem esta registado.
    Teste de stress do padrao: o codigo antigo encrava em <3000 rondas, o novo passa 1 milhao.
  - `0055`/`0056`: diagnostico (`dbz3: gpu-diag` a cada 2 s, `dbz3: VdSwap #n`, e aos 10/20 s as
    threads do guest presas numa espera, com o objeto e quantos sinais recebeu).
  - `0057-auto-reset-event-handoff`: a causa real do ecra preto. O Burst Limit usa UM evento
    automatico para as duas direcoes do aperto de mao thread principal <-> worker (mutex + estado +
    `SetEvent`). No NT, `SetEvent` com alguem a espera entrega o sinal a essa thread; no runtime
    POSIX o sinal ficava "no ar" e a thread principal, que volta logo a esperar no mesmo evento,
    roubava-o. Agora a espera entra numa fila e o sinal vai para o primeiro da fila (Set e Pulse).
    Teste do padrao exato: o codigo anterior encrava 3/3 vezes logo na 1.a ronda; o novo passa
    200 mil rondas (Set e Pulse), mais testes de regressao dos eventos.
  - `0058-capture-resolve-readback`: a captura `.dfc` grava a imagem de cada resolve (lida de
    volta da GPU no 1.o frame capturado), para ver em que passe a imagem se perde.
  - `0059-bl-clear-to-1x`: o atalho "Clear direto em 1x" do Dragon Fury reconhece os shaders do
    Clear() do XDK do Burst Limit (menos 4 transferencias 4x → 1x por frame).
  - `0060-swap-diagnostics`: o fetch constant do frontbuffer de cada swap no log e na captura.
  - `0061-capture-texture-events`: cargas e invalidacoes de texturas na captura.
  - `0062-capture-draw-outcome`: onde cada draw da captura terminou em `IssueDraw`.
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

## Rodada 1 (2026-10-14, depois do 0057)

O jogo arranca, passa os menus e entra na luta nos dois aparelhos. Problema: **sem texturas em
jogo (tudo preto)**; no Mali o ecra pisca (menus e luta, como no Dragon Fury).

| zip | aparelho | otimizacoes da GPU | fps luta | GPU ms luta | fins de passe/f | fps menus |
|---|---|---|---:|---:|---:|---:|
| 19:46 | RP5 | desligadas | 25 | 39,0 | 44 | 54 |
| 19:53 | RP5 | ligadas | 48 | 18,5 | 31 | 58 |
| 20:07 | RG Cube | ligadas | 14 | 70,0 | 24 | 44 |

- As otimizacoes do Dragon Fury ja valem no Burst Limit (RP5 25 → 48 fps em luta).
- Suspeita principal para o preto: atalhos validados so no Budokai 3 que reconhecem shaders do
  D3D do XDK (restauro 6E57…, profundidade via cor ADCA…) e eliminam resolves/restauros que no
  DBZ3 ninguem mais lia (`dbz3_rt_elide_restores`, `dbz3_skip_depth_restore`; 4,9 resolves
  eliminados/f no RP5). Falta confirmar com A/B (otimizacoes desligadas) e captura `.dfc`.
- ~300-800 avisos "Recovered stale physical page protection" por sessao (caminho de recuperacao do
  runtime original, paginas sem observador): verificar nas capturas se tocam texturas.

## Rodada 2 (2026-10-14, capturas `.dfc` da luta: RP5 20:32, RG Cube 20:28)

O que ficou provado:

- **As otimizacoes do Dragon Fury nao sao a causa do preto.** No zip 19:46 o log confirma tudo
  desligado ("sinais de textura como spec constants: false", sem `rt_direct`, sem atalhos de
  resolve, 19,7 resolves/f para a memoria) e a luta ja estava preta.
- `clear_memory_page_state = true` nao muda nada (tirar o `cvars.txt`).
- Os menus (2D) estao certos; o preto e so na luta. As vezes aparece um frame do jogo com as
  cores trocadas.
- **A captura nao ve o que a GPU desenha.** No Vulkan os resolves ficam na memoria da GPU e nunca
  voltam a memoria do guest: as texturas 1280x720 que a captura grava saem a zero mesmo sem os
  atalhos. Por isso entra o **0058**: durante a captura (1.o frame), cada resolve e lido de volta
  da GPU (espera a GPU, so nesse frame) e gravado no `.dfc` (registo 8). Os atalhos de resolve
  (eliminacao e "modo rapido") ficam desligados nesse frame para tudo passar pela memoria.
  `python3 tools/dfc_textures.py captura.dfc --out pasta --resolves` tira um PNG por resolve
  (cor, profundidade e 32_FLOAT em cinzento) com a % de pixeis nao pretos: ve-se em que passe a
  imagem se perde.

Mapa de um frame de luta (RP5, 332 draws, iguais no Mali):

| draws | o que e | alvo |
|---|---|---|
| 0–23 | 24 pontos sem cor (mascara 0, scissor 16x16) | p1280 1x |
| 24–53 | sombra / silhueta (Clear 1x + Clear 4x p520) → resolve 0ECB9000 1024x1024 | p1040 1x |
| 54 | Clear do XDK (VS 0A6D1DD7…, PS 2E372EA2…, 4x p640) | 4x → 1x |
| 55–128 | 1.a passagem da cena (lutadores com 2.o stream skinned em 0x0CB9…–0x0CC1…) | p1280 1x D@720 |
| 129–136 | resolves cor 0E589000 + profundidade 0E921000; profundidade 24_8 → linear 32_FLOAT (0F451000) | |
| 137–211 | Clear + 2.a passagem da cena (mesmos draws) | p1280 1x |
| 212–218 | distorcao + aura (sprites 4A56… com a profundidade linear) → 0DE59000 | |
| 220–245 | pos-processamento: brilho (640x360), motion blur, DOF, filtro suave (saltados por omissao) | |
| 246–304 | HUD (profundidade sempre a passar) → 0E1F1000 | |
| 306–307 | Clear 4x (cor@0 + profundidade@0) e copia de 0E1F1000 para o alvo | |
| 308–331 | mais 24 pontos sem cor, resolve para o frontbuffer 0C000000 | |

- O Clear do XDK do Burst Limit usa shaders diferentes dos do Budokai 3, por isso o atalho
  "Clear direto em 1x" (`dbz3_clear_to_1x`, 0042) nunca disparava: 4 transferencias 4x → 1x por
  frame (Mali: transfer ~12 ms/frame). **0059** junta os shaders do BL ao atalho.
- Os avisos "Recovered stale physical page protection" sao das 3 threads de skinning a escrever
  os vertices dos lutadores (anel triplo de 8 MB: 0x0C4…, 0x0CC…, 0x0D4…); 300–2000 por sessao.
  Por ver se deixam a GPU com vertices velhos (nao explica o ecra todo preto: o cenario e estatico).

## Rodada 3 (2026-10-14 22:08, RP5, captura com leitura de volta dos resolves)

Otimizacoes desligadas, os 4 efeitos ligados. **A GPU desenha a luta**: o resolve para o
frontbuffer (0C000000) tem o cenario, os lutadores e o pos-processamento. Defeitos vistos na
imagem: ceu preto e uma esfera branca enorme por cima do cenario (a ver depois).

Como o frame com leitura de volta sai certo e o jogo normal mostra preto, a suspeita passa a
ser uma de duas:
1. sincronizacao: a leitura de volta fecha e espera cada submissao depois de cada resolve;
2. memoria do guest: a leitura de volta copia a imagem para a memoria do CPU; se algo volta a
   carregar o frontbuffer (ou outra copia) a partir da memoria do CPU, sem a leitura de volta
   carrega zeros (preto).
Teste A/B sem recompilar, no `cvars.txt`: `readback_resolve = "full"` (sincrona) contra
`readback_resolve = "fast"` (copia atrasada, sem esperar a GPU).

## Rodada 4 (2026-10-14 22:47 e 22:54, RP5)

- Captura com as otimizacoes do Dragon Fury **ligadas** (efeitos ligados): a cena sai certa ate
  0DE59000, mas a composicao do brilho (F59256…, le 0DE59000 + o brilho 640x360) sai preta e tudo
  dai para a frente tambem, ate ao frontbuffer. Com elas **desligadas** (captura das 22:08) a
  mesma composicao sai certa e o frontbuffer tem a luta.
- Conclusao: as otimizacoes do Budokai 3 apagam o pos-processamento do Burst Limit. Passam a vir
  desligadas por omissao no launcher (o atalho certo para o BL fica para o render nativo).
- Por confirmar no aparelho: otimizacoes desligadas com os efeitos desligados (a predefinicao).

## Rodada 5 (2026-10-14 23:44, RP5) e patch 0060

- Otimizacoes desligadas, efeitos ligados, sem leitura de volta: luta a 13–16 fps, ecra preto.
  Mas a captura das 22:08 (mesma configuracao) tinha a luta inteira no frontbuffer: a imagem
  existe na GPU e nao chega ao ecra. Suspeita: a apresentacao (o presenter le o frontbuffer pelo
  fetch constant que o VdSwap escreve; se na luta ele descrever outro formato/tiling/endian, a
  imagem lida sai preta ou com cores trocadas).
- O Mali tambem fica preto: nao e um problema do driver do Adreno 650.
- O upstream do BL nao tem commits depois do v5.2 (`bb60bc1`) e o fork do SDK tambem nao. A
  "versao Linux" do BL e o build de Windows (D3D12) com vkd3d-proton/Wine: **o backend Vulkan do
  SDK do BL nunca foi usado pelo autor**; tudo o que o fork mudou em codigo partilhado so foi
  validado com D3D12.
- **0060**: regista no log cada variante do fetch constant do swap (`dbz3: swap fb=...`) e grava
  os registos do swap na captura. Comparar menus com a luta.

## Rodada 6 (2026-10-14 23:55, RP5, otimizacoes desligadas, efeitos ligados)

- O swap usa sempre o mesmo fetch constant (0C000000 8_8_8_8 1280x720 tiled, endian 0), nos
  menus e na luta: a apresentacao nao e o problema.
- O utilizador ve um vislumbre da imagem so na cutscene do inicio da luta. A captura das 22:08
  era dessa cutscene; a das 23:55 e da luta a valer.
- Na luta: a cena (0DE59000, depois das auras) sai **certa**; o passe seguinte, a composicao do
  brilho (PS F59256…: `oC0 = tex0 * c0.w + tex1 * c0.x`, c0 = (8,0,0,1), com tex0 = 0DE59000),
  sai **preto**, e tudo dai ate ao frontbuffer tambem. O draw, as constantes e os fetch constants
  sao iguais aos da cutscene (onde sai certo), exceto o LOD bias do tex0 (-1 na luta, 0 na cutscene).
- Suspeita: a textura de 0DE59000 nao e recarregada depois do resolve (fica com o conteudo velho,
  que no fim do frame anterior ja era preto: o preto alimenta-se a si proprio). **0061** grava na
  captura cada carga e cada invalidacao de textura (registo 9) e `dfc_textures.py --eventos
  --addr 0DE59000` mostra a linha do tempo.
- Desassemblador de shaders Xenos no PC (shader.cpp + translator_disasm.cpp do SDK + stubs): usar
  o ucode da captura com os dwords trocados (big-endian).

## Rodada 7 (2026-10-15 00:14, RP5, com 0061)

- A textura 0DE59000 **e** invalidada pelo resolve e recarregada antes da composicao: nao e
  textura velha. Mesmo assim a composicao sai preta, e o HUD inteiro tambem nao aparece no
  resolve depois do HUD (0% de pixeis nao pretos).
- O que falha na luta sao **todos os draws com teste de profundidade ligado e funcao "sempre"**
  (composicao, motion blur, HUD); os da cena (LEQUAL) e os sem profundidade funcionam. Na
  cutscene os mesmos draws funcionam. `free_camera` esta desligado (controles.log), por isso o
  "esconder HUD" do modo foto nao deveria estar ativo.
- **0062**: cada draw da captura grava a linha do `return` em `IssueDraw` (registo 10;
  `dfc_textures.py --eventos`). Linhas (no SDK com 0000–0062): 4373 esconder HUD, 4763 pipeline
  provisorio (compilacao assincrona), 5122 desenhado; 4346 override "skip"; 4613 sem vertices.
- Teste sem recompilar: `async_shader_compilation = false` no cvars.txt (se a composicao/HUD
  ficam com pipelines provisorios para sempre, isto resolve).

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
