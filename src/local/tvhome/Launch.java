package local.tvhome;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.net.URISyntaxException;

/** Opens other apps' screens, logging every failure instead of crashing the home screen. */
final class Launch {

    private Launch() {
    }

    /**
     * Android reports "that screen is gone" or "you may not open it" only by throwing,
     * so these two catches are the one place the home screen handles that.
     */
    static boolean start(Context context, Intent intent, String what) {
        return start(context, intent, what, null);
    }

    /** As {@link #start(Context, Intent, String)}, with the app zooming out of {@code fromTile} when given. */
    static boolean start(Context context, Intent intent, String what, View fromTile) {
        if (intent == null) {
            Log.e(HomeState.LOG_TAG, "Launch.start: no intent for " + what);
            return false;
        }
        Intent newTask = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle zoomFromTile = fromTile == null ? null : ActivityOptions.makeScaleUpAnimation(
                fromTile, 0, 0, fromTile.getWidth(), fromTile.getHeight()).toBundle();
        try {
            context.startActivity(newTask, zoomFromTile);
            Log.i(HomeState.LOG_TAG, "Launch.start: opened " + what);
            return true;
        } catch (ActivityNotFoundException | SecurityException refused) {
            Log.e(HomeState.LOG_TAG, "Launch.start: could not open " + what + ", intent=" + newTask, refused);
            return false;
        }
    }

    /** Parses an app-provided "intent:" link; null (logged) when it is malformed. */
    static Intent fromUri(String intentUri, String what) {
        if (intentUri == null || intentUri.isEmpty()) {
            return null;
        }
        try {
            return Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
        } catch (URISyntaxException malformed) {
            Log.e(HomeState.LOG_TAG, "Launch.fromUri: malformed link for " + what + ", uri=" + intentUri, malformed);
            return null;
        }
    }

    /** Amazon's Settings menu directly, falling back to Android's standard Settings action. */
    static void openSettings(Context context) {
        Intent amazonSettings = new Intent("amazon.intent.action.MAINSETTINGS")
                .addCategory("android.intent.category.MAINSETTINGS");
        if (start(context, amazonSettings, "Amazon settings menu")) {
            return;
        }
        start(context, new Intent(android.provider.Settings.ACTION_SETTINGS), "Android settings");
    }
}
