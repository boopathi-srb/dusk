package local.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Small shared helpers for building the screens in code. */
final class UiKit {

    static final int FROSTED_FILL = 0x17FFFFFF;
    static final int FROSTED_STROKE = 0x38FFFFFF;
    static final int TEXT_SHADOW = 0x66000000;

    private static final int FROSTED_FOCUSED_FILL = 0x40FFFFFF;
    private static final int FROSTED_FOCUSED_STROKE = 0x99FFFFFF;
    private static final float BUTTON_FOCUS_SCALE = 1.06f;
    private static final long FOCUS_ANIMATION_MS = 140L;

    private UiKit() {
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    static GradientDrawable rounded(Context context, int fillColor, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        shape.setCornerRadius(dp(context, radiusDp));
        if (strokeDp > 0) {
            shape.setStroke(dp(context, strokeDp), strokeColor);
        }
        return shape;
    }

    static GradientDrawable roundedGradient(Context context, int topLeftColor, int bottomRightColor, float radiusDp) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {topLeftColor, bottomRightColor});
        shape.setCornerRadius(dp(context, radiusDp));
        return shape;
    }

    /** Frosted glass at rest, brighter glass while focused: for the Settings pill. */
    static StateListDrawable frostedButton(Context context, float radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused},
                rounded(context, FROSTED_FOCUSED_FILL, radiusDp, FROSTED_FOCUSED_STROKE, 1f));
        states.addState(new int[] {}, rounded(context, FROSTED_FILL, radiusDp, FROSTED_STROKE, 1f));
        return states;
    }

    private static final int ON_FILL = 0x40FFD27A;
    private static final int ON_FOCUSED_FILL = 0x66FFD27A;
    private static final int ON_STROKE = 0xCCFFD27A;

    /** Like {@link #frostedButton}, plus a warm glow while selected ("on" toggles, the current preset). */
    static StateListDrawable toggleButton(Context context, float radiusDp) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_selected, android.R.attr.state_focused},
                rounded(context, ON_FOCUSED_FILL, radiusDp, ON_STROKE, 1.5f));
        states.addState(new int[] {android.R.attr.state_selected}, rounded(context, ON_FILL, radiusDp, ON_STROKE, 1f));
        states.addState(new int[] {android.R.attr.state_focused},
                rounded(context, FROSTED_FOCUSED_FILL, radiusDp, FROSTED_FOCUSED_STROKE, 1f));
        states.addState(new int[] {}, rounded(context, FROSTED_FILL, radiusDp, FROSTED_STROKE, 1f));
        return states;
    }

    /** Grows a lone control a little while it has focus. */
    static void growOnFocus(View view) {
        view.setOnFocusChangeListener((focusedView, hasFocus) -> {
            float scale = hasFocus ? BUTTON_FOCUS_SCALE : 1f;
            focusedView.animate().scaleX(scale).scaleY(scale).setDuration(FOCUS_ANIMATION_MS).start();
        });
    }

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        view.setShadowLayer(dp(context, 4), 0, dp(context, 1), TEXT_SHADOW);
        view.setIncludeFontPadding(false);
        return view;
    }
}
