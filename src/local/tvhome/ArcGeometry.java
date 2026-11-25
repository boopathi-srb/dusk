package local.tvhome;

/**
 * Where things sit on screen, as fractions of the screen size, so the layout is the same
 * at any resolution. The sun and moon travel along the top half of an ellipse whose
 * flat side is the horizon. Pure logic, no Android types.
 */
final class ArcGeometry {

    static final float HORIZON_Y = 0.56f;
    static final float CENTRE_X = 0.5f;
    static final float RADIUS_X = 0.3875f;
    static final float RADIUS_Y = 0.38f;

    private ArcGeometry() {
    }

    /** {x, y} in pixels for a progress of 0 (left end) to 1 (right end); out-of-range values are clamped. */
    static float[] pointAt(float progress, int width, int height) {
        double angle = Math.PI * (1.0 - clamp01(progress));
        float x = (float) (width * (CENTRE_X + RADIUS_X * Math.cos(angle)));
        float y = (float) (height * (HORIZON_Y - RADIUS_Y * Math.sin(angle)));
        return new float[] {x, y};
    }

    static float clamp01(float value) {
        if (Float.isNaN(value) || value < 0f) {
            return 0f;
        }
        return Math.min(value, 1f);
    }
}
