package local.tvhome;

/** Correcting an intended animation length for the TV's animator scale. Pure arithmetic. */
final class MotionTiming {

    private MotionTiming() {
    }

    /**
     * {@code intendedMs} divided by the TV's animator scale, so it looks as long as intended.
     * A scale of 0 means animations are switched off on the TV, which is respected, not divided by.
     */
    static long compensate(long intendedMs, float animatorScale) {
        if (intendedMs <= 0) {
            return 0;
        }
        if (animatorScale <= 0f || Float.isNaN(animatorScale)) {
            return intendedMs;
        }
        return Math.round(intendedMs / animatorScale);
    }
}
