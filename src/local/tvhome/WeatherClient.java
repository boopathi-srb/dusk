package local.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Fetches the city's current weather and today's sunrise and sunset from Open-Meteo (free, no
 * account, no key), at most every 10 minutes (it updates every 15), and keeps the last reading so the screen has it at
 * once after a restart. The request carries only the city's coordinates, set over adb; with no
 * city set, nothing is fetched.
 */
final class WeatherClient {

    interface Listener {
        /** Called on the main thread with a new reading. */
        void onWeather(WeatherRules.Report report);
    }

    private static final String URL_TEMPLATE = "https://api.open-meteo.com/v1/forecast"
            + "?latitude=%.4f&longitude=%.4f"
            + "&current=temperature_2m,weather_code,cloud_cover,cloud_cover_low,cloud_cover_mid,cloud_cover_high"
            + "&daily=sunrise,sunset&timezone=auto&forecast_days=1";
    private static final int MAX_BYTES = 64 * 1024;
    private static final String PREFS = "weather";
    private static final String KEY_JSON = "json";
    private static final String KEY_FETCHED_AT = "fetched_at";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean fetching;

    private WeatherClient() {
    }

    /** The last reading kept on the TV, or null. */
    static WeatherRules.Report cached(Context context) {
        SharedPreferences prefs = prefs(context);
        String json = prefs.getString(KEY_JSON, null);
        return json == null ? null : parse(json, prefs.getLong(KEY_FETCHED_AT, 0L));
    }

    /** Fetches in the background when the kept reading is missing or older than 10 minutes. */
    static void refreshIfNeeded(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        double[] location = HomeSettings.location(app);
        if (location == null || fetching || !WeatherRules.needsRefresh(cached(app), System.currentTimeMillis())) {
            return;
        }
        fetching = true;
        new Thread(() -> {
            WeatherRules.Report report = fetchAndKeep(app, location);
            fetching = false;
            if (report != null && listener != null) {
                MAIN.post(() -> listener.onWeather(report));
            }
        }, "weather-fetch").start();
    }

    static String requestUrl(double latitude, double longitude) {
        return String.format(Locale.ROOT, URL_TEMPLATE, latitude, longitude);
    }

    /** Drops the kept reading, e.g. after the city changes, so the next refresh fetches at once. */
    static void forget(Context context) {
        prefs(context.getApplicationContext()).edit().clear().apply();
    }

    /** Reads Open-Meteo's answer; null (logged) when it is not the expected shape. */
    static WeatherRules.Report parse(String json, long fetchedAtMs) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject current = root.getJSONObject("current");
            JSONObject daily = root.getJSONObject("daily");
            int total = current.optInt("cloud_cover", -1);
            return new WeatherRules.Report(
                    current.getDouble("temperature_2m"),
                    current.getInt("weather_code"),
                    total,
                    WeatherRules.thickCover(total, current.optInt("cloud_cover_low", -1), current.optInt("cloud_cover_mid", -1)),
                    current.optInt("cloud_cover_high", -1),
                    WeatherRules.minuteOf(firstOf(daily.optJSONArray("sunrise"))),
                    WeatherRules.minuteOf(firstOf(daily.optJSONArray("sunset"))),
                    fetchedAtMs);
        } catch (JSONException unexpected) {
            Log.e(HomeState.LOG_TAG, "WeatherClient.parse: unexpected answer, length=" + json.length(), unexpected);
            return null;
        }
    }

    private static WeatherRules.Report fetchAndKeep(Context app, double[] location) {
        String url = requestUrl(location[0], location[1]);
        byte[] body = Web.get(url, MAX_BYTES, null, "WeatherClient.fetchAndKeep");
        if (body == null) {
            return null;
        }
        String json = new String(body, StandardCharsets.UTF_8);
        long now = System.currentTimeMillis();
        WeatherRules.Report report = parse(json, now);
        if (report == null) {
            return null;
        }
        prefs(app).edit().putString(KEY_JSON, json).putLong(KEY_FETCHED_AT, now).apply();
        Log.i(HomeState.LOG_TAG, "WeatherClient: code=" + report.weatherCode + " temp=" + report.temperatureC
                + " cover=" + report.cloudCoverPercent + " sunrise=" + report.sunriseMinute + " sunset=" + report.sunsetMinute);
        return report;
    }

    private static String firstOf(JSONArray values) {
        return values == null || values.length() == 0 ? null : values.optString(0, null);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
