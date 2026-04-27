# Performance Testing — Agent Guide

This document is the operational manual for the `xaos.utils.perf` telemetry
harness. Read it before instrumenting new code, capturing a baseline, or
attempting an A/B comparison between two versions of the game.

The full design rationale lives at
`docs/superpowers/specs/2026-04-26-perf-stats-design.md` (gitignored — local
only). This guide is the lighter, action-oriented companion that's tracked in
git so future agents always have it.

---

## What it is

A thread-safe, near-zero-overhead instrumentation library at
`src/xaos/utils/perf/` that records timing histograms (count / sum / min /
max / p50 / p99) and counters at named code sites and dumps a CSV row per
second to `~/.towns/perf.csv`. Every metric is gated by a `towns.ini` flag
so production runs incur zero observable overhead when disabled.

Built primarily to baseline the renderer rewrite (`optimize.md` item 6, was
item 8 before earlier removals) but designed as foundational infrastructure
for *every* future optimization in the backlog. Items 1, 3, 4, 5, 7 in
`optimize.md` all expect to A/B against this same harness without any
re-plumbing.

---

## How to enable

`src/towns.ini` carries the toggles, defaults all `true`:

```
#Performance debug
PERF_LOG = true                 # master switch; false → harness inert
PERF_LOG_PATH =                 # blank → ~/.towns/perf.csv; absolute path overrides
PERF_LOG_PERIOD_MS = 1000       # how often the dumper writes a row
PERF_RENDERING_FRAME = true     # frame.total / frame.render.* / frame.swap
PERF_RENDERING_GL = true        # gl.bind_texture / gl.draw_texture / gl.begin_quads / gl.clear
PERF_ENGINE_SIM = true          # sim.tick / sim.tasks / sim.entities_iterated
PERF_ENGINE_PATH = true         # path.search / path.search.count / path.queue.depth
PERF_ENGINE_HAPPINESS = true    # happiness.cache.* / happiness.recalc.citizen
PERF_ENGINE_GC = true           # gc.minor.* / gc.major.* / heap.*
```

To narrow a session to one investigation, flip the unwanted categories to
`false`. Disabled categories produce **no columns** in the CSV header and
no recording overhead at the call sites — the harness short-circuits inside
`SpanHandle` / `CounterHandle` / `SampleHandle` to a singleton no-op.

The master `PERF_LOG = false` short-circuits everything regardless of
category settings: no daemon thread, no file open, no GC listeners
attached.

Missing or malformed boolean values default to `true` (favor visibility on
a debug feature; the user folder is unwritable case is the only failure
that silently disables the dumper, and that gets logged).

---

## How to run a perf test

### Capturing a single session

1. Confirm `PERF_LOG = true` in `src/towns.ini` (it ships true; only flip
   if you're measuring an inert build).
2. Launch the game: `mvn -f Towns/pom.xml exec:exec`.
3. Play normally for at least 5 minutes — the longer the better for stable
   p99 estimates. Cover at least one of: building placement, citizen
   pathing, combat, save/load. The CSV grows by ~2 KB per minute at the
   default schema.
4. Quit cleanly (main menu → exit, or close the window). The shutdown hook
   flushes the partial-second tail row.
5. The CSV is at `~/.towns/perf-YYYYMMDD-HHMMSS.csv` (Windows:
   `C:/Users/<name>/.towns/perf-YYYYMMDD-HHMMSS.csv`). **Each launch
   creates a new file** named with the launch timestamp, so prior runs
   are preserved automatically — no rename step needed. Lexical sort
   matches chronological sort, so the latest file is the lexically
   last one. To grab the most recent run programmatically:

   ```bash
   # bash / git-bash / WSL
   ls -1 ~/.towns/perf-*.csv | tail -1

   # PowerShell
   Get-ChildItem $env:USERPROFILE\.towns\perf-*.csv |
       Sort-Object Name | Select-Object -Last 1
   ```

   To override the default location, set `PERF_LOG_PATH` in
   `towns.ini` to an absolute path. With an explicit path no
   timestamp is appended — you own the naming.

### A/B comparison between two builds

Standard workflow when measuring an optimization:

```bash
# 1. Capture baseline on the unchanged branch.
git -C Towns checkout main
mvn -f Towns/pom.xml exec:exec
# play, exit. Note the new perf-YYYYMMDD-HHMMSS.csv that just appeared
# in ~/.towns/. Optionally rename for clarity:
mv ~/.towns/perf-2026*.csv ~/.towns/perf.baseline.csv

# 2. Switch to the optimization branch and re-run with matching playstyle.
git -C Towns checkout the-optimization-branch
mvn -f Towns/pom.xml exec:exec
# play similarly, exit
mv ~/.towns/perf-2026*.csv ~/.towns/perf.optimized.csv

# 3. Compare. Until the analyzer in todo.md ships, a per-A/B 50-line
#    Python or jq script is sufficient. Useful summaries:
#    - p50 / p99 ratio per metric, baseline vs optimized
#    - frame.total p99 delta (the main stutter signal)
#    - render-pass total time delta (renderer A/B specifically)
#    - GC count + freed-mb deltas (allocation-pressure A/B)
```

Repeat the same playstyle in both runs as closely as possible — same map,
same starting save if available, same number of citizens, same camera
movement. Otherwise the comparison is contaminated.

### Targeting one subsystem

If you're investigating only pathfinding (e.g. `optimize.md` item 1 or 2),
flip everything except the path category off — the CSV becomes ~10 columns
wide and is much easier to scan:

```
PERF_RENDERING_FRAME = false
PERF_RENDERING_GL = false
PERF_ENGINE_SIM = false
PERF_ENGINE_PATH = true
PERF_ENGINE_HAPPINESS = false
PERF_ENGINE_GC = true            # keep GC in case allocation is the cause
```

---

## Output schema

`~/.towns/perf.csv`. Header line + one data row per `PERF_LOG_PERIOD_MS`
period + a final partial-period row on shutdown.

Columns are sorted alphabetically by metric name (after the two fixed
leading columns):

- `timestamp_iso` — ISO-8601 wall-clock timestamp at the start of the period.
- `seconds_since_start` — fractional seconds since the game launched.
- For each enabled **span** metric, six columns:
  `<name>_count, <name>_total_ns, <name>_min_ns, <name>_max_ns, <name>_p50_ns, <name>_p99_ns`.
- For each enabled **counter** metric, one column: `<name>` (sum over the period).
- For each enabled **gauge** metric (heap state, etc.), one column: `<name>`
  (last sampled value at tick time).

Empty histograms emit `0,0,0,0,0,0` (not blanks) so spreadsheets can sum or
average without surprises.

---

## Metric reference (v1)

13 spans + 18 counters + 5 gauges = 36 metrics, ~107 CSV columns when all
categories are on. Schema is **stable from row 1** — every metric in an
enabled category appears in the header on the first dump tick, with zero
counts until the metric first fires. This is enforced by a `KNOWN_METRICS`
catalog inside `PerfStats.java`; adding a new instrumented metric requires
appending one row there or you'll get a mid-CSV header re-emit (the
metric still works, just messier to parse).

| Category | Metric | Type | Where it lives | Notes |
|---|---|---|---|---|
| `RENDERING_FRAME` | `frame.total` | span | `Game.run()` | full per-iteration block |
| `RENDERING_FRAME` | `frame.render.world` | span | `MainPanel.render()` | terrains + entities pass |
| `RENDERING_FRAME` | `frame.render.mouse` | span | `MainPanel.render()` | mouse + task preview |
| `RENDERING_FRAME` | `frame.render.ui` | span | `UIPanel.render()` | full HUD pass |
| `RENDERING_FRAME` | `frame.render.minimap` | span | `MiniMapPanel.render()` | |
| `RENDERING_FRAME` | `frame.swap` | span | `DisplayManager.swapAndPoll()` | vsync wait hides here |
| `RENDERING_GL` | `gl.bind_texture` | counter | `UtilsGL.setTexture*()` | five sites |
| `RENDERING_GL` | `gl.draw_texture` | counter | `UtilsGL.drawTexture*()` | three primitive overloads |
| `RENDERING_GL` | `gl.begin_quads` | counter | `UtilsGL.glBegin()` | gated on `mode == GL_QUADS` |
| `RENDERING_GL` | `gl.clear` | counter | `Game.run()` near `glClear` | |
| `ENGINE_SIM` | `sim.tick` | span | `World.nextTurn()` | full body |
| `ENGINE_SIM` | `sim.tasks` | span | `TaskManager.executeAll()` | |
| `ENGINE_SIM` | `sim.entities_iterated` | counter | `World.nextTurn()` | items + buildings + livings + projectiles per tick |
| `ENGINE_SIM` | `game.speed` | gauge | `Game.<init>` | latest `World.SPEED` value (1..`SPEED_MAX`, default 3) at dump tick |
| `ENGINE_SIM` | `game.paused` | gauge | `Game.<init>` | 1 if `Game.isPaused()` at dump tick, else 0 |
| `ENGINE_PATH` | `path.search` | span | `AStarQueue` | per `item.search(...)` call |
| `ENGINE_PATH` | `path.search.count` | counter | `AStarQueue` | per `search()` call |
| `ENGINE_PATH` | `path.queue.depth` | counter | `AStarQueue` | sampled once per outer cycle |
| `ENGINE_HAPPINESS` | `happiness.cache.build.full` | span | `HappinessCache` constructor | full-build precompute |
| `ENGINE_HAPPINESS` | `happiness.cache.build.tile` | counter | `HappinessCache.buildCacheForTile` | per lazy tile build |
| `ENGINE_HAPPINESS` | `happiness.cache.invalidate.item_added` | counter | `HappinessCache.onItemPlaced` | |
| `ENGINE_HAPPINESS` | `happiness.cache.invalidate.item_removed` | counter | `HappinessCache.onItemRemoved` | |
| `ENGINE_HAPPINESS` | `happiness.cache.invalidate.wall_changed` | counter | `HappinessCache.onWallChanged` | |
| `ENGINE_HAPPINESS` | `happiness.cache.invalidate.mined` | counter | `HappinessCache.onMiningChanged` | |
| `ENGINE_HAPPINESS` | `happiness.cache.invalidate.discovered` | counter | `HappinessCache.onDiscovered` | |
| `ENGINE_HAPPINESS` | `happiness.cache.tiles_dirtied` | counter | `HappinessCache.markNeighborhoodDirty` | rectangle area per batch |
| `ENGINE_HAPPINESS` | `happiness.recalc.citizen` | span | `World.modifyHappiness(Citizen)` | should be cache-cheap; if p99 climbs into ms territory the cache lookup has regressed |
| `ENGINE_GC` | `gc.minor.count` / `gc.minor.duration` / `gc.minor.freed_mb` | counter / span / counter | JMX `GarbageCollectorMXBean` (minor collectors) | **`duration` is cycle, NOT pause** — see caveat below |
| `ENGINE_GC` | `gc.major.count` / `gc.major.duration` / `gc.major.freed_mb` | counter / span / counter | JMX (major collectors) | same caveat |
| `ENGINE_GC` | `heap.used_mb` | gauge | `MemoryMXBean` | sampled at dump time |
| `ENGINE_GC` | `heap.committed_mb` | gauge | `MemoryMXBean` | sampled at dump time |
| `ENGINE_GC` | `heap.alloc_mb_per_sec` | gauge | derived | (used delta + freed total) / period |

---

## Caveats

These matter when interpreting the data. Read them before claiming a metric
proves anything.

### `gc.*.duration` is cycle duration, not pause time

JMX `getDuration()` reports the wall-clock duration of a GC cycle, not the
stop-the-world pause time. ZGC runs almost entirely concurrently with the
application, with sub-millisecond pauses bookending each cycle. A
`gc.major.duration_p99 = 200ms` does **not** mean the game stalled for
200 ms — it means a 200 ms concurrent cycle ran in the background.

The metric for "did GC stall the frame?" is `frame.total_p99_ns` spiking
in the same period that `gc.*.count` increments. Treat GC counters as
*correlation evidence*, not direct stall measurement.

If pause-precise data ever becomes essential, JFR is the right tool —
specifically `jdk.GCPhasePause` events. Out of scope for this harness.

### Histogram clamps at ~655 ms

The 64-bucket binary-decade histogram covers 10 µs to ~655 ms with ~19%
bucket width and ±10% percentile accuracy on smooth distributions.
Durations beyond 655 ms (long save loads, world generation, occasional
multi-hundred-ms major GC cycles) all land in the top bucket. Their
`*_count` and `*_total_ns` track correctly — only the percentile estimate
saturates at "≥ ~655 ms".

For the metrics this harness instruments (frames at 33 ms, sim ticks at
tens of ms, A* searches in microseconds, ZGC cycles typically 100–300 ms)
the range fits. If a future use case wants to time multi-second
operations, either re-bucket the Histogram or instrument with a separate
`SampleHandle` that just records last-known duration.

### Sub-10 µs spans hit a noise floor

Recording overhead per `Span.close()` is ~200–500 ns. Spans shorter than
~10 µs are mostly measurement noise; the harness clamps them into bucket
0 (`[10 µs, ~12 µs)`). Don't instrument hot inner loops that finish in
nanoseconds — use a counter instead.

### Counters are reset each period

Per-period counters (`gl.draw_texture`, `path.search.count`, etc.) report
the sum *over that period*, not cumulative since startup. The dumper
atomically resets after each snapshot. To get cumulative behavior, sum
the column in post-processing.

### `gc.*.freed_mb` is approximate

`GarbageCollectorMXBean` doesn't expose per-cycle freed bytes directly;
the dumper derives a coarse estimate from heap-used deltas and currently
attributes all of it to the minor side. If this becomes load-bearing,
parse the `GarbageCollectionNotificationInfo`'s memory-pool-before/after
maps in `GcListener` instead.

### Wall-clock timestamps are best-effort

`PERF_LOG_PERIOD_MS` is the dumper sleep time, not a hard guarantee. Under
heavy GC pressure or thread starvation the actual period may stretch. The
`seconds_since_start` column reflects the actual flush time, not the
nominal cadence — use it (not `timestamp_iso`) for delta calculations
between rows.

### Each launch writes a new timestamped file

`~/.towns/perf-YYYYMMDD-HHMMSS.csv` is created fresh on every launch with
a per-second-resolution timestamp suffix. Old files are preserved
indefinitely; clean them up manually when they accumulate
(`rm ~/.towns/perf-*.csv` to wipe everything; `ls -1 ~/.towns/perf-*.csv
| head -n -10 | xargs rm` to keep the 10 most recent).

The earlier "truncated on each launch" semantic was a usability trap on
Windows: a stale OS-level lock on `perf.csv` (held by Explorer preview,
a tail process, or a JVM that didn't shut down cleanly) would block the
next launch's `TRUNCATE_EXISTING` open and silently disable telemetry.
Timestamped filenames sidestep this entirely — every launch writes to a
file that didn't exist a second ago.

### Game-speed and pause are recorded but not normalized

`game.speed` and `game.paused` capture the *current* speed multiplier
and pause state at dump time. The harness measures **wall-clock** time,
not game-time, so:

- At 4× speed, `sim.tick.count` per real second is ~4× higher (more
  ticks fire per real second), but per-call duration (`p50`/`p99` of
  `sim.tick`) is unchanged — same code, same cost per call.
- During pause, `sim.*` counts and `path.*` activity drop to ~0, but
  `frame.*` and `gl.*` keep ticking at full rate (rendering continues).
- Two CSV runs at different speeds will look very different in *total*
  counts even if per-call performance is identical. To compare runs
  apples-to-apples, either play both at 1× speed and 0 pause, or
  filter rows by `game.speed` / `game.paused` in post-processing.

For the renderer A/B specifically, speed doesn't matter — rendering is
pinned to 30 FPS regardless. Just play both runs at any consistent
speed.

---

## Adding a new metric

Four steps:

1. **Pick a category.** `Category.java` has six values; reuse one if it
   fits, or add a new enum value. New categories also need a new
   `PERF_<TYPE>_<NAME>` entry in `towns.ini` and a parsing line in
   `Game.<init>()` where the existing `PERF_*` keys are read.

2. **Declare a static handle** at the top of the file that contains the
   instrumented code:

   ```java
   import xaos.utils.perf.Category;
   import xaos.utils.perf.PerfStats;
   import xaos.utils.perf.SpanHandle;   // or CounterHandle / SampleHandle

   private static final SpanHandle SPAN_FOO_BAR =
       PerfStats.span("foo.bar", Category.ENGINE_FOO);
   ```

   Handles are lazy — declaring one before `PerfStats.init()` runs is
   safe; the handle resolves on first use.

3. **Use it at the call site.**

   ```java
   try (Span s = SPAN_FOO_BAR.start()) {
       // ... existing code ...
   }
   // or for a counter:
   CNT_FOO_BAR.inc();
   CNT_FOO_BAR.add(n);
   // or for a gauge (sampled at dump time):
   PerfStats.sample("foo.bar.gauge", Category.ENGINE_FOO, () -> someCurrentValue());
   ```

4. **Append a row to `KNOWN_METRICS` in `PerfStats.java`** so the dumper
   pre-registers the metric at init time and the CSV schema is stable
   from row 1. Forgetting this isn't a crash — the metric still works
   via lazy registration — but late-registered metrics trigger a
   header re-emit mid-CSV, which is exactly what the catalog exists
   to prevent.

   Samples (gauges) are exempt — `PerfStats.sample(...)` registers
   eagerly because the supplier is supplied at declaration time, so
   long as that call happens before the first dump tick the schema
   stays stable.

---

## Adding a new category

1. Add the enum value to `Category.java`.
2. Add a `PerfStatsConfig` field for it (see existing fields for pattern).
3. Add the `PERF_<TYPE>_<NAME>` line in `towns.ini`.
4. Wire the parsing in `Game.<init>()` (where the existing `PERF_*` keys
   are read).

---

## Removing a metric

Removing instrumentation: delete the handle declaration and the call
site(s). Disabled categories already short-circuit the work, so removing
metrics is purely a code-cleanup operation.

If a former metric appears in someone's old CSV, that's expected — the
schema can change at any time, and the file is regenerated each launch.

---

## Tests

`mvn -f Towns/pom.xml test` runs the perf-module tests under
`test/xaos/utils/perf/`:

- `HistogramTest` — bucketing, percentile accuracy on synthetic
  distributions, thread-safety, snapshot-and-reset correctness.
- `PerfStatsTest` — handle resolution, lazy registration, category gating,
  master-disable, recordRaw equivalence.
- `CsvWriterTest` — header generation, row formatting, empty-period
  behavior, locale-independent decimal formatting.
- `PerfStatsDumperTest` — integration: tempfile dumper writes header +
  ≥1 numerically-sane row, master-disabled creates no file.

If you change the histogram bucket math (e.g. shift `MIN_NANOS` again or
change `SUB_BUCKETS_PER_DECADE`), the boundary tests in `HistogramTest`
hardcode the current edges and need updating in lockstep.

---

## Future work — analyzer / A-B comparison tool

Captured in `todo.md` under "Performance analysis". Not built yet. Until
it is, A/B comparisons are done by hand: load both CSVs into a spreadsheet
or pandas, diff the columns of interest. Expect to write a 50-line script
per investigation; promote shared logic to a real tool only when the
patterns stabilize.

---

## References

- **Design spec:** `docs/superpowers/specs/2026-04-26-perf-stats-design.md`
  (gitignored — local only)
- **Backlog this exists to support:** `optimize.md` (numbered items 1–7)
- **Backlog of follow-up tooling:** `todo.md` → "Performance analysis"
- **Related history:**
  - `changelist.md` → "Performance instrumentation" (when this lands)
  - `session-two.md` → discussion of GC stutter and ZGC adoption rationale
- **JDK references:** `java.lang.management.GarbageCollectorMXBean`,
  `com.sun.management.GarbageCollectionNotificationInfo`,
  `java.util.concurrent.atomic.AtomicLongArray` (used by `Histogram`)
