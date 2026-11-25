package local.tvhome;

/**
 * Time-of-day decisions for the sky and the greeting. Pure logic, no Android types.
 * Minutes are minutes since midnight (0..1439); any other value is wrapped into that range.
 */
final class SkyClock {

    enum Phase { MORNING, AFTERNOON, EVENING, NIGHT }

    static final int MINUTES_PER_DAY = 24 * 60;
    static final int NOON = 12 * 60;
    static final int NIGHT_START = 22 * 60;

    /** Returned by the progress methods when that body is not in the sky. */
    static final float NOT_IN_SKY = -1f;

    /**
     * Where the sun is at sunset: just above the right end of the arc. Through golden dusk it keeps
     * sinking, slowly, to the very end, so it never jumps.
     */
    static final float DUSK_PROGRESS = 0.96f;

    /** Golden dusk lasts this long after sunset; then the sky goes dark even though it is still "evening". */
    static final int DUSK_MINUTES = 60;


    private final int sunriseMinute;
    private final int sunsetMinute;

    SkyClock(int sunriseMinute, int sunsetMinute) {
        if (!isValid(sunriseMinute, sunsetMinute)) {
            throw new IllegalArgumentException("SkyClock: sunrise must be before noon and sunset between noon"
                    + " and 21:00, got sunriseMinute=" + sunriseMinute + " sunsetMinute=" + sunsetMinute);
        }
        this.sunriseMinute = sunriseMinute;
        this.sunsetMinute = sunsetMinute;
    }

    /** Sunrise before noon, and sunset after noon with a full hour of dusk before 22:00. */
    static boolean isValid(int sunriseMinute, int sunsetMinute) {
        return sunriseMinute >= 0 && sunriseMinute < NOON && sunsetMinute > NOON
                && sunsetMinute + DUSK_MINUTES <= NIGHT_START;
    }

    Phase phaseAt(int minuteOfDay) {
        int minute = normalise(minuteOfDay);
        if (minute >= sunriseMinute && minute < NOON) {
            return Phase.MORNING;
        }
        if (minute >= NOON && minute < sunsetMinute) {
            return Phase.AFTERNOON;
        }
        if (minute >= sunsetMinute && minute < NIGHT_START) {
            return Phase.EVENING;
        }
        return Phase.NIGHT;
    }

    static String greeting(Phase phase) {
        switch (phase) {
            case MORNING:
                return "Good morning";
            case AFTERNOON:
                return "Good afternoon";
            case EVENING:
                return "Good evening";
            default:
                return "Good night";
        }
    }

    /** "Hey Sam!"; "Hey there!" when no name is set. */
    static String hey(String name) {
        return name == null || name.trim().isEmpty() ? "Hey there!" : "Hey " + name.trim() + "!";
    }

    /** "Good evening Sam!"; just the greeting when no name is set. */
    static String greeting(Phase phase, String name) {
        String base = greeting(phase);
        if (name == null || name.trim().isEmpty()) {
            return base;
        }
        return base + " " + name.trim() + "!";
    }

    /**
     * The line under the greeting, about the sky itself: "Sunset at 5:50 PM", "Sunset in 2 h 10 min",
     * "The sun set at 5:50 PM", or "Sunrise at 6:20 AM".
     */
    String skyNote(int minuteOfDay, boolean use24Hour) {
        int minute = normalise(minuteOfDay);
        switch (phaseAt(minute)) {
            case MORNING:
                return "Sunset at " + clockTime(sunsetMinute, use24Hour);
            case AFTERNOON:
                return "Sunset in " + duration(sunsetMinute - minute);
            case EVENING:
                return "The sun set at " + clockTime(sunsetMinute, use24Hour);
            default:
                return "Sunrise at " + clockTime(sunriseMinute, use24Hour);
        }
    }

    /** "5:50 PM" or "17:50". */
    static String clockTime(int minuteOfDay, boolean use24Hour) {
        int minute = normalise(minuteOfDay);
        int hour = minute / 60;
        int minutes = minute % 60;
        if (use24Hour) {
            return String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minutes);
        }
        int twelveHour = hour % 12 == 0 ? 12 : hour % 12;
        return String.format(java.util.Locale.ROOT, "%d:%02d %s", twelveHour, minutes, hour < 12 ? "AM" : "PM");
    }

    /** "25 min", "2 h", "2 h 10 min". */
    static String duration(int minutes) {
        int safe = Math.max(0, minutes);
        if (safe < 60) {
            return safe + " min";
        }
        int rest = safe % 60;
        return rest == 0 ? (safe / 60) + " h" : (safe / 60) + " h " + rest + " min";
    }

    int sunriseMinute() {
        return sunriseMinute;
    }

    int sunsetMinute() {
        return sunsetMinute;
    }

    /** True from the end of dusk until sunrise: the sky is dark, with stars and the moon. */
    boolean isDark(int minuteOfDay) {
        int minute = normalise(minuteOfDay);
        return minute >= duskEndMinute() || minute < sunriseMinute;
    }

    /** 0 = rising at the left end of the arc; {@link #DUSK_PROGRESS} at sunset; 1 = gone below the right end at the end of dusk. */
    float sunProgressAt(int minuteOfDay) {
        int minute = normalise(minuteOfDay);
        Phase phase = phaseAt(minute);
        if (phase == Phase.MORNING || phase == Phase.AFTERNOON) {
            return DUSK_PROGRESS * (minute - sunriseMinute) / (float) (sunsetMinute - sunriseMinute);
        }
        if (isDark(minute)) {
            return NOT_IN_SKY;
        }
        float intoDusk = (minute - sunsetMinute) / (float) DUSK_MINUTES;
        return DUSK_PROGRESS + (1f - DUSK_PROGRESS) * Math.max(0f, Math.min(1f, intoDusk));
    }

    private int duskEndMinute() {
        return sunsetMinute + DUSK_MINUTES;
    }

    static int normalise(int minuteOfDay) {
        return ((minuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
    }
}
