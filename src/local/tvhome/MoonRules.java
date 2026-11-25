package local.tvhome;

/**
 * The real moon, worked out from the date with no internet: its phase (new, crescent, half, full),
 * how much of it is lit, and where it is on the sky's arc. The moon follows the sun's path a little
 * later each day: a new moon travels with the sun, a half moon six hours behind it, and a full moon
 * twelve hours behind, rising on the left as the sun sets on the right. Pure logic, no Android types.
 */
final class MoonRules {

    /** Days from one new moon to the next. */
    static final double SYNODIC_DAYS = 29.530588853;
    /** A known new moon: 6 January 2000, 18:14 UTC. */
    static final long REFERENCE_NEW_MOON_MS = 947_182_440_000L;
    /** Below this share lit, the moon is too thin to see, so it is not drawn. */
    static final float MIN_VISIBLE_LIT = 0.03f;
    /** By day the moon is pale; this much of its night brightness. */
    static final float DAYTIME_BRIGHTNESS = 0.5f;

    private static final double DAY_MS = 24.0 * 60 * 60 * 1000;

    private MoonRules() {
    }

    /** 0 = new moon, 0.25 = first quarter (half, growing), 0.5 = full, 0.75 = last quarter. */
    static float phaseAt(long epochMillis) {
        double days = (epochMillis - REFERENCE_NEW_MOON_MS) / DAY_MS;
        double cycles = days / SYNODIC_DAYS;
        return (float) (cycles - Math.floor(cycles));
    }

    /** Share of the disc that is lit: 0 at new moon, 0.5 at a half moon, 1 at full moon. */
    static float litShare(float phase) {
        return (float) ((1 - Math.cos(2 * Math.PI * phase)) / 2);
    }

    /** Growing (first half of the cycle): lit on the right. Shrinking: lit on the left. */
    static boolean isWaxing(float phase) {
        return phase < 0.5f;
    }

    /** Whether the lit part is less than half (a crescent) rather than more (gibbous). */
    static boolean isCrescent(float phase) {
        return litShare(phase) < 0.5f;
    }

    /**
     * Where the moon is on the arc: 0 rising at the left end, 1 setting at the right end; or
     * {@link SkyClock#NOT_IN_SKY} while it is below the horizon or too thin to see.
     */
    static float progressAt(SkyClock clock, int minuteOfDay, float phase) {
        if (clock == null || litShare(phase) < MIN_VISIBLE_LIT) {
            return SkyClock.NOT_IN_SKY;
        }
        int lagMinutes = Math.round(phase * SkyClock.MINUTES_PER_DAY);
        int sunTime = SkyClock.normalise(minuteOfDay - lagMinutes);
        int rise = clock.sunriseMinute();
        int set = clock.sunsetMinute();
        if (sunTime < rise || sunTime > set) {
            return SkyClock.NOT_IN_SKY;
        }
        return (sunTime - rise) / (float) (set - rise);
    }

    /** How bright the moon is drawn: full after dark, pale while the sky is light. */
    static float brightnessAt(SkyClock clock, int minuteOfDay) {
        return clock != null && clock.isDark(minuteOfDay) ? 1f : DAYTIME_BRIGHTNESS;
    }
}
