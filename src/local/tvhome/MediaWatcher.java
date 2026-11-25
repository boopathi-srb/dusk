package local.tvhome;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches the TV's "now playing" reports (the ones its media controls and Alexa use) and remembers
 * the last thing played in apps that share nothing else, for Continue watching. Android only lets
 * a notification-access service read these reports, which is why this is one; it never reads or
 * acts on notifications themselves (it overrides none of their callbacks).
 */
public final class MediaWatcher extends NotificationListenerService implements MediaSessionManager.OnActiveSessionsChangedListener {

    private final Map<MediaSession.Token, MediaController.Callback> watched = new HashMap<>();
    private HandlerThread worker;
    private Handler workerHandler;
    private HandlerThread lookupWorker;
    private Handler lookupHandler;
    /** The last video asked about, so a failed lookup is not repeated on every playback report. */
    private String lastLookupKey;
    private MediaSessionManager sessions;

    @Override
    public void onListenerConnected() {
        worker = new HandlerThread("media-watcher");
        worker.start();
        workerHandler = new Handler(worker.getLooper());
        lookupWorker = new HandlerThread("youtube-lookup");
        lookupWorker.start();
        lookupHandler = new Handler(lookupWorker.getLooper());
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MediaWatcher.class);
        List<MediaController> active = activeSessions(me);
        if (active == null) {
            return;
        }
        sessions.addOnActiveSessionsChangedListener(this, me, workerHandler);
        workerHandler.post(() -> onActiveSessionsChanged(active));
        Log.i(HomeState.LOG_TAG, "MediaWatcher: connected, sessions=" + active.size());
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) {
            sessions.removeOnActiveSessionsChangedListener(this);
        }
        if (workerHandler != null) {
            workerHandler.post(this::stopWatchingAll);
        }
        if (worker != null) {
            worker.quitSafely();
        }
        if (lookupWorker != null) {
            lookupWorker.quitSafely();
        }
        Log.i(HomeState.LOG_TAG, "MediaWatcher: disconnected");
    }

    /** Runs on the worker thread. */
    @Override
    public void onActiveSessionsChanged(List<MediaController> controllers) {
        if (controllers == null) {
            return;
        }
        for (MediaController controller : controllers) {
            if (!watched.containsKey(controller.getSessionToken())) {
                startWatching(controller);
            }
            remember(controller);
        }
    }

    /** Asking for sessions without notification access is refused by throwing; this logs it. */
    private List<MediaController> activeSessions(ComponentName me) {
        try {
            return sessions.getActiveSessions(me);
        } catch (SecurityException refused) {
            Log.e(HomeState.LOG_TAG, "MediaWatcher: notification access not granted, cannot read sessions", refused);
            return null;
        }
    }

    private void startWatching(MediaController controller) {
        MediaController.Callback callback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(PlaybackState state) {
                remember(controller);
            }

            @Override
            public void onMetadataChanged(MediaMetadata metadata) {
                remember(controller);
            }

            @Override
            public void onSessionDestroyed() {
                remember(controller);
                controller.unregisterCallback(this);
                watched.remove(controller.getSessionToken());
            }
        };
        controller.registerCallback(callback, workerHandler);
        watched.put(controller.getSessionToken(), callback);
    }

    private void stopWatchingAll() {
        for (Map.Entry<MediaSession.Token, MediaController.Callback> entry : watched.entrySet()) {
            new MediaController(this, entry.getKey()).unregisterCallback(entry.getValue());
        }
        watched.clear();
    }

    /** Saves what is (or was last) playing in this app, or forgets it once it has finished. */
    private void remember(MediaController controller) {
        String packageName = controller.getPackageName();
        MediaMetadata metadata = controller.getMetadata();
        PlaybackState state = controller.getPlaybackState();
        if (MediaHistoryRules.isSkippedApp(packageName) || metadata == null || state == null) {
            return;
        }
        String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        long duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
        boolean playing = state.getState() == PlaybackState.STATE_PLAYING;
        long position = MediaHistoryRules.positionNow(state.getPosition(), state.getLastPositionUpdateTime(),
                SystemClock.elapsedRealtime(), state.getPlaybackSpeed(), playing);
        if (MediaHistoryRules.isFinished(position, duration)) {
            MediaHistory.forget(this, packageName);
            return;
        }
        if (!MediaHistoryRules.isWorthKeeping(packageName, title, position, duration)) {
            return;
        }
        MediaHistory.save(this, packageName, title, position, duration, artOf(metadata), artUriOf(metadata),
                System.currentTimeMillis());
        lookUpVideoIfNeeded(packageName, title, metadata.getString(MediaMetadata.METADATA_KEY_ARTIST));
    }

    /** YouTube never shares the video's ID; find it once per video so the card gets a picture and link. */
    private void lookUpVideoIfNeeded(String packageName, String title, String channel) {
        String lookupKey = packageName + "\n" + title;
        if (!YouTubeRules.isYouTube(packageName) || lookupKey.equals(lastLookupKey)
                || MediaHistory.videoIdFor(this, packageName, title) != null) {
            return;
        }
        lastLookupKey = lookupKey;
        lookupHandler.post(() -> {
            String videoId = YouTubeLookup.findVideoId(title, channel);
            if (videoId != null) {
                MediaHistory.rememberVideoId(this, packageName, title, videoId);
            }
        });
    }

    private static Bitmap artOf(MediaMetadata metadata) {
        Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (art == null) {
            art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        }
        return art != null ? art : metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
    }

    private static String artUriOf(MediaMetadata metadata) {
        String uri = metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);
        return uri != null ? uri : metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
    }
}
