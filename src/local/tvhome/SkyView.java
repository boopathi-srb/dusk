package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The drawn sky: colours for the time of day, still stars after dark, and a dotted arc with the
 * sun and the real moon on it (both, when both are up; the moon in its true phase). Redrawn only when the minute changes. The home screen turns the
 * still stars off and lays a twinkling {@link StarField} on top instead.
 */
final class SkyView extends View {

    /** The design was drawn 1600 units wide; line widths and dot sizes scale from it. */
    private static final float DESIGN_WIDTH = 1600f;
    private static final float GLOW_RADIUS = 95f;
    private static final float BODY_RADIUS = 20f;

    private SkyClock clock;
    private final StarMap stars = StarMap.shared();
    private final Paint skyPaint = new Paint();
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arcPath = new Path();
    private final Paint moonDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path moonLit = new Path();
    private final RectF moonDisc = new RectF();
    private final RectF moonTerminator = new RectF();
    /** The unlit part of the moon shows faintly, so its round shape still reads. */
    private static final int MOON_DARK_SIDE = 0x1AE3E7EF;

    private SkyPalette palette;
    private int minuteOfDay = -1;
    private boolean drawStillStars = true;
    private float moonPhase = MoonRules.phaseAt(System.currentTimeMillis());
    /** How much the cloud lets the sun and moon through: 1 clear, 0 hidden. */
    private float bodyLevel = 1f;

    SkyView(Context context, SkyClock clock) {
        super(context);
        this.clock = clock;
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        starPaint.setColor(0xFFFFFFFF);
        moonDarkPaint.setColor(MOON_DARK_SIDE);
        setMinuteOfDay(HomeSettings.minuteOfDayNow());
    }

    /** Cheap to call every minute: only redraws when the minute really changed. */
    void setMinuteOfDay(int minute) {
        int normalised = SkyClock.normalise(minute);
        if (normalised == minuteOfDay) {
            return;
        }
        minuteOfDay = normalised;
        SkyPalette next = SkyPalette.forMinute(clock, normalised);
        if (next != palette) {
            palette = next;
            applyPalette();
        }
        invalidate();
    }

    /** How much of the sun or moon shows through the cloud (from the weather): 1 clear, 0 hidden. */
    void setBodyLevel(float level) {
        float clamped = Math.max(0f, Math.min(1f, level));
        if (Math.abs(clamped - bodyLevel) < 0.01f) {
            return;
        }
        bodyLevel = clamped;
        invalidate();
    }

    /** The moon's phase for the date being shown (today, or the previewed day). */
    void setMoonPhase(float phase) {
        if (Math.abs(phase - moonPhase) < 0.001f) {
            return;
        }
        moonPhase = phase;
        invalidate();
    }

    /** New sun times (from a weather reading): recolours and moves the sun or moon at once. */
    void setClock(SkyClock newClock) {
        clock = newClock;
        int minute = minuteOfDay;
        minuteOfDay = -1;
        palette = null;
        setMinuteOfDay(minute);
    }

    SkyClock clock() {
        return clock;
    }

    /** Where the sun is on screen right now, or null when it is not up (for the sun rays). */
    float[] sunPoint() {
        float progress = clock.sunProgressAt(minuteOfDay);
        return progress == SkyClock.NOT_IN_SKY || getWidth() == 0 ? null : ArcGeometry.pointAt(progress, getWidth(), getHeight());
    }

    /** Off when a twinkling {@link StarField} draws the stars instead. */
    void setDrawStillStars(boolean draw) {
        drawStillStars = draw;
        invalidate();
    }

    SkyPalette palette() {
        return palette;
    }

    SkyClock.Phase phase() {
        return clock.phaseAt(minuteOfDay);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        applyPalette();
        buildArcPath(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        canvas.drawRect(0, 0, width, height, skyPaint);
        if (drawStillStars && palette.showStars) {
            drawStars(canvas, width, height);
        }
        canvas.drawPath(arcPath, arcPaint);
        drawMoon(canvas, width, height);
        drawSun(canvas, width, height);
    }

    private void applyPalette() {
        if (palette == null) {
            return;
        }
        float scale = getWidth() / DESIGN_WIDTH;
        skyPaint.setShader(new LinearGradient(0, 0, 0, Math.max(1, getHeight()),
                palette.skyColors, SkyPalette.SKY_STOPS, Shader.TileMode.CLAMP));
        arcPaint.setColor(palette.lineColor);
        arcPaint.setStrokeWidth(2.5f * scale);
        arcPaint.setPathEffect(new DashPathEffect(new float[] {3f * scale, 9f * scale}, 0));
    }

    private void buildArcPath(int width, int height) {
        float centreX = width * ArcGeometry.CENTRE_X;
        float horizonY = height * ArcGeometry.HORIZON_Y;
        float radiusX = width * ArcGeometry.RADIUS_X;
        float radiusY = height * ArcGeometry.RADIUS_Y;
        RectF oval = new RectF(centreX - radiusX, horizonY - radiusY, centreX + radiusX, horizonY + radiusY);
        arcPath.reset();
        arcPath.addArc(oval, 180f, 180f);
    }

    private void drawStars(Canvas canvas, int width, int height) {
        float scale = width / DESIGN_WIDTH;
        for (int index = 0; index < StarMap.STAR_COUNT; index++) {
            starPaint.setAlpha(stars.alpha[index]);
            canvas.drawCircle(stars.x[index] * width, stars.y[index] * height, stars.radius[index] * scale, starPaint);
        }
    }

    private void drawSun(Canvas canvas, int width, int height) {
        float progress = clock.sunProgressAt(minuteOfDay);
        if (progress == SkyClock.NOT_IN_SKY || bodyLevel <= 0f) {
            return;
        }
        float[] point = ArcGeometry.pointAt(progress, width, height);
        BodyLook look = BodyLook.at(clock, minuteOfDay).dimmed(bodyLevel);
        drawGlow(canvas, point, look, width);
        bodyPaint.setColor(look.body);
        canvas.drawCircle(point[0], point[1], BODY_RADIUS * width / DESIGN_WIDTH, bodyPaint);
    }

    private void drawMoon(Canvas canvas, int width, int height) {
        float progress = MoonRules.progressAt(clock, minuteOfDay, moonPhase);
        if (progress == SkyClock.NOT_IN_SKY || bodyLevel <= 0f) {
            return;
        }
        float[] point = ArcGeometry.pointAt(progress, width, height);
        float brightness = MoonRules.brightnessAt(clock, minuteOfDay) * bodyLevel;
        BodyLook look = BodyLook.moon(brightness * MoonRules.litShare(moonPhase));
        drawGlow(canvas, point, look, width);
        float radius = BODY_RADIUS * width / DESIGN_WIDTH;
        moonDisc.set(point[0] - radius, point[1] - radius, point[0] + radius, point[1] + radius);
        moonDarkPaint.setAlpha(Math.round(((MOON_DARK_SIDE >>> 24) & 0xFF) * bodyLevel));
        canvas.drawOval(moonDisc, moonDarkPaint);
        bodyPaint.setColor(BodyLook.moon(brightness).body);
        canvas.drawPath(litPart(point, radius), bodyPaint);
    }

    /**
     * The lit part of the moon: half the disc on the lit side, closed by the terminator, an ellipse
     * whose width follows the phase (bulging into the lit half for a crescent, out of it when gibbous).
     */
    private Path litPart(float[] centre, float radius) {
        float terminatorWidth = radius * (float) Math.abs(Math.cos(2 * Math.PI * moonPhase));
        moonTerminator.set(centre[0] - terminatorWidth, centre[1] - radius, centre[0] + terminatorWidth, centre[1] + radius);
        boolean crescent = MoonRules.isCrescent(moonPhase);
        moonLit.reset();
        if (MoonRules.isWaxing(moonPhase)) {
            moonLit.arcTo(moonDisc, -90f, 180f, true);
            moonLit.arcTo(moonTerminator, 90f, crescent ? -180f : 180f);
        } else {
            moonLit.arcTo(moonDisc, 90f, 180f, true);
            moonLit.arcTo(moonTerminator, -90f, crescent ? -180f : 180f);
        }
        moonLit.close();
        return moonLit;
    }

    private void drawGlow(Canvas canvas, float[] point, BodyLook look, int width) {
        float glowRadius = GLOW_RADIUS * look.glowScale * width / DESIGN_WIDTH;
        glowPaint.setShader(new RadialGradient(point[0], point[1], glowRadius,
                new int[] {look.glowCore, look.glowEdge}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(point[0], point[1], glowRadius, glowPaint);
    }
}
