# TownsRebuilt

A modernization fork of [supermalparit/Towns](https://github.com/supermalparit/Towns) — the Java colony simulation originally released commercially on Steam in 2014. The goal of this fork is to rebuild the build system, runtime libraries, and JVM foundations for modern hardware, while keeping the original gameplay untouched.

## What's different from upstream

- **Maven build system.** Replaces the pre-built jar + batch-script launcher. `mvn exec:exec` runs the game from source; `mvn package -Pdist` produces a self-contained Windows app image via `jpackage` — no external JRE required.
- **LWJGL 2 → LWJGL 3 + GLFW.** The 2014-era system-scope jars (`lwjgl.jar`, `lwjgl_util.jar`, `slick-util.jar`, `pngdecoder.jar`, `platform.jar`, `jna.jar`) and their hand-managed native DLLs are gone. LWJGL 3 pulls natives from Maven Central. Audio switched from Slick-Util to STB Vorbis; image decoding from PNGDecoder to `ImageIO`; input from `Keyboard`/`Mouse` to GLFW callbacks.
- **Java 25 LTS.** Source/target compiler level bumped from 8 to 25. Runs with Generational ZGC + Compact Object Headers by default for sub-millisecond GC pauses and reduced heap pressure.
- **Steam integration removed.** `JNASteamAPI.java` deleted; the game no longer depends on `steam_api.dll`.
- **Regression fixes from the LWJGL 2 → 3 port:**
  - Fullscreen now queries the primary monitor's video mode instead of honoring stored window dimensions — restores pre-port behavior (the original used `Display.getDesktopDisplayMode()`) that was silently dropped in the GLFW migration.
  - Main-menu typing dialogs ("Set a savegame name", "Add server") correctly receive printable keystrokes again — GLFW's char callback was only wired to the in-game typing panel.
  - `desktopWidth`/`desktopHeight` typo in the window-size clamp corrected.
  - `handleResize()` no longer re-initializes the window; GLFW's built-in size limits handle it.
- **Trimmer distribution.** The jpackage dist profile uses a custom `jlink` runtime with only the JDK modules the game actually uses (`java.base`, `java.desktop`, `jdk.unsupported`), plus `--strip-debug` and `--compress=zip-6`. Cuts dist size from ~152 MB to ~78 MB with no functional change.

## Build & run

Requires JDK 25 (Microsoft OpenJDK 25 recommended) and Maven on the `PATH`.

```sh
# Compile only — validates sources and produces target/classes/.
# Use this for CI or a quick syntax/type check.
mvn compile

# Clean rebuild — wipes target/ first.
mvn clean compile

# Run the game from source.
# The exec-maven-plugin launches a separate JVM with the correct working
# directory (src/, where towns.ini and data/ live) and the required JVM flags
# (Generational ZGC, Compact Object Headers).
mvn exec:exec

# Build a self-contained Windows app image — no Java install required on the
# target machine. Output: target/dist/Towns/Towns.exe (+ runtime/, app/ folders).
# Uses jpackage under the hood with a custom jlink runtime (java.base,
# java.desktop, jdk.unsupported) for a trimmer distribution.
mvn package -Pdist
```

## Project layout

```
Towns/
├── pom.xml                  Maven build (Java 25, exec + dist profiles)
├── src/
│   ├── towns.ini            Main config
│   ├── graphics.ini         Graphics config
│   ├── audio.ini            Audio config
│   ├── data/                XML gameplay definitions
│   └── xaos/                All Java source (~175 files)
└── LICENSE
```

Runtime binary assets (graphics PNGs, audio OGGs, fonts) are **not** included in this repository — they are the original commercial game's assets. You need a legitimate copy of the game to supply them.

## Upstream

This fork is based on [supermalparit/Towns](https://github.com/supermalparit/Towns) and does not reimplement the game. All gameplay code, XML data, and design belong to the original author.

## License

GNU GPL v3. See [LICENSE](LICENSE). Redistribution of this fork or its derivatives must remain GPLv3, and the license texts under the distributed JDK runtime (`target/dist/Towns/runtime/legal/`) must be preserved when shipping a built app image.
