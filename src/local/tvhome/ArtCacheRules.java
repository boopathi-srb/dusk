package local.tvhome;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Keeping Continue watching pictures on the TV, so they show at once after a restart even before
 * Wi-Fi connects, and retrying the ones that could not be fetched yet. Pure logic.
 */
final class ArtCacheRules {

    /** Retries for missing pictures: 15 s, 30 s, then 45 s after a load, then give up until the next one. */
    static final int MAX_RETRIES = 3;
    private static final long RETRY_STEP_MS = 15_000L;

    private ArtCacheRules() {
    }

    /** A stable, safe file name for a picture link, e.g. "art_3f2a…​.img"; null without a link. */
    static String fileName(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        StringBuilder hex = new StringBuilder("art_");
        for (byte part : sha256(url)) {
            hex.append(Character.forDigit((part >> 4) & 0xF, 16)).append(Character.forDigit(part & 0xF, 16));
        }
        return hex.append(".img").toString();
    }

    /** Whether to try the row again: some pictures are missing and retries are left. */
    static boolean shouldRetry(int missingPictures, int retriesDone) {
        return missingPictures > 0 && retriesDone < MAX_RETRIES;
    }

    /** How long to wait before retry number {@code retriesDone + 1}. */
    static long retryDelayMs(int retriesDone) {
        return RETRY_STEP_MS * (Math.max(0, retriesDone) + 1);
    }

    /** SHA-256 is part of every Java runtime, so the checked exception cannot happen. */
    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 missing", impossible);
        }
    }
}
