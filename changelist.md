# Changelist

Summary of all changes in this fork (`TownsRebuilt`) relative to upstream
[supermalparit/Towns](https://github.com/supermalparit/Towns). Organized by theme rather
than strict chronology — reads as the story of the work, newest-within-theme last.

> **Note:** This file is intentionally tracked in git (unlike session logs and
> planning documents, which are listed in `.gitignore`). Add an entry here
> whenever a meaningful change lands.

---

## Build & packaging

- **Maven build system.** Replaces the pre-built `xaos.jar` + batch-script launcher
  that shipped in the 2014 commercial build. `pom.xml` defines:
  - An `exec` invocation — `mvn exec:exec` runs the game from source with the correct
    working directory and JVM flags.
  - A `dist` profile — `mvn package -Pdist` produces a self-contained Windows app image
    via `jpackage`. Output lands at `target/dist/Towns/Towns.exe` and runs without any
    Java installation on the target machine.
  - Source layout kept non-standard (`src/…` instead of `src/main/java/…`) to match
    the upstream project and minimize churn.
- **JDK 25 LTS.** Compiler source/target bumped from 8 to 25 (Microsoft OpenJDK 25).
  Runtime JVM flags added: `-XX:+UseZGC -XX:+ZGenerational` (sub-millisecond GC pauses)
  and `-XX:+UseCompactObjectHeaders` (smaller object headers, reduced cache pressure).
- **Trimmer distribution via custom `jlink`.** The `dist` profile now specifies
  `<addModules>` (only `java.base, java.desktop, jdk.unsupported` — the three modules
  `jdeps` confirms the game actually uses) and `<jLinkOptions>` (`--strip-debug
  --no-header-files --no-man-pages --compress=zip-6`). Without this, jpackage was
  falling back to copying the entire JDK (including `ct.sym`, man pages, dev-tool
  leftovers). Dist size dropped from ~152 MB to ~78 MB with no functional change.

## Dependency migration (LWJGL 2 → LWJGL 3)

- **Removed 6 `system`-scope JARs and 10 hand-managed native DLLs.** Gone:
  `lwjgl.jar`, `lwjgl_util.jar`, `slick-util.jar`, `pngdecoder.jar`, `platform.jar`,
  `jna.jar`, plus `lwjgl.dll`, `OpenAL32.dll`/`OpenAL64.dll`, `jinput-dx8*.dll`,
  `jinput-raw*.dll`, `steam_api*.dll`. No more `-Djava.library.path` flag; no more
  `lib/` directory of binaries to ship alongside the jar.
- **Added LWJGL 3.3.3** as Maven Central dependencies (core, GLFW, OpenGL, OpenAL, STB;
  each with `natives-windows` classifier). LWJGL 3 auto-extracts natives from the JARs
  at runtime.
- **Steam integration removed.** `JNASteamAPI.java` deleted; all Steam references
  stripped from `Towns.java`. `steam_appid.txt` no longer needed.

## Input, windowing, and audio rewrite

- **GLFW window management** — new `xaos/utils/DisplayManager.java` replaces the
  LWJGL 2 `Display` + AWT `Frame` combo. Handles window lifecycle, VSync, fullscreen
  toggle, resize events, and HiDPI-aware framebuffer tracking. The LWJGL 2 `MainFrame`
  is still present in the source but dead-code (scheduled for removal).
- **GLFW input** — new `xaos/utils/InputState.java` replaces `Keyboard` and `Mouse`.
  Mirrors the LWJGL 2 `nextMouseEvent()` / `nextKeyEvent()` pattern. Separate
  `charQueue` fed by GLFW's char callback, for proper Unicode/IME-friendly text input.
- **Key-name compatibility** — new `xaos/utils/KeyMapper.java` provides bidirectional
  LWJGL 2 key-name ↔ GLFW key-code mapping so `towns.ini` key bindings written in the
  LWJGL 2 format (e.g. `LSHIFT`, `RETURN`) still load correctly.
- **Audio rewrite** — `UtilsAL.java` ported from Slick-Util (`Audio`, `AudioLoader`,
  `SoundStore`) to LWJGL 3 OpenAL + STB Vorbis. `stb_vorbis_decode_memory()` loads OGG
  files into OpenAL buffers. Public API preserved exactly so no gameplay code needed
  to change.
- **Image decoding** — PNGDecoder replaced with `javax.imageio.ImageIO`.
- **Cursor** — `UtilsGL.setNativeCursor()` rewritten for GLFW `GLFWImage` +
  `glfwCreateCursor`.

## Regression fixes from the LWJGL 2 → 3 port

- **Fullscreen resolution.** `DisplayManager.init()` was passing the stored window
  dimensions straight to `glfwCreateWindow(..., monitor, NULL)`. Combined with a
  previously saved `FULLSCREEN=true / WINDOW_WIDTH=1024 / WINDOW_HEIGHT=600` in the
  user ini, this produced an exclusive 1024×600 fullscreen that Windows 10's
  fullscreen-optimizations compositor mangled into a 1176×664 framebuffer — visibly
  wrong resolution, misaligned mouse. Fixed by querying
  `glfwGetVideoMode(glfwGetPrimaryMonitor())` when `fs==true` and using *its*
  width/height/refreshRate. Restores the pre-port LWJGL 2 behavior (which called
  `Display.getDesktopDisplayMode()` — verified from the obfuscated 2014 bytecode).
- **Mouse scaling.** `InputState` was multiplying cursor coordinates by
  `framebufferWidth / windowWidth`. But `glOrtho`, every UI hit-test, and all gameplay
  cursor math use logical (window) coords — the scaling drifted the cursor on HiDPI
  displays (~15% on a 125%-DPI Windows setup). Removed the multiplication in both the
  cursor callback and `getMouseX`/`getMouseY`.
- **Width-clamp typo.** `Game.java:239` had `Math.min(desktopWidth, configuredHeight)`
  — clamping *height* against desktop *width*. Changed to `desktopHeight`.
- **Double `DisplayManager.init()`.** `handleResize()` was calling `init()` a second
  time to re-create the window when it fell below the minimum size. Replaced with
  `glfwSetWindowSizeLimits()` at initial window creation — GLFW enforces the minimum
  directly, so no re-init is needed. `handleResize()` now only updates viewport and
  panel geometry.
- **Main-menu typing dialogs didn't accept printable text.** The "Set a savegame name"
  and "Add server" dialogs are `TypingPanel` instances created by `MainMenuPanel`
  without assigning to `UIPanel.typingPanel` (which is the in-game typing-panel
  reference). The char-event drain in `Game.checkKeyboardEvents` was gated on
  `UIPanel.typingPanel != null`, so GLFW char events (printable text) never reached
  `TypingPanel.charInput()` while the main menu was active. `Enter`/`Backspace`/`Esc`
  worked because they go through the key event path via `MainMenuPanel.keyPressed`.
  Fixed by adding `MainMenuPanel.isTypingTextActive()` and widening the drain gate.
  Deliberately excludes the key-rebind dialog, which uses key codes and would be
  corrupted by char events being appended to its text.

## Encoding

- **UTF-8 source normalized.** Original source had non-ASCII (accented) characters
  in Spanish developer comments that broke under Java 25 compilation. Accented
  characters were transliterated to plain ASCII equivalents but the comments were
  otherwise left as-is (Spanish, upstream author's style).

## Static analysis (documented, not yet fixed)

Twelve of the largest source files have been analyzed for bugs, allocation hotspots,
and algorithmic issues. Several confirmed bugs and performance issues are tracked but
not yet addressed:

- `LivingEntity.doHit()` — ranged damage falloff computes X distance twice; Y is never
  used, so vertical separation has no effect on damage.
- `Utils.sqrt()` — lookup table covers only inputs 1–225; silently returns `15` for
  any larger input.
- `Utils.getSaveFiles()` — sort comparator inverted; returns oldest-first.
- `UIPanel.mousePressed()` — copy-paste bug calls `resizePilePanel()` instead of
  `resizeProfessionsPanel()`.
- `UIPanel` bottom-panel loop — off-by-one boundary check (`>` vs `>=`) risks
  `IndexOutOfBoundsException`.
- `ItemManagerItem.setLightRadius()` — mutates a static field as a side effect of an
  instance setter called during XML load.
- Performance: `TaskManager.assignHaulTasks()` O(n²), `Citizen.isCitizenWalkingToFood()`
  O(n²), `Task.checkCancelTask()` O(n³), `LivingEntity.path` / `World` entity-map race
  (main thread vs. A* background thread), `TaskManager.executeAll()` double-fires per
  tick under certain timing conditions.

Fixing these is planned but out of scope of the migration work so far.

## Repository hygiene

- **Added `.gitignore`** (previously absent). Ignores:
  - `target/` (Maven output)
  - `*.log` (runtime logs; the game currently writes `error.log` to the process CWD,
    which lands inside the repo during dev runs — the fix to route it under the user
    folder is tracked separately)
  - `src/data/graphics/` + `audio/` + `fonts/` + `fontMetamorphous.fnt` (runtime
    binary assets from the commercial build, not part of the open-source repo)
  - An explicit list of development-notes `*.md` files (session logs, analysis, plans,
    todo). Only `README.md`, `CHANGELOG.md`, and `changelist.md` are tracked markdown.
- **Rewrote `README.md`** for the fork: describes the build process, what's different
  from upstream, and the project layout. Upstream author credited.
