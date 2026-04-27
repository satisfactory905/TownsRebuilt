package xaos.utils.perf;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import xaos.utils.Log;

/**
 * Wires a {@link NotificationListener} onto every
 * {@link GarbageCollectorMXBean} so per-cycle GC durations are recorded
 * into {@code gc.minor.duration} / {@code gc.major.duration} spans.
 *
 * <p>The "minor vs major" decision is made on a name-substring match per
 * the spec's reference snippet -- ZGC, G1, Parallel, and CMS use names
 * containing "young"/"minor" or "old"/"major" depending on the JDK version,
 * and we treat anything not flagged "major"/"old" as a minor cycle.
 *
 * <p>Duration extraction is reflective so we don't import
 * {@code com.sun.management.GarbageCollectionNotificationInfo} and force
 * a specific JVM. On HotSpot/OpenJDK 25 the userData is a
 * {@code CompositeData} with a nested {@code gcInfo} member that exposes
 * a long {@code duration} (milliseconds). Anything else returns -1 and
 * the notification is silently dropped.
 *
 * <p>Caveat the design accepts: {@code getDuration()} reports the GC
 * <i>cycle</i> duration, not stop-the-world pause time. For ZGC that
 * matters; this metric is for correlation, not pause-precise measurement.
 */
final class GcListener {

	private static final String GARBAGE_COLLECTION_NOTIFICATION =
		"com.sun.management.gc.notification";

	private SpanHandle gcMinor;
	private SpanHandle gcMajor;

	private final List<NotificationEmitter> emitters = new ArrayList<> ();
	private final List<NotificationListener> listeners = new ArrayList<> ();

	void install () {
		// Lazy-resolve through the public factory so the handles are
		// tracked by PerfStats's invalidation list. If ENGINE_GC is
		// disabled the handles will go to DISABLED_HISTOGRAM on first
		// use and recordRaw becomes a no-op -- the listener still runs,
		// but does no work.
		gcMinor = PerfStats.span ("gc.minor.duration", Category.ENGINE_GC);
		gcMajor = PerfStats.span ("gc.major.duration", Category.ENGINE_GC);

		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans ()) {
			if (!(bean instanceof NotificationEmitter)) continue;
			NotificationEmitter emitter = (NotificationEmitter) bean;
			String n = bean.getName ().toLowerCase (Locale.ROOT);
			boolean isMajor = n.contains ("major") || n.contains ("old");
			final SpanHandle target = isMajor ? gcMajor : gcMinor;
			NotificationListener listener = (notification, hb) -> {
				try {
					if (!GARBAGE_COLLECTION_NOTIFICATION.equals (notification.getType ())) return;
					Object userData = notification.getUserData ();
					long durationMs = extractGcDurationMs (userData);
					if (durationMs >= 0L) {
						target.recordRaw (durationMs * 1_000_000L);
					}
				} catch (Throwable t) {
					Log.log (Log.LEVEL_ERROR,
						"GC notification listener error: " + t,
						"PerfStats");
				}
			};
			try {
				emitter.addNotificationListener (listener, null, null);
				emitters.add (emitter);
				listeners.add (listener);
				Log.log (Log.LEVEL_DEBUG,
					"GC listener installed on '" + bean.getName () + "' (" + (isMajor ? "major" : "minor") + ")",
					"PerfStats");
			} catch (Throwable t) {
				Log.log (Log.LEVEL_ERROR,
					"Could not install GC listener on " + bean.getName () + ": " + t,
					"PerfStats");
			}
		}
	}

	void uninstall () {
		for (int i = 0; i < emitters.size (); i++) {
			try {
				emitters.get (i).removeNotificationListener (listeners.get (i));
			} catch (Throwable ignored) {
				// JMX bean may already be detached on shutdown -- not fatal.
			}
		}
		emitters.clear ();
		listeners.clear ();
	}

	private static long extractGcDurationMs (Object userData) {
		if (userData == null) return -1L;
		try {
			if (userData instanceof javax.management.openmbean.CompositeData cd) {
				if (cd.containsKey ("gcInfo")) {
					Object gcInfo = cd.get ("gcInfo");
					if (gcInfo instanceof javax.management.openmbean.CompositeData gi) {
						if (gi.containsKey ("duration")) {
							Object dur = gi.get ("duration");
							if (dur instanceof Number n) {
								return n.longValue ();
							}
						}
					}
				}
			}
		} catch (Throwable ignored) {
			// fall through
		}
		return -1L;
	}
}
