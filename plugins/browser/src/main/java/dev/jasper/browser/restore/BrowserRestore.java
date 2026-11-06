package dev.jasper.browser.restore;

import dev.jasper.sdk.RestoreState;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * What a browser item saves with Jasper's workspace, and what it comes back from (spec section 16): its URL, its zoom,
 * the OSC 8 target it was opened for and whether it is in /dev/null mode. It never saves form contents, credentials or cookies. {@code workspace.toml}
 * is plain text, so what comes back from it is checked as untrusted input.
 */
public final class BrowserRestore {
    /** The restorer kind of a browser item. */
    public static final String KIND = "dev.jasper.browser.page";
    private static final String URL = "url", TARGET = "target", ZOOM = "zoom", DEVNULL = "devnull";
    // The schemes the address field loads as typed (AddressInput, which this package cannot use: the item package uses
    // this one); BrowserRestoreTest holds the two to the same set.
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "about", "jasper-about");
    // Pages whose address holds the page itself, which a restore cannot load again.
    private static final Set<String> UNSAVED = Set.of("data", "blob");
    // WebLink's rule for a target.
    private static final int MAX_TARGET = 256;
    // The item package's Zoom range, which this package cannot use; BrowserRestoreTest holds the two to the same range.
    private static final int MIN_ZOOM = 25, MAX_ZOOM = 500;

    private BrowserRestore() { }

    /**
     * The restore state of an item at {@code url}, opened for {@code target}, with a zoom of its own or none: the keys
     * {@code url}, {@code target}, {@code zoom} and {@code devnull}, each when present, within {@link RestoreState#MAX_CHARACTERS} counted
     * over keys and values.
     * <ul>
     *   <li>A URL is saved without its user name and password ({@link #withoutUserInfo}).</li>
     *   <li>A URL that would pass that budget beside the target and the zoom, which always fit, is saved without its
     *       query and fragment; when it still would, no {@code url} is saved.</li>
     *   <li>A {@code data:} or {@code blob:} page saves no {@code url}.</li>
     *   <li>Jasper's own {@code jasper-about:} pages save their URL.</li>
     *   <li>An item in /dev/null mode saves {@code devnull = "true"} and no {@code url}: nothing of where it browsed is
     *       written to {@code workspace.toml}.</li>
     *   <li>A target that is not 1 to 256 characters without a control character, as WebLink allows, is not saved.</li>
     * </ul>
     * An item that saves no {@code url} comes back at the start page, and one that saves no {@code zoom} at Page zoom.
     *
     * @param url where the item is, or what it loads first
     * @param target the OSC 8 target it was opened for, or empty
     * @param zoom the item's own zoom, a whole percent from 25 to 500, or empty for none
     * @param devNull whether the item is in /dev/null mode
     * @return the state; always present, as {@code ContentTab.setRestore} takes it
     * @throws IllegalArgumentException for a zoom outside 25 to 500
     */
    public static Optional<RestoreState> state(URI url, Optional<String> target, OptionalInt zoom, boolean devNull) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(zoom, "zoom");
        if (zoom.isPresent() && !validZoom(zoom.getAsInt())) throw new IllegalArgumentException("Not a zoom: " + zoom.getAsInt() + "%");
        var values = new HashMap<String, String>();
        target.filter(BrowserRestore::validTarget).ifPresent(name -> values.put(TARGET, name));
        zoom.ifPresent(percent -> values.put(ZOOM, Integer.toString(percent)));
        if (devNull) values.put(DEVNULL, "true");
        int budget = RestoreState.MAX_CHARACTERS - URL.length()
            - values.entrySet().stream().mapToInt(entry -> entry.getKey().length() + entry.getValue().length()).sum();
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!devNull && !UNSAVED.contains(scheme)) {
            String address = withoutUserInfo(url).toString();
            if (address.length() > budget) address = withoutQueryAndFragment(address);
            if (address.length() <= budget && parses(address)) values.put(URL, address);
        }
        return Optional.of(new RestoreState(KIND, values));
    }

    /**
     * Where a saved item comes back: its saved {@code url}, read as {@link #state} writes it (a whole URI, nothing escaped
     * or trimmed), when it has one of the schemes the address field loads as typed: {@code http}, {@code https},
     * {@code file}, {@code about} or {@code jasper-about}.
     *
     * @param state a saved item's restore state
     * @return the address; empty when it saved none, or one that is not such an address, and the item comes back at the
     *     start page
     */
    public static Optional<URI> url(RestoreState state) {
        String saved = state.values().get(URL);
        if (saved == null) return Optional.empty();
        try {
            URI url = new URI(saved);
            String scheme = url.getScheme();
            return scheme != null && SCHEMES.contains(scheme.toLowerCase(Locale.ROOT)) ? Optional.of(url) : Optional.empty();
        } catch (URISyntaxException notAnAddress) {
            return Optional.empty();
        }
    }

    /**
     * {@code url} without its user name and password, which an item neither saves nor shows: what its authority holds up
     * to its last {@code @}, as Chromium reads it. Everything else in it stays as written, escapes included. An authority
     * {@link URI} reads as a registry, such as one whose host has an underscore, has no user information of its own in
     * {@link URI}'s terms, and loses it all the same.
     *
     * @param url an address
     * @return the address without its user information; {@code url} itself when it has none
     */
    public static URI withoutUserInfo(URI url) {
        String authority = url.getRawAuthority();
        int at = authority == null ? -1 : authority.lastIndexOf('@');
        if (at < 0) return url;
        var address = new StringBuilder();
        if (url.getScheme() != null) address.append(url.getScheme()).append(':');
        address.append("//").append(authority, at + 1, authority.length());
        if (url.getRawPath() != null) address.append(url.getRawPath());
        if (url.getRawQuery() != null) address.append('?').append(url.getRawQuery());
        if (url.getRawFragment() != null) address.append('#').append(url.getRawFragment());
        return URI.create(address.toString());
    }

    /**
     * The OSC 8 target a saved item was opened for.
     *
     * @param state a saved item's restore state
     * @return the target; empty when it saved none, or one that is not 1 to 256 characters without a control character
     */
    public static Optional<String> target(RestoreState state) {
        return Optional.ofNullable(state.values().get(TARGET)).filter(BrowserRestore::validTarget);
    }

    /**
     * The zoom a saved item comes back at: its saved {@code zoom}, a whole percent from 25 to 500 in decimal digits, as
     * {@link #state} writes it.
     *
     * @param state a saved item's restore state
     * @return the zoom; empty when it saved none, or one that is not such a percent, and the item comes back at Page zoom
     */
    public static OptionalInt zoom(RestoreState state) {
        String saved = state.values().get(ZOOM);
        // At most three ASCII digits: Integer.parseInt would also take a sign and other scripts' digits.
        if (saved == null || saved.isEmpty() || saved.length() > 3 || !saved.chars().allMatch(c -> c >= '0' && c <= '9'))
            return OptionalInt.empty();
        int percent = Integer.parseInt(saved);
        return validZoom(percent) ? OptionalInt.of(percent) : OptionalInt.empty();
    }

    /**
     * Whether a saved item was in /dev/null mode: its {@code devnull} is exactly {@code "true"}.
     *
     * @param state a saved item's restore state
     * @return whether it comes back in a new /dev/null store, at the start page
     */
    public static boolean devNull(RestoreState state) {
        return "true".equals(state.values().get(DEVNULL));
    }

    private static boolean validZoom(int percent) {
        return percent >= MIN_ZOOM && percent <= MAX_ZOOM;
    }

    private static boolean parses(String address) {
        try {
            return new URI(address).getScheme() != null;
        } catch (URISyntaxException notAnAddress) {
            return false;
        }
    }

    private static boolean validTarget(String name) {
        return !name.isEmpty() && name.length() <= MAX_TARGET && name.chars().noneMatch(Character::isISOControl);
    }

    /**
     * {@code address} up to its first {@code ?} or {@code #}. A URI writes neither in its scheme, authority or path, so
     * what is left is the address without its query and fragment.
     */
    private static String withoutQueryAndFragment(String address) {
        int end = 0;
        while (end < address.length() && address.charAt(end) != '?' && address.charAt(end) != '#') end++;
        return address.substring(0, end);
    }
}
