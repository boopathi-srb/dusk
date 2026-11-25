package local.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * Finds the TV's city when nobody set one: reads the country and postcode Fire OS stores, and asks
 * OpenStreetMap once where that postcode is. The answer is kept, so this runs again only if the
 * postcode changes. Only the postcode and country leave the TV.
 */
final class CityLookup {

    /** Where Fire OS keeps the area it works out from the internet connection. */
    private static final String FIRE_OS_AREA = "com.amazon.tv.routinginfo.attribute.geoContext";
    /** OpenStreetMap asks every app to name itself. */
    private static final String USER_AGENT = "Dusk-TV-launcher (Fire TV home screen)";
    private static final int MAX_ANSWER_BYTES = 64 * 1024;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean looking;

    private CityLookup() {
    }

    /** Looks the city up in the background when none is set or kept for this postcode; {@code onFound} runs on the main thread. */
    static void refreshIfNeeded(Context context, Runnable onFound) {
        Context app = context.getApplicationContext();
        if (looking || HomeSettings.hasManualLocation(app)) {
            return;
        }
        String[] area = fireOsArea(app);
        if (area == null || HomeSettings.hasAutoLocationFor(app, area[0])) {
            return;
        }
        String url = GeoRules.searchUrl(area[0], area[1]);
        if (url == null) {
            Log.w(HomeState.LOG_TAG, "CityLookup: Fire OS area not usable, country=" + area[1]);
            return;
        }
        looking = true;
        new Thread(() -> {
            boolean found = lookUp(app, url, area[0]);
            looking = false;
            if (found && onFound != null) {
                MAIN.post(onFound);
            }
        }, "city-lookup").start();
    }

    private static boolean lookUp(Context app, String url, String postcode) {
        byte[] answer = Web.get(url, MAX_ANSWER_BYTES, USER_AGENT, "CityLookup.lookUp");
        if (answer == null) {
            return false;
        }
        double[] position = firstPosition(new String(answer, StandardCharsets.UTF_8));
        if (position == null) {
            Log.w(HomeState.LOG_TAG, "CityLookup.lookUp: OpenStreetMap does not know this postcode");
            return false;
        }
        HomeSettings.saveAutoLocation(app, postcode, position[0], position[1]);
        return true;
    }

    /** {postcode, country} from Fire OS, or null (logged) when it keeps none or in another form. */
    private static String[] fireOsArea(Context app) {
        String payload = GeoRules.areaPayload(Settings.Global.getString(app.getContentResolver(), FIRE_OS_AREA));
        if (payload == null) {
            return null;
        }
        try {
            JSONObject area = new JSONObject(new String(Base64.decode(payload, Base64.DEFAULT), StandardCharsets.UTF_8));
            return new String[] {area.optString("ippc", null), area.optString("ipcc", null)};
        } catch (JSONException | IllegalArgumentException unreadable) {
            Log.w(HomeState.LOG_TAG, "CityLookup.fireOsArea: Fire OS area unreadable", unreadable);
            return null;
        }
    }

    /** {latitude, longitude} of the first answer; null when there is none or it is malformed. */
    private static double[] firstPosition(String json) {
        try {
            JSONArray places = new JSONArray(json);
            if (places.length() == 0) {
                return null;
            }
            JSONObject place = places.getJSONObject(0);
            double latitude = SettingsRules.latitude(place.optString("lat"));
            double longitude = SettingsRules.longitude(place.optString("lon"));
            return Double.isNaN(latitude) || Double.isNaN(longitude) ? null : new double[] {latitude, longitude};
        } catch (JSONException malformed) {
            Log.w(HomeState.LOG_TAG, "CityLookup.firstPosition: unexpected answer, length=" + json.length(), malformed);
            return null;
        }
    }
}
