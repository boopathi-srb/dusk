package local.tvhome;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The one place the home screen downloads anything: weather, show pictures and the YouTube video
 * lookup. Call off the main thread. Every failure is logged and returns null.
 */
final class Web {

    private static final int TIMEOUT_MS = 8000;
    private static final int BUFFER_BYTES = 16 * 1024;

    private Web() {
    }

    /**
     * The body of {@code url}, or null (logged) on any failure, a status other than 200, or a body
     * larger than {@code maxBytes}. {@code userAgent} may be null for the platform default.
     * Network failures are reported only by throwing, so this catch turns them into a logged null.
     */
    static byte[] get(String url, int maxBytes, String userAgent, String caller) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            if (userAgent != null) {
                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Accept-Language", "en");
            }
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                Log.w(HomeState.LOG_TAG, caller + ": status=" + status + " url=" + url);
                return null;
            }
            return readLimited(connection.getInputStream(), maxBytes, url, caller);
        } catch (IOException failed) {
            Log.w(HomeState.LOG_TAG, caller + ": download failed, url=" + url, failed);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static byte[] readLimited(InputStream stream, int maxBytes, String url, String caller) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read = input.read(buffer);
            while (read != -1) {
                if (bytes.size() + read > maxBytes) {
                    Log.w(HomeState.LOG_TAG, caller + ": answer larger than " + maxBytes + " bytes, url=" + url);
                    return null;
                }
                bytes.write(buffer, 0, read);
                read = input.read(buffer);
            }
            return bytes.toByteArray();
        }
    }
}
