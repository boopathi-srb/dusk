package local.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.view.View;
import android.view.WindowManager;

/**
 * A full-screen copy of the sky shown the instant Home is pressed, so Amazon's home is
 * never visible while ours comes forward. Attached once and kept hidden: flipping
 * visibility takes one frame, creating the window took about 300 ms (measured on this TV).
 * Uses the accessibility overlay window type, which needs no extra permission.
 * Call only from the main thread.
 */
final class ScreenCover {

    private final WindowManager windowManager;
    private final SkyView coverSky;
    private boolean attached;

    ScreenCover(AccessibilityService service) {
        windowManager = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);
        coverSky = new SkyView(service, HomeSettings.skyClock(service));
        coverSky.setVisibility(View.GONE);
    }

    void attach() {
        if (attached) {
            return;
        }
        windowManager.addView(coverSky, buildFullScreenLayout());
        attached = true;
    }

    void detach() {
        if (!attached) {
            return;
        }
        windowManager.removeView(coverSky);
        attached = false;
    }

    void show() {
        coverSky.setClock(HomeSettings.skyClock(coverSky.getContext()));
        coverSky.setMinuteOfDay(HomeSettings.minuteOfDayNow());
        coverSky.setVisibility(View.VISIBLE);
    }

    void hide() {
        coverSky.setVisibility(View.GONE);
    }

    private static WindowManager.LayoutParams buildFullScreenLayout() {
        int letInputPassThrough = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                letInputPassThrough,
                PixelFormat.OPAQUE);
        layout.windowAnimations = 0;
        return layout;
    }
}
