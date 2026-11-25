package local.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The last thing watched in each app that shares nothing else (YouTube), as {@link MediaWatcher}
 * saw it. Kept only on the TV: one small record per app, and its picture as a file.
 */
final class MediaHistory {

    private static final String PREFS = "media_history";
    private static final int ART_WIDTH_PX = 400;
    private static final int ART_QUALITY = 90;
    private static final String KEY_VIDEO_ID = "video_id";

    private MediaHistory() {
    }

    static void save(Context context, String packageName, String title, long positionMs, long durationMs,
            Bitmap art, String artUri, long seenAtMs) {
        String savedArt = art != null ? saveArt(context, packageName, art) : artUri;
        String videoId = videoIdFor(context, packageName, title);
        JSONObject record = new JSONObject();
        try {
            record.put("title", title)
                    .put("position", positionMs)
                    .put("duration", durationMs)
                    .put("seen_at", seenAtMs)
                    .put("art", savedArt == null ? JSONObject.NULL : savedArt)
                    .put(KEY_VIDEO_ID, videoId == null ? JSONObject.NULL : videoId);
        } catch (JSONException unexpected) {
            Log.e(HomeState.LOG_TAG, "MediaHistory.save: could not build record, package=" + packageName, unexpected);
            return;
        }
        prefs(context).edit().putString(packageName, record.toString()).apply();
        Log.i(HomeState.LOG_TAG, "MediaHistory.save: package=" + packageName + " position=" + positionMs
                + " duration=" + durationMs + " art=" + WatchNextRules.artScheme(savedArt));
    }

    /** The video ID found for this app's saved video, when its title is still {@code title}; else null. */
    static String videoIdFor(Context context, String packageName, String title) {
        JSONObject record = recordOf(context, packageName);
        if (record == null || title == null || !title.equals(record.optString("title")) || record.isNull(KEY_VIDEO_ID)) {
            return null;
        }
        return record.optString(KEY_VIDEO_ID, null);
    }

    /** Adds the found video ID to the saved record, unless something else has been saved since. */
    static void rememberVideoId(Context context, String packageName, String title, String videoId) {
        JSONObject record = recordOf(context, packageName);
        if (record == null || title == null || !title.equals(record.optString("title"))) {
            Log.i(HomeState.LOG_TAG, "MediaHistory.rememberVideoId: record changed meanwhile, package=" + packageName);
            return;
        }
        try {
            record.put(KEY_VIDEO_ID, videoId);
        } catch (JSONException unexpected) {
            Log.e(HomeState.LOG_TAG, "MediaHistory.rememberVideoId: could not update record, package=" + packageName, unexpected);
            return;
        }
        prefs(context).edit().putString(packageName, record.toString()).apply();
        Log.i(HomeState.LOG_TAG, "MediaHistory.rememberVideoId: package=" + packageName + " id=" + videoId);
    }

    private static JSONObject recordOf(Context context, String packageName) {
        String json = prefs(context).getString(packageName, null);
        if (json == null) {
            return null;
        }
        try {
            return new JSONObject(json);
        } catch (JSONException damaged) {
            Log.e(HomeState.LOG_TAG, "MediaHistory.recordOf: damaged record, package=" + packageName, damaged);
            return null;
        }
    }

    static void forget(Context context, String packageName) {
        prefs(context).edit().remove(packageName).apply();
        Log.i(HomeState.LOG_TAG, "MediaHistory.forget: finished or skipped, package=" + packageName);
    }

    /** Forgets every saved video and deletes their pictures. */
    static void clearAll(Context context) {
        prefs(context).edit().clear().apply();
        File[] files = context.getFilesDir().listFiles((folder, name) -> name.startsWith("media_art_"));
        int deleted = 0;
        for (File picture : files == null ? new File[0] : files) {
            if (picture.delete()) {
                deleted++;
            } else {
                Log.w(HomeState.LOG_TAG, "MediaHistory.clearAll: could not delete " + picture.getName());
            }
        }
        Log.i(HomeState.LOG_TAG, "MediaHistory.clearAll: history cleared, pictures deleted=" + deleted);
    }

    static List<WatchNextRules.Item> readAll(Context context) {
        List<WatchNextRules.Item> items = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            WatchNextRules.Item item = toItem(entry.getKey(), String.valueOf(entry.getValue()));
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static WatchNextRules.Item toItem(String packageName, String json) {
        try {
            JSONObject record = new JSONObject(json);
            String savedArt = record.isNull("art") ? null : record.getString("art");
            String videoId = record.isNull(KEY_VIDEO_ID) ? null : record.optString(KEY_VIDEO_ID, null);
            long position = record.getLong("position");
            String art = savedArt != null ? savedArt : YouTubeRules.thumbnailUrl(videoId);
            return new WatchNextRules.Item(packageName, record.getString("title"), WatchNextRules.TYPE_CONTINUE,
                    record.getLong("seen_at"), position, record.getLong("duration"),
                    YouTubeRules.watchIntentUri(packageName, videoId, position), art, null, null);
        } catch (JSONException damaged) {
            Log.e(HomeState.LOG_TAG, "MediaHistory.toItem: damaged record, package=" + packageName, damaged);
            return null;
        }
    }

    /** Writing a file reports failure only by throwing; returns the file's uri, or null (logged). */
    private static String saveArt(Context context, String packageName, Bitmap art) {
        File file = new File(context.getFilesDir(), "media_art_" + packageName + ".jpg");
        Bitmap small = art.getWidth() > ART_WIDTH_PX
                ? Bitmap.createScaledBitmap(art, ART_WIDTH_PX, Math.max(1, art.getHeight() * ART_WIDTH_PX / art.getWidth()), true)
                : art;
        try (FileOutputStream out = new FileOutputStream(file)) {
            small.compress(Bitmap.CompressFormat.JPEG, ART_QUALITY, out);
            return "file://" + file.getAbsolutePath();
        } catch (IOException failed) {
            Log.e(HomeState.LOG_TAG, "MediaHistory.saveArt: could not write " + file, failed);
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
