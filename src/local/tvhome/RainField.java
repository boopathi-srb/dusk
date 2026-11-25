package local.tvhome;

import java.util.Random;

/**
 * Where each raindrop is at a moment, and when lightning flashes. Drops fall at a slight slant
 * and wrap from the bottom back to the top. Positions are fractions of the screen. Pure logic.
 */
final class RainField {

    static final int MAX_DROPS = 150;
    /** Light rain still shows this many; heavy rain shows them all. */
    static final int MIN_DROPS = 40;
    private static final long SEED = 31337L;
    /** Rightward drift per unit of fall: rain falls at a slight slant. */
    static final float SLANT = 0.12f;

    static final long FLASH_MS = 420L;
    static final long FLASH_MIN_GAP_MS = 12_000L;
    static final long FLASH_MAX_GAP_MS = 40_000L;

    final float[] x = new float[MAX_DROPS];
    final float[] y = new float[MAX_DROPS];
    /** Screen heights per second. */
    final float[] speed = new float[MAX_DROPS];
    /** Streak length as a share of screen height. */
    final float[] length = new float[MAX_DROPS];

    RainField() {
        Random random = new Random(SEED);
        for (int index = 0; index < MAX_DROPS; index++) {
            x[index] = random.nextFloat() * 1.1f - 0.1f;
            y[index] = random.nextFloat();
            speed[index] = 0.9f + random.nextFloat() * 0.7f;
            length[index] = 0.025f + random.nextFloat() * 0.03f;
        }
    }

    static int dropCount(float level) {
        if (level <= 0f || Float.isNaN(level)) {
            return 0;
        }
        float clamped = Math.min(1f, level);
        return Math.round(MIN_DROPS + (MAX_DROPS - MIN_DROPS) * clamped);
    }

    /** {x, y} of the head of drop {@code index} at {@code timeMs}, wrapped into the screen. */
    float[] dropAt(int index, long timeMs) {
        float fallen = y[index] + speed[index] * (timeMs / 1000f);
        float wrapped = fallen - (float) Math.floor(fallen);
        return new float[] {x[index] + SLANT * wrapped, wrapped};
    }

    /** Flash brightness 0..1 for {@code sinceFlashMs} after a strike: a sharp flash with a flicker. */
    static float flashLevel(long sinceFlashMs) {
        if (sinceFlashMs < 0 || sinceFlashMs >= FLASH_MS) {
            return 0f;
        }
        float share = sinceFlashMs / (float) FLASH_MS;
        float fade = 1f - share;
        boolean flickerGap = share > 0.18f && share < 0.3f;
        return flickerGap ? fade * 0.25f : fade;
    }

    static long nextFlashGapMs(Random random) {
        return FLASH_MIN_GAP_MS + (long) (random.nextDouble() * (FLASH_MAX_GAP_MS - FLASH_MIN_GAP_MS));
    }
}
