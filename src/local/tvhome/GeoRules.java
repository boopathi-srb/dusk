package local.tvhome;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Finding the TV's city with no setup: Fire OS keeps the country and postcode it works out from the
 * internet connection; OpenStreetMap turns that postcode into a position once. Pure logic.
 */
final class GeoRules {

    /** Fire OS prefixes the stored area with this before the base64 text. */
    static final String AREA_PREFIX = "A/";
    private static final String SEARCH = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1";
    private static final int MAX_POSTCODE_LENGTH = 10;

    private GeoRules() {
    }

    /** The base64 part of Fire OS's stored area, or null when it is missing or not in that form. */
    static String areaPayload(String stored) {
        if (stored == null || !stored.startsWith(AREA_PREFIX) || stored.length() == AREA_PREFIX.length()) {
            return null;
        }
        return stored.substring(AREA_PREFIX.length()).trim();
    }

    /** Postcodes are letters, digits, spaces and dashes, at most 10 long (e.g. "110001", "SW1A 1AA"). */
    static boolean isPostcode(String postcode) {
        if (postcode == null || postcode.trim().isEmpty() || postcode.trim().length() > MAX_POSTCODE_LENGTH) {
            return false;
        }
        for (char character : postcode.trim().toCharArray()) {
            if (!Character.isLetterOrDigit(character) && character != ' ' && character != '-') {
                return false;
            }
        }
        return true;
    }

    /** Two letters, e.g. "IN". */
    static boolean isCountryCode(String country) {
        return country != null && country.trim().length() == 2
                && Character.isLetter(country.trim().charAt(0)) && Character.isLetter(country.trim().charAt(1));
    }

    /** OpenStreetMap's search for that postcode in that country; null when either is not valid. */
    static String searchUrl(String postcode, String country) {
        if (!isPostcode(postcode) || !isCountryCode(country)) {
            return null;
        }
        return SEARCH + "&postalcode=" + encode(postcode.trim()) + "&countrycodes=" + country.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** UTF-8 always exists, so the checked exception cannot happen; the old API needs the catch. */
    private static String encode(String text) {
        try {
            return URLEncoder.encode(text, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new IllegalStateException("UTF-8 missing", impossible);
        }
    }
}
