package local.tvhome;

import android.content.Context;
import android.provider.Settings;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * One timing and one curve for every focus movement (tiles, glass, name label), so they always
 * move together.
 *
 * This TV's animator scale is set to 0.5 to make the system menus snappier, which halves every
 * app's animations too (measured: a 240 ms move drew only 8 frames). The home screen divides its
 * own durations by that scale, so its motion keeps the intended length.
 */
final class Motion {

    /** Focus moves: long enough to read as gliding, short enough to keep up with a held arrow key. */
    static final long FOCUS_MS = 320L;
    /** Fading something out: quicker than moving. */
    static final long FADE_MS = 180L;

    /** A gentle ease in and out: starts softly, glides, and settles without a jolt. */
    static final Interpolator GLIDE = new PathInterpolator(0.3f, 0f, 0.15f, 1f);

    private Motion() {
    }

    /** {@code intendedMs} as it should look on screen, corrected for the TV's animator scale. */
    static long duration(Context context, long intendedMs) {
        float scale = Settings.Global.getFloat(context.getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return MotionTiming.compensate(intendedMs, scale);
    }
}
