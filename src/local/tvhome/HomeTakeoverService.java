package local.tvhome;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * Brings the home screen forward when Home is pressed.
 *   Fast path: the log watcher sees the Home press line; the sky cover goes up and our
 *              home starts before Amazon's home has drawn.
 *   Fallback:  with no log access, react when Amazon's home window appears (brief flash).
 */
public final class HomeTakeoverService extends AccessibilityService implements LogWatcher.Listener {

    private static final String AMAZON_LAUNCHER = "com.amazon.tv.launcher";
    private static final String AMAZON_HOME = "com.amazon.tv.launcher.ui.HomeActivity_vNext";

    /** Longest the cover may stay up if our home never reports itself drawn. */
    private static final long COVER_TIMEOUT_MS = 3000L;

    /** When the service connects this soon after boot, open our home instead of Amazon's. */
    private static final long JUST_BOOTED_MS = 5L * 60L * 1000L;

    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final Runnable hideCoverTask = this::hideCover;

    private ScreenCover screenCover;
    private LogWatcher logWatcher;

    /** The running service, so the control panel can set the sleep timer; null while it is off. */
    private static volatile HomeTakeoverService running;
    private final Runnable sleepTask = this::turnScreenOff;
    private long sleepAtElapsedMs;

    /**
     * Turns the screen off after {@code minutes} (0 cancels). Lives in this always-running service so
     * the timer keeps going while another app is in front. False (logged) when the service is off.
     */
    static boolean setSleepTimer(int minutes) {
        HomeTakeoverService service = running;
        if (service == null) {
            Log.e(HomeState.LOG_TAG, "HomeTakeoverService.setSleepTimer: Home service not running, minutes=" + minutes);
            return false;
        }
        service.mainThread.post(() -> service.scheduleSleep(minutes));
        return true;
    }

    /** Whole minutes until the screen turns off, rounded up; 0 when no timer is set. */
    static int sleepMinutesLeft() {
        HomeTakeoverService service = running;
        if (service == null || service.sleepAtElapsedMs == 0L) {
            return 0;
        }
        long leftMs = service.sleepAtElapsedMs - SystemClock.elapsedRealtime();
        return leftMs <= 0 ? 0 : (int) ((leftMs + 59_999L) / 60_000L);
    }

    private void scheduleSleep(int minutes) {
        mainThread.removeCallbacks(sleepTask);
        sleepAtElapsedMs = minutes <= 0 ? 0L : SystemClock.elapsedRealtime() + minutes * 60_000L;
        if (minutes > 0) {
            mainThread.postDelayed(sleepTask, minutes * 60_000L);
        }
        Log.i(HomeState.LOG_TAG, "HomeTakeoverService.scheduleSleep: minutes=" + minutes);
    }

    private void turnScreenOff() {
        sleepAtElapsedMs = 0L;
        boolean done = performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
        Log.i(HomeState.LOG_TAG, "HomeTakeoverService.turnScreenOff: screen off requested, accepted=" + done);
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        screenCover = new ScreenCover(this);
        screenCover.attach();
        HomeState.setHomeReadyListener(() -> mainThread.post(this::hideCover));
        startLogWatcherIfPermitted();
        if (SystemClock.elapsedRealtime() < JUST_BOOTED_MS) {
            coverThenShowHome("boot");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        if (HomeState.isLogWatcherRunning()) {
            return;
        }
        boolean amazonHomeAppeared = AMAZON_LAUNCHER.contentEquals(String.valueOf(event.getPackageName()))
                && AMAZON_HOME.contentEquals(String.valueOf(event.getClassName()));
        if (amazonHomeAppeared) {
            Log.i(HomeState.LOG_TAG, "onAccessibilityEvent: Amazon home appeared, fallback redirect");
            showHome("amazon-home-appeared");
        }
    }

    @Override
    public void onInterrupt() {
        Log.w(HomeState.LOG_TAG, "onInterrupt: system interrupted the Home service");
    }

    @Override
    public void onDestroy() {
        running = null;
        mainThread.removeCallbacks(sleepTask);
        HomeState.setHomeReadyListener(null);
        stopLogWatcher();
        mainThread.removeCallbacks(hideCoverTask);
        hideCover();
        if (screenCover != null) {
            screenCover.detach();
        }
        super.onDestroy();
    }

    /** Runs on the watcher thread; the cover's views belong to the main thread. */
    @Override
    public void onHomePress() {
        Log.i(HomeState.LOG_TAG, "onHomePress: Home press seen in the activity log");
        mainThread.post(() -> coverThenShowHome("home-press"));
    }

    @Override
    public void onWatcherStopped(String reason) {
        HomeState.setLogWatcherRunning(false);
        Log.e(HomeState.LOG_TAG, "onWatcherStopped: log watcher ended, reason=" + reason
                + "; falling back to window events");
    }

    private void startLogWatcherIfPermitted() {
        boolean canReadLogs = checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED;
        if (!canReadLogs) {
            Log.w(HomeState.LOG_TAG, "startLogWatcherIfPermitted: READ_LOGS not granted, using window events only");
            return;
        }
        logWatcher = new LogWatcher(this);
        HomeState.setLogWatcherRunning(true);
        new Thread(logWatcher, "home-log-watcher").start();
        Log.i(HomeState.LOG_TAG, "startLogWatcherIfPermitted: READ_LOGS granted, watcher started");
    }

    private void stopLogWatcher() {
        if (logWatcher != null) {
            logWatcher.stop();
            logWatcher = null;
        }
    }

    private void coverThenShowHome(String reason) {
        screenCover.show();
        mainThread.removeCallbacks(hideCoverTask);
        mainThread.postDelayed(hideCoverTask, COVER_TIMEOUT_MS);
        showHome(reason);
    }

    private void hideCover() {
        mainThread.removeCallbacks(hideCoverTask);
        if (screenCover != null) {
            screenCover.hide();
        }
    }

    /** No transition: an animated change would show Amazon's home sliding out under the cover. */
    private void showHome(String reason) {
        Intent home = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(HomeState.EXTRA_LAUNCH_REASON, reason);
        startActivity(home, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
        Log.i(HomeState.LOG_TAG, "showHome: asked system to show home, reason=" + reason);
    }
}
