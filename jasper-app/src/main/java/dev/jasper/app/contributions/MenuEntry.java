package dev.jasper.app.contributions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

/** One immutable node of a contributed menu section. */
public sealed interface MenuEntry permits MenuEntry.Item, MenuEntry.WebAddressItem, MenuEntry.Separator, MenuEntry.Submenu {
    /** A contributed action. */
    record Item(String actionId) implements MenuEntry { }

    /**
     * A contributed action the terminal's right-click menu shows only while the right-clicked pane's selection is one web
     * address ({@link #addressIn}); the action chosen from it hears that address (browser plan 3b Task 6).
     */
    record WebAddressItem(String actionId) implements MenuEntry {
        /**
         * {@code selection} as one web address: without leading and trailing whitespace and with none inside, an absolute
         * URI whose scheme is {@code http} or {@code https}, in either letter case, with a host, such as
         * {@code https://example.com/a}; empty for anything else.
         */
        public static Optional<URI> addressIn(String selection) {
            String text = selection.strip();
            if (text.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) return Optional.empty();
            try {
                var address = new URI(text);
                String scheme = address.getScheme();
                boolean web = scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
                return web && address.getHost() != null ? Optional.of(address) : Optional.empty();
            } catch (URISyntaxException notAnAddress) {
                return Optional.empty();
            }
        }
    }

    /** A separator. */
    record Separator() implements MenuEntry { }

    /** A nested menu. */
    record Submenu(String title, List<MenuEntry> entries) implements MenuEntry {
        public Submenu { entries = List.copyOf(entries); }
    }
}
