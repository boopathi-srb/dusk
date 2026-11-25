package local.tvhome;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Follows the system activity log on its own thread and reports each Home press.
 * Needs READ_LOGS; without it logcat only returns this app's own lines.
 */
final class LogWatcher implements Runnable {

    interface Listener {
        /** Called on the watcher thread. */
        void onHomePress();

        /** Called on the watcher thread, once, when reading ends for any reason. */
        void onWatcherStopped(String reason);
    }

    private static final String LOGCAT_TIME_FORMAT = "MM-dd HH:mm:ss.SSS";

    private final Listener listener;
    private volatile Process logcatProcess;
    private volatile boolean stopRequested;

    LogWatcher(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void run() {
        String[] command = HomePressLog.logcatCommandSince(currentLogcatTimestamp());
        Process process = startLogcat(command);
        if (process == null) {
            listener.onWatcherStopped("logcat could not be started");
            return;
        }
        logcatProcess = process;
        Log.i(HomeState.LOG_TAG, "LogWatcher.run: following the activity log");
        String stopReason = reportHomePressesUntilClosed(process);
        process.destroy();
        listener.onWatcherStopped(stopReason);
    }

    void stop() {
        stopRequested = true;
        Process process = logcatProcess;
        if (process != null) {
            process.destroy();
        }
    }

    private static String currentLogcatTimestamp() {
        return new SimpleDateFormat(LOGCAT_TIME_FORMAT, Locale.US).format(new Date());
    }

    /** Starting a process reports failure only by throwing. */
    private static Process startLogcat(String[] command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException startFailure) {
            Log.e(HomeState.LOG_TAG, "LogWatcher.startLogcat: failed, command=" + String.join(" ", command), startFailure);
            return null;
        }
    }

    /** Reading a stream reports failure only by throwing. */
    private String reportHomePressesUntilClosed(Process process) {
        try (BufferedReader logLines = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String logLine = logLines.readLine();
            while (logLine != null && !stopRequested) {
                if (HomePressLog.isHomePressLine(logLine)) {
                    listener.onHomePress();
                }
                logLine = logLines.readLine();
            }
            return stopRequested ? "stop requested" : "logcat output ended";
        } catch (IOException readFailure) {
            Log.e(HomeState.LOG_TAG, "LogWatcher.reportHomePressesUntilClosed: read failed, stopRequested="
                    + stopRequested, readFailure);
            return "read failed: " + readFailure.getMessage();
        }
    }
}
