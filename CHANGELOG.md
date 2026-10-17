# Changelog

Cada versão em português, inglês e espanhol. O texto de cada release no GitHub (o que o app mostra ao
atualizar) fica em [docs/release/](docs/release/). O histórico de desenvolvimento, com os patches e os
números de cada rodada, fica em [docs/ESTADO.md](docs/ESTADO.md).

Each version in Portuguese, English and Spanish. The GitHub release text (what the app shows when it
updates) is in [docs/release/](docs/release/).

# 0.1.0: primeira versão · first release · primera versión (2026-10-17)

## PT

Primeira versão pública. Dragon Ball Z: Burst Limit (Xbox 360) rodando nativo no Android, sem emulação,
a partir do DBZ Burst Limit Recompiled v5.2. Nenhum arquivo do jogo vem incluído: você importa a sua
própria cópia USA (NTSC-U).

### O que o app faz
- Importa o jogo do `.iso` ou de uma pasta extraída, só a versão USA, e avisa se o disco é de outro jogo,
  de outra região, alterado ou cortado. **Verificar arquivos** confere a instalação.
- Todas as opções da versão de PC que funcionam no Android: roupas do modo história, cenários extras,
  começar transformado, carregar ki (L3), personagens extras de mods, ícones de botão, campo de visão,
  câmera livre e modo foto.
- Mods e packs de texturas HD no formato da versão de PC, extração de texturas para criar packs.
- Saves: exportar/importar, o mesmo `.zip` da versão de PC e do Xenia.
- Menu do jogo próprio do Android (Back + Start), launcher em português, inglês e espanhol.
- Atualizações pelo próprio app, com o resumo do que mudou.
- Autoteste do render e **Exportar logs** para reportar problemas.

### Desempenho
- Render nativo em 4 passos (imagem final direta, profundidade linear por compute, pós-processamento
  enxuto com contorno, redução, desfoque e brilho por compute, passes direto na textura), mais as
  otimizações de GPU do Dragon Fury. A 540p os mesmos passos ligam (patch 0091,
  ainda não confirmado no aparelho).
- Retroid Pocket 5 (Snapdragon 865): 720p com todos os efeitos a até 60 FPS (cerca de 47-50 FPS nas
  cenas pesadas). RG Cube (Mali-G57): 22-24 FPS a 720p com efeitos.

### Opções gráficas
- 5 predefinições (Mínimo, Leve, Equilibrado, Alto, Ultra) com os processadores recomendados; o app
  sugere a do seu aparelho.
- Resolução interna de 480p, 540p, 720p, 2K ou 4K.
- Efeitos do jogo ligáveis (profundidade de campo, brilho, desfoque de movimento, filtro suave), AMD
  FSR 1 + CAS, FXAA, anisotrópico.
- Drivers Turnip (Mesa) baixados pelo app, com o nível de suporte por Snapdragon. Experimental.

### 120 FPS
- Em telas de 120 Hz: **120 (interpolado)**, o jogo a 60 com um quadro gerado entre cada dois, e
  **120 (nativo)**, o jogo inteiro a 120. Os dois são experimentais: ainda não testados num aparelho de
  120 Hz.

### Controles
- Controles na tela com D-pad de 8 direções (ou analógico), visual do jogo e transparência; aparecem ao
  tocar e somem com um controle físico.
- Layouts de luta, menus e câmera livre, editor de layout e macros. Controles físicos com zona morta e
  vibração.

### Online
- Rede local (Wi-Fi), o método LAN da versão de PC. Experimental: ainda não testado entre dois
  aparelhos.

### Limitações conhecidas
- Só a versão USA do jogo.
- Lobby pela internet (Ranked, placares) ainda não disponível; sem cross-play com o PC.
- Mali-G57 MC4 (ex.: RG Cube): 540p a 30 FPS é o objetivo; a correção (patch 0091) ainda não foi
  confirmada no aparelho. A 480p só parte do render nativo liga.
- Turnip no Adreno 650 (Retroid Pocket 5) deixa a imagem errada: use o driver do sistema.
- Acima de 720p (2K, 4K) o render nativo desliga. Não há 1080p.
- Packs de texturas e mods ainda não foram testados com packs grandes nos aparelhos.

## EN

First public release. Dragon Ball Z: Burst Limit (Xbox 360) running natively on Android, not emulated,
built from DBZ Burst Limit Recompiled v5.2. No game files are included: you import your own USA (NTSC-U)
copy.

### What the app does
- Imports the game from the `.iso` or an extracted folder, USA version only, and tells you if the disc
  is another game, another region, modified or truncated. **Verify files** checks the install.
- Every PC version option that works on Android: story mode costumes, extra stages, start transformed,
  ki charge (L3), extra characters from mods, button icons, field of view, free camera and photo mode.
- PC-format mods and HD texture packs, texture dumping to make packs.
- Saves: export/import, the same `.zip` as the PC version and Xenia.
- Android in-game menu (Back + Start), launcher in Portuguese, English and Spanish.
- Updates through the app itself, with a summary of what changed.
- Render self-test and **Export logs** to report problems.

### Performance
- A 4-step native renderer (final image presented directly, linear depth by compute, lean
  post-processing with outline, downsample, blur and glow by compute, passes drawn straight into their
  textures), plus the Dragon Fury GPU optimizations. At 540p the same steps turn on (patch
  0091, not yet confirmed on a device).
- Retroid Pocket 5 (Snapdragon 865): 720p with every effect at up to 60 FPS (about 47-50 FPS in heavy
  scenes). RG Cube (Mali-G57): 22-24 FPS at 720p with effects.

### Graphics options
- 5 presets (Minimum, Light, Balanced, High, Ultra) with recommended processors; the app suggests the
  one for your device.
- Internal resolution 480p, 540p, 720p, 2K or 4K.
- Game effect toggles (depth of field, glow, motion blur, soft filter), AMD FSR 1 + CAS, FXAA,
  anisotropic.
- Turnip (Mesa) drivers downloaded by the app, with the support level per Snapdragon. Experimental.

### 120 FPS
- On 120 Hz screens: **120 (interpolated)**, the game at 60 with a generated frame between each two, and
  **120 (native)**, the whole game at 120. Both are experimental: not yet tested on a 120 Hz device.

### Controls
- Touch controls with an 8-way D-pad (or analog stick), the game's look and transparency; shown when you
  touch, hidden when you use a physical controller.
- Fight, menu and free camera layouts, layout editor and macros. Physical controllers with deadzone and
  vibration.

### Online
- Local network (Wi-Fi), the PC version's LAN method. Experimental: not yet tested between two devices.

### Known limitations
- USA version of the game only.
- Internet lobby (Ranked, leaderboards) not available yet; no cross-play with PC.
- Mali-G57 MC4 (e.g. RG Cube): 540p at 30 FPS is the goal; the fix (patch 0091) is not yet confirmed on
  the device. At 480p only part of the native renderer runs.
- Turnip on the Adreno 650 (Retroid Pocket 5) gives a wrong image: use the system driver.
- Above 720p (2K, 4K) the native renderer turns off. There is no 1080p.
- Texture packs and mods have not been tested with large packs on the devices yet.

## ES

Primera versión pública. Dragon Ball Z: Burst Limit (Xbox 360) ejecutándose de forma nativa en Android,
sin emulación, a partir de DBZ Burst Limit Recompiled v5.2. No incluye archivos del juego: importas tu
propia copia USA (NTSC-U).

### Qué hace la app
- Importa el juego del `.iso` o de una carpeta extraída, solo la versión USA, y avisa si el disco es de
  otro juego, de otra región, está modificado o cortado. **Verificar archivos** comprueba la instalación.
- Todas las opciones de la versión de PC que funcionan en Android: trajes del modo historia, escenarios
  extra, empezar transformado, cargar ki (L3), personajes extra de mods, iconos de botones, campo de
  visión, cámara libre y modo foto.
- Mods y packs de texturas HD en el formato de PC, extracción de texturas para crear packs.
- Partidas: exportar/importar, el mismo `.zip` que la versión de PC y Xenia.
- Menú del juego propio de Android (Back + Start), launcher en portugués, inglés y español.
- Actualizaciones desde la propia app, con un resumen de lo que cambió.
- Autotest del render y **Exportar logs** para reportar problemas.

### Rendimiento
- Un render nativo en 4 pasos (imagen final directa, profundidad lineal por compute, posprocesado ligero
  con contorno, reducción, desenfoque y brillo por compute, pases dibujados directamente en sus
  texturas), más las optimizaciones de GPU de Dragon Fury. A 540p se activan los mismos pasos (parche
  0091, aún sin confirmar en un dispositivo).
- Retroid Pocket 5 (Snapdragon 865): 720p con todos los efectos a hasta 60 FPS (unos 47-50 FPS en las
  escenas pesadas). RG Cube (Mali-G57): 22-24 FPS a 720p con efectos.

### Opciones gráficas
- 5 preajustes (Mínimo, Ligero, Equilibrado, Alto, Ultra) con los procesadores recomendados; la app
  sugiere el de tu dispositivo.
- Resolución interna de 480p, 540p, 720p, 2K o 4K.
- Efectos del juego activables (profundidad de campo, brillo, desenfoque de movimiento, filtro suave),
  AMD FSR 1 + CAS, FXAA, anisotrópico.
- Drivers Turnip (Mesa) descargados desde la app, con el nivel de soporte por Snapdragon. Experimental.

### 120 FPS
- En pantallas de 120 Hz: **120 (interpolado)**, el juego a 60 con un fotograma generado entre cada dos,
  y **120 (nativo)**, todo el juego a 120. Los dos son experimentales: aún sin probar en un dispositivo
  de 120 Hz.

### Controles
- Controles táctiles con cruceta de 8 direcciones (o stick analógico), el estilo del juego y
  transparencia; aparecen al tocar y se ocultan con un mando físico.
- Diseños de combate, menús y cámara libre, editor de diseño y macros. Mandos físicos con zona muerta y
  vibración.

### Online
- Red local (Wi-Fi), el método LAN de la versión de PC. Experimental: aún sin probar entre dos
  dispositivos.

### Limitaciones conocidas
- Solo la versión USA del juego.
- Lobby por internet (Ranked, clasificaciones) aún no disponible; sin juego cruzado con PC.
- Mali-G57 MC4 (p. ej. RG Cube): 540p a 30 FPS es el objetivo; la corrección (parche 0091) aún no está
  confirmada en el dispositivo. A 480p solo funciona parte del render nativo.
- Turnip en la Adreno 650 (Retroid Pocket 5) da una imagen incorrecta: usa el driver del sistema.
- Por encima de 720p (2K, 4K) el render nativo se desactiva. No hay 1080p.
- Los packs de texturas y los mods aún no se probaron con packs grandes en los dispositivos.
