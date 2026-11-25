package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Soft beams of light around the morning sun, turning very slowly. Redrawn about 20 times a
 * second while active, which is plenty for a turn of a few degrees a second.
 */
final class SunRays extends View {

    private static final long FRAME_MS = 50L;
    private static final int RAY_COUNT = 12;
    /** One full turn every two minutes. */
    private static final float TURN_MS = 120_000f;
    private static final float DESIGN_WIDTH = 1600f;
    private static final float RAY_LENGTH = 340f;
    /** Half the width of a ray's far end, in degrees. */
    private static final float RAY_HALF_ANGLE = 4.5f;
    private static final int RAY_CENTRE = 0x40FFF3DA;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path ray = new Path();
    private float[] sun;
    private boolean active;

    SunRays(Context context) {
        super(context);
        setFocusable(false);
    }

    /** Turns on around {@code sunPoint} (screen pixels); null or false hides the rays. */
    void show(boolean shine, float[] sunPoint) {
        sun = sunPoint;
        boolean next = shine && sunPoint != null;
        if (next == active) {
            return;
        }
        active = next;
        setVisibility(next ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active || sun == null) {
            return;
        }
        float length = RAY_LENGTH * getWidth() / DESIGN_WIDTH;
        paint.setShader(new RadialGradient(sun[0], sun[1], length,
                new int[] {RAY_CENTRE, RAY_CENTRE & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        float turn = 360f * ((SystemClock.uptimeMillis() % (long) TURN_MS) / TURN_MS);
        for (int index = 0; index < RAY_COUNT; index++) {
            double angle = Math.toRadians(turn + index * (360f / RAY_COUNT));
            double spread = Math.toRadians(RAY_HALF_ANGLE);
            ray.reset();
            ray.moveTo(sun[0], sun[1]);
            ray.lineTo(sun[0] + (float) (Math.cos(angle - spread) * length), sun[1] + (float) (Math.sin(angle - spread) * length));
            ray.lineTo(sun[0] + (float) (Math.cos(angle + spread) * length), sun[1] + (float) (Math.sin(angle + spread) * length));
            ray.close();
            canvas.drawPath(ray, paint);
        }
        postInvalidateDelayed(FRAME_MS);
    }
}
