package local.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Some apps (Stremio, for one) skip Android's shared Watch Next list and instead publish their own
 * home-screen channel named "Continue Watching". This reads those channels' programmes as cards.
 * Same permission as {@link WatchNextReader}. Call off the main thread.
 *
 * The provider refuses filtered queries from apps without system access, so whole tables are read
 * with an explicit column list and filtered here.
 */
final class ContinueChannelReader {

    private static final String[] CHANNEL_COLUMNS = {"_id", "package_name", "display_name"};
    private static final String[] PROGRAM_COLUMNS = {
        "channel_id", "package_name", "title", "intent_uri", "last_playback_position_millis",
        "duration_millis", "thumbnail_uri", "poster_art_uri", "season_display_number", "episode_display_number",
    };

    private ContinueChannelReader() {
    }

    static List<WatchNextRules.Item> readAll(Context context) {
        List<WatchNextRules.Item> items = new ArrayList<>();
        if (context.checkSelfPermission(WatchNextReader.READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(HomeState.LOG_TAG, "ContinueChannelReader.readAll: READ_TV_LISTINGS not granted");
            return items;
        }
        List<Long> channelIds = continueChannelIds(context);
        if (channelIds.isEmpty()) {
            return items;
        }
        Cursor rows = query(context, TvContract.PreviewPrograms.CONTENT_URI, PROGRAM_COLUMNS);
        if (rows == null) {
            return items;
        }
        collectPrograms(rows, channelIds, items);
        rows.close();
        Log.i(HomeState.LOG_TAG, "ContinueChannelReader.readAll: channels=" + channelIds.size() + " programs=" + items.size());
        return items;
    }

    private static List<Long> continueChannelIds(Context context) {
        List<Long> ids = new ArrayList<>();
        Cursor rows = query(context, TvContract.Channels.CONTENT_URI, CHANNEL_COLUMNS);
        if (rows == null) {
            return ids;
        }
        int idIndex = rows.getColumnIndex("_id");
        int nameIndex = rows.getColumnIndex("display_name");
        int packageIndex = rows.getColumnIndex("package_name");
        while (rows.moveToNext()) {
            String name = WatchNextReader.stringAt(rows, nameIndex);
            if (WatchNextRules.isContinueChannel(name)) {
                ids.add(WatchNextReader.longAt(rows, idIndex));
                Log.i(HomeState.LOG_TAG, "ContinueChannelReader: channel \"" + name + "\" from "
                        + WatchNextReader.stringAt(rows, packageIndex));
            }
        }
        rows.close();
        return ids;
    }

    private static void collectPrograms(Cursor rows, List<Long> channelIds, List<WatchNextRules.Item> into) {
        int channelIndex = rows.getColumnIndex("channel_id");
        int packageIndex = rows.getColumnIndex("package_name");
        int titleIndex = rows.getColumnIndex("title");
        int intentIndex = rows.getColumnIndex("intent_uri");
        int positionIndex = rows.getColumnIndex("last_playback_position_millis");
        int durationIndex = rows.getColumnIndex("duration_millis");
        int thumbnailIndex = rows.getColumnIndex("thumbnail_uri");
        int posterIndex = rows.getColumnIndex("poster_art_uri");
        int seasonIndex = rows.getColumnIndex("season_display_number");
        int episodeIndex = rows.getColumnIndex("episode_display_number");
        while (rows.moveToNext()) {
            if (!channelIds.contains(WatchNextReader.longAt(rows, channelIndex))) {
                continue;
            }
            into.add(new WatchNextRules.Item(
                    WatchNextReader.stringAt(rows, packageIndex),
                    WatchNextReader.stringAt(rows, titleIndex),
                    WatchNextRules.TYPE_CONTINUE,
                    0L,
                    WatchNextReader.longAt(rows, positionIndex),
                    WatchNextReader.longAt(rows, durationIndex),
                    WatchNextReader.stringAt(rows, intentIndex),
                    WatchNextRules.preferredArt(WatchNextReader.stringAt(rows, thumbnailIndex),
                            WatchNextReader.stringAt(rows, posterIndex)),
                    WatchNextReader.stringAt(rows, seasonIndex),
                    WatchNextReader.stringAt(rows, episodeIndex)));
        }
    }

    /** The TV provider refuses some callers, or some columns, by throwing; this reports it instead. */
    private static Cursor query(Context context, Uri uri, String[] columns) {
        try {
            return context.getContentResolver().query(uri, columns, null, null, null);
        } catch (SecurityException | IllegalArgumentException | android.database.sqlite.SQLiteException refused) {
            Log.e(HomeState.LOG_TAG, "ContinueChannelReader.query: refused, uri=" + uri, refused);
            return null;
        }
    }
}
