package local.tvhome;

import android.util.Log;

import java.nio.charset.StandardCharsets;

/**
 * Finds a YouTube video's ID from its title and channel on YouTube's search page (the only thing
 * sent is that search). Call off the main thread. Returns null, logged, when YouTube cannot be
 * reached or no result matches exactly.
 */
final class YouTubeLookup {

    /** The search page is about 1.7 MB. */
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    /** Without a desktop browser name YouTube sends its mobile site, which carries no results data. */
    private static final String DESKTOP_BROWSER = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    private YouTubeLookup() {
    }

    static String findVideoId(String title, String channel) {
        String url = YouTubeRules.searchUrl(title, channel);
        if (url == null) {
            return null;
        }
        byte[] page = Web.get(url, MAX_PAGE_BYTES, DESKTOP_BROWSER, "YouTubeLookup.findVideoId");
        if (page == null) {
            return null;
        }
        String videoId = YouTubeRules.findVideoId(new String(page, StandardCharsets.UTF_8), title, channel);
        if (videoId == null) {
            Log.w(HomeState.LOG_TAG, "YouTubeLookup.findVideoId: no exact match, title=" + title + " channel=" + channel
                    + " pageBytes=" + page.length);
        } else {
            Log.i(HomeState.LOG_TAG, "YouTubeLookup.findVideoId: found id=" + videoId + " title=" + title);
        }
        return videoId;
    }
}
