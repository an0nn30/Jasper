package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * A link Jasper asks a {@link WebBrowser} to open: the address, who asked, where it asks to open, the name an OSC 8
 * link gave it and the window it came from.
 *
 * <p>The address is an {@code http} or {@code https} address with a host, or a {@code file:} address, which only a
 * {@link LinkOrigin#CLICK} in a {@link LinkPlacement#TAB} carries: a local file the user clicked in a terminal. A
 * clicked link comes from program output, and an OSC 8 link's address can differ from the text shown, so a browser
 * should treat the address as untrusted input and show it before acting on it.
 *
 * @param address an absolute {@code http} or {@code https} address with a host, or an absolute {@code file:} address, as
 *     the link or the caller gave it
 * @param origin who asked for it
 * @param placement where the link asks to open; {@link LinkPlacement#TAB} for {@link LinkOrigin#EXTERNAL}
 * @param target an OSC 8 {@code target=} name, 1 to 256 characters with no control character, so a browser can reuse
 *     the item it names; empty when the link has none, and for {@link LinkOrigin#EXTERNAL}
 * @param window the Jasper window the address came from, as the browser's plugin's own handle; empty when it came from
 *     elsewhere
 * @since 0.23.0
 */
public record WebLink(URI address, LinkOrigin origin, LinkPlacement placement, Optional<String> target, Optional<WindowOwner> window) {
    /** The longest target, in characters. */
    private static final int MAX_TARGET = 256;

    /**
     * Requires every value and checks the rules above.
     *
     * @throws IllegalArgumentException when the address is none of those, a {@code file:} address comes from anything but
     *     a click in a tab, an {@link LinkOrigin#EXTERNAL} link asks for a split or carries a target, or the target is
     *     empty, longer than 256 characters or holds a control character
     */
    public WebLink {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(window, "window");
        String scheme = address.getScheme() == null ? "" : address.getScheme().toLowerCase(Locale.ROOT);
        switch (scheme) {
            case "http", "https" -> {
                if (address.getHost() == null)
                    throw new IllegalArgumentException("Only an absolute http or https address with a host opens in a browser: " + address);
            }
            case "file" -> {
                if (address.isOpaque() || address.getPath() == null || !address.getPath().startsWith("/"))
                    throw new IllegalArgumentException("Only an absolute file address opens in a browser: " + address);
                if (origin != LinkOrigin.CLICK || placement != LinkPlacement.TAB)
                    throw new IllegalArgumentException("A file address opens only as a click in a tab: " + address);
            }
            default -> throw new IllegalArgumentException("Only an http, https or file address opens in a browser: " + address);
        }
        if (origin == LinkOrigin.EXTERNAL && (placement != LinkPlacement.TAB || target.isPresent()))
            throw new IllegalArgumentException("An external link opens in a tab and has no target: " + address);
        target.ifPresent(name -> {
            if (name.isEmpty() || name.length() > MAX_TARGET || name.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("A target is 1 to " + MAX_TARGET + " characters with no control character");
        });
    }
}
