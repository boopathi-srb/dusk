package local.tvhome;

import java.util.Random;

/**
 * Where the stars are and how each one twinkles. The same seed always gives the same sky, so
 * the home screen, the Home-press cover and All apps all show identical stars. Pure logic.
 */
final class StarMap {

    static final int STAR_COUNT = 140;
    private static final long SEED = 20261125L;
    /** Below this line (the apps and Continue watching) stars are fainter, a background shimmer. */
    static final float LOWER_SKY_START = 0.55f;
    private static final float LOWER_SKY_DIMMING = 0.35f;

    /** Star sizes in design units: big enough that the twinkle reads from the sofa. */
    static final float SMALLEST_RADIUS = 1.4f;
    static final float LARGEST_RADIUS = 3.4f;

    /** One full twinkle takes between these many milliseconds, different for each star. */
    private static final float SLOWEST_TWINKLE_MS = 5200f;
    private static final float FASTEST_TWINKLE_MS = 1800f;
    /** How dim a star gets at the bottom of its twinkle, as a share of its brightness. */
    private static final float DIMMEST = 0.25f;

    /** Positions as fractions of the screen; radius in design units; alpha 0..255. */
    final float[] x = new float[STAR_COUNT];
    final float[] y = new float[STAR_COUNT];
    final float[] radius = new float[STAR_COUNT];
    final int[] alpha = new int[STAR_COUNT];
    final float[] periodMs = new float[STAR_COUNT];
    final float[] phase = new float[STAR_COUNT];

    private static final StarMap SHARED = new StarMap();

    private StarMap() {
        Random random = new Random(SEED);
        for (int index = 0; index < STAR_COUNT; index++) {
            x[index] = random.nextFloat();
            y[index] = random.nextFloat();
            radius[index] = SMALLEST_RADIUS + random.nextFloat() * (LARGEST_RADIUS - SMALLEST_RADIUS);
            int brightness = 110 + random.nextInt(146);
            alpha[index] = y[index] < LOWER_SKY_START ? brightness : Math.round(brightness * LOWER_SKY_DIMMING);
            periodMs[index] = FASTEST_TWINKLE_MS + random.nextFloat() * (SLOWEST_TWINKLE_MS - FASTEST_TWINKLE_MS);
            phase[index] = random.nextFloat() * (float) (2 * Math.PI);
        }
    }

    static StarMap shared() {
        return SHARED;
    }

    /**
     * A star's brightness at {@code timeMs}: a slow sine between {@link #DIMMEST} and full,
     * so stars fade in and out softly rather than blinking.
     */
    static int twinkleAlpha(int baseAlpha, float periodMs, float phase, long timeMs) {
        if (periodMs <= 0f) {
            return clampAlpha(baseAlpha);
        }
        double wave = Math.sin(2 * Math.PI * (timeMs % (long) periodMs) / periodMs + phase);
        float brightness = DIMMEST + (1f - DIMMEST) * (float) ((wave + 1) / 2);
        return clampAlpha(Math.round(baseAlpha * brightness));
    }

    private static int clampAlpha(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
