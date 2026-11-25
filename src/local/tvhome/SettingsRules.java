package local.tvhome;

/** Checks for the settings people set over adb: their name and their city's position. Pure logic. */
final class SettingsRules {

    /** Long enough for a nickname or first name; longer would not fit the greeting. */
    static final int MAX_NAME_LENGTH = 20;

    private SettingsRules() {
    }

    /** The name trimmed and cut to {@link #MAX_NAME_LENGTH}; empty when none is given. */
    static String cleanName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH).trim() : trimmed;
    }

    /**
     * The owner's first name from the TV's own name, e.g. "Sam" from "Sam's Fire TV" or "Sam Lee’s
     * Fire TV Stick"; empty when the name is not "someone's …" (e.g. "Living Room").
     */
    static String nameFromDeviceName(String deviceName) {
        if (deviceName == null) {
            return "";
        }
        String text = deviceName.trim();
        int possessive = Math.max(text.indexOf("'s "), text.indexOf("’s "));
        if (possessive <= 0) {
            return "";
        }
        String owner = text.substring(0, possessive).trim();
        int space = owner.indexOf(' ');
        return cleanName(space > 0 ? owner.substring(0, space) : owner);
    }

    /** Parses a latitude or longitude; NaN when it is not a number or outside {@code ±limit}. */
    static double coordinate(String text, double limit) {
        if (text == null || text.trim().isEmpty()) {
            return Double.NaN;
        }
        double value;
        // Java reports text that is not a number only by throwing; that is "not a coordinate" here.
        try {
            value = Double.parseDouble(text.trim());
        } catch (NumberFormatException notANumber) {
            return Double.NaN;
        }
        return Math.abs(value) <= limit ? value : Double.NaN;
    }

    static double latitude(String text) {
        return coordinate(text, 90);
    }

    static double longitude(String text) {
        return coordinate(text, 180);
    }
}
