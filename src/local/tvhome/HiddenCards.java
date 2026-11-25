package local.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/** Keeps the fingerprints of hidden Continue watching cards on the TV (see {@link ContinueHiding}). */
final class HiddenCards {

    private static final String PREFS = "continue_hidden";
    private static final String KEY_FINGERPRINTS = "fingerprints";

    private HiddenCards() {
    }

    static List<String> read(Context context) {
        List<String> hidden = new ArrayList<>();
        String json = prefs(context).getString(KEY_FINGERPRINTS, null);
        if (json == null) {
            return hidden;
        }
        try {
            JSONArray saved = new JSONArray(json);
            for (int index = 0; index < saved.length(); index++) {
                hidden.add(saved.getString(index));
            }
        } catch (JSONException damaged) {
            Log.e(HomeState.LOG_TAG, "HiddenCards.read: damaged list, showing everything", damaged);
        }
        return hidden;
    }

    /** Replaces the hidden list with {@code fingerprints}. */
    static void save(Context context, List<String> fingerprints) {
        prefs(context).edit().putString(KEY_FINGERPRINTS, new JSONArray(fingerprints).toString()).apply();
        Log.i(HomeState.LOG_TAG, "HiddenCards.save: hidden cards=" + fingerprints.size());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
