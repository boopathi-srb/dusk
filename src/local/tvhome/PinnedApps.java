package local.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * The ordered list of apps on the home row, by package name. Every operation returns a
 * new list and never changes the one passed in. Pure logic, no Android types.
 */
final class PinnedApps {

    static final String SEPARATOR = ",";

    private PinnedApps() {
    }

    static List<String> parse(String saved) {
        List<String> packages = new ArrayList<>();
        if (saved == null) {
            return packages;
        }
        for (String part : saved.split(SEPARATOR)) {
            String packageName = part.trim();
            if (!packageName.isEmpty() && !packages.contains(packageName)) {
                packages.add(packageName);
            }
        }
        return packages;
    }

    static String serialise(List<String> packages) {
        return packages == null ? "" : String.join(SEPARATOR, packages);
    }

    static List<String> add(List<String> pinned, String packageName) {
        List<String> result = copy(pinned);
        if (isBlank(packageName) || result.contains(packageName)) {
            return result;
        }
        result.add(packageName);
        return result;
    }

    static List<String> remove(List<String> pinned, String packageName) {
        List<String> result = copy(pinned);
        result.remove(packageName);
        return result;
    }

    /** Moves an app by {@code offset} places; a move past either end leaves the list unchanged. */
    static List<String> move(List<String> pinned, String packageName, int offset) {
        List<String> result = copy(pinned);
        int from = result.indexOf(packageName);
        int to = from + offset;
        if (from < 0 || to < 0 || to >= result.size()) {
            return result;
        }
        result.remove(from);
        result.add(to, packageName);
        return result;
    }

    static List<String> keepInstalled(List<String> pinned, List<String> installed) {
        List<String> result = new ArrayList<>();
        if (pinned == null || installed == null) {
            return result;
        }
        for (String packageName : pinned) {
            if (installed.contains(packageName)) {
                result.add(packageName);
            }
        }
        return result;
    }

    private static List<String> copy(List<String> pinned) {
        return pinned == null ? new ArrayList<>() : new ArrayList<>(pinned);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
