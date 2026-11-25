package local.tvhome;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** The day and time shown at the top right ("WEDNESDAY" over "8:15 PM"), or on one line. Pure logic. */
final class DateLine {

    private static final DateTimeFormatter TWELVE_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter TWENTY_FOUR_HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final String SEPARATOR = "  ·  ";
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private DateLine() {
    }

    /** The day in capitals, e.g. "WEDNESDAY"; empty without a time. */
    static String day(LocalDateTime time) {
        return time == null ? "" : time.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
    }

    /** The day and date in capitals, e.g. "WEDNESDAY  ·  25 NOV"; empty without a time. */
    static String dayAndDate(LocalDateTime time) {
        return time == null ? "" : day(time) + SEPARATOR + time.format(DAY_MONTH).toUpperCase(Locale.ENGLISH);
    }

    /** The time, e.g. "11:02 PM" or "23:02"; empty without a time. */
    static String time(LocalDateTime time, boolean use24Hour) {
        return time == null ? "" : time.format(use24Hour ? TWENTY_FOUR_HOUR : TWELVE_HOUR);
    }

    static String format(LocalDateTime time, boolean use24Hour) {
        if (time == null) {
            return "";
        }
        return day(time) + SEPARATOR + time(time, use24Hour);
    }
}
