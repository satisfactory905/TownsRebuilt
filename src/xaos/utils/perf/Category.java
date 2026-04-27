package xaos.utils.perf;

/**
 * Logical groupings for perf metrics. Each category can be toggled
 * independently in towns.ini under the [DEBUG] section using
 * {@code PERF_<NAME>=true|false}. When a category is disabled every
 * handle resolved against it returns the singleton DISABLED_* no-op
 * variant from PerfStats so the hot path remains under ~10 ns.
 *
 * <p>The naming scheme {@code PERF_<TYPE>_<NAME>} is forward-compatible:
 * future categories like PERF_ENGINE_AI or PERF_RENDERING_PARTICLES
 * slot in without renaming any existing entries.
 */
public enum Category {

	RENDERING_FRAME,
	RENDERING_GL,
	ENGINE_SIM,
	ENGINE_PATH,
	ENGINE_HAPPINESS,
	ENGINE_GC;

	/**
	 * Returns the towns.ini key that toggles this category, e.g.
	 * RENDERING_FRAME -> "PERF_RENDERING_FRAME".
	 */
	public String iniKey () {
		return "PERF_" + name ();
	}
}
