<p align="center"><img src="docs/banner.png" alt="Dragon Burst" width="820"></p>

# Dragon Burst

[Português](README.md) · **English** · [Español](README.es.md)

Native Android port of **[DBZ Burst Limit Recompiled](https://github.com/iExplosiveRage/DBZ-Burst-Limit-Recompiled)**
(iExplosiveRage), the static recompilation of Dragon Ball Z: Burst Limit for Xbox 360. The game code runs
natively on arm64 (not emulated), on the ReXGlue runtime with Vulkan and the Dragon Fury (Budokai 3 HD
port) infrastructure.

> **No game files included.** You need your own legal copy, **USA (NTSC-U)** version.
>
> **Status: first foundation, not tested on a device yet.** See [docs/ESTADO.md](docs/ESTADO.md) (Portuguese).

## Features

- Import the game straight from the `.iso` (or an extracted folder), with a USA version check.
- Every PC feature that works on Android: 30/60 FPS, extra costumes and stages, start transformed, ki charge
  (L3), extra characters from mods, Xbox/PS/Switch button icons, free camera, field of view, in-game menu
  (Back + Start), save export/import.
- PC-format mods, HD texture packs (texture replacement ported to Vulkan) and texture dumping.
- Game effects toggles (depth of field, glow, motion blur, soft filter).
- Touch controls with an editor and macros, Turnip drivers, GPU optimizations for Adreno and Mali.
- Local network (Wi-Fi) online. The internet lobby is still being ported.

Full feature-by-feature list: [docs/COMPATIBILIDADE.md](docs/COMPATIBILIDADE.md).

Test devices: Retroid Pocket 5 (Snapdragon 865) and Anbernic RG Cube (Mali-G57).

## Build

```bash
bash tools/setup-external.sh
bash tools/apply-patches.sh
bash tools/codegen.sh /path/to/BurstLimit.iso   # or default.xex
./gradlew assembleRelease
```

Needs the Android NDK 28.2.13676358, JDK 17, clang 20+, cmake, ninja.

## Credits

iExplosiveRage (DBZ Burst Limit Recompiled), NovaPowers (DBZ Budokai 3 HD), the ReXGlue SDK team, SDL,
libadrenotools (bylaws), the Turnip/Mesa driver builders.

Unofficial, non-commercial fan project. Dragon Ball belongs to Bird Studio/Shueisha, Toei Animation and
Bandai Namco.
