package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Soft shapes drifting slowly left to right: clouds, late-morning wisps, or low morning mist.
 * Each shape is drawn once into its own GPU layer and only slid sideways about 20 times a second;
 * they move a pixel or so per step, so 60 fps would only make the TV redraw the screen for nothing
 * (measured: 60 fps clouds made 99% of frames late).
 */
final class DriftLayer extends FrameLayout {

    /** Draws one shape filling its view. */
    interface Painter {
        void paint(Canvas canvas, float width, float height, Paint paint);
    }

    private static final long STEP_MS = 50L;

    /** Per shape: top as a share of screen height, width in dp, seconds to cross the screen, start position 0..1. */
    private final float[][] specs;
    private final float heightRatio;
    private final Painter painter;
    private final View[] shapes;
    private final Runnable step = this::step;
    private int shown;
    private long startedAtMs;

    DriftLayer(Context context, float[][] specs, float heightRatio, Painter painter) {
        super(context);
        this.specs = specs;
        this.heightRatio = heightRatio;
        this.painter = painter;
        this.shapes = new View[specs.length];
        setClipChildren(false);
        setFocusable(false);
        setVisibility(GONE);
    }

    /** Shows the first {@code count} shapes (0 hides the layer) at {@code level} opacity. */
    void show(int count, float level) {
        int wanted = Math.max(0, Math.min(specs.length, count));
        setAlpha(Math.max(0f, Math.min(1f, level)));
        if (wanted == shown) {
            return;
        }
        shown = wanted;
        removeCallbacks(step);
        removeAllViews();
        if (wanted == 0) {
            setVisibility(GONE);
            return;
        }
        build(wanted);
        startedAtMs = SystemClock.uptimeMillis();
        setVisibility(VISIBLE);
        step();
    }

    private void build(int count) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        for (int index = 0; index < shapes.length; index++) {
            shapes[index] = null;
        }
        for (int index = 0; index < count; index++) {
            int width = UiKit.dp(getContext(), specs[index][1]);
            Shape shape = new Shape(getContext(), painter);
            shape.setLayerType(LAYER_TYPE_HARDWARE, null);
            LayoutParams placement = new LayoutParams(width, Math.max(1, Math.round(width * heightRatio)));
            placement.topMargin = Math.round(screenHeight * specs[index][0]);
            addView(shape, placement);
            shapes[index] = shape;
        }
    }

    private void step() {
        if (shown == 0) {
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        long elapsedMs = SystemClock.uptimeMillis() - startedAtMs;
        for (int index = 0; index < shown; index++) {
            View shape = shapes[index];
            int shapeWidth = shape.getLayoutParams().width;
            float crossMs = specs[index][2] * 1000f;
            float travelled = (specs[index][3] + elapsedMs / crossMs) % 1f;
            shape.setTranslationX(-shapeWidth + travelled * (screenWidth + shapeWidth));
        }
        postDelayed(step, STEP_MS);
    }

    @Override
    protected void onDetachedFromWindow() {
        show(0, 0f);
        super.onDetachedFromWindow();
    }

    private static final class Shape extends View {
        private final Painter painter;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Shape(Context context, Painter painter) {
            super(context);
            this.painter = painter;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            painter.paint(canvas, getWidth(), getHeight(), paint);
        }
    }

    // ---- The three kinds of shape ----

    static final float[][] CLOUD_SPECS = {
        {0.03f, 170f, 210f, 0.15f},
        {0.13f, 120f, 170f, 0.62f},
        {0.22f, 200f, 260f, 0.85f},
        {0.07f, 100f, 150f, 0.40f},
    };
    static final float CLOUD_HEIGHT_RATIO = 0.45f;

    static final float[][] WISP_SPECS = {
        {0.05f, 320f, 240f, 0.30f},
        {0.17f, 260f, 200f, 0.75f},
    };
    static final float WISP_HEIGHT_RATIO = 0.16f;

    /** Low bands sitting just above and behind the apps. */
    static final float[][] MIST_SPECS = {
        {0.44f, 760f, 320f, 0.10f},
        {0.50f, 640f, 260f, 0.55f},
        {0.55f, 820f, 380f, 0.80f},
    };
    static final float MIST_HEIGHT_RATIO = 0.14f;

    /** A few overlapping puffs that fade to clear at their edges. */
    static void paintCloud(Canvas canvas, float width, float height, Paint paint) {
        float[][] puffs = {{0.22f, 0.62f, 0.42f}, {0.42f, 0.45f, 0.55f}, {0.62f, 0.52f, 0.50f}, {0.80f, 0.65f, 0.36f}};
        for (float[] puff : puffs) {
            softCircle(canvas, width * puff[0], height * puff[1], height * puff[2], 0x8CFFFFFF, paint);
        }
    }

    /** A long, thin streak of high cloud. */
    static void paintWisp(Canvas canvas, float width, float height, Paint paint) {
        for (int index = 0; index < 6; index++) {
            float share = 0.12f + index * 0.15f;
            softCircle(canvas, width * share, height * (0.5f + (index % 2 == 0 ? -0.08f : 0.08f)), height * 0.75f, 0x40FFFFFF, paint);
        }
    }

    /** A wide, very soft band of mist, clearest at its ends. */
    static void paintMist(Canvas canvas, float width, float height, Paint paint) {
        paint.setShader(new LinearGradient(0, 0, width, 0,
                new int[] {0x00FFFFFF, 0x55FFFFFF, 0x66FFFFFF, 0x55FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(0, 0, width, height, height / 2f, height / 2f, paint);
        paint.setShader(null);
    }

    private static void softCircle(Canvas canvas, float centreX, float centreY, float radius, int centreColour, Paint paint) {
        paint.setShader(new RadialGradient(centreX, centreY, radius,
                new int[] {centreColour, centreColour & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(centreX, centreY, radius, paint);
        paint.setShader(null);
    }
}
