package local.tvhome;

import java.util.ArrayList;
import java.util.List;

/**
 * Hiding the Continue watching cards that exist right now, without touching the apps' own lists
 * (Dusk may read them but not change them). Each card is remembered by a fingerprint: app, title,
 * episode and how far in it is. Watching that show again changes how far in it is, so it shows up
 * again by itself. Pure logic, no Android types.
 */
final class ContinueHiding {

    private ContinueHiding() {
    }

    static String fingerprint(WatchNextRules.Item item) {
        return item.packageName + "|" + item.title + "|" + item.season + "|" + item.episode + "|" + item.positionMs;
    }

    static List<String> fingerprints(List<WatchNextRules.Item> items) {
        List<String> prints = new ArrayList<>();
        if (items == null) {
            return prints;
        }
        for (WatchNextRules.Item item : items) {
            prints.add(fingerprint(item));
        }
        return prints;
    }

    /** {@code items} without the hidden ones; {@code hidden} may be null or empty. */
    static List<WatchNextRules.Item> withoutHidden(List<WatchNextRules.Item> items, List<String> hidden) {
        List<WatchNextRules.Item> shown = new ArrayList<>();
        if (items == null) {
            return shown;
        }
        for (WatchNextRules.Item item : items) {
            if (hidden == null || !hidden.contains(fingerprint(item))) {
                shown.add(item);
            }
        }
        return shown;
    }
}
