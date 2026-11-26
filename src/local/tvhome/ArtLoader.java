package local.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Loads a Continue watching picture, shrunk to card size: from the TV itself (content://,
 * android.resource://, file://) or from the https link the streaming app gave. Call off the
 * main thread.
 */
final class ArtLoader {

    /** Cards are 144 dp wide; 400 px is sharp at the TV's density without wasting memory. */
    private static final int TARGET_WIDTH_PX = 400;
    /** Show pictures are a few hundred KB; anything far larger is not a thumbnail. */
    private static final int MAX_DOWNLOAD_BYTES = 4 * 1024 * 1024;

    private ArtLoader() {
    }

    static Bitmap load(Context context, String artUri) {
        if (WatchNextRules.isLocalArt(artUri)) {
            return loadLocal(context, artUri);
        }
        return WatchNextRules.isWebArt(artUri) ? loadWeb(context, artUri) : null;
    }

    /** Whether this picture is a web link (it may be missing until Wi-Fi is up). */
    static boolean isWebPicture(String artUri) {
        return WatchNextRules.isWebArt(artUri);
    }

    private static Bitmap loadLocal(Context context, String artUri) {
        Uri uri = Uri.parse(artUri);
        int width = measureWidth(context, uri);
        if (width <= 0) {
            return null;
        }
        return decode(context, uri, shrinkFor(width));
    }

    /** From the copy kept on the TV when there is one; otherwise downloaded once and kept. */
    private static Bitmap loadWeb(Context context, String url) {
        File kept = new File(cacheFolder(context), ArtCacheRules.fileName(url));
        byte[] bytes = kept.exists() ? readKept(kept) : null;
        if (bytes == null) {
            bytes = Web.get(url, MAX_DOWNLOAD_BYTES, null, "ArtLoader.loadWeb");
            if (bytes == null) {
                return null;
            }
            keep(kept, bytes);
        }
        BitmapFactory.Options boundsOnly = new BitmapFactory.Options();
        boundsOnly.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, boundsOnly);
        if (boundsOnly.outWidth <= 0) {
            Log.w(HomeState.LOG_TAG, "ArtLoader.loadWeb: not a picture, url=" + url);
            return null;
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, shrinkFor(boundsOnly.outWidth));
    }

    private static File cacheFolder(Context context) {
        File folder = new File(context.getCacheDir(), "art");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            Log.w(HomeState.LOG_TAG, "ArtLoader.cacheFolder: could not create " + folder);
        }
        return folder;
    }

    /** Reading a file reports failure only by throwing; this logs it and returns null (then it is fetched again). */
    private static byte[] readKept(File file) {
        try {
            return java.nio.file.Files.readAllBytes(file.toPath());
        } catch (IOException unreadable) {
            Log.w(HomeState.LOG_TAG, "ArtLoader.readKept: could not read " + file.getName(), unreadable);
            return null;
        }
    }

    /** Writing reports failure only by throwing; a picture that can't be kept is simply fetched next time. */
    private static void keep(File file, byte[] bytes) {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
            out.write(bytes);
        } catch (IOException failed) {
            Log.w(HomeState.LOG_TAG, "ArtLoader.keep: could not keep " + file.getName(), failed);
        }
    }

    private static BitmapFactory.Options shrinkFor(int width) {
        BitmapFactory.Options shrink = new BitmapFactory.Options();
        shrink.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, width / TARGET_WIDTH_PX)));
        return shrink;
    }

    private static int measureWidth(Context context, Uri uri) {
        BitmapFactory.Options boundsOnly = new BitmapFactory.Options();
        boundsOnly.inJustDecodeBounds = true;
        decode(context, uri, boundsOnly);
        return boundsOnly.outWidth;
    }

    /** Opening another app's file reports refusal or absence only by throwing. */
    private static Bitmap decode(Context context, Uri uri, BitmapFactory.Options options) {
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream == null) {
                Log.w(HomeState.LOG_TAG, "ArtLoader.decode: no stream for uri=" + uri);
                return null;
            }
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (IOException | SecurityException unreadable) {
            Log.w(HomeState.LOG_TAG, "ArtLoader.decode: could not read uri=" + uri, unreadable);
            return null;
        }
    }
}
