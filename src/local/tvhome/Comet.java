package local.tvhome;

import java.util.Random;

/**
 * One shooting star: where it starts, where it goes, and how bright it is at each moment of its
 * short flight. Positions are fractions of the screen. Pure logic.
 */
final class Comet {

    static final long FLIGHT_MS = 1100L;
    /** The first one comes soon after the screen opens, so it gets noticed; later ones are rarer. */
    static final long FIRST_MIN_MS = 6_000L;
    static final long FIRST_MAX_MS = 15_000L;
    static final long NEXT_MIN_MS = 35_000L;
    static final long NEXT_MAX_MS = 80_000L;
    /** The streak behind the head, as a share of the distance travelled. */
    static final float TAIL_SHARE = 0.45f;

    private static final float FADE_IN_SHARE = 0.15f;
    private static final float FADE_OUT_SHARE = 0.35f;

    final float startX;
    final float startY;
    final float travelX;
    final float travelY;

    Comet(float startX, float startY, float travelX, float travelY) {
        this.startX = startX;
        this.startY = startY;
        this.travelX = travelX;
        this.travelY = travelY;
    }

    /** A comet high on the right, falling down and to the left at a shallow angle. */
    static Comet random(Random random) {
        float startX = 0.40f + random.nextFloat() * 0.55f;
        float startY = 0.03f + random.nextFloat() * 0.25f;
        float travelX = -(0.22f + random.nextFloat() * 0.12f);
        float travelY = 0.08f + random.nextFloat() * 0.08f;
        return new Comet(startX, startY, travelX, travelY);
    }

    static long delayMs(Random random, boolean first) {
        long min = first ? FIRST_MIN_MS : NEXT_MIN_MS;
        long max = first ? FIRST_MAX_MS : NEXT_MAX_MS;
        return min + (long) (random.nextDouble() * (max - min));
    }

    /** Share of the flight done, 0..1; it speeds up slightly, like something falling. */
    static float progress(long elapsedMs) {
        float linear = clamp01(elapsedMs / (float) FLIGHT_MS);
        return linear * (0.6f + 0.4f * linear);
    }

    /** {x, y} of the head at {@code elapsedMs}. */
    float[] headAt(long elapsedMs) {
        float done = progress(elapsedMs);
        return new float[] {startX + travelX * done, startY + travelY * done};
    }

    /** Brightness 0..1: fades in quickly, holds, then fades out before the end. */
    static float brightness(long elapsedMs) {
        float linear = clamp01(elapsedMs / (float) FLIGHT_MS);
        if (linear < FADE_IN_SHARE) {
            return linear / FADE_IN_SHARE;
        }
        float fadeOutFrom = 1f - FADE_OUT_SHARE;
        return linear > fadeOutFrom ? (1f - linear) / FADE_OUT_SHARE : 1f;
    }

    static boolean isOver(long elapsedMs) {
        return elapsedMs >= FLIGHT_MS;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value) || value < 0f) {
            return 0f;
        }
        return Math.min(1f, value);
    }
}
