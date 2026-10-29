package dev.jasper.browser.restore;

import dev.jasper.sdk.RestoreState;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What a browser item saves with Jasper's workspace, and what it comes back from (spec section 16): its URL and the OSC 8
 * target it was opened for. It never saves form contents, credentials or cookies. {@code workspace.toml} is plain text,
 * so what comes back from it is checked as untrusted input.
 */
public final class BrowserRestore {
    /** The restorer kind of a browser item. */
    public static final String KIND = "dev.jasper.browser.page";
    private static final String URL = "url", TARGET = "target";
    // The schemes the address field loads as typed (AddressInput, which this package cannot use: the item package uses
    // this one); BrowserRestoreTest holds the two to the same set.
    private static final Set<String> SCHEMES = Set.of("http", "https", "file", "about", "jasper-about");
    // Pages whose address holds the page itself, which a restore cannot load again.
    private static final Set<String> UNSAVED = Set.of("data", "blob");
    // WebLink's rule for a target.
    private static final int MAX_TARGET = 256;

    private BrowserRestore() { }

    /**
     * The restore state of an item at {@code url}, opened for {@code target}: the keys {@code url} and {@code target},
     * each when present, within {@link RestoreState#MAX_CHARACTERS} counted over keys and values.
     * <ul>
     *   <li>A URL that would pass that budget is saved without its query and fragment; when it still would, no
     *       {@code url} is saved.</li>
     *   <li>A {@code data:} or {@code blob:} page saves no {@code url}.</li>
     *   <li>Jasper's own {@code jasper-about:} pages save their URL.</li>
     *   <li>A target that is not 1 to 256 characters without a control character, as WebLink allows, is not saved.</li>
     * </ul>
     * An item that saves no {@code url} comes back at the start page.
     *
     * @param url where the item is, or what it loads first
     * @param target the OSC 8 target it was opened for, or empty
     * @return the state; always present, as {@code ContentTab.setRestore} takes it
     */
    public static Optional<RestoreState> state(URI url, Optional<String> target) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(target, "target");
        var values = new HashMap<String, String>();
        target.filter(BrowserRestore::validTarget).ifPresent(name -> values.put(TARGET, name));
        int budget = RestoreState.MAX_CHARACTERS - URL.length()
            - values.entrySet().stream().mapToInt(entry -> entry.getKey().length() + entry.getValue().length()).sum();
        String scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        if (!UNSAVED.contains(scheme)) {
            String address = url.toString();
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
     * The OSC 8 target a saved item was opened for.
     *
     * @param state a saved item's restore state
     * @return the target; empty when it saved none, or one that is not 1 to 256 characters without a control character
     */
    public static Optional<String> target(RestoreState state) {
        return Optional.ofNullable(state.values().get(TARGET)).filter(BrowserRestore::validTarget);
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
