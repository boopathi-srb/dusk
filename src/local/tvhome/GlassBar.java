package local.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * The frosted glass behind the app row, used as the dock's background. When the dock wave
 * spreads the row, the glass stretches the same amount on each side, so the space between the
 * glass and the outer apps never changes. A background (not a child view) so it never affects
 * the dock's size; it draws past its bounds, which the unclipped parents allow.
 */
final class GlassBar extends Drawable {


    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final float radiusPx;
    private final Context context;
    private float spreadPx;
    private float targetSpreadPx;
    private ValueAnimator stretch;

    GlassBar(Context context, float radiusDp) {
        this.context = context;
        radiusPx = UiKit.dp(context, radiusDp);
        fillPaint.setColor(UiKit.FROSTED_FILL);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(UiKit.FROSTED_STROKE);
        strokePaint.setStrokeWidth(UiKit.dp(context, 1));
    }

    /** Stretches each side out by {@code eachSidePx} (0 = resting width), in step with the tiles. */
    void stretchTo(float eachSidePx) {
        if (eachSidePx == targetSpreadPx) {
            return;
        }
        targetSpreadPx = eachSidePx;
        if (stretch != null) {
            stretch.cancel();
        }
        stretch = ValueAnimator.ofFloat(spreadPx, eachSidePx);
        stretch.setDuration(Motion.duration(context, Motion.FOCUS_MS));
        stretch.setInterpolator(Motion.GLIDE);
        stretch.addUpdateListener(animation -> {
            spreadPx = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        stretch.start();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float halfStroke = strokePaint.getStrokeWidth() / 2f;
        bar.set(bounds.left - spreadPx + halfStroke, bounds.top + halfStroke,
                bounds.right + spreadPx - halfStroke, bounds.bottom - halfStroke);
        canvas.drawRoundRect(bar, radiusPx, radiusPx, fillPaint);
        canvas.drawRoundRect(bar, radiusPx, radiusPx, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
        strokePaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
