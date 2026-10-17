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
  - `0063-ignore-fetch-lod-bias-diagnostic`: cvar de diagnostico que ignora o LOD bias.
  - `0064-spirv-texture-exp-adjust-word3`: o `exp_adjust` das texturas lido da word 3 (era da word 4,
    o LOD bias): o preto da luta.
  - `0065-bl-native-present`: render nativo, passo 1 — a imagem final da luta apresentada direto.
  - `0066-bl-native-linear-depth`: render nativo, passo 2 — profundidade linear por compute.
  - `0067-bl-native-linear-depth-fix`: k_32_FLOAT no `Dbz3FindResolveTexture` (sem isto o passo 2
    nunca liga) e log da razao. Regra: patch ja publicado nunca se altera — o `apply-patches.sh`
    marca-o como aplicado e a versao nova nao entra (foi o que aconteceu a 1.a correcao do 0066).
  - `0068-bl-native-post-lite`: render nativo, passo 3a (draws nulos, resolves repetidos e sem leitor,
    preparacao do motion blur saltada com ele desligado).
  - `0069-resolve-dedupe-skipped-readers`: "sem leitor" passa a ser "so lido por draws saltados"
    (no RP5 o 100F9000 do brilho ficou 2 s sem leitor entre rounds e voltou a ser lido).
  - `0070-discard-full-overwrite-load`: passe que comeca com um quad opaco a cobrir o scissor
    nao carrega a cor (DONT_CARE, area de render = scissor): todos os passes do pos-processamento.
  - `0071-render-into-texture`: passo 4 - passe que acaba num resolve de cor 8888 para uma textura
    desenha direto nela (aprendido por assinatura do quad que abre o passe, 3 frames iguais); desiste
    e bane a assinatura se outro resolve, outro alvo, a textura lida no passe ou o render target
    voltar a ser preciso. So com dynamic rendering (o driver do RP5 e Vulkan 1.1).
- Autoteste (16/10 13:40, RP5 e RG Cube, efeitos ligados): nenhuma otimizacao muda a imagem.
  RG Cube: referencia 43,6 ms (22,4 FPS); passo 1 vale 1,4 ms, passo 2 4,5 ms, passo 3 ~1,5 ms;
  sem RT direto 79 ms, sem resolve sem memoria 69 ms. Desenho 28 ms em 16 passes, resolves 13,7 ms
  em 17,5/frame - o passo 4 ataca os resolves.
- Autoteste 16/10 14:06 (RG Cube): com dbz3_rt_direct LIGADO o Goku (P1, no ar) nao aparece e o
  fundo fica com outro desfoque - no RP5 nao. O passo 4 nao chegou a ligar (sem linha no log).
  `0072`: as partes do RT direto (restauros, restauro da profundidade, Clear 1x, uploads, transicao
  do resolve) passam a mudar ao vivo e entram no autoteste; o passo 4 compara tambem a base original
  do resolve e o log diz por que nao liga.
- Autoteste 16/10 20:04 (RG Cube, build 0072, restauros ignorados desligados no Mali): o Goku ja
  aparece, mas com dbz3_rt_direct LIGADO o Krillin (P2) fica meio transparente com o cenario por
  cima e o fundo mais desfocado (14,7%); nenhuma das partes separadas muda isso (Clear 1x: 1,75%).
  GPU 64 ms (15 FPS, efeitos todos ligados); sem RT direto 84-105 ms. Passo 4: `aprender falhou:
  resolve diferente 6055` - a captura mostrou que TODOS os resolves 8888 do pos tem
  `copy_dest_swap = 1` (troca R/B) e o passo 4 exigia 0.
  `0073-rit-bgra-rt-direct-switches`: o passo 4 aprende a troca e desenha numa vista B8G8R8A8 da
  textura (MUTABLE_FORMAT + lista de formatos; pipeline com a cor 0 em B8G8R8A8, bit
  `dbz3_color_0_bgra` na chave do passe; se o pipeline ainda compila, o passe vai pelo caminho
  normal sem banir). As duas partes do RT direto sem chave ganham-na:
  `dbz3_rt_direct_drop_transfers` e `dbz3_rt_direct_clear_siblings`; cada par de transferencia
  saltada aparece uma vez no log. O autoteste passa a usar a config atual como referencia
  (`sem X` / `com X`).
- Autoteste 16/10 20:30 (RG Cube) / 20:32 (RP5), build 0073: nada muda a imagem nos dois (o Krillin
  transparente nao voltou nesta cena; transferencias saltadas desligadas: 0,6% no Mali, igual).
  Mali ~21 FPS na referencia, sem RT direto 17 FPS, tudo desligado 13 FPS. Passo 4 no Mali:
  `18 assinaturas (18 prontas)` mas `nao renderizavel 15402` - as texturas do pos nao tem STORAGE
  (8888 tem a vista SNORM), so essas recebiam COLOR_ATTACHMENT. RP5: passo 4 nao corre (sem
  dynamic rendering), como esperado.
  `0074-rit-non-storage-textures`: qualquer 8888 2D sem mips pode ser alvo de cor (lista de formatos
  {UNORM, SNORM, B8G8R8A8}, se o SNORM tambem for alvo de cor); vistas proprias de alvo de cor; o
  log separa "nao renderizavel" de "vista".
- Autoteste 16/10 21:08 (RP5) / 21:12 (RG Cube), build 0074: **passo 4 funciona no Mali** - 18
  assinaturas, 14 prontas, 13551 passes desenhados direto na textura, imagem igual. Mali ~24 FPS /
  40 ms de GPU com tudo (o passo 4 vale ~4-5 ms). No RP5 (cena com o fundo longe) fazer todas as
  transferencias muda 22% da imagem (fundo nitido em vez de desfocado) - o mesmo que desligar o modo
  direto: alguma transferencia saltada faz falta para a profundidade/desfoque. No Mali fazer todas
  custa ~20 ms. Pares saltados: D832p13->D720p16, C0p13->D720p16, D0p16->C0p13/p16, C0p16->D0p16,
  C0p16<->C14 (profundidade linear), C0p16<->C0p8, C0p13<->C0p16.
  `0075-rt-direct-keep-kinds`: `dbz3_rt_keep_dd/cd/dc/cc` mantem so um tipo de transferencia; o
  autoteste testa cada um ("com transferencias X->Y").
- Autoteste 16/10 22:17 (RP5, Qualcomm) / 22:19 (RG Cube) / 22:22 (RP5, Turnip v26.3.0-R6),
  build 0075: nos dois primeiros so `com transferencias profundidade->cor` corrige a imagem
  (24% / 11,5%, o mesmo que sem modo direto). Custo: RP5 ~3 ms (22 -> 25 ms), Mali ~8 ms
  (48 -> 56 ms). Pares D->C saltados: D0@0p16 -> C0@0p13/p16, D0@720p16 -> C0@0p13.
  `0076-keep-depth-to-color-per-driver-cache`: `dbz3_rt_keep_dc` ligado por omissao (e no
  launcher); a cache de pipelines do driver passa a um ficheiro por driver
  (`vulkan_driver_pipelines_<driverID>_<versao>.bin`) - o Turnip estava a ler a da Qualcomm.
- **Turnip no RP5 (22:22):** imagem toda errada em todos os estados, ate "TUDO desligado" (fundo cor
  de azeitona, lutadores com cores de normais, riscas nas texturas; sem resolve sem memoria fica
  pior). Geometria certa: o problema e das texturas do jogo. E o mesmo que o Dragon Fury viu no
  RP5 com Turnip a 13/10 (ESTADO dele: "texturas e cores erradas, riscas"). So no Turnip: memoria
  do guest em 4 storage buffers (128 MB) e texturas BC nativas. App: com driver alternativo,
  memoria num so buffer de 512 MB sempre e, nos Adreno 6xx, BC descomprimido (como no driver da
  Qualcomm e no Mali) - bits 1 e 8 das "Correcoes do Turnip", agora automaticos.
- Autoteste 16/10 22:45 (RP5 Turnip, flags 9) / 22:46 (RP5 Qualcomm) / 22:52 (RG Cube): Qualcomm e
  Mali certos (so "sem transferencias profundidade->cor" muda: 19,6% / 15,3% - confirmado). Turnip:
  com TUDO desligado a cena aparece quase certa mas com R e B trocados; com tudo ligado, fundo cor de
  azeitona. Causa provavel: as 8888 com MUTABLE_FORMAT + lista {R8G8B8A8, B8G8R8A8} do passo 4
  (0073/0074), presentes em todos os estados (o Turnip so foi testado com elas).
  `0077-no-rit-textures-on-turnip`: no Turnip (driverID 18) as texturas sao criadas como no Xenia
  (`dbz3_rit_textures_turnip = false`); o passo 4 fica desligado nesse driver.
- Pesquisa do Turnip (OptXbox docs/en/TURNIP.md): suporte por geracao, builds, TU_DEBUG, limites (128
  MB de storage buffer em todas as geracoes, sem fragment shader interlock), emuladores. App:
  `drivers/TurnipSupport.java` (tabela SOC_MODEL -> Adreno -> estavel/experimental/sem suporte,
  mostrada na aba Drivers); TU_DEBUG automatico com Turnip (sysmem no A830 e A710/720/722;
  FD_DEV_FEATURES no Samsung A740); buffer unico e BC descomprimido voltam a ser so manuais; protecao
  contra driver que fecha o jogo no arranque (o seguinte usa o do sistema e a aba Drivers avisa);
  linha "GPU: ..." no logcat.
- Autoteste 16/10 23:38 (RP5 Qualcomm) / 23:44 (RP5 Turnip R6, com 0077) / 23:49 (RG Cube): Qualcomm e
  Mali certos. Turnip continua errado: referencia com o fundo cor de azeitona e lutadores com cores
  de normais; sem "resolve sem memoria" as texturas saem em riscas (tiling errado). O 0077 nao era a
  causa. Igual ao Dragon Fury no RP5 com Turnip (R5, R6, PurpleVK T30): o caminho do Xenia neste SDK
  com o Turnip do Adreno 650, independente das otimizacoes, e nao e GMEM/LRZ/UBWC/barreiras (o DF
  testou). O Purple (vulkan.purple.so) fechou o jogo no arranque e a protecao voltou ao driver do
  sistema (funcionou). Proximo: builds estaveis antigos recomendados para o 865 (K11MCH1 v24.3.0 R9,
  MrPurple T19) e as opcoes do compilador ir3 (IR3_SHADER_DEBUG nopreamble/nouboopt/nofp16, bits
  128/256/512 das Correcoes do Turnip) - se for um shader mal compilado, uma delas muda a imagem.
- 17/10 00:06 (RP5 Qualcomm) / 00:12 (RP5 Turnip v25.3.0 R11, Mesa 25.3): Turnip 25.3 com o mesmo
  defeito (fundo azeitona, lutadores com cores de normais; sem "resolve sem memoria" 90% diferente)
  - nao e da versao 26.3.
  **Bisect do Turnip** (aba Drivers): cada arranque do jogo com driver alternativo aplica um passo de
  `TurnipSupport.BISECT` (0 base, 1 sem dynamic rendering/stencil export, 2-4 ir3 nopreamble/nouboopt/
  nofp16, 5 sysmem, 6 noubwc+nolrz, 7 BC descomprimido, 8 buffer unico 512 MB, 9 sem robustBufferAccess/
  robustness2, 10 flushall+syncdraw, 11 depth float32), escrito em logs/turnip_bisect.txt e no topo do
  autoteste.txt.
  `0078-rit-render-pass-dc-pairs-mask-robust`: passo 4 tambem sem dynamic rendering (render pass +
  framebuffer proprios para a vista da textura, so passes de cor 0; framebuffers destruidos quando a
  submissao termina) - para o RP5 com o driver da Qualcomm; os pares profundidade->cor com chave
  propria (`dbz3_rt_dc_base0_to_pitch13/16`, `dbz3_rt_dc_other`, no autoteste); `vulkan_mask_features`
  aceita `robustBufferAccess`.
- Autoteste 17/10 00:32 (RP5 Turnip, sem o bisect ligado: nenhum turnip_bisect.txt) / 00:34 (RG Cube) /
  00:42 (RP5 Qualcomm), build 0078: **passo 4 funciona no RP5 com o driver da Qualcomm** (106572 passes
  direto na textura, imagem igual) e no Mali (27825). RP5 com efeitos: ~53 FPS / 18 ms de GPU (antes
  22-25 ms). Dos pares profundidade->cor so **D@0 -> C pitch 16** muda a imagem (23,7%); os outros dois
  nao. `0079-only-needed-depth-to-color-pair`: `dbz3_rt_dc_base0_to_pitch13` e `dbz3_rt_dc_other`
  desligados por omissao. Turnip: o congelar da luta nao segurou a imagem (o Goku mexeu-se entre fotos)
  e a imagem continua a do buffer de normais.
- 17/10 (offline, sem build): **passo 3b, primeiro efeito** - `0080-bl-native-contour`. Mapa do custo
  do pos no RP5 (Qualcomm, frame 1 das capturas de 16/10 07:32/07:48/08:00, as duas cadeias do frame):
  contorno ABBF 0,89-0,93 ms de draw + 1,16-1,19 de resolve; contorno por ID na cena 404B49 0,75;
  desfoques 97906B 0,84 + 0,60; composicao F59256 0,46-0,48 + 1,26-1,97; reducao B0133E 0,33 + 0,30.
  Escolhido o **ABBF (mascara do contorno dos lutadores -> 0F0B9000)**: o draw mais caro do pos e com
  saida discreta. O PS (desmontado com o `translator_disasm` do SDK) le o ID (floor(canal*255/64)) e a
  profundidade linear dos 4 vizinhos; se os IDs nao sao todos iguais escreve a cor (c0/c1/c2) do ID
  do vizinho mais perto da camara, senao 0 (profundidade fora da imagem = 0, borda preta).
  Referencia em numpy (`tools/ref_contour.py`) contra os resolves das capturas: **17 draws em 11
  capturas (RP5 Qualcomm, RP5 Turnip, RG Cube), 0 pixeis diferentes**. No SDK: com
  `dbz3_bl_native_contour` (ligada, HotReload) o quad e saltado e o resolve seguinte e um compute
  (le a profundidade so nas arestas, buffer + copia para a textura, porque as 8888 do pos nao tem
  STORAGE); reconhecimento exato (alvo, mistura, constantes do VS e do PS, os 4 vertices, fetch
  constants), aprende o destino no 1.o frame (antes do passo 4, que ficaria com esse resolve), corre
  fora de passes (serve o render pass do RP5). Precisa do passo 2 (a 0F451000 tem de estar em dia).
  No autoteste: "contorno por compute (passo 3b)"; no launcher vai com o pos-processamento enxuto.
  A ver no aparelho: `contorno por compute ativo`, o contador (~2 passes/frame, 0 falhas), imagem
  igual ligado/desligado (contorno e o desfoque dele, especiais com `c1` colorido) e o ganho em ms.
  - jogo `0004-android-app-menu`: sem o menu do PC no Android (o do app substitui-o).
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

## Rodada 8 (2026-10-15 00:31, RP5, com 0062 e `async_shader_compilation = false`)

- Ainda preto. **Todos os 254 draws do frame chegam ao fim de `IssueDraw`** (linha 5122): nenhum
  e saltado (nem HUD escondido, nem pipeline provisorio). A GPU executa-os e mesmo assim a
  composicao e o HUD nao aparecem.
- Os registos de render (0x2000–0x2FFF) da composicao sao **identicos** entre a cutscene (onde
  funciona) e a luta. A unica diferenca: o LOD bias do tf0 (word 4 do fetch constant = 0x003E0000
  ou 0x003E0003 → −1.0) em **todos** os draws que falham (composicao, HUD 2E37…, D9BA…, EC9B…);
  na cutscene e 0.
- **0063**: `dbz3_ignore_fetch_lod_bias = true` (cvars.txt) traduz os shaders sem o LOD bias do
  fetch constant. Se a luta aparecer, o problema esta no caminho do LOD bias (gradientes × 2^bias).

## Rodada 9 (2026-10-15 00:39, RP5): primeira luta com imagem

- A luta apareceu (Goku vs Krillin, ceu), 11–13 fps no RP5 com os 4 efeitos ligados (GPU ~75 ms:
  desenho 47 ms/27 passes, resolve 13 ms, texturas 8,5 ms, 21 resolves/frame).
- O diagnostico do 0063 **nao estava ligado** nessa sessao (cvars.txt so com
  `async_shader_compilation = false`). A captura mostra porque deu imagem: nessa luta o jogo
  **nao** pos o LOD bias (word 4 = 0); nas sessoes pretas punha −1 / −1,16 (0x003E…, 0x003B…).
  Confirma a correlacao: com o bias do jogo os draws de ecra inteiro e o HUD saem pretos.
- O launcher passa a escrever `dbz3_ignore_fetch_lod_bias = true` (contorno ate se perceber o
  erro no caminho do LOD bias do tradutor SPIR-V).

## Rodada 10 (2026-10-15 07:29–07:32, RP5, efeitos desligados, LOD bias ignorado)

- Luta com imagem certa (captura 07:31:58: HUD, cenario, auras). **23–27 fps** em luta (GPU
  ~37–42 ms: desenho 11–14 ms/20–24 passes, ~21 resolves/f, ~12 cargas/f) contra 12–15 fps com os
  efeitos ligados.
- Continua: o inicio do round 1 fica preto alguns segundos/minutos e depois aparece. Falta uma
  captura **durante o preto** com o contorno ligado. Pistas das capturas pretas anteriores: a
  1.a passagem da cena tem ~130 draws (64 nos frames bons) e a profundidade linear (0F451000)
  sai constante ~1,0 (nos frames bons vai de ~40 a ~6400).

## Rodada 11 (2026-10-15 07:55, RP5): a causa do preto

- Captura no preto do round 1 com o contorno do LOD bias ligado: continua preto. Diferenca de
  todas as constantes entre a composicao boa (cutscene) e a preta: so a word 4 do fetch 0
  (0x00000000 vs 0x003E0000).
- **Causa**: o tradutor SPIR-V aplicava o expoente do resultado (`exp_adjust`) lendo os bits
  13:18 da **word 4** do fetch constant; o `exp_adjust` esta nos bits 13:18 da **word 3** (o
  tradutor DXBC, usado pelo D3D12 do PC, le a word 3). Na word 4 esses bits sao parte do LOD
  bias (12:21): com o LOD bias −1 (0x3E0) davam −16, e cada amostra saia ×2^−16 → preto. O
  Budokai 3 nunca usa LOD bias, por isso nunca apareceu; o Burst Limit liga-o em parte da luta
  (round 1, intro), por isso "preto e depois aparece".
- **0064** corrige (word 3). O contorno `dbz3_ignore_fetch_lod_bias` volta a desligado.

## Rodada 12 (2026-10-15 08:04–08:10, RP5, com 0064): a luta funciona

| otimizacoes do Dragon Fury | efeitos | fps luta | GPU ms | desenho | transfer | resolve | texturas |
|---|---|---:|---:|---|---|---:|---:|
| desligadas | ligados | 12–16 | 60–80 | 32–49 ms/25–31p | 5–9 ms | 12–15 ms | 8–12 ms |
| **ligadas** | ligados | **41–57** | **17–23** | 7–11 ms/21–28p | 0 | 8,5–10,5 ms | 0,5 ms |

- Imagem certa nas duas (capturas 08:05:14 e 08:10:04: cenario de relva, HUD, auras). O preto
  da rodada 4 com as otimizacoes ligadas era o mesmo bug do exp_adjust, nao elas.
- As otimizacoes voltam a vir ligadas por omissao no launcher.
- Os efeitos do launcher funcionam (toml com os 4 a true e os draws de DOF/brilho/motion blur
  presentes na captura).
- Proximo: RG Cube (Mali) com tudo isto; depois o render nativo (o resolve, ~9–10 ms com ~20
  resolves/frame, e agora o maior custo; desenho ~8–11 ms em 21–28 passes).

## Rodada 13 (2026-10-15 13:27–13:38, RG Cube / Mali-G57, otimizacoes ligadas)

- Imagem certa na luta (captura 13:37:59). 13–17 fps com efeitos, 17–19 sem (GPU 52–74 ms:
  desenho 26–46 ms em 16–25 passes, resolve 15–20 ms). Desligar os efeitos quase nao muda: o
  jogo faz a cadeia de passes de ecra inteiro na mesma.
- Tempo por draw no Mali (captura com cada draw no seu passe, valores inflacionados mas
  comparaveis), frame da luta sem efeitos:

| fase | draws | % do tempo |
|---|---:|---:|
| pos-processamento (brilho/composicao, mesmo com os efeitos "desligados") | 25 | 31% |
| profundidade linear (copia da cena, A55B…, D987… 24_8 → 32_FLOAT) | 8 | 22% |
| final (Clear 4x + copia de 0E1F1000 + resolve para o frontbuffer) | 27 | 14% |
| sombra/silhueta (p1040) | 30 | 10% |
| cena 2.a passagem + auras | 87 | 8% |
| HUD | 65 | 7% |
| cena 1.a passagem | 75 | 6% |

- Prioridades do render nativo (Mali primeiro): 1) final: apresentar 0E1F1000 direto (sem a
  copia + resolve para 0C000000); 2) profundidade linear calculada direto do depth buffer num
  passe so; 3) cadeia de pos-processamento num so passe quando os efeitos estao desligados;
  4) juntar a 1.a passagem da cena so quando algum efeito a le.

## Render nativo — passo 1 (patch 0065, `dbz3_bl_native_present`)

- O fim de cada frame da luta: Clear 4x, quad de copia (VS 4666C5A9…, PS EC9B5278…: `tex * VS c5`)
  de 0E1F1000 para o render target, 24 pontos e resolve para o frontbuffer. Com `c5 = (1,1,1,1)` a
  copia e identidade: o quad e o resolve para o frontbuffer sao saltados e o presenter le direto
  a textura de 0E1F1000 (o fetch constant do quad entra no lugar do fetch 0 do swap). Com outra
  cor (fades entre rounds) fica o caminho normal. Interruptor no launcher (Desempenho).
- Resultado (logs de 15/10): Mali 18–20 FPS / GPU 50–54 ms com efeitos desligados; RP5 47–54 FPS
  com efeitos.

## Render nativo — passo 2 (patch 0066, `dbz3_bl_native_linear_depth`)

- Depois do resolve da profundidade (0E921000), o jogo desenha um quad de ecra inteiro (VS
  36FB87D3…, PS D9872B5A…: `c255.x / (c255.x - d * c255.y)`, A = 10000, B = 9999) num render
  target 32_FLOAT e resolve-o para a textura k_32_FLOAT 0F451000 (lida pelo bright pass, motion
  blur e DOF). Agora: no 1.o frame o resolve ensina o destino; dai em diante o quad e saltado e o
  resolve e feito por um compute (variante 6 do imageStore) que le a profundidade direto do render
  target (D@720, intacto entre o resolve e o quad) e escreve na textura. As texturas k_32_FLOAT
  ganharam usage STORAGE. Se algo nao bater (destino, depth noutro render target), volta ao caminho
  normal e reaprende. Interruptor no launcher (Desempenho).
- Logs de 16/10 (RG Cube e RP5): o passo nunca ficava pronto — o destino era "aprendido" a cada
  frame (16 mil linhas) porque `Dbz3FindResolveTexture` so aceitava 8888/24_8. Corrigido no 0067
  (k_32_FLOAT = R32_SFLOAT) e o log diz agora a razao quando nao da (`ainda nao (N)`).

## Render nativo — passo 3a (patch 0068)

- Logs de 16/10 07:08/07:11: o passo 2 ligou nos dois (RG Cube 19,6 -> 21,7 FPS, GPU 50 -> 45 ms;
  RP5 ~50 FPS, GPU 16-19 ms com efeitos).
- Mapa do pos com efeitos desligados (captura 07:07, RG Cube): os draw overrides saltam os desfoques,
  mas os resolves deles continuam - 12 resolves no pos. Agora:
  - `dbz3_resolve_dedupe`: resolve sem draws desde o anterior e com os mesmos parametros, para um
    destino que ja tem essa imagem, e saltado (0FF19000 depois do DOF desligado); destino que
    ninguem le antes de ser reescrito durante 120 escritas deixa de ser resolvido (10009000 x2,
    0F0B9000, 0DE59000 x2, e a profundidade 0E921000 que so o quad do passo 2 lia). Um draw que o
    volte a ler reativa-o.
  - `dbz3_skip_null_draws`: os 24 pontos por frame (PS 2E37..., mascara 0, sem profundidade) fora
    de consultas de oclusao.
  - `dbz3_bl_native_post_lite`: com o motion blur desligado, a preparacao dele (29D1...) e o resolve.
  Um interruptor no launcher (Depuracao) liga os tres.
- Proximo (passo 3b): a cadeia toda num compute (contorno ABBF, brilho B0133E/F59256, mascara CAC24,
  DOF/cor AA377), validado fora do aparelho com as imagens dos resolves das capturas.

- 17/10 03:09 (RP5 Qualcomm) / 03:14 (RP5 Turnip), build 0080: o contorno por compute nunca ligou -
  "contorno ainda nao (1)" (destino nao aprendido) nos dois; imagem igual. `0081-contour-learn-original-base`:
  o resolve do contorno tambem e aceite pela base original no EDRAM (como o passo 4 no 0072) e, se nao
  bater, uma linha "contorno, resolve nao bate: ..." diz qual campo falha.

## Launcher e menu do jogo (rodada de 16/10)

- Tema novo tirado dos menus do proprio Burst Limit (violeta, borda creme, dourado, lavanda),
  escuro por omissao. Inicio com palco animado (dois lutadores em silhueta, figuras originais, a
  disparar com o choque de energia ao centro) e os links: Discord, YouTube, GitHub, Ko-fi.
- Abas: Inicio, Desempenho (predefinicoes Leve/Equilibrado/Alto/Ultra pelo SoC + 30/60/120 FPS),
  Controles, Jogo (opcoes do BL, saves, online), Mods, Texturas, Drivers, Depuracao (logs,
  capturas, atalhos do renderizador), Configuracoes (o app), Sobre.
- Importacao da ISO: copia pelo kernel (`transferTo`), velocidade e tempo restante; verificacao
  do disco pelo cabecalho do XEX (title ID 424107DC, media ID USA 036A7E66): outro jogo, outra
  versao, executavel alterado, imagem cortada, CPK danificado. Lista da instalacao para o
  Verificar notar ficheiros cortados.
- Controles por toque: botoes de comando com direcao (especial 1/2, golpe final, dash, agarrar)
  que mandam a direcao e o botao juntos; lado do P1 pela camara + posicao dos lutadores (a
  confirmar nos logs, `controles.log` mostra `lado=`); o L3 (carregar ki) so aparece com a opcao
  ligada. Layouts antigos ganham os botoes novos (versao 2).
- Menu do jogo novo do Android (AndroidMenu.kt), por toque e por comando (Back + Start, pausa ou
  engrenagem): Pausa (camara livre, congelar, sair), Imagem, Efeitos (FOV), Jogo, Controles,
  Save (exportar/importar pelos comandos `save_export`/`save_import`), Sistema (captura). Sons
  do proprio jogo. O menu do PC deixou de existir no Android (patches/game/0004).

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

1. **Rodada 1 do OptXbox nos dois aparelhos** ([guia do OptXbox](https://github.com/dfdx047/Optxbox/blob/main/docs/pt-BR/FERRAMENTA.md)): instalar, importar, jogar
   menus + uma luta de Versus com "Diagnóstico de desempenho" ligado, exportar os logs (aba Sobre) e
   analisar com `--prefix "dbz3: " --adb-pattern "DragonBurst-logs-*.zip"`. Objetivo: o jogo abre,
   chega aos menus e entra em luta; anotar FPS/GPU ms por aparelho.
2. Corrigir o que a rodada 1 mostrar (crashes de arranque, formatos de textura, áudio, entrada).
3. **Mali (RG Cube)**: medir 30 FPS / 1x; se não bastar, portar `draw_resolution_percent` (resolução
   abaixo de 720p) para o Vulkan.
4. **Mapa D3D do Burst Limit** (`tools/d3d_map.py`, [guia de port](https://github.com/dfdx047/Optxbox/blob/main/docs/pt-BR/GUIA_DE_PORT.md)): achar o
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
  ([DIAGNOSTICO.md do OptXbox](https://github.com/dfdx047/Optxbox/blob/main/docs/pt-BR/DIAGNOSTICO.md))
  e dos patches 0000–0048.
- Config do runtime: `burstlimit.toml` (o mesmo nome do PC). Logs exportados:
  `DragonBurst-logs-<data>.zip` (com `aparelho.txt`, `cvars.txt`, `logcat.txt`, `logs/`,
  `burstlimit.toml`).
- String de versão online: `dragonburst-android` (salas só entre aparelhos com o mesmo APK).
