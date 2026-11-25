package local.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * YouTube shares only a video's title and channel while it plays, never its ID. These rules find
 * the ID on YouTube's search page by an exact title and channel match, and build the picture and
 * the link that opens that video. Pure logic, no Android types.
 */
final class YouTubeRules {

    private static final String[] YOUTUBE_PACKAGES = {"com.amazon.firetv.youtube", "com.google.android.youtube.tv"};
    private static final String VIDEO_START = "\"videoRenderer\":{\"videoId\":\"";
    private static final String TITLE_START = "\"title\":{\"runs\":[{\"text\":\"";
    private static final String[] CHANNEL_STARTS = {
        "\"longBylineText\":{\"runs\":[{\"text\":\"",
        "\"ownerText\":{\"runs\":[{\"text\":\"",
    };
    private static final int VIDEO_ID_LENGTH = 11;

    private YouTubeRules() {
    }

    static boolean isYouTube(String packageName) {
        for (String youtube : YOUTUBE_PACKAGES) {
            if (youtube.equals(packageName)) {
                return true;
            }
        }
        return false;
    }

    /** YouTube video IDs are 11 characters of letters, digits, '-' and '_'. */
    static boolean isVideoId(String id) {
        if (id == null || id.length() != VIDEO_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            boolean allowed = (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9') || character == '-' || character == '_';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** The search page for the title and channel; null when there is no title. */
    static String searchUrl(String title, String channel) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        String query = channel == null || channel.trim().isEmpty() ? title.trim() : title.trim() + " " + channel.trim();
        return "https://www.youtube.com/results?search_query=" + encode(query);
    }

    /** 16:9 picture without black bars, 320 px wide: sharp enough for a card. */
    static String thumbnailUrl(String videoId) {
        return isVideoId(videoId) ? "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg" : null;
    }

    /** An "intent:" link that opens the video in that YouTube app at the given point. */
    static String watchIntentUri(String packageName, String videoId, long positionMs) {
        if (!isVideoId(videoId) || packageName == null || packageName.isEmpty()) {
            return null;
        }
        long seconds = Math.max(0L, positionMs / 1000L);
        return "intent://www.youtube.com/watch?v=" + videoId + "&t=" + seconds + "s"
                + "#Intent;scheme=https;action=android.intent.action.VIEW;package=" + packageName + ";end";
    }

    /**
     * The ID of the first video on the search page whose title and channel match exactly (ignoring
     * case, spaces and punctuation); null when none does. A near match is never taken: a wrong card
     * is worse than one without a picture.
     */
    static String findVideoId(String page, String title, String channel) {
        if (page == null || normalise(title).isEmpty()) {
            return null;
        }
        int start = page.indexOf(VIDEO_START);
        while (start >= 0) {
            int next = page.indexOf(VIDEO_START, start + VIDEO_START.length());
            String entry = page.substring(start, next < 0 ? page.length() : next);
            String videoId = entry.substring(VIDEO_START.length(), Math.min(entry.length(), VIDEO_START.length() + VIDEO_ID_LENGTH));
            if (isVideoId(videoId) && matches(entry, title, channel)) {
                return videoId;
            }
            start = next;
        }
        return null;
    }

    private static boolean matches(String entry, String title, String channel) {
        if (!normalise(textAfter(entry, TITLE_START)).equals(normalise(title))) {
            return false;
        }
        if (normalise(channel).isEmpty()) {
            return true;
        }
        for (String channelStart : CHANNEL_STARTS) {
            String entryChannel = textAfter(entry, channelStart);
            if (entryChannel != null) {
                return normalise(entryChannel).equals(normalise(channel));
            }
        }
        return false;
    }

    /** Lower case letters and digits, single spaces between words. */
    static String normalise(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder plain = new StringBuilder();
        boolean pendingSpace = false;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                if (pendingSpace && plain.length() > 0) {
                    plain.append(' ');
                }
                plain.append(Character.toLowerCase(character));
                pendingSpace = false;
            } else {
                pendingSpace = true;
            }
        }
        return plain.toString();
    }

    /** The JSON string value that starts right after {@code marker}, unescaped; null when absent. */
    static String textAfter(String json, String marker) {
        int at = json == null ? -1 : json.indexOf(marker);
        if (at < 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        int index = at + marker.length();
        while (index < json.length()) {
            char character = json.charAt(index);
            if (character == '"') {
                return text.toString();
            }
            if (character != '\\' || index + 1 >= json.length()) {
                text.append(character);
                index++;
                continue;
            }
            index = appendEscaped(json, index + 1, text);
        }
        return null;
    }

    /** Reads the escape that starts at {@code index} (just after the backslash); returns where reading continues. */
    private static int appendEscaped(String json, int index, StringBuilder text) {
        char escaped = json.charAt(index);
        if (escaped == 'u' && index + 4 < json.length() && isHex(json.substring(index + 1, index + 5))) {
            text.append((char) Integer.parseInt(json.substring(index + 1, index + 5), 16));
            return index + 5;
        }
        switch (escaped) {
            case 'n': text.append('\n'); break;
            case 't': text.append('\t'); break;
            case 'r': text.append('\r'); break;
            case 'b': text.append('\b'); break;
            case 'f': text.append('\f'); break;
            default: text.append(escaped); break;
        }
        return index + 1;
    }

    private static boolean isHex(String digits) {
        for (int index = 0; index < digits.length(); index++) {
            if (Character.digit(digits.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** UTF-8 always exists, so the checked exception cannot happen; kept to satisfy the old API. */
    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 missing", impossible);
        }
    }
}
