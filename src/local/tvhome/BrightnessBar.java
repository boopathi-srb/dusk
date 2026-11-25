package local.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;

/**
 * A slider drawn by hand: left and right on the remote dim or brighten the screen a step at a
 * time. The track brightens while focused, like the other glass controls.
 */
final class BrightnessBar extends View {

    interface Listener {
        void onBrightnessChosen(int value);
    }

    private static final int TRACK_COLOUR = 0x33FFFFFF;
    private static final int TRACK_FOCUSED_COLOUR = 0x55FFFFFF;
    private static final int FILL_COLOUR = 0xFFFFD27A;
    private static final int KNOB_COLOUR = 0xFFFFFFFF;
    private static final float TRACK_HEIGHT_DP = 10f;
    private static final float KNOB_RADIUS_DP = 11f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final Listener listener;
    private int value;

    BrightnessBar(Context context, int startValue, Listener listener) {
        super(context);
        this.listener = listener;
        this.value = ControlRules.clampBrightness(startValue);
        setFocusable(true);
        setContentDescription("Brightness");
    }

    void setValue(int newValue) {
        value = ControlRules.clampBrightness(newValue);
        invalidate();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 0;
        if (direction == 0) {
            return super.onKeyDown(keyCode, event);
        }
        int next = ControlRules.stepBrightness(value, direction);
        if (next != value) {
            setValue(next);
            listener.onBrightnessChosen(next);
        }
        return true;
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, android.graphics.Rect previouslyFocused) {
        super.onFocusChanged(gainFocus, direction, previouslyFocused);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float knob = UiKit.dp(getContext(), KNOB_RADIUS_DP);
        float trackHeight = UiKit.dp(getContext(), TRACK_HEIGHT_DP);
        float middle = getHeight() / 2f;
        track.set(knob, middle - trackHeight / 2f, getWidth() - knob, middle + trackHeight / 2f);
        paint.setColor(isFocused() ? TRACK_FOCUSED_COLOUR : TRACK_COLOUR);
        canvas.drawRoundRect(track, trackHeight / 2f, trackHeight / 2f, paint);
        float knobX = track.left + track.width() * ControlRules.brightnessShare(value);
        paint.setColor(FILL_COLOUR);
        canvas.drawRoundRect(track.left, track.top, knobX, track.bottom, trackHeight / 2f, trackHeight / 2f, paint);
        paint.setColor(KNOB_COLOUR);
        canvas.drawCircle(knobX, middle, isFocused() ? knob : knob * 0.8f, paint);
    }
}
