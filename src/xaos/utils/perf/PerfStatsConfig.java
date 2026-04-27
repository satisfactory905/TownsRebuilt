package xaos.utils.perf;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Set;

import xaos.utils.Log;

/**
 * Pre-parsed configuration POJO consumed by {@link PerfStats#init(PerfStatsConfig)}.
 *
 * <p>This class deliberately does not read towns.ini directly; the towns.ini
 * loading lives in Game.java and is responsible for translating the raw
 * properties into one of these objects. Keeping the perf module free of any
 * Game-specific configuration plumbing means it can be unit-tested in
 * isolation and reused outside the running game (e.g. from an offline tool).
 *
 * <p>Use {@link #fromProperties(Properties)} as a convenience helper that
 * applies the spec's defaults (master enable -&gt; true, missing or malformed
 * boolean -&gt; true, every category on, period 1000 ms, empty path means the
 * caller picks {@code ~/.towns/perf.csv} or wherever it sees fit).
 */
public final class PerfStatsConfig {

	private final boolean masterEnabled;
	private final Path csvPath;
	private final long periodMs;
	private final Set<Category> enabledCategories;

	public PerfStatsConfig (boolean masterEnabled,
							Path csvPath,
							long periodMs,
							Set<Category> enabledCategories) {
		this.masterEnabled = masterEnabled;
		this.csvPath = csvPath;
		this.periodMs = periodMs <= 0 ? 1000L : periodMs;
		// Defensive copy into an EnumSet so the snapshot is stable.
		this.enabledCategories = (enabledCategories == null || enabledCategories.isEmpty ())
			? EnumSet.noneOf (Category.class)
			: EnumSet.copyOf (enabledCategories);
	}

	public boolean isMasterEnabled () {
		return masterEnabled;
	}

	public Path getCsvPath () {
		return csvPath;
	}

	public long getPeriodMs () {
		return periodMs;
	}

	public Set<Category> getEnabledCategories () {
		// Return an unmodifiable view -- callers should not mutate.
		return EnumSet.copyOf (enabledCategories);
	}

	/**
	 * Returns true when the master switch is on AND the given category is
	 * enabled. PerfStats uses this to decide whether a handle factory call
	 * returns a real or a DISABLED_* singleton.
	 */
	public boolean isCategoryEnabled (Category c) {
		return masterEnabled && enabledCategories.contains (c);
	}

	/**
	 * Convenience: build a config from a Properties bag using the keys named
	 * in the spec. Missing keys, malformed booleans, and parse failures all
	 * resolve to "true" / sensible defaults -- the spec deliberately favors
	 * visibility on this debug feature.
	 *
	 * @param props the properties bag (typically loaded from towns.ini); may
	 *              be null, in which case all defaults apply
	 * @param defaultCsvPath path to use when PERF_LOG_PATH is empty or absent
	 */
	public static PerfStatsConfig fromProperties (Properties props, Path defaultCsvPath) {
		boolean master = parseBoolDefaultTrue (props, "PERF_LOG");

		String pathStr = props == null ? null : props.getProperty ("PERF_LOG_PATH");
		Path csvPath;
		if (pathStr == null || pathStr.trim ().isEmpty ()) {
			csvPath = defaultCsvPath;
		} else {
			csvPath = Path.of (pathStr.trim ());
		}

		long period = 1000L;
		String periodStr = props == null ? null : props.getProperty ("PERF_LOG_PERIOD_MS");
		if (periodStr != null && !periodStr.trim ().isEmpty ()) {
			try {
				period = Long.parseLong (periodStr.trim ());
				if (period <= 0) {
					Log.log (Log.LEVEL_ERROR,
						"PERF_LOG_PERIOD_MS must be > 0, got " + period + " -- using 1000",
						"PerfStats");
					period = 1000L;
				}
			} catch (NumberFormatException nfe) {
				Log.log (Log.LEVEL_ERROR,
					"PERF_LOG_PERIOD_MS is not a number: '" + periodStr + "' -- using 1000",
					"PerfStats");
				period = 1000L;
			}
		}

		EnumSet<Category> enabled = EnumSet.noneOf (Category.class);
		for (Category c : Category.values ()) {
			if (parseBoolDefaultTrue (props, c.iniKey ())) {
				enabled.add (c);
			}
		}

		return new PerfStatsConfig (master, csvPath, period, enabled);
	}

	private static boolean parseBoolDefaultTrue (Properties props, String key) {
		if (props == null) return true;
		String v = props.getProperty (key);
		if (v == null) return true;
		String t = v.trim ();
		if (t.isEmpty ()) return true;
		// Accept the usual variants; anything else defaults to true with a warning.
		if (t.equalsIgnoreCase ("true")) return true;
		if (t.equalsIgnoreCase ("false")) return false;
		Log.log (Log.LEVEL_ERROR,
			"Malformed boolean for " + key + ": '" + v + "' -- defaulting to true",
			"PerfStats");
		return true;
	}
}
