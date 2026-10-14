<p align="center"><img src="docs/banner.png" alt="Dragon Burst" width="820"></p>

# Dragon Burst

**Português** · [English](README.en.md) · [Español](README.es.md)

Port nativo para Android do **[DBZ Burst Limit Recompiled](https://github.com/iExplosiveRage/DBZ-Burst-Limit-Recompiled)**
(iExplosiveRage): a recompilação estática do Dragon Ball Z: Burst Limit de Xbox 360. O código do jogo roda
nativo em arm64 (não é emulado), com o runtime ReXGlue em Vulkan e a infraestrutura do Dragon Fury
(port do DBZ Budokai 3 HD).

> **Sem arquivos do jogo.** Você precisa da sua própria cópia legal, versão **USA (NTSC-U)**.
>
> **Estado: primeira base, ainda não testada em aparelho.** Ver [docs/ESTADO.md](docs/ESTADO.md).

## O que tem

- Importar o jogo direto do `.iso` (ou de uma pasta extraída), com verificação da versão USA.
- Todas as novidades do projeto do PC que funcionam no Android: 30/60 FPS, roupas e cenários extras,
  começar transformado, carregar ki (L3), personagens extras de mods, ícones de botão Xbox/PS/Switch,
  câmera livre, campo de visão, menu do jogo (Back + Start), exportar/importar save.
- Mods no formato do PC, packs de texturas HD (substituição de texturas portada para o Vulkan) e extração
  de texturas.
- Efeitos do jogo ligáveis (profundidade de campo, glow, motion blur, filtro suave).
- Controles de toque com editor e macros, drivers Turnip, otimizações da GPU para Adreno e Mali.
- Online em rede local (Wi-Fi). O lobby pela internet ainda está sendo portado.

Lista completa, recurso por recurso: [docs/COMPATIBILIDADE.md](docs/COMPATIBILIDADE.md).

## Aparelhos de teste

Retroid Pocket 5 (Snapdragon 865 / Adreno 650) e Anbernic RG Cube (Unisoc T770 / Mali-G57).

## Compilar

Passo a passo em [docs/ESTADO.md](docs/ESTADO.md#como-compilar-pc-linux). Resumo:

```bash
bash tools/setup-external.sh
bash tools/apply-patches.sh
bash tools/codegen.sh /path/to/BurstLimit.iso   # or default.xex
./gradlew assembleRelease
```

## Créditos

iExplosiveRage (DBZ Burst Limit Recompiled), NovaPowers (DBZ Budokai 3 HD), equipe do ReXGlue SDK, SDL,
libadrenotools (bylaws), quem compila os drivers Turnip/Mesa.

Projeto de fã, não oficial e sem fins comerciais. Dragon Ball pertence a Bird Studio/Shueisha,
Toei Animation e Bandai Namco.
