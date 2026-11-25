package local.tvhome;

/**
 * What Open-Meteo's weather numbers mean for the home screen: which kind of sky, how to say it,
 * and when a reading is too old. Pure logic, no Android types.
 */
final class WeatherRules {

    enum Condition { CLEAR, PARTLY_CLOUDY, OVERCAST, FOG, DRIZZLE, RAIN, HEAVY_RAIN, THUNDER, UNKNOWN }

    /** Readings older than this are fetched again. */
    static final long REFRESH_AFTER_MS = 10L * 60L * 1000L;
    /** Readings older than this are not shown at all (the TV may have been off for a day). */
    static final long DISCARD_AFTER_MS = 6L * 60L * 60L * 1000L;

    /** One reading, as fetched. Sun times are minutes after midnight, or -1 when missing. */
    static final class Report {
        final double temperatureC;
        final int weatherCode;
        final int cloudCoverPercent;
        final int sunriseMinute;
        final int sunsetMinute;
        final long fetchedAtMs;

        Report(double temperatureC, int weatherCode, int cloudCoverPercent,
                int sunriseMinute, int sunsetMinute, long fetchedAtMs) {
            this.temperatureC = temperatureC;
            this.weatherCode = weatherCode;
            this.cloudCoverPercent = cloudCoverPercent;
            this.sunriseMinute = sunriseMinute;
            this.sunsetMinute = sunsetMinute;
            this.fetchedAtMs = fetchedAtMs;
        }

        Condition condition() {
            return fromCode(weatherCode);
        }
    }

    private WeatherRules() {
    }

    /** WMO weather codes, as Open-Meteo reports them. Snow is shown as cloud: it does not happen here. */
    static Condition fromCode(int code) {
        if (code == 0 || code == 1) {
            return Condition.CLEAR;
        }
        if (code == 2) {
            return Condition.PARTLY_CLOUDY;
        }
        if (code == 3 || (code >= 71 && code <= 77) || code == 85 || code == 86) {
            return Condition.OVERCAST;
        }
        if (code == 45 || code == 48) {
            return Condition.FOG;
        }
        if (code >= 51 && code <= 57) {
            return Condition.DRIZZLE;
        }
        if (code == 65 || code == 82) {
            return Condition.HEAVY_RAIN;
        }
        if ((code >= 61 && code <= 67) || code == 80 || code == 81) {
            return Condition.RAIN;
        }
        if (code == 95 || code == 96 || code == 99) {
            return Condition.THUNDER;
        }
        return Condition.UNKNOWN;
    }

    static String describe(Condition condition) {
        if (condition == null) {
            return "";
        }
        switch (condition) {
            case CLEAR:
                return "Clear";
            case PARTLY_CLOUDY:
                return "Partly cloudy";
            case OVERCAST:
                return "Cloudy";
            case FOG:
                return "Foggy";
            case DRIZZLE:
                return "Drizzle";
            case RAIN:
                return "Rain";
            case HEAVY_RAIN:
                return "Heavy rain";
            case THUNDER:
                return "Thunderstorm";
            default:
                return "";
        }
    }

    /** "2026-11-25T06:22" to 382; -1 when the text is not a time. */
    static int minuteOf(String isoDateTime) {
        if (isoDateTime == null) {
            return -1;
        }
        int marker = isoDateTime.indexOf('T');
        if (marker < 0 || isoDateTime.length() < marker + 6 || isoDateTime.charAt(marker + 3) != ':') {
            return -1;
        }
        int hour = twoDigits(isoDateTime, marker + 1);
        int minute = twoDigits(isoDateTime, marker + 4);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return -1;
        }
        return hour * 60 + minute;
    }

    static boolean needsRefresh(Report report, long nowMs) {
        return report == null || nowMs - report.fetchedAtMs >= REFRESH_AFTER_MS || nowMs < report.fetchedAtMs;
    }

    static boolean isUsable(Report report, long nowMs) {
        return report != null && nowMs - report.fetchedAtMs < DISCARD_AFTER_MS && nowMs >= report.fetchedAtMs;
    }

    /** "21° · Cloudy · Sunrise at 6:22 AM"; just the sky note when there is no reading. */
    static String subtitle(Report report, String skyNote) {
        if (report == null) {
            return skyNote;
        }
        String description = describe(report.condition());
        String temperature = Math.round(report.temperatureC) + "°";
        String weather = description.isEmpty() ? temperature : temperature + "  ·  " + description;
        return skyNote == null || skyNote.isEmpty() ? weather : weather + "  ·  " + skyNote;
    }

    private static int twoDigits(String text, int start) {
        char tens = text.charAt(start);
        char ones = text.charAt(start + 1);
        if (!Character.isDigit(tens) || !Character.isDigit(ones)) {
            return -1;
        }
        return (tens - '0') * 10 + (ones - '0');
    }
}
