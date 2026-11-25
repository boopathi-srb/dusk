package local.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads Android TV's shared "Watch Next" list, where apps record what you are partway
 * through. Call off the main thread. Returns an empty list (and logs why) when the
 * permission is missing or the TV refuses.
 */
final class WatchNextReader {

    /** Not in the public SDK constants on API 30, but declared by this TV's provider (dangerous, grantable). */
    static final String READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";

    /** Column names from Android's TvContract.WatchNextPrograms, as stored in the provider. */
    private static final String COLUMN_PACKAGE = "package_name";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_TYPE = "watch_next_type";
    private static final String COLUMN_ENGAGED = "last_engagement_time_utc_millis";
    private static final String COLUMN_POSITION = "last_playback_position_millis";
    private static final String COLUMN_DURATION = "duration_millis";
    private static final String COLUMN_INTENT = "intent_uri";
    private static final String COLUMN_THUMBNAIL = "thumbnail_uri";
    private static final String COLUMN_POSTER = "poster_art_uri";
    private static final String COLUMN_SEASON = "season_display_number";
    private static final String COLUMN_EPISODE = "episode_display_number";

    private static final String[] PROJECTION = {
        COLUMN_PACKAGE, COLUMN_TITLE, COLUMN_TYPE, COLUMN_ENGAGED, COLUMN_POSITION, COLUMN_DURATION, COLUMN_INTENT,
        COLUMN_THUMBNAIL, COLUMN_POSTER, COLUMN_SEASON, COLUMN_EPISODE,
    };

    private WatchNextReader() {
    }

    static List<WatchNextRules.Item> readAll(Context context) {
        boolean granted = context.checkSelfPermission(READ_TV_LISTINGS)
                == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            Log.w(HomeState.LOG_TAG, "WatchNextReader.readAll: READ_TV_LISTINGS not granted, row stays hidden");
            return new ArrayList<>();
        }
        Cursor rows = query(context);
        if (rows == null) {
            return new ArrayList<>();
        }
        List<WatchNextRules.Item> items = toItems(rows);
        rows.close();
        Log.i(HomeState.LOG_TAG, "WatchNextReader.readAll: rows=" + items.size() + " " + describeRows(items));
        return items;
    }

    /** Which app wrote each row and of what type, e.g. "in.startv.hotstar:0", for the log only. */
    private static String describeRows(List<WatchNextRules.Item> items) {
        StringBuilder description = new StringBuilder("rowsByApp=[");
        for (WatchNextRules.Item item : items) {
            description.append(item.packageName).append(':').append(item.type)
                    .append(":art=").append(WatchNextRules.artScheme(item.artUri))
                    .append(':').append(WatchNextRules.seriesLabel(item.season, item.episode)).append(' ');
        }
        return description.append(']').toString();
    }

    /** The TV provider refuses some callers by throwing, so this catch reports it instead of crashing. */
    private static Cursor query(Context context) {
        try {
            Cursor rows = context.getContentResolver().query(
                    TvContract.WatchNextPrograms.CONTENT_URI, PROJECTION, null, null, null);
            if (rows == null) {
                Log.w(HomeState.LOG_TAG, "WatchNextReader.query: provider returned no cursor");
            }
            return rows;
        } catch (SecurityException | IllegalArgumentException refused) {
            Log.e(HomeState.LOG_TAG, "WatchNextReader.query: TV provider refused, uri="
                    + TvContract.WatchNextPrograms.CONTENT_URI, refused);
            return null;
        }
    }

    private static List<WatchNextRules.Item> toItems(Cursor rows) {
        List<WatchNextRules.Item> items = new ArrayList<>();
        int packageIndex = rows.getColumnIndex(COLUMN_PACKAGE);
        int titleIndex = rows.getColumnIndex(COLUMN_TITLE);
        int typeIndex = rows.getColumnIndex(COLUMN_TYPE);
        int engagedIndex = rows.getColumnIndex(COLUMN_ENGAGED);
        int positionIndex = rows.getColumnIndex(COLUMN_POSITION);
        int durationIndex = rows.getColumnIndex(COLUMN_DURATION);
        int intentIndex = rows.getColumnIndex(COLUMN_INTENT);
        int thumbnailIndex = rows.getColumnIndex(COLUMN_THUMBNAIL);
        int posterIndex = rows.getColumnIndex(COLUMN_POSTER);
        int seasonIndex = rows.getColumnIndex(COLUMN_SEASON);
        int episodeIndex = rows.getColumnIndex(COLUMN_EPISODE);
        while (rows.moveToNext()) {
            items.add(new WatchNextRules.Item(
                    stringAt(rows, packageIndex),
                    stringAt(rows, titleIndex),
                    (int) longAt(rows, typeIndex),
                    WatchNextRules.toMillis(longAt(rows, engagedIndex)),
                    longAt(rows, positionIndex),
                    longAt(rows, durationIndex),
                    stringAt(rows, intentIndex),
                    WatchNextRules.preferredArt(stringAt(rows, thumbnailIndex), stringAt(rows, posterIndex)),
                    stringAt(rows, seasonIndex),
                    stringAt(rows, episodeIndex)));
        }
        return items;
    }

    static String stringAt(Cursor rows, int index) {
        return index < 0 || rows.isNull(index) ? null : rows.getString(index);
    }

    static long longAt(Cursor rows, int index) {
        return index < 0 || rows.isNull(index) ? 0L : rows.getLong(index);
    }
}
