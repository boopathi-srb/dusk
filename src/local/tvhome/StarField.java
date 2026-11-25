package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * The stars, twinkling, and now and then a shooting star. Stars redraw about 20 times a second,
 * which is plenty for a slow fade; during the one-second flight of a shooting star the view
 * redraws every frame so the streak moves smoothly. Runs only while the home screen is visible
 * and the sky is dark.
 */
final class StarField extends View {

    private static final float DESIGN_WIDTH = 1600f;
    private static final long TWINKLE_FRAME_MS = 50L;
    private static final float COMET_WIDTH = 2.2f;
    private static final float COMET_HEAD_RADIUS = 2.4f;

    private final StarMap stars = StarMap.shared();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private boolean active;
    private Comet comet;
    private long cometStartMs;
    private long nextCometAtMs;

    StarField(Context context) {
        super(context);
        starPaint.setColor(0xFFFFFFFF);
        headPaint.setColor(0xFFFFFFFF);
        tailPaint.setStyle(Paint.Style.STROKE);
        tailPaint.setStrokeCap(Paint.Cap.ROUND);
        setFocusable(false);
    }

    /** Twinkles at {@code level} brightness (0 hides it): fainter at dawn and under cloud. */
    void show(float level) {
        setAlpha(Math.max(0f, Math.min(1f, level)));
        setActive(level > 0f);
    }

    /** Twinkles while true; hidden and idle while false. */
    void setActive(boolean twinkle) {
        if (twinkle == active) {
            return;
        }
        active = twinkle;
        comet = null;
        nextCometAtMs = SystemClock.uptimeMillis() + Comet.delayMs(random, true);
        setVisibility(twinkle ? VISIBLE : GONE);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!active) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        drawStars(canvas, now);
        boolean flying = drawCometIfAny(canvas, now);
        if (flying) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(TWINKLE_FRAME_MS);
        }
    }

    private void drawStars(Canvas canvas, long now) {
        int width = getWidth();
        int height = getHeight();
        float scale = width / DESIGN_WIDTH;
        for (int index = 0; index < StarMap.STAR_COUNT; index++) {
            starPaint.setAlpha(StarMap.twinkleAlpha(stars.alpha[index], stars.periodMs[index], stars.phase[index], now));
            canvas.drawCircle(stars.x[index] * width, stars.y[index] * height, stars.radius[index] * scale, starPaint);
        }
    }

    /** Starts a comet when one is due, draws it while it flies; true while one is in flight. */
    private boolean drawCometIfAny(Canvas canvas, long now) {
        if (comet == null && now >= nextCometAtMs) {
            comet = Comet.random(random);
            cometStartMs = now;
            android.util.Log.i(HomeState.LOG_TAG, "StarField: shooting star from x=" + comet.startX + " y=" + comet.startY);
        }
        if (comet == null) {
            return false;
        }
        long elapsed = now - cometStartMs;
        if (Comet.isOver(elapsed)) {
            comet = null;
            nextCometAtMs = now + Comet.delayMs(random, false);
            return false;
        }
        drawComet(canvas, elapsed);
        return true;
    }

    private void drawComet(Canvas canvas, long elapsed) {
        int width = getWidth();
        int height = getHeight();
        float scale = width / DESIGN_WIDTH;
        float[] head = comet.headAt(elapsed);
        float headX = head[0] * width;
        float headY = head[1] * height;
        float tailX = headX - comet.travelX * Comet.TAIL_SHARE * width;
        float tailY = headY - comet.travelY * Comet.TAIL_SHARE * height;
        int alpha = Math.round(255 * Comet.brightness(elapsed));
        tailPaint.setStrokeWidth(COMET_WIDTH * scale);
        tailPaint.setShader(new LinearGradient(headX, headY, tailX, tailY,
                (alpha << 24) | 0x00FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawLine(tailX, tailY, headX, headY, tailPaint);
        headPaint.setAlpha(alpha);
        canvas.drawCircle(headX, headY, COMET_HEAD_RADIUS * scale, headPaint);
    }
}
