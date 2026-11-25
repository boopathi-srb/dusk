package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

import java.util.Random;

/**
 * Falling rain, heavier or lighter with the weather, and lightning flashes in a storm. Redrawn
 * 25 times a second while it rains, with plain (not smoothed) thin lines: at 30 fps with smoothed
 * lines the TV missed 13% of frames while moving between apps (measured).
 */
final class RainLayer extends View {

    private static final long FRAME_MS = 40L;
    private static final float DESIGN_WIDTH = 1600f;
    private static final int DROP_COLOUR = 0x66D6E2F0;
    private static final int FLASH_ALPHA_MAX = 90;

    private final RainField field = new RainField();
    private final Paint dropPaint = new Paint();
    private final Paint flashPaint = new Paint();
    private final Random random = new Random();
    private int drops;
    private boolean lightning;
    private long nextFlashAtMs;
    private long lastFlashAtMs = -1;

    RainLayer(Context context) {
        super(context);
        dropPaint.setColor(DROP_COLOUR);
        dropPaint.setStrokeCap(Paint.Cap.ROUND);
        flashPaint.setColor(0xFFFFFFFF);
        setFocusable(false);
    }

    void show(float level, boolean withLightning) {
        int count = RainField.dropCount(level);
        boolean wasOff = drops == 0;
        drops = count;
        if (withLightning && !lightning) {
            nextFlashAtMs = SystemClock.uptimeMillis() + RainField.nextFlashGapMs(random) / 3;
        }
        lightning = withLightning;
        setVisibility(count > 0 ? VISIBLE : GONE);
        if (count > 0 && wasOff) {
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (drops == 0) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        int width = getWidth();
        int height = getHeight();
        dropPaint.setStrokeWidth(1.6f * width / DESIGN_WIDTH);
        for (int index = 0; index < drops; index++) {
            float[] head = field.dropAt(index, now);
            float headX = head[0] * width;
            float headY = head[1] * height;
            float tail = field.length[index] * height;
            canvas.drawLine(headX - tail * RainField.SLANT, headY - tail, headX, headY, dropPaint);
        }
        drawLightning(canvas, now, width, height);
        postInvalidateDelayed(FRAME_MS);
    }

    private void drawLightning(Canvas canvas, long now, int width, int height) {
        if (!lightning) {
            return;
        }
        if (now >= nextFlashAtMs) {
            lastFlashAtMs = now;
            nextFlashAtMs = now + RainField.nextFlashGapMs(random);
        }
        float level = lastFlashAtMs < 0 ? 0f : RainField.flashLevel(now - lastFlashAtMs);
        if (level > 0f) {
            flashPaint.setAlpha(Math.round(FLASH_ALPHA_MAX * level));
            canvas.drawRect(0, 0, width, height, flashPaint);
        }
    }
}
