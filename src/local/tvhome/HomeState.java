package local.tvhome;

/** State shared by the home screen and the Home-button service. Both run in the same process. */
final class HomeState {

    static final String LOG_TAG = "TvHome";

    static final String EXTRA_LAUNCH_REASON = "local.tvhome.LAUNCH_REASON";

    private static volatile boolean logWatcherRunning;

    /** Set by the service; run when the home screen is drawn and the cover can go. */
    private static volatile Runnable homeReadyListener;

    private HomeState() {
    }

    static boolean isLogWatcherRunning() {
        return logWatcherRunning;
    }

    static void setLogWatcherRunning(boolean running) {
        logWatcherRunning = running;
    }

    static void setHomeReadyListener(Runnable listener) {
        homeReadyListener = listener;
    }

    /** Safe to call with no listener set, which is the case when the service is off. */
    static void notifyHomeReady() {
        Runnable listener = homeReadyListener;
        if (listener != null) {
            listener.run();
        }
    }
}
