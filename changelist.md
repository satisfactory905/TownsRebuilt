# Changelist

Summary of all changes in this fork (`TownsRebuilt`) relative to upstream
[supermalparit/Towns](https://github.com/supermalparit/Towns). Organized by theme rather
than strict chronology — reads as the story of the work, newest-within-theme last.

> **Note:** This file is intentionally tracked in git (unlike session logs and
> planning documents, which are listed in `.gitignore`). Add an entry here
> whenever a meaningful change lands.
>
> **Scope rule:** Entries describe what this fork has added or changed **relative
> to upstream**, not relative to an intermediate state of the fork itself. Fixes
> for regressions introduced during the fork's own migration work do **not**
> belong here — net-vs-upstream, restoring upstream behavior is a no-op. Only
> entries where the final state differs from upstream go in this file.

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

## Encoding

- **UTF-8 source normalized.** Original source had non-ASCII (accented) characters
  in Spanish developer comments that broke under Java 25 compilation. Accented
  characters were transliterated to plain ASCII equivalents but the comments were
  otherwise left as-is (Spanish, upstream author's style).

## Test infrastructure and first pure-function refactor

- **JUnit 5 test infrastructure.** Introduced `junit-jupiter` 5.11.3 (test scope,
  never shaded into the dist jar) and `maven-surefire-plugin` 3.5.2. Added
  `<testSourceDirectory>${project.basedir}/test</testSourceDirectory>` parallel
  to the existing non-standard `<sourceDirectory>${project.basedir}/src</sourceDirectory>`.
  `mvn test` is now a valid build goal; tests live at `test/xaos/...` mirroring
  the `src/xaos/...` package layout. No prior test framework existed.
- **`xaos.utils.HitDetection.isPointInRect`** — first pure-function extraction
  from the `code-optimizations.md` backlog. A `public final` utility class with a
  single static method: returns whether `(x, y)` falls within the half-open
  rectangle `[rectX, rectX+rectW) x [rectY, rectY+rectH)` (left/top inclusive,
  right/bottom exclusive). Covered by 12 parameterized `@CsvSource` boundary
  cases (strictly-inside, each edge inclusive/exclusive, one-pixel-inside edges,
  past each edge, zero-dimension rectangles, negative coordinates).
- **Refactored `Game.checkMouseEvents`.** Replaced five inline 4-term AND bounds
  checks (one left-click routing site + four scroll-edge guards) with calls to
  `HitDetection.isPointInRect`, caching `currentContextMenu` to a local `ctx` at
  each site to avoid four repeated static-getter calls per check per frame.
  Behavior preserved by substitution — the helper returns the same boolean as
  the inlined expression for every input.

This set establishes the **pattern for future per-method optimizations** from
the code-level backlog: extract the logic to a pure static helper, write
parameterized unit tests over boundary conditions, substitute at call sites.
Behavior preservation follows by construction. For fixes that can't be
characterized this way (pathfinding, races, simulation tick logic), a
save-state-driven integration harness is planned but deferred until the first
optimization genuinely requires it.

- **`Game.collectTextureFileNames` extraction.** Replaced the O(n^2) dedup in
  `loadAllIniTextures` (`ArrayList<String>` with `contains()` per texture) with
  an O(n) pass using a `LinkedHashSet<String>`. The dedup + filter logic is
  extracted to a package-private static helper `collectTextureFileNames
  (Properties) : Set<String>`, tested in `test/xaos/main/GameTest.java` with
  six cases (null input, empty properties, single texture, duplicate values,
  non-TEXTURE_FILE keys excluded, tile-prefixed keys like
  `[grass]TEXTURE_FILE` included). Load order is preserved via
  `LinkedHashSet` so downstream `UtilsGL.loadTexture` calls run in the same
  order as before. Also switches from the unchecked-cast
  `(Enumeration<String>) properties.propertyNames()` pattern to the
  type-safe `properties.stringPropertyNames()`.

- **`Game.java` minor cleanups.** OpenGL version-parse exceptions are now logged
  at `LEVEL_DEBUG` instead of silently swallowed (previously the two
  `catch (Exception e) {}` blocks dropped parse failures with no diagnostic).
  `setModsLoaded` and `setServers` use `String.split(",")` instead of
  `StringTokenizer` (both retain the `length() > 0` filter so empty-token
  handling is equivalent). Also deleted ~60 lines of commented-out dead code
  from `run()` (disabled victory-check, tutorial-trigger, mission-completed,
  and FPS-counter blocks) to reduce reading noise. No behavior change to any
  live code path.

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
