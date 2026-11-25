package local.tvhome;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * What the control panel reads from and changes on the TV: screen brightness, Wi-Fi status, the
 * app now playing, and Fire TV's Settings pages. Every refusal is logged.
 */
final class SystemControls {

    /** Fire TV's own name for its "Controllers and Bluetooth devices" page. */
    static final String ACTION_REMOTES_AND_BLUETOOTH = "android.settings.CONTROLLERS_SETTINGS";
    private static final int UNKNOWN_BRIGHTNESS = 128;
    /** Android closes apps a moment after being asked; measure memory after it has. */
    private static final long SETTLE_AFTER_CLOSING_MS = 1500L;
    /** Fire TV's main Settings app (its picture and sound pages are closed to other apps). */
    static final String FIRE_TV_SETTINGS = "com.amazon.tv.settings.v2";
    private static final ComponentName SCREENSAVER_NOW = new ComponentName("com.android.systemui",
            "com.android.systemui.Somnambulator");

    private SystemControls() {
    }

    static int readBrightness(Context context) {
        return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, UNKNOWN_BRIGHTNESS);
    }

    /** Needs "modify system settings", granted once over adb (see tv.sh); logs and returns false without it. */
    static boolean writeBrightness(Context context, int value) {
        if (!Settings.System.canWrite(context)) {
            Log.e(HomeState.LOG_TAG, "SystemControls.writeBrightness: not allowed to change system settings, value=" + value
                    + " (run ./tv.sh on to grant WRITE_SETTINGS)");
            return false;
        }
        int clamped = ControlRules.clampBrightness(value);
        boolean saved = Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, clamped);
        if (!saved) {
            Log.e(HomeState.LOG_TAG, "SystemControls.writeBrightness: TV refused value=" + clamped);
        }
        return saved;
    }

    static boolean isAdbOn(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
    }

    /**
     * Turns ADB debugging on or off. Needs WRITE_SECURE_SETTINGS, granted once over adb (see tv.sh);
     * without it Android refuses by throwing, which this logs and reports as false.
     */
    static boolean setAdb(Context context, boolean on) {
        try {
            boolean saved = Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, on ? 1 : 0);
            Log.i(HomeState.LOG_TAG, "SystemControls.setAdb: on=" + on + " saved=" + saved);
            return saved;
        } catch (SecurityException refused) {
            Log.e(HomeState.LOG_TAG, "SystemControls.setAdb: not allowed, on=" + on
                    + " (run ./tv.sh on to grant WRITE_SECURE_SETTINGS)", refused);
            return false;
        }
    }

    /**
     * Closes the background processes of every app on the home screen's app list and reports how
     * much memory came back. Apps in front or playing are left alone by Android. Call off the main thread.
     */
    static String freeMemory(Context context) {
        ActivityManager activities = context.getSystemService(ActivityManager.class);
        if (activities == null) {
            Log.e(HomeState.LOG_TAG, "SystemControls.freeMemory: no activity manager");
            return ControlRules.freedLabel(0, 0);
        }
        long before = availableMemory(activities);
        int closed = 0;
        for (String packageName : launchablePackages(context)) {
            if (!packageName.equals(context.getPackageName())) {
                activities.killBackgroundProcesses(packageName);
                closed++;
            }
        }
        SystemClock.sleep(SETTLE_AFTER_CLOSING_MS);
        long after = availableMemory(activities);
        Log.i(HomeState.LOG_TAG, "SystemControls.freeMemory: apps=" + closed + " availableBefore=" + before + " after=" + after);
        return ControlRules.freedLabel(before, after);
    }

    private static long availableMemory(ActivityManager activities) {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        activities.getMemoryInfo(memory);
        return memory.availMem;
    }

    private static List<String> launchablePackages(Context context) {
        PackageManager packages = context.getPackageManager();
        List<String> names = new ArrayList<>();
        Intent tvApps = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Intent phoneApps = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (Intent kind : new Intent[] {tvApps, phoneApps}) {
            for (android.content.pm.ResolveInfo app : packages.queryIntentActivities(kind, 0)) {
                String name = app.activityInfo.packageName;
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /** Android's own "start the screensaver now" screen; Fire TV then shows its usual screensaver. */
    static void startScreensaver(Context context) {
        Intent now = new Intent(Intent.ACTION_MAIN).setComponent(SCREENSAVER_NOW);
        if (!Launch.start(context, now, "screensaver")) {
            Log.e(HomeState.LOG_TAG, "SystemControls.startScreensaver: this TV has no screensaver starter");
        }
    }

    /** The connected Wi-Fi network's name, or null (Android shares it only with location access, see tv.sh). */
    static String wifiNetworkName(Context context) {
        if (!isOnWifi(context)) {
            return null;
        }
        WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
        return wifi == null ? null : ControlRules.networkName(wifi.getConnectionInfo().getSSID());
    }

    static boolean isOnWifi(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities network = connectivity == null ? null
                : connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        return network != null && network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
    }

    static String wifiLabel(Context context) {
        boolean onWifi = isOnWifi(context);
        WifiManager wifi = context.getApplicationContext().getSystemService(WifiManager.class);
        int rssi = onWifi && wifi != null ? wifi.getConnectionInfo().getRssi() : Integer.MIN_VALUE;
        return ControlRules.wifiLabel(onWifi, rssi);
    }

    /** The app playing (or paused) right now, or null; reading sessions needs the Continue watching listener's access. */
    static MediaController nowPlaying(Context context) {
        List<MediaController> sessions = nowPlayingCandidates(context);
        int[] states = new int[sessions.size()];
        boolean[] hasTitle = new boolean[sessions.size()];
        for (int index = 0; index < sessions.size(); index++) {
            PlaybackState state = sessions.get(index).getPlaybackState();
            states[index] = state == null ? PlaybackState.STATE_NONE : state.getState();
            hasTitle[index] = titleOf(sessions.get(index)) != null;
        }
        int picked = ControlRules.pickSession(states, hasTitle);
        return picked == ControlRules.NO_SESSION ? null : sessions.get(picked);
    }

    static String titleOf(MediaController controller) {
        MediaMetadata metadata = controller.getMetadata();
        String title = metadata == null ? null : metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        return title == null || title.trim().isEmpty() ? null : title;
    }

    static int stateOf(MediaController controller) {
        PlaybackState state = controller.getPlaybackState();
        return state == null ? PlaybackState.STATE_NONE : state.getState();
    }

    static void togglePlayback(MediaController controller) {
        boolean playing = stateOf(controller) == PlaybackState.STATE_PLAYING;
        if (playing) {
            controller.getTransportControls().pause();
        } else {
            controller.getTransportControls().play();
        }
        Log.i(HomeState.LOG_TAG, "SystemControls.togglePlayback: package=" + controller.getPackageName()
                + " now=" + (playing ? "paused" : "playing"));
    }

    /**
     * Opens a page in the named Fire TV Settings app (named, so Android never asks "open with"),
     * falling back to the main Settings when this TV has no such page or refuses it.
     */
    static void openPage(Context context, String action, String settingsApp, String what) {
        Intent page = new Intent(action).setPackage(settingsApp);
        if (!Launch.start(context, page, what)) {
            Launch.openSettings(context);
        }
    }

    /** Asking for sessions without notification access is refused by throwing; this logs it. */
    private static List<MediaController> nowPlayingCandidates(Context context) {
        List<MediaController> candidates = new ArrayList<>();
        MediaSessionManager manager = context.getSystemService(MediaSessionManager.class);
        if (manager == null) {
            return candidates;
        }
        List<MediaController> active;
        try {
            active = manager.getActiveSessions(new ComponentName(context, MediaWatcher.class));
        } catch (SecurityException refused) {
            Log.e(HomeState.LOG_TAG, "SystemControls.nowPlaying: notification access not granted", refused);
            return candidates;
        }
        for (MediaController controller : active) {
            if (ControlRules.isNowPlayingApp(controller.getPackageName())) {
                candidates.add(controller);
            }
        }
        return candidates;
    }
}
