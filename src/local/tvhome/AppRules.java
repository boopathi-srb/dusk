package local.tvhome;

/** Which installed apps stay out of the All apps list. Pure logic, no Android types. */
final class AppRules {

    /**
     * System pieces that register a launch entry but are not apps to open: broadcast
     * operator portals, the TV's own player shells, Amazon's home and its helpers.
     */
    private static final String[] HIDDEN_PREFIXES = {
        "com.mediatek.opapp",
        "com.vewd.core.",
        "com.amazon.tv.launcher",
        "com.amazon.firehomestarter",
        "com.amazon.tv.earlyaccess",
        "com.amazon.ftv.profilepicker",
        "com.amazon.tv.quicksettings",
        "com.amazon.ssm",
    };

    private AppRules() {
    }

    static boolean isHidden(String packageName, String ownPackageName) {
        if (packageName == null || packageName.isEmpty()) {
            return true;
        }
        if (packageName.equals(ownPackageName)) {
            return true;
        }
        for (String prefix : HIDDEN_PREFIXES) {
            if (packageName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
