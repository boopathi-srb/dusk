package local.tvhome;

import java.util.Random;

/**
 * A small flock of birds crossing the sky now and then: where each bird is and how its wings
 * are angled at each moment. Positions are fractions of the screen. Pure logic.
 */
final class FlockPath {

    static final long CROSSING_MS = 16_000L;
    static final long FIRST_MIN_MS = 10_000L;
    static final long FIRST_MAX_MS = 25_000L;
    static final long NEXT_MIN_MS = 90_000L;
    static final long NEXT_MAX_MS = 200_000L;
    static final long WING_BEAT_MS = 520L;

    /** Each bird's place in the loose V: offset behind the leader and above or below its line. */
    static final float[][] FORMATION = {
        {0f, 0f}, {0.025f, -0.018f}, {0.025f, 0.018f}, {0.05f, -0.034f}, {0.052f, 0.03f},
    };

    final float startY;
    final float driftY;

    FlockPath(float startY, float driftY) {
        this.startY = startY;
        this.driftY = driftY;
    }

    /** A flock at a random height in the upper sky, drifting slightly up or down as it crosses. */
    static FlockPath random(Random random) {
        return new FlockPath(0.10f + random.nextFloat() * 0.22f, (random.nextFloat() - 0.5f) * 0.08f);
    }

    static long delayMs(Random random, boolean first) {
        long min = first ? FIRST_MIN_MS : NEXT_MIN_MS;
        long max = first ? FIRST_MAX_MS : NEXT_MAX_MS;
        return min + (long) (random.nextDouble() * (max - min));
    }

    /** {x, y} of bird {@code index} at {@code elapsedMs}; the flock enters on the left and leaves on the right. */
    float[] birdAt(int index, long elapsedMs) {
        float progress = Math.max(0f, Math.min(1f, elapsedMs / (float) CROSSING_MS));
        float leaderX = -0.08f + progress * 1.16f;
        float leaderY = startY + driftY * progress + 0.006f * (float) Math.sin(progress * Math.PI * 6);
        float[] slot = FORMATION[Math.floorMod(index, FORMATION.length)];
        return new float[] {leaderX - slot[0], leaderY + slot[1]};
    }

    /** Wing angle −1..1 (down..up), each bird a little out of step with the next. */
    static float wingAt(int index, long elapsedMs) {
        double phase = 2 * Math.PI * (elapsedMs + index * 90L) / WING_BEAT_MS;
        return (float) Math.sin(phase);
    }

    static boolean isOver(long elapsedMs) {
        return elapsedMs >= CROSSING_MS;
    }
}
