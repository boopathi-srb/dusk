package local.tvhome;

/**
 * Recognises the one line Fire OS writes to the system activity log when the
 * remote's Home button is pressed. Pure string logic, no Android types.
 *
 * Real line from this TV (Fire OS 8.1.8.5):
 *   I/ActivityTaskManager(  934): START u0 {act=android.intent.action.MAIN
 *   cat=[android.intent.category.HOME] flg=0x10000100
 *   cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 0
 *
 * The same activity is also started by other callers (Settings uses uid 10183); those are left alone.
 * Two callers mean "show home" and are taken over:
 *   uid 0     the remote's Home button;
 *   uid 1000  Fire OS itself, e.g. sending the TV back to home when it goes to sleep (seen 11-26:
 *             "... flg=0x10200000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext (has extras)} from uid 1000"),
 *             so Dusk, not Amazon's home, is on screen when the TV wakes.
 */
final class HomePressLog {

    static final String LOG_BUFFER = "system";
    static final String LOG_FILTER_KEEP = "ActivityTaskManager:I";
    static final String LOG_FILTER_SILENCE_REST = "*:S";

    private static final String START_MARKER = "START u0 {";
    private static final String HOME_CATEGORY_MARKER = "cat=[android.intent.category.HOME]";
    private static final String AMAZON_HOME_COMPONENT_MARKER =
            "cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext";
    private static final String HOME_BUTTON_CALLER_SUFFIX = "} from uid 0";
    private static final String SYSTEM_CALLER_SUFFIX = "} from uid 1000";

    private HomePressLog() {
    }

    static boolean isHomePressLine(String logLine) {
        if (logLine == null) {
            return false;
        }
        String line = logLine.trim();
        return line.contains(START_MARKER)
                && line.contains(HOME_CATEGORY_MARKER)
                && line.contains(AMAZON_HOME_COMPONENT_MARKER)
                && (line.endsWith(HOME_BUTTON_CALLER_SUFFIX) || line.endsWith(SYSTEM_CALLER_SUFFIX));
    }

    /** Follows only activity-manager lines written after {@code sinceTimestamp} ("MM-dd HH:mm:ss.SSS"). */
    static String[] logcatCommandSince(String sinceTimestamp) {
        return new String[] {
            "logcat", "-b", LOG_BUFFER, "-v", "brief", "-T", sinceTimestamp,
            LOG_FILTER_KEEP, LOG_FILTER_SILENCE_REST,
        };
    }
}
