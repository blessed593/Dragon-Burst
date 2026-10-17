# Compatibilidade: Burst Limit Recompiled (PC) → Dragon Burst (Android)

Tudo o que o projeto do PC tem (README do [DBZ Burst Limit Recompiled](https://github.com/iExplosiveRage/DBZ-Burst-Limit-Recompiled),
versão fixada em `tools/versions.env`) e o que acontece com cada coisa no Android.

Legenda: **✅ portado** · **🧪 portado, falta testar no aparelho** · **🔁 substituído** (o launcher faz o papel) ·
**⏳ planejado** · **❌ não se aplica / não dá no Android (por quê)**

## Jogo e conteúdo

| Recurso do PC | Cvar / onde | Android | Notas |
|---|---|---|---|
| Recompilação nativa (sem JIT) | — | 🧪 | Mesmo código gerado do `default.xex` USA, compilado para arm64 (`-march=armv8.2-a`). |
| Só a versão USA (NTSC-U) | SHA-1 `aec598f8…` | ✅ | `tools/codegen.sh` e o launcher conferem o SHA-1. |
| Instalar a partir do `.iso` | `--install_iso` | 🔁 | Launcher: **Importar .iso** (leitor XDVDFS próprio, só copia `default.xex` + `LONG2DATA/`) ou **Importar pasta**. |
| Tela inicial (Play/Mods/Settings/Update) | `start_screen` | 🔁 | O launcher é a tela inicial; `start_screen = false` sempre. |
| Atualizações | `update_check_url` | 🔁 | Launcher verifica as releases do `dfdx047/Dragon-Burst`. |
| Menu de configurações no jogo (Back + Start) | — | 🧪 | Mesmo menu do PC; os itens só de PC são escondidos (patch `game/0001`): `fullscreen`, `window_size`, `dlss_*`, `fsr_mode`, `fsr_sharpness`, `texture_lod_bias`, `discord_presence`, `bind_free_camera`, `bind_freeze_game`, `start_screen`, `draw_resolution_percent`. Fontes do sistema Android para o overlay (patch 0052). |
| Taxa de quadros 30/60/120 | `frame_rate` | 🧪 | Launcher: Desempenho → Taxa de quadros (30 = original). 120 (nativo) = `frame_rate = "120"`, experimental, só em telas de 110 Hz ou mais (a tela passa para o modo de 120 Hz). 144/unlocked ficam de fora. |
| 120 FPS interpolado | `dbz3_frame_interpolation`, `dbz3_frame_interpolation_quality` | 🧪 | Novo no Android (patch 0092): o jogo a 60 e um quadro gerado entre cada dois no presenter Vulkan; só em telas de 110 Hz ou mais; +1 quadro de latência. |
| Cutscenes suaves a 60 | (automático) | 🧪 | Vem junto do `frame_rate = 60`. |
| Correção do tempo de jogo | (automático) | 🧪 | |
| 4 roupas escondidas | `story_costumes` | 🧪 | Configurações → Jogo. |
| 3 cenários novos | `extra_stages` | 🧪 | Configurações → Jogo. |
| Começar transformado (RB/LB) | `start_forms` | 🧪 | Configurações → Jogo. |
| Carregar ki (L3) | `ki_charge`, `ki_charge_rate` | 🧪 | Configurações → Jogo + botão de toque **Ki** (L3). |
| Personagens extras de mods (`slot.toml`) | `extra_characters` | 🧪 | Configurações → Jogo; R2/L2 na seleção. |
| Ícones de botão Xbox Series / PS / Switch | `button_icons` | 🧪 | Configurações → Jogo (auto/xbox/playstation/switch). |
| "Online" em vez de "Xbox LIVE" | `online_branding` | 🧪 | |
| Sons de menu | `menu_sounds` | 🧪 | Patch `game/0001`: tocam por um stream do SDL3 em vez do `PlaySound` do Windows. |
| Vibração | `vibration` | 🧪 | Controles → Comando físico; vibração do SDL3 (`DragonBurstHaptics`). |
| Zona morta dos analógicos | `left/right_stick_deadzone_percentage` | 🧪 | Controles → Comando físico. |
| Câmera livre / modo foto | `free_camera`, `freeze_game` | 🧪 | Liga pelo menu do jogo (as teclas Insert/Numpad 0 não existem). Layout de toque próprio "Câmera livre" (comando completo com os dois analógicos), trocado sozinho quando a câmera liga. |
| Campo de visão | `field_of_view` | 🧪 | Aplicado na matriz do jogo (CPU): funciona igual. |
| Exportar / importar save | menu do jogo → SAVE | 🧪 | `saves/` fica dentro da app; Configurações → **Saves** copia os `.zip` para Download/ e traz `.zip` de fora (PC ou Xenia). |
| Painel de FPS | `debug_overlay` (F3) | 🧪 | Desempenho → Mostrar FPS. |
| Discord | `discord_presence` | ❌ | Não existe app do Discord com IPC local no Android. |
| Teclado / atalhos F1, F3, Insert | — | ❌ | Back + Start abre o menu; o resto fica no launcher. |

## Mods e texturas

| Recurso do PC | Cvar / onde | Android | Notas |
|---|---|---|---|
| Mods (pasta `mods/`, `mod.toml`, `mods.toml`) | `mods_enabled` | 🧪 | Aba **Mods**: instalar `.zip`, ligar/desligar, tipo detectado (modelo, modelo PS3, áudio, interface, personagem extra), conflitos (o último em ordem alfabética ganha), corrigir pasta a mais. Menu "Mods" do jogo também funciona. |
| Modelos de PS3 convertidos na hora | (automático) | 🧪 | Código do jogo, independente da plataforma. |
| Substituição de texturas | `texture_replace_enabled`, `texture_folder` | 🧪 | No PC só existia no D3D12; o Dragon Burst implementou o envio no Vulkan (patch **0049**). Aba **Texturas**: packs `.zip` (vários, um ativo), imagens soltas, validação dos nomes/formatos e o log do jogo. |
| Pré-carregar packs pequenos | `texture_replace_preload` | 🧪 | Aba Texturas. |
| Decodificação em segundo plano | `texture_replace_async`, `texture_replace_ram_mb` | 🧪 | Padrões do PC (async ligado, 3072 MB). |
| Extrair texturas (dump) | `texture_dump_enabled` | 🧪 | Aba Texturas → Extrair texturas originais (vai para `textures/dump`). |
| Ícones de botão em alta resolução | `button_icons` | 🧪 | Usam a mesma substituição de texturas: dependem do patch 0049. |

## Gráficos

| Recurso do PC | Cvar | Android | Notas |
|---|---|---|---|
| Renderizador | — | 🔁 | PC usa D3D12; Android usa o backend **Vulkan** do SDK + os 49 patches do Dragon Fury (DBZ3-Android). |
| Resolução interna 1x–3x (720p × N) | `draw_resolution_scale_x/y` | 🧪 | Desempenho → Resolução interna. |
| Resolução abaixo de 720p | `draw_resolution_percent` | ⏳ | Só existe no D3D12. Candidato para Mali se a medição pedir (precisa portar para o Vulkan). |
| FSR 1 + CAS, FXAA, anisotrópico | `present_effect`, `swap_post_effect`, `anisotropic_override` | 🧪 | Desempenho → Imagem. FSR 1/CAS no Vulkan vêm do patch 0015. |
| Manter proporção / esticar | `present_letterbox` | 🧪 | |
| Profundidade de campo, glow, motion blur | `depth_of_field`, `glow_blur`, `motion_blur` | 🧪 | No PC o desligar passa por `draw_overrides` (só D3D12); o Dragon Burst ligou-o no Vulkan (patch **0050**). Desligados por padrão, como no PC. |
| Filtro suave | `soft_filter` | 🧪 | Idem (patch 0050). |
| Esconder HUD (câmera livre) | `draw_overrides` | 🧪 | Patch 0050. |
| Clears no lugar / extents na CPU | (otimizações D3D12) | ❌ | Específicas do D3D12. O Vulkan tem as do Dragon Fury: `dbz3_clear_to_1x`, `dbz3_rt_direct`, resolve direto, shaders especializados (Desempenho → Otimizações da GPU). |
| Detalhe de textura | `texture_lod_bias` | ❌ | Só D3D12. |
| FSR 4 / FSR 3.1.5 da cena, FSR 2/3 | `fsr_mode`, … | ❌ | Bibliotecas Windows (FidelityFX D3D12 / DLL). |
| DLSS / DLAA | `dlss_mode` | ❌ | NVIDIA RTX no Windows. |
| Janela / tela cheia / tamanho | `fullscreen`, `window_size` | ❌ | Tela cheia sempre (patch 0048). |
| Projeção da cena na GPU (FOV online / rotação da câmera livre) | — | ⏳ | Só no D3D12 do PC; o FOV offline já funciona pela matriz. Pequeno. |

## Online

| Recurso do PC | Cvar | Android | Notas |
|---|---|---|---|
| Lobby pela internet (WebSocket + ICE/libjuice) | `online_lobby_url` | ⏳ | O cliente WebSocket do lobby é WinHTTP no PC; no Android falta um cliente (plano: JNI → OkHttp/`java.net`). libjuice compila no Android. |
| Ranked Match e placares | (lobby) | ⏳ | Depende do lobby. |
| Rede local / Radmin (UDP direto) | `REX_XNET_IP`, `REX_XNET_SEARCH_IP` | 🧪 | Configurações → Online → **Rede local**: Anfitrião/Entrar, IP automático do Wi-Fi. Sockets POSIX no patch **0051**. |
| Atraso de entrada online | `online_input_delay` | 🧪 | Configurações → Online. |
| Nome online | `online_name` | 🧪 | Configurações → Online. |
| Cross-play PC ↔ Android | versão online | ❌ por enquanto | A string de versão é `dragonburst-android` (salas só entre builds iguais). Depois de testar a lógica de rede, dá para alinhar com uma release do PC. |

## Só do Android (vindo do Dragon Fury)

Controles de toque com editor e macros (layouts luta / menus / câmera livre), seletor de drivers Vulkan
(Turnip, libadrenotools) e correções do Turnip para Adreno 6xx, frameskip, telemetria `dbz3:` para o
OptXbox (perf a cada 5 s, captura de 2 frames `.dfc`, exportar logs `DragonBurst-logs-*.zip`), pausa do jogo
com os menus abertos, atalho na tela inicial, tema Dragon Burst / Material You, idiomas pt/en/es.
