package local.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Saved choices and fixed settings, read in one place. The viewer's name comes from the TV's own
 * name ("Sam's Fire TV") unless set; the city is the one set over adb (see tv.sh), else found once
 * from the TV's postcode. With no city at all, no weather is fetched.
 */
final class HomeSettings {

    /** Used until a weather reading gives the real times, or always when no city is set. */
    static final int FALLBACK_SUNRISE_MINUTE = 6 * 60;
    static final int FALLBACK_SUNSET_MINUTE = 18 * 60;

    static final List<String> DEFAULT_PINNED = Collections.unmodifiableList(Arrays.asList(
            "com.netflix.ninja",
            "in.startv.hotstar",
            "com.amazon.firetv.youtube",
            "com.suntv.sunnxt",
            "com.fancode.tv",
            "com.spotify.tv.android",
            "com.stremio.one"));

    private static final String PREFS_NAME = "home";
    private static final String KEY_PINNED = "pinned_packages";
    private static final String KEY_NAME = "viewer_name";
    private static final String KEY_LATITUDE = "latitude";
    private static final String KEY_LONGITUDE = "longitude";
    private static final String KEY_AUTO_POSTCODE = "auto_postcode";
    private static final String KEY_AUTO_LATITUDE = "auto_latitude";
    private static final String KEY_AUTO_LONGITUDE = "auto_longitude";

    private HomeSettings() {
    }

    /** Today's real sun times from the last weather reading, else the fallback times. */
    static SkyClock skyClock(Context context) {
        return skyClockFor(WeatherClient.cached(context));
    }

    static SkyClock skyClockFor(WeatherRules.Report report) {
        boolean usable = WeatherRules.isUsable(report, System.currentTimeMillis())
                && SkyClock.isValid(report.sunriseMinute, report.sunsetMinute);
        return usable ? new SkyClock(report.sunriseMinute, report.sunsetMinute)
                : new SkyClock(FALLBACK_SUNRISE_MINUTE, FALLBACK_SUNSET_MINUTE);
    }

    static int minuteOfDayNow() {
        Calendar now = Calendar.getInstance();
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    static List<String> pinned(Context context) {
        String saved = prefs(context).getString(KEY_PINNED, null);
        return saved == null ? DEFAULT_PINNED : PinnedApps.parse(saved);
    }

    static void savePinned(Context context, List<String> packages) {
        prefs(context).edit().putString(KEY_PINNED, PinnedApps.serialise(packages)).apply();
    }

    /** Who the greeting is for: the name set over adb, else the owner's name from the TV's own name. */
    static String viewerName(Context context) {
        if (prefs(context).contains(KEY_NAME)) {
            return prefs(context).getString(KEY_NAME, "");
        }
        return SettingsRules.nameFromDeviceName(
                Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME));
    }

    /** The name set by the viewer, or empty when the greeting uses the TV's own name. */
    static String savedViewerName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    /** Saves the greeting's name; an empty name goes back to the owner's name from the TV's own name. */
    static void saveViewerName(Context context, String name) {
        String clean = SettingsRules.cleanName(name);
        if (clean.isEmpty()) {
            prefs(context).edit().remove(KEY_NAME).apply();
            Log.i(HomeState.LOG_TAG, "HomeSettings.saveViewerName: cleared, using the TV's own name");
            return;
        }
        prefs(context).edit().putString(KEY_NAME, clean).apply();
        Log.i(HomeState.LOG_TAG, "HomeSettings.saveViewerName: name set, length=" + clean.length());
    }

    /** {latitude, longitude}: the city set over adb, else the one found from the TV's postcode, else null. */
    static double[] location(Context context) {
        double[] manual = position(context, KEY_LATITUDE, KEY_LONGITUDE);
        return manual != null ? manual : position(context, KEY_AUTO_LATITUDE, KEY_AUTO_LONGITUDE);
    }

    static boolean hasManualLocation(Context context) {
        return position(context, KEY_LATITUDE, KEY_LONGITUDE) != null;
    }

    /** Whether a city was already found for this postcode (so no new lookup is needed). */
    static boolean hasAutoLocationFor(Context context, String postcode) {
        return postcode != null && postcode.equals(prefs(context).getString(KEY_AUTO_POSTCODE, null))
                && position(context, KEY_AUTO_LATITUDE, KEY_AUTO_LONGITUDE) != null;
    }

    static void saveAutoLocation(Context context, String postcode, double latitude, double longitude) {
        prefs(context).edit().putString(KEY_AUTO_POSTCODE, postcode)
                .putString(KEY_AUTO_LATITUDE, String.valueOf(latitude))
                .putString(KEY_AUTO_LONGITUDE, String.valueOf(longitude)).apply();
        Log.i(HomeState.LOG_TAG, "HomeSettings.saveAutoLocation: city found from the TV's postcode");
    }

    private static double[] position(Context context, String latitudeKey, String longitudeKey) {
        double latitude = SettingsRules.latitude(prefs(context).getString(latitudeKey, null));
        double longitude = SettingsRules.longitude(prefs(context).getString(longitudeKey, null));
        return Double.isNaN(latitude) || Double.isNaN(longitude) ? null : new double[] {latitude, longitude};
    }

    /** Saves the city's position ("auto" goes back to the TV's postcode); false (logged) when not valid. */
    static boolean saveLocation(Context context, String latitude, String longitude) {
        if ("auto".equalsIgnoreCase(latitude == null ? "" : latitude.trim())) {
            prefs(context).edit().remove(KEY_LATITUDE).remove(KEY_LONGITUDE).apply();
            Log.i(HomeState.LOG_TAG, "HomeSettings.saveLocation: cleared, using the TV's postcode");
            return true;
        }
        if (Double.isNaN(SettingsRules.latitude(latitude)) || Double.isNaN(SettingsRules.longitude(longitude))) {
            Log.e(HomeState.LOG_TAG, "HomeSettings.saveLocation: not a valid position, latitude=" + latitude
                    + " longitude=" + longitude);
            return false;
        }
        prefs(context).edit().putString(KEY_LATITUDE, latitude.trim()).putString(KEY_LONGITUDE, longitude.trim()).apply();
        Log.i(HomeState.LOG_TAG, "HomeSettings.saveLocation: city position set");
        return true;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
