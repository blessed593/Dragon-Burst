<p align="center"><img src="docs/banner.png" alt="Dragon Burst" width="820"></p>

# Dragon Burst

[Português](README.md) · [English](README.en.md) · **Español**

Port nativo para Android de **[DBZ Burst Limit Recompiled](https://github.com/iExplosiveRage/DBZ-Burst-Limit-Recompiled)**
(iExplosiveRage), la recompilación estática de Dragon Ball Z: Burst Limit de Xbox 360. El código del juego se
ejecuta de forma nativa en arm64 (no se emula), con el runtime ReXGlue en Vulkan y la base de Dragon Fury
(port de Budokai 3 HD).

> **No incluye archivos del juego.** Necesitas tu propia copia legal, versión **USA (NTSC-U)**.
>
> **Estado: primera base, aún sin probar en un dispositivo.** Ver [docs/ESTADO.md](docs/ESTADO.md) (en portugués).

## Qué incluye

- Importar el juego directamente del `.iso` (o de una carpeta extraída), comprobando la versión USA.
- Todo lo del proyecto de PC que funciona en Android: 30/60 FPS, trajes y escenarios extra, empezar
  transformado, cargar ki (L3), personajes extra de mods, iconos de botones Xbox/PS/Switch, cámara libre,
  campo de visión, menú del juego (Back + Start), exportar/importar partida.
- Mods en el formato de PC, packs de texturas HD (reemplazo de texturas portado a Vulkan) y extracción de
  texturas.
- Efectos del juego activables (profundidad de campo, brillo, desenfoque de movimiento, filtro suave).
- Controles táctiles con editor y macros, drivers Turnip, optimizaciones de GPU para Adreno y Mali.
- Online en red local (Wi-Fi). El lobby por internet aún se está portando.

Lista completa: [docs/COMPATIBILIDADE.md](docs/COMPATIBILIDADE.md).

Dispositivos de prueba: Retroid Pocket 5 (Snapdragon 865) y Anbernic RG Cube (Mali-G57).

## Compilar

```bash
bash tools/setup-external.sh
bash tools/apply-patches.sh
bash tools/codegen.sh /path/to/default.xex
./gradlew assembleRelease
```

## Créditos

iExplosiveRage (DBZ Burst Limit Recompiled), NovaPowers (DBZ Budokai 3 HD), el equipo de ReXGlue SDK, SDL,
libadrenotools (bylaws), quienes compilan los drivers Turnip/Mesa.

Proyecto de fans, no oficial y sin fines comerciales. Dragon Ball pertenece a Bird Studio/Shueisha,
Toei Animation y Bandai Namco.
