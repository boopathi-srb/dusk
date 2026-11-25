package local.tvhome;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which "Watch Next" rows become Continue watching cards, and what each card says.
 * Pure logic, no Android types; {@link WatchNextReader} feeds it from the TV.
 */
final class WatchNextRules {

    /** Values of the watch_next_type column, as defined by Android's TvContract. */
    static final int TYPE_CONTINUE = 0;
    static final int TYPE_NEXT = 1;
    static final int TYPE_NEW = 2;
    static final int TYPE_WATCHLIST = 3;

    /** A few apps write seconds, not milliseconds; anything below this is treated as seconds. */
    static final long SECONDS_CUTOFF = 100_000_000_000L;

    /** Something untouched for this long has been moved on from. */
    static final long STALE_AFTER_MS = 60L * 24 * 60 * 60 * 1000;

    private static final long MINUTE_MS = 60_000L;

    static final class Item {
        final String packageName;
        final String title;
        final int type;
        final long engagedAtMs;
        final long positionMs;
        final long durationMs;
        final String intentUri;
        /** The app's picture for this title, wide thumbnail preferred over poster; may be null. */
        final String artUri;
        /** For a series, as the app shows them (often "2" and "5"); null for a film. */
        final String season;
        final String episode;

        Item(String packageName, String title, int type, long engagedAtMs,
                long positionMs, long durationMs, String intentUri, String artUri,
                String season, String episode) {
            this.artUri = artUri;
            this.season = season;
            this.episode = episode;
            this.packageName = packageName;
            this.title = title;
            this.type = type;
            this.engagedAtMs = engagedAtMs;
            this.positionMs = positionMs;
            this.durationMs = durationMs;
            this.intentUri = intentUri;
        }
    }

    private WatchNextRules() {
    }

    /** "S2 · E5" for a series episode, "E5" or "S2" when only one is known, "" for a film. */
    static String seriesLabel(String season, String episode) {
        String seasonPart = isBlank(season) ? "" : "S" + season.trim();
        String episodePart = isBlank(episode) ? "" : "E" + episode.trim();
        if (seasonPart.isEmpty() || episodePart.isEmpty()) {
            return seasonPart + episodePart;
        }
        return seasonPart + " \u00B7 " + episodePart;
    }

    /** Longest title shown under a focused card; longer ones end in "…". */
    static final int CARD_TITLE_MAX_CHARS = 42;

    /** The words under a focused card: "Game Of Thrones · S8 E4", or just the title for films and videos. */
    static String cardTitle(Item item) {
        if (item == null || isBlank(item.title)) {
            return "";
        }
        String episode = seriesLabel(item.season, item.episode).replace(" \u00B7 ", " ");
        String title = item.title.trim();
        int room = CARD_TITLE_MAX_CHARS - (episode.isEmpty() ? 0 : episode.length() + 3);
        if (title.length() > room) {
            title = title.substring(0, Math.max(1, room - 1)).trim() + "\u2026";
        }
        return episode.isEmpty() ? title : title + " \u00B7 " + episode;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** The wide thumbnail when the app gave one, else its poster; null when neither is set. */
    static String preferredArt(String thumbnailUri, String posterUri) {
        if (thumbnailUri != null && !thumbnailUri.trim().isEmpty()) {
            return thumbnailUri.trim();
        }
        return posterUri == null || posterUri.trim().isEmpty() ? null : posterUri.trim();
    }

    /** Whether a picture is stored on the TV itself, so it loads without internet access. */
    static boolean isLocalArt(String artUri) {
        if (artUri == null) {
            return false;
        }
        String lower = artUri.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("content://") || lower.startsWith("android.resource://") || lower.startsWith("file://");
    }

    /** Names apps give their own resume channel: "Continue Watching", "Keep watching", "Resume". */
    private static final String[] CONTINUE_CHANNEL_WORDS = {"continue", "keep watching", "resume"};

    static boolean isContinueChannel(String displayName) {
        if (displayName == null) {
            return false;
        }
        String lower = displayName.toLowerCase(java.util.Locale.ROOT);
        for (String word : CONTINUE_CHANNEL_WORDS) {
            if (lower.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** A picture on the web, over https only (no plain http). */
    static boolean isWebArt(String artUri) {
        return artUri != null && artUri.toLowerCase(java.util.Locale.ROOT).startsWith("https://");
    }

    /** "https" for a web link, "content" for a TV file, "none" when missing: for the log only. */
    static String artScheme(String artUri) {
        if (artUri == null) {
            return "none";
        }
        int colon = artUri.indexOf(':');
        return colon <= 0 ? "unknown" : artUri.substring(0, colon).toLowerCase(java.util.Locale.ROOT);
    }

    static long toMillis(long rawTime) {
        if (rawTime <= 0) {
            return 0;
        }
        return rawTime < SECONDS_CUTOFF ? rawTime * 1000 : rawTime;
    }

    /** Newest first, one card per app-and-title, at most {@code max}. */
    static List<Item> pick(List<Item> items, long nowMs, int max) {
        if (items == null || max <= 0) {
            return Collections.emptyList();
        }
        List<Item> kept = new ArrayList<>();
        for (Item item : items) {
            if (isWorthShowing(item, nowMs)) {
                kept.add(item);
            }
        }
        kept.sort((first, second) -> Long.compare(second.engagedAtMs, first.engagedAtMs));
        return firstUnique(kept, max);
    }

    /**
     * The "now playing" cards only for apps that keep no list of their own. JioHotstar reports
     * what is playing as well as keeping its list, and its list entry is the better card (picture,
     * exact episode link), so its "now playing" card would be a duplicate.
     */
    static List<Item> onlyAppsWithoutList(List<Item> nowPlaying, List<Item> appLists) {
        List<Item> kept = new ArrayList<>();
        if (nowPlaying == null) {
            return kept;
        }
        List<String> appsWithList = new ArrayList<>();
        if (appLists != null) {
            for (Item listed : appLists) {
                appsWithList.add(listed.packageName);
            }
        }
        for (Item playing : nowPlaying) {
            if (!appsWithList.contains(playing.packageName)) {
                kept.add(playing);
            }
        }
        return kept;
    }

    static boolean isWorthShowing(Item item, long nowMs) {
        if (item == null || item.title == null || item.title.trim().isEmpty()) {
            return false;
        }
        if (item.type == TYPE_WATCHLIST) {
            return false;
        }
        boolean knownTime = item.engagedAtMs > 0;
        return !knownTime || nowMs - item.engagedAtMs <= STALE_AFTER_MS;
    }

    static float progress(long positionMs, long durationMs) {
        if (durationMs <= 0 || positionMs <= 0) {
            return 0f;
        }
        return Math.min(1f, positionMs / (float) durationMs);
    }

    static String status(int type, long positionMs, long durationMs) {
        if (type == TYPE_NEXT) {
            return "Next episode";
        }
        if (type == TYPE_NEW) {
            return "New";
        }
        long leftMs = durationMs - positionMs;
        if (durationMs <= 0 || positionMs <= 0 || leftMs <= 0) {
            return "Continue";
        }
        return timeLeft(leftMs);
    }

    private static String timeLeft(long leftMs) {
        long minutes = (leftMs + MINUTE_MS - 1) / MINUTE_MS;
        if (minutes < 60) {
            return minutes + " min left";
        }
        long hours = minutes / 60;
        long restMinutes = minutes % 60;
        return restMinutes == 0 ? hours + " h left" : hours + " h " + restMinutes + " min left";
    }

    private static List<Item> firstUnique(List<Item> sorted, int max) {
        List<Item> unique = new ArrayList<>();
        List<String> seenKeys = new ArrayList<>();
        for (Item item : sorted) {
            String key = item.packageName + "\n" + item.title.trim();
            if (!seenKeys.contains(key)) {
                seenKeys.add(key);
                unique.add(item);
            }
            if (unique.size() == max) {
                break;
            }
        }
        return unique;
    }
}
