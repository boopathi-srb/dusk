package local.tvhome;

/**
 * The colour of the sun or moon on the arc, by time of day: an orange sun at sunrise that whitens
 * by noon, a white afternoon sun that warms through orange to red over the last 90 minutes, a red sun
 * at dusk, and a dimmer white moon once it is dark.
 * Pure logic (ARGB ints).
 */
final class BodyLook {

    private static final int SUNRISE_ORANGE = 0xFFFF9A3C;
    private static final int MIDDAY_WHITE = 0xFFFFF8EC;
    private static final int DUSK_RED = 0xFFFF4B2B;
    private static final int MOON_WHITE = 0xFFE3E7EF;

    private static final int SUNRISE_GLOW = 0xE6FF8A33;
    private static final int MIDDAY_GLOW = 0xF2FFFBEE;
    private static final int DUSK_GLOW = 0xE6FF5A33;
    /** The sun warms from white to red over this long before sunset. */
    static final int GOLDEN_MINUTES = 90;
    /** The moon glows faintly. */
    private static final int MOON_GLOW = 0x4DE4E8F0;

    /** The moon's glow is smaller than the sun's. */
    private static final float MOON_GLOW_SCALE = 0.6f;

    final int body;
    final int glowCore;
    /** The glow's edge: the core colour, fully transparent. */
    final int glowEdge;
    final float glowScale;

    private BodyLook(int body, int glowCore, float glowScale) {
        this.body = body;
        this.glowCore = glowCore;
        this.glowEdge = glowCore & 0x00FFFFFF;
        this.glowScale = glowScale;
    }

    /** The moon's colours, dimmed by {@code brightness} (1 at night, pale by day). */
    static BodyLook moon(float brightness) {
        float share = Math.max(0f, Math.min(1f, brightness));
        int bodyAlpha = Math.round(0xFF * share);
        int glowAlpha = Math.round(((MOON_GLOW >>> 24) & 0xFF) * share);
        return new BodyLook((MOON_WHITE & 0x00FFFFFF) | bodyAlpha << 24, (MOON_GLOW & 0x00FFFFFF) | glowAlpha << 24,
                MOON_GLOW_SCALE);
    }

    static BodyLook at(SkyClock clock, int minuteOfDay) {
        if (clock == null || clock.isDark(minuteOfDay)) {
            return new BodyLook(MOON_WHITE, MOON_GLOW, MOON_GLOW_SCALE);
        }
        int minute = SkyClock.normalise(minuteOfDay);
        switch (clock.phaseAt(minute)) {
            case MORNING:
                int sunrise = clock.sunriseMinute();
                float towardsNoon = (minute - sunrise) / (float) (SkyClock.NOON - sunrise);
                return new BodyLook(blend(SUNRISE_ORANGE, MIDDAY_WHITE, towardsNoon),
                        blend(SUNRISE_GLOW, MIDDAY_GLOW, towardsNoon), 1f);
            case AFTERNOON:
                return goldenHour(minute - (clock.sunsetMinute() - GOLDEN_MINUTES));
            default:
                return new BodyLook(DUSK_RED, DUSK_GLOW, 1f);
        }
    }

    /**
     * The afternoon sun: white until golden hour, then warming to orange and on to red by sunset,
     * so it is already the dusk red when the sun goes down.
     */
    private static BodyLook goldenHour(int minutesIntoGolden) {
        if (minutesIntoGolden <= 0) {
            return new BodyLook(MIDDAY_WHITE, MIDDAY_GLOW, 1f);
        }
        float warmth = Math.min(1f, minutesIntoGolden / (float) GOLDEN_MINUTES);
        if (warmth < 0.5f) {
            return new BodyLook(blend(MIDDAY_WHITE, SUNRISE_ORANGE, warmth * 2f), blend(MIDDAY_GLOW, SUNRISE_GLOW, warmth * 2f), 1f);
        }
        return new BodyLook(blend(SUNRISE_ORANGE, DUSK_RED, (warmth - 0.5f) * 2f), blend(SUNRISE_GLOW, DUSK_GLOW, (warmth - 0.5f) * 2f), 1f);
    }

    /** Mixes two ARGB colours channel by channel; {@code amount} 0 = {@code from}, 1 = {@code to}. */
    static int blend(int from, int to, float amount) {
        float share = Float.isNaN(amount) ? 0f : Math.max(0f, Math.min(1f, amount));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int start = (from >>> shift) & 0xFF;
            int end = (to >>> shift) & 0xFF;
            result |= Math.round(start + (end - start) * share) << shift;
        }
        return result;
    }
}
