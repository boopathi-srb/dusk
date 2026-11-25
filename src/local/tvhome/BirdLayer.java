package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Now and then a small flock of birds crosses the morning or afternoon sky. Between flocks the
 * view does nothing; during a crossing it redraws about 30 times a second.
 */
final class BirdLayer extends View {

    private static final long FRAME_MS = 33L;
    private static final long IDLE_CHECK_MS = 1000L;
    private static final float DESIGN_WIDTH = 1600f;
    private static final float WING_SPAN = 11f;
    private static final float WING_LIFT = 5f;
    private static final int BIRD_COLOUR = 0xB3253043;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path wings = new Path();
    private final Random random = new Random();
    private boolean active;
    private FlockPath flock;
    private long flockStartMs;
    private long nextFlockAtMs;

    BirdLayer(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(BIRD_COLOUR);
        setFocusable(false);
    }

    void setActive(boolean fly) {
        if (fly == active) {
            return;
        }
        active = fly;
        flock = null;
        nextFlockAtMs = SystemClock.uptimeMillis() + FlockPath.delayMs(random, true);
        setVisibility(fly ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (flock == null && now >= nextFlockAtMs) {
            flock = FlockPath.random(random);
            flockStartMs = now;
        }
        if (flock == null) {
            postInvalidateDelayed(IDLE_CHECK_MS);
            return;
        }
        long elapsed = now - flockStartMs;
        if (FlockPath.isOver(elapsed)) {
            flock = null;
            nextFlockAtMs = now + FlockPath.delayMs(random, false);
            postInvalidateDelayed(IDLE_CHECK_MS);
            return;
        }
        drawFlock(canvas, elapsed);
        postInvalidateDelayed(FRAME_MS);
    }

    private void drawFlock(Canvas canvas, long elapsed) {
        float scale = getWidth() / DESIGN_WIDTH;
        paint.setStrokeWidth(2f * scale);
        for (int index = 0; index < FlockPath.FORMATION.length; index++) {
            float[] spot = flock.birdAt(index, elapsed);
            float x = spot[0] * getWidth();
            float y = spot[1] * getHeight();
            float lift = FlockPath.wingAt(index, elapsed) * WING_LIFT * scale;
            float span = WING_SPAN * scale;
            wings.reset();
            wings.moveTo(x - span, y - lift);
            wings.quadTo(x - span / 2f, y - lift - span * 0.2f, x, y);
            wings.quadTo(x + span / 2f, y - lift - span * 0.2f, x + span, y - lift);
            canvas.drawPath(wings, paint);
        }
    }
}
