package local.tvhome;

/**
 * Which "now playing" reports are worth remembering as Continue watching cards. Used for apps that
 * share nothing else (YouTube). Pure logic, no Android types.
 */
final class MediaHistoryRules {

    /** Music, Alexa, Amazon's own players and this launcher: not things to "continue watching". */
    private static final String[] SKIPPED_PREFIXES = {
        "local.tvhome",
        "com.spotify",
        "com.amazon.alexamediaplayer",
        "com.amazon.vizzini",
        "com.amazon.bueller.music",
        "com.amazon.tv.launcher",
    };

    /** Anything watched for less than this was a peek, not something to come back to. */
    static final long MIN_WATCHED_MS = 15_000L;
    /** Past this share of its length, a video counts as finished. */
    static final float FINISHED_SHARE = 0.95f;

    private MediaHistoryRules() {
    }

    static boolean isSkippedApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return true;
        }
        for (String prefix : SKIPPED_PREFIXES) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static boolean isFinished(long positionMs, long durationMs) {
        return durationMs > 0 && positionMs >= durationMs * FINISHED_SHARE;
    }

    static boolean isWorthKeeping(String packageName, String title, long positionMs, long durationMs) {
        if (isSkippedApp(packageName) || title == null || title.trim().isEmpty()) {
            return false;
        }
        return positionMs >= MIN_WATCHED_MS && !isFinished(positionMs, durationMs);
    }

    /**
     * Where playback is now: a report gives a position at a moment; while playing it has moved on
     * by the time passed, times the speed.
     */
    static long positionNow(long reportedPositionMs, long reportedAtMs, long nowMs, float speed, boolean playing) {
        long reported = Math.max(0L, reportedPositionMs);
        if (!playing || nowMs <= reportedAtMs || speed <= 0f) {
            return reported;
        }
        return reported + (long) ((nowMs - reportedAtMs) * speed);
    }
}
