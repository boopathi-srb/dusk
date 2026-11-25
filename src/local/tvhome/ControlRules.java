package local.tvhome;

/**
 * Decisions for the control panel: brightness steps, the Wi-Fi line, and which "now playing" app
 * to show. Pure logic, no Android types.
 */
final class ControlRules {

    /** Android's brightness scale. Never all the way to 0: a black panel looks like a broken TV. */
    static final int MIN_BRIGHTNESS = 10;
    static final int MAX_BRIGHTNESS = 255;
    /** One remote press moves brightness by about a tenth of the range. */
    static final int BRIGHTNESS_STEP = 25;

    /** The same numbers as Android's PlaybackState.STATE_PAUSED and STATE_PLAYING. */
    static final int STATE_PAUSED = 2;
    static final int STATE_PLAYING = 3;

    static final int NO_SESSION = -1;

    /** Signal strength in dBm: at or above these, Wi-Fi is "strong" or "good"; below, "weak". */
    private static final int STRONG_RSSI = -60;
    private static final int GOOD_RSSI = -70;

    /** System players and this launcher report sessions too; they are not "now playing" for the viewer. */
    private static final String[] NOT_NOW_PLAYING_PREFIXES = {
        "local.tvhome",
        "com.amazon.alexamediaplayer",
        "com.amazon.vizzini",
        "com.amazon.tv.launcher",
    };

    private ControlRules() {
    }

    static int clampBrightness(int value) {
        return Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, value));
    }

    /** {@code direction} is +1 (brighter) or -1 (dimmer); anything else leaves the value as it is. */
    static int stepBrightness(int current, int direction) {
        int step = Integer.signum(direction) * BRIGHTNESS_STEP;
        return clampBrightness(clampBrightness(current) + step);
    }

    /** 0 at the dimmest allowed level, 1 at full. */
    static float brightnessShare(int value) {
        return (clampBrightness(value) - MIN_BRIGHTNESS) / (float) (MAX_BRIGHTNESS - MIN_BRIGHTNESS);
    }

    static int brightnessPercent(int value) {
        return Math.round(brightnessShare(value) * 100f);
    }

    static String wifiLabel(boolean connected, int rssiDbm) {
        if (!connected) {
            return "Wi-Fi · offline";
        }
        if (rssiDbm >= STRONG_RSSI) {
            return "Wi-Fi · strong";
        }
        return rssiDbm >= GOOD_RSSI ? "Wi-Fi · good" : "Wi-Fi · weak";
    }

    /** What Android reports when it will not say the network's name (no location access). */
    private static final String UNKNOWN_SSID = "<unknown ssid>";

    /** The Wi-Fi network's name without Android's surrounding quotes; null when unknown or not given. */
    static String networkName(String reportedSsid) {
        if (reportedSsid == null) {
            return null;
        }
        String name = reportedSsid.trim();
        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            name = name.substring(1, name.length() - 1);
        }
        return name.isEmpty() || UNKNOWN_SSID.equals(name) ? null : name;
    }

    static boolean isNowPlayingApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        for (String prefix : NOT_NOW_PLAYING_PREFIXES) {
            if (packageName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Which session to show: the first one playing, else the first one paused. Only sessions with a
     * title count. Returns {@link #NO_SESSION} when none qualifies or the lists do not line up.
     */
    static int pickSession(int[] states, boolean[] hasTitle) {
        if (states == null || hasTitle == null || states.length != hasTitle.length) {
            return NO_SESSION;
        }
        int firstPaused = NO_SESSION;
        for (int index = 0; index < states.length; index++) {
            if (!hasTitle[index]) {
                continue;
            }
            if (states[index] == STATE_PLAYING) {
                return index;
            }
            if (states[index] == STATE_PAUSED && firstPaused == NO_SESSION) {
                firstPaused = index;
            }
        }
        return firstPaused;
    }

    /** Sleep timer choices in minutes, stepped through by pressing the tile; 0 = off. */
    static final int[] SLEEP_CHOICES_MINUTES = {0, 15, 30, 60};
    private static final long BYTES_PER_MB = 1024L * 1024L;
    /** Less than this freed is not worth announcing as a number. */
    private static final long NOTICEABLE_FREED_MB = 5L;

    /** The next sleep choice after {@code currentMinutes}; an unknown value starts the cycle again. */
    static int nextSleepChoice(int currentMinutes) {
        for (int index = 0; index < SLEEP_CHOICES_MINUTES.length; index++) {
            if (SLEEP_CHOICES_MINUTES[index] == currentMinutes) {
                return SLEEP_CHOICES_MINUTES[(index + 1) % SLEEP_CHOICES_MINUTES.length];
            }
        }
        return SLEEP_CHOICES_MINUTES[1];
    }

    static String sleepLabel(int minutesLeft) {
        return minutesLeft <= 0 ? "Sleep off" : "Sleep " + minutesLeft + " min";
    }

    static String adbLabel(boolean on) {
        return on ? "ADB on" : "ADB off";
    }

    static String freedLabel(long availableBeforeBytes, long availableAfterBytes) {
        long freedMb = (availableAfterBytes - availableBeforeBytes) / BYTES_PER_MB;
        return freedMb >= NOTICEABLE_FREED_MB ? "Freed " + freedMb + " MB" : "Already free";
    }

    /** A whole day and night, sunrise to the next sunrise. */
    static final int PREVIEW_SPAN_MINUTES = 24 * 60;

    /**
     * How many minutes past its starting sunrise the sky preview is after {@code elapsedMs}: moving
     * evenly through {@code spanMinutes}, held at either end outside the run.
     */
    static int previewOffsetMinutes(long elapsedMs, long durationMs, int spanMinutes) {
        if (durationMs <= 0 || spanMinutes <= 0) {
            return 0;
        }
        float share = Math.max(0f, Math.min(1f, elapsedMs / (float) durationMs));
        return Math.round(share * spanMinutes);
    }

    /**
     * Which sunrise the preview starts from: the next one. Before today's sunrise that is today's
     * (0 days ahead); from sunrise on, tomorrow's (1 day ahead).
     */
    static int previewStartDaysAhead(int minuteNow, int sunriseMinute) {
        return minuteNow < sunriseMinute ? 0 : 1;
    }

    static String playbackLabel(String appName, int state) {
        String what = state == STATE_PLAYING ? "Playing" : "Paused";
        return appName == null || appName.isEmpty() ? what : appName + " · " + what;
    }
}
