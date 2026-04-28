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

## Options menu

- **Graphics → VSync toggle and FPS Cap dropdown.** Two new rows added under
  the existing main-menu `Options → Graphics` submenu. VSync flips ON/OFF as a
  one-click toggle (calls `DisplayManager.setSwapInterval` immediately). FPS Cap
  is a cascading dropdown — `30 / 60 / 90 / 120 / 144 / 165 / 240 / Unlimited`,
  with "Unlimited" mapping to the existing `FPS_CAP = 0` sentinel. Off-list
  values from a hand-edited `towns.ini` are honored on display ("FPS Cap: 75")
  rather than silently snapped. Both settings persist via the existing
  `Utils.saveOptions()` flow into `<userFolder>/towns.ini`. Upstream had no
  in-menu way to change either — both were ini-only.
- **`src/towns.ini` cleanup.** Removed `FPS_CAP` and `VSYNC` (now menu-managed,
  with code-side defaults `0` and `true` in `Game.java`) and the dead
  `TRANSITION_TILES` entry (no source references in upstream or fork).

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

- **`World.modifyHappiness` eliminates per-citizen-per-hour allocation via
  reservoir sampling.** The hourly LOS happiness scan used to allocate a
  fresh `ArrayList<Integer>` per citizen and box each visible happy item's
  happiness value into an `Integer` before picking one entry at random.
  Replaced with **reservoir sampling of size 1**: as each visible happy
  item is encountered during the scan, a 1/count coin flip decides whether
  to replace the currently-selected value. After the scan completes, the
  selected value is uniformly random across all visible happy items —
  mathematically equivalent to the original random-index pick over the
  populated list. Allocation per eligible citizen per hour: was
  ArrayList + N boxed Integers, now zero. Semantics preserved exactly —
  the scan still picks one visible happy item at random and adds its
  happiness to the citizen's happiness.

- **`World.nextTurn` eliminates per-tick `Integer[]` allocation.** The items and
  livings iteration loops used `map.keySet().toArray(new Integer[0])` every
  tick to get a snapshot that was safe against `Item.delete()` /
  `LivingEntity.delete()` mutating the map during iteration. The snapshot is
  load-bearing but the per-tick array allocation was not. Extracted the
  pattern to a package-private static helper
  `World.forEachSnapshotReversed(Map<Integer,V>, Integer[], BiConsumer<Integer,V>)`
  that takes a reusable buffer. Two instance fields
  (`itemsIterationBuffer`, `livingsIterationBuffer`) hold the buffers across
  ticks; `Collection.toArray(T[])` grows them only when needed, so after the
  initial grow-to-peak there's zero allocation. Covered by 7 parameterized
  unit tests in `test/xaos/main/WorldTest.java` (population, key/value
  pairing, empty map, action removing current/other entries, entries added
  during iteration not visited, buffer growth). Snapshot semantics preserved
  exactly — no behavior change to live code paths.

- **`World.java` minor cleanups.** Replaced five `new Integer(...)` constructor
  calls in hot paths (`modifyHappiness`, `checkEvents`-adjacent allocation,
  `onLeavingHero`, and the kill-count map) with `Integer.valueOf(...)` so the
  JVM's -128..127 integer cache is used (and to get rid of the deprecation
  warning on the explicit constructor). Deleted ~80 lines of commented-out
  dead code: the `addGod()` / `getGods()` methods and their three call-site
  stubs (constructor, `nextTurn`), plus two disabled soldier-happiness stubs
  (the early-return guard inside `modifyHappiness(Citizen)` and the
  parameterless `modifyHappiness()` soldier iteration loop). The gods feature
  is compile-gated by `TownsProperties.GODS_ACTIVATED = false`, so the
  commented stubs were unreachable anyway; the flag remains and still gates
  the feature off. No behavior change to any live code path.

- **`World.modifyHappiness(Citizen)` swapped to a per-tile happiness-visibility
  cache.** The hourly per-citizen line-of-sight scan was the dominant cost of
  `modifyHappiness`: an O(MAX_LOS²) bresenham sweep of the `[x±LOS, y±LOS]`
  square, repeated for every eligible citizen on every hour tick. Replaced
  with a precomputed `xaos.main.HappinessCache` (transient `World` field) that
  stores, per tile, the list of `(happiness, Chebyshev-distance)` entries for
  every visible happy item within `MAX_LOS = 48` (= base 12 + 4 equipment
  slots × 9 max LOS bonus). `modifyHappiness(Citizen)` now does a list lookup
  + filter by the citizen's effective `LOSCurrent` + reservoir-sample of size
  1 — O(visible_items), no bresenham. The cache builds lazily per tile on
  first read and is invalidated incrementally:
  - `Cell.setEntity` fires `onItemPlaced` / `onItemRemoved` when the entity's
    `ItemManagerItem.getHappiness()` is non-zero, and `onWallChanged` when
    the entity's `Item.blocksLos()` predicate flips (mirrors
    `LivingEntity.isCellAllowed`'s item-side conditions: locked + operative +
    (wall OR locked-and-closed door)).
  - `Item.setLocked`, `Item.setOperative`, and `Item.setWallConnectorStatus`
    fire `onWallChanged` when their mutation flips `blocksLos()` — covering
    the door open/close paths from `TaskManager.TASK_LOCK / TASK_UNLOCK_OPEN
    / TASK_UNLOCK_CLOSE` that bypass `setEntity`.
  - `Cell.setMined` and `Cell.setDiscovered` fire `onMiningChanged` /
    `onDiscovered` on actual flips. Wall/mining/discovery hooks dirty-mark
    a `2·MAX_LOS` neighborhood (any tile whose visible-set could include or
    exclude lines passing through the changed tile).
  Cache is constructed at the end of `World.generateAll()` and at the end
  of `World.readExternal()`; before construction, all setters' null guards
  make hooks a no-op (so world-gen and save-load mutations don't waste work
  against an uninitialized cache). Behavior preservation:
  - Same bresenham 2-way OR check (`A→B || B→A`) as the original scan, so the
    visible set is identical to what the inline code produced.
  - Same self-tile branch (Chebyshev distance 0 entry).
  - Same `Utils.getRandomBetween(1, count) == 1` reservoir sampling. The
    cache iterates entries in the same order the original scan did (outer x,
    inner y, both ascending), so for any citizen with `LOSCurrent < MAX_LOS`
    the RNG-draw sequence is bit-identical and save-state determinism is
    preserved.
  - Same flat-add happiness application (no distance-attenuation formula
    existed in the original; the `distance` field in cache entries is
    consumed only by the LOSCurrent filter).
  Verified by 13 cache-equivalence tests (single item, walls, fluids,
  undiscovered, multi-item, varied-world fuzzer, save/load equivalence,
  incremental hook updates, dirty-mark hook updates) plus 7 reference
  ground-truth tests of the original scan as a verbatim helper. Allocation
  per `modifyHappiness(Citizen)` call is unchanged from the prior reservoir-
  sampling commit (still zero); the savings are CPU — the bresenham sweep
  is gone from the hot path. Cache memory is bounded by total visible-happy-
  item edges in the world.

## Performance instrumentation

- **`xaos.utils.perf` telemetry harness.** New foundational instrumentation
  module (under `src/xaos/utils/perf/`) used to baseline upcoming
  optimizations against measured numbers rather than code-reasoning. 11
  source classes (~1,700 LoC) plus 4 unit-test files (~830 LoC, 34 tests)
  cover histogram math, percentile estimation, lazy-resolution handle API,
  thread-safe recording, daemon-thread CSV dumper, and JMX
  `NotificationListener` wiring for GC events. Public API is a static
  facade `PerfStats.span / counter / sample` that returns pre-resolved
  handles; recording is lock-free (`AtomicLongArray` bucket histograms);
  all file I/O happens on a 1 Hz daemon thread; disabled-handle hot-path
  cost is one field load + one branch (under 10 ns). Memory budget is
  ~7 KB total for thirteen span histograms.
- **`towns.ini` performance flags.** New `PERF_LOG`, `PERF_LOG_PATH`,
  `PERF_LOG_PERIOD_MS` and six per-category toggles
  (`PERF_RENDERING_FRAME`, `PERF_RENDERING_GL`, `PERF_ENGINE_SIM`,
  `PERF_ENGINE_PATH`, `PERF_ENGINE_HAPPINESS`, `PERF_ENGINE_GC`). Default
  shipped state is everything on; categories that are off produce *no*
  CSV columns and short-circuit recording at the call site.
- **CSV output to `~/.towns/perf.csv`.** One row per second with
  `timestamp_iso`, `seconds_since_start`, six columns per span
  (`count, total_ns, min_ns, max_ns, p50_ns, p99_ns`) and one column per
  counter / gauge. File truncates on each launch; final partial-second
  row flushes via shutdown hook. ~102 columns when fully enabled.
  Histogram covers 10 µs to ~655 ms with ~19% bucket width and ~10%
  percentile accuracy.
- **Instrumentation sites in v1.** Spans at `Game.run` (`frame.total`),
  the four render passes (`frame.render.world / mouse / ui / minimap`),
  `DisplayManager.swapAndPoll` (`frame.swap`), `World.nextTurn`
  (`sim.tick`), `TaskManager.executeAll` (`sim.tasks`), `AStarQueue`
  search dispatch (`path.search`), `HappinessCache` constructor
  (`happiness.cache.build.full`) and `World.modifyHappiness(Citizen)`
  (`happiness.recalc.citizen`). Counters at every `glBindTexture` /
  `drawTexture*` / `glBegin(GL_QUADS)` / `glClear` site
  (`gl.bind_texture / draw_texture / begin_quads / clear`), entity
  iteration in `nextTurn` (`sim.entities_iterated`), A* search start
  (`path.search.count`, `path.queue.depth`), and every cache-invalidation
  hook in `HappinessCache` plus the lazy-rebuild path
  (`happiness.cache.invalidate.* / build.tile / tiles_dirtied`). GC
  category polls JMX `MemoryMXBean` and `GarbageCollectorMXBean`
  per-period and registers `NotificationListener` on each collector for
  per-event cycle durations (`gc.minor.* / gc.major.* / heap.*`).
- **Operational guide at `perf-testing.md`** (tracked in git, alongside
  `README.md` / `CHANGELOG.md` / `changelist.md`). Covers how to enable,
  the metric reference, A/B comparison workflow, caveats (notably:
  JMX `gc.*.duration` is cycle duration not stop-the-world pause; use
  `frame.total` p99 spikes as the actual stutter signal), and how to add
  / remove metrics. Future per-CSV analyzer / color-coded comparison
  tool deferred to `todo.md` until real baseline data exists to inform
  threshold design.

- **Stable CSV schema via known-metrics catalog.** The first baseline
  capture revealed a usability bug: handles register lazily on first
  use, so the dumper re-emits the CSV header line every time a new
  metric fires for the first time (~7 mid-file re-emits across a
  typical session — `gl.bind_texture` first, then world-load triggers
  `happiness.cache.*`, then in-game render triggers `frame.render.*`,
  etc.). This breaks naive spreadsheet imports because the column
  count changes mid-file. Fixed by adding a `KNOWN_METRICS` catalog
  inside `PerfStats.java` listing every span and counter from v1
  instrumentation; `PerfStats.init()` walks the catalog and pre-creates
  the registry entries for enabled categories, so the dumper's first
  CSV row carries the full schema with zero counts for not-yet-fired
  metrics. Late-registered metrics still work via lazy registration —
  the catalog is just an optimization for known cases. Documented in
  `perf-testing.md`'s "Adding a new metric" section as step 4.

- **`game.speed` and `game.paused` gauges.** Two new sampled gauges
  under `PERF_ENGINE_SIM` capture the current `World.SPEED` value
  (1..`SPEED_MAX`, default 3) and `Game.isPaused()` (0 or 1) at each
  CSV tick. Lets the analyst attribute a stutter row to the speed
  setting active at the time, distinguish "paused frame" from
  "running but very fast", and filter rows by speed when comparing
  runs. The harness still measures wall-clock time, not game-time, so
  per-call durations (`p50`/`p99` on `sim.tick` etc.) are unaffected
  by speed; only counts scale. `perf-testing.md` covers the analysis
  implications under "Game-speed and pause are recorded but not
  normalized".

## Engine pacing

- **Sim, animations, and UI blink decoupled from FPS.** The render rate
  no longer dictates simulation speed. Three frame-counted consumers
  moved to wall-clock-paced timestamps using a single
  `Game.frameNowNanos` captured once per `Game.run()` iteration:
  - `World.nextTurn` (the simulation tick) — `World.SPEED` 1..5 maps
    to nanosecond intervals (233 ms / 167 ms / 100 ms / 67 ms / 33 ms
    per turn) instead of the prior frame counts (7/5/3/2/1 frames per
    turn at 30 FPS). The post-work timestamp is captured *after*
    `nextTurn` body completes, so a slow tick never double-fires on
    the next render iteration. Pause behavior preserved exactly:
    when paused, tasks still run at tick cadence, but the world body
    doesn't advance. `World.updateNextFrameTurn()` deleted; the
    save-state fields `readyForNextTurnFrameCounter` and
    `readyForNextTurn` retained for binary save-format
    compatibility but no longer read by pacing logic.
  - `Tile.updateAnimation` (the single entry point for every
    animation in the game — citizens, heroes, buildings, items,
    special tiles via `World.updateSpecialTilesAnimation`) — replaces
    the per-tile `currentFrameDelay` short counter with a per-tile
    `lastAnimationFrameNanos` timestamp. Each tile bootstraps with a
    randomized phase on first call, preserving the desync-on-load
    behavior previously seeded by `refreshTransients`.
  - `UIPanel.blinkTurns` — derived once per render from a fixed
    1-second cycle (`BLINK_CYCLE_NANOS`). The 15+ existing call sites
    that test `blinkTurns >= MAX_BLINK_TURNS / 2` work unchanged;
    `MAX_BLINK_TURNS` is now hardcoded at 30 instead of pinned to
    `Game.FPS_INGAME`.
  Each consumer advances at most once per render iteration (no
  catch-up rule). A single slow render frame freezes the affected
  pacing state for that duration and resumes one tick at a time
  rather than triggering a flood of catch-up ticks. Walking
  animations never warp, sim never double-fires, blink stays at 1 Hz.

- **`towns.ini`: `FPS_INGAME` + `FPS_MAINMENU` replaced by `FPS_CAP` +
  `VSYNC`.** New default `FPS_CAP = 0` means no artificial sleep cap;
  VSync (default `VSYNC = true`) is the natural ceiling at the
  monitor's refresh rate. **Menu and in-game both follow `FPS_CAP`;
  there is no menu-specific cap.** The 3 loading-screen sync sites
  (autosave, game-load, menu's intermittent render) call
  `Game.getEffectiveFpsCap()`, which resolves `FPS_CAP = 0` to a 240
  fallback so `DisplayManager.sync` never divides by zero — these are
  tight inner loops where a sentinel "uncapped" call would otherwise
  spin. The main `Game.run` loop uses `if (FPS_CAP > 0) sync(FPS_CAP)`
  directly, so truly-uncapped in-game stays truly uncapped (no sleep,
  VSync gates if enabled). VSync is applied via the new
  `DisplayManager.setSwapInterval(boolean)` called from `Game.<init>`
  after `UtilsGL.initGL`.

  **Migration note for users with explicit `FPS_INGAME = 30` in their
  `towns.ini`:** after this change they'll launch with `FPS_CAP = 0`
  (unlimited) on next run. For most setups on a 60-144 Hz monitor with
  VSync this just means smoother visuals at correct gameplay speed.
  Re-add `FPS_CAP = 30` if you specifically want the prior cap (e.g.
  for laptop battery life or deterministic-looking gameplay capture).

- **`Game.REFERENCE_FPS = 30` constant.** Introduced for legacy
  frame-counted timers that aren't being converted in this pass —
  `Tile`'s `ANIMATION_FRAME_DELAY` default, `MiniMapPanel.textureRefreshRate`,
  `UIPanel`'s hover/repeat delay thresholds, and `TaskManager`'s
  automated-production interval. These continue to count frames but
  now reference an explicit "30 frames per reference second" constant
  rather than misusing the renamed render-cap value. The minimap one
  is tracked in `todo.md` for proper event-driven conversion later
  (it should fire on `Cell.setDiscovered` / level change / building
  add-remove, not poll every 30 frames). The hover-delay timers will
  trigger slightly earlier at high render rates as a known
  cosmetic-only side effect.

- **`Game.frameNowNanos` shared frame clock.** Captured once per
  `Game.run()` iteration via `System.nanoTime()` and read by all
  pacing consumers in that iteration via `Game.getFrameNow()`. Avoids
  redundant `nanoTime()` calls per frame and gives every consumer the
  same time reference within a frame. Package-public
  `Game.setFrameNowForTest(long)` lets cross-package unit tests drive
  pacing-dependent logic deterministically.

## Performance instrumentation

- **Sub-spans inside `World.nextTurn` for spike attribution.** The
  100-citizen baseline showed `sim.tick` p99 spiking to 200–640 ms in
  isolated frames (61% of all observed stutter events), but the
  top-level `sim.tick` span couldn't say *which* part of `nextTurn`
  was responsible. Adds five sub-spans under `PERF_ENGINE_SIM` that
  decompose the body:
  - `sim.tick.daily` — daily-tick block (date rollover, autosave-if-enabled). Only records when the daily conditional fires.
  - `sim.tick.hourly` — hourly-tick block (modifyHappiness + checkImmigrants + checkHeroesLeave/Come/Friendships + checkCaravansCome + checkSiege + checkEvents). Only records on hourly ticks.
  - `sim.tick.items` — items snapshot iteration.
  - `sim.tick.livings` — livingsDiscovered snapshot iteration.
  - `sim.tick.fluids` — moveFluids + evaporateFluids.

  Each sub-span's histogram captures per-event work cost rather than a
  diluted average across all ticks, so a single-frame spike in one
  block stands out cleanly in p99. Pre-registered in
  `PerfStats.KNOWN_METRICS` so the CSV schema stays stable from row 1.

- **Timestamped perf CSV filenames** (`perf-YYYYMMDD-HHMMSS.csv`).
  The original "truncate `perf.csv` on each launch" semantic hit a
  Windows file-lock issue: a stale lock on the existing `perf.csv`
  (Explorer preview pane, a tail process, an unclean previous JVM
  shutdown) would block the next launch's `TRUNCATE_EXISTING` open
  and silently disable telemetry — exactly what happened on the
  100-citizen baseline attempt that motivated this fix. Default
  path now incorporates a per-second launch timestamp, so each
  launch writes to a unique file that didn't exist a second ago.
  Side benefit: no manual rename step to preserve baselines across
  optimization A/B runs — every session is kept automatically.
  Explicit `PERF_LOG_PATH` in `towns.ini` bypasses the timestamp
  (user owns their naming when they set that). Lexical sort of
  filenames matches chronological order, so analysis scripts grab
  the latest with `ls -1 ~/.towns/perf-*.csv | tail -1`.

- **`sim.tick.livings.entity` per-iteration sub-span.** The
  parent `sim.tick.livings` span wraps the entire livings loop, so a
  small per-entity AI improvement gets diluted by 1/N before showing
  up in p50/p99 of the parent. Adds a child span around the
  individual `LivingEntity.nextTurn()` call inside the loop so the
  histogram captures *single-entity* AI cost. Wraps only the
  `nextTurn()` call itself, not the post-mortem (delete + tutorial
  trigger), so rare death events don't pollute the per-iteration
  distribution. Same pattern as the existing `sim.tick.*` sub-spans;
  registered in `PerfStats.KNOWN_METRICS` so CSV schema stays
  stable. Adds one span (~6 columns) to the unified perf CSV.

## Performance optimizations

- **Minimap event-driven rebuild + lazy first-paint.** Two changes to
  `MiniMapPanel` that together kill the largest single `frame.render.ui`
  spike observed in baselines (153 ms one-shot at world load, ~99 % of a
  155 ms p99 outlier). Net-vs-upstream:
  - `loadTextures()` no longer eagerly rebuilds every floor's texture on
    first render. Upstream walked `[0, MAP_DEPTH)` and called
    `reloadTexture(i)` for each — at ~14 floors of 200×200 cells with
    recursive `getCellColor` walks for mined cells, this dominated the
    first `MiniMapPanel.render()` call (which runs inside the
    `UIPanel.render` span, so the cost surfaced as a UI render spike).
    `loadTextures()` now just allocates the dirty-flag array and marks
    every level as needing rebuild; per-level rebuilds happen on demand
    when each level is actually viewed. `initialize()` skips null entries
    when freeing textures since unviewed levels stay null until rendered.
  - `textureRefreshRate` (a per-frame countdown, decremented every render
    and reset to `Game.REFERENCE_FPS = 30` after each rebuild) is replaced
    with a wall-clock `REBUILD_COALESCE_NANOS` window (1 s). First dirty
    after a clean period rebuilds immediately (better latency for typical
    bursty events like a single dig); sustained change streams still cap
    at 1 Hz. Rebuild trigger is now purely the `setMinimapReload(level)`
    dirty flag — already called from `Cell`, `Stockpile`, `Item`,
    `Terrain`, `EventData`, and `World` on real change events. Cadence is
    FPS-independent, consistent with the engine-pacing decoupling work.
    The `textureRefreshRate = 0` hack in `mousePressed` (a poll-bypass
    trick to force rebuild after a minimap-click camera jump) is removed
    too — clicking to move the camera doesn't change texture content, so
    the forced rebuild was unnecessary.

