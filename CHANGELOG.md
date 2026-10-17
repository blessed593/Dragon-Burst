# Changelog

## 0.1.0 (em preparação)

Primeira base do port, sobre o Burst Limit Recompiled v5.2 (`bb60bc1`).

- 49 patches do Dragon Fury refeitos sobre o SDK do Burst Limit (0000–0048).
- Substituição de texturas e `draw_overrides` no Vulkan (0049, 0050), sockets do online em POSIX (0051),
  fontes do Android no menu do jogo (0052).
- Jogo: caminhos Android, HTTP por JNI, sons de menu pelo SDL3, saves, itens só de PC escondidos do menu.
- Launcher: importar `.iso`/pasta (só USA), opções do BL, mods, packs e extração de texturas, saves,
  online em rede local, controles de toque com os comandos do BL e layout da câmera livre.
- 120 FPS (patch 0092): a tela segue a taxa escolhida (antes ficava sempre a 60 Hz); 120 (nativo,
  experimental) e 120 (interpolado: um quadro gerado entre cada dois no presenter Vulkan) em telas de
  120 Hz; linha perf com `game_fps` e `presented_fps`.
