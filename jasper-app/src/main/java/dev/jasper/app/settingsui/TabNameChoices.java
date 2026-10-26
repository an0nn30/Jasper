package dev.jasper.app.settingsui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the Tab name row previews (tab names spec 5), supplied by the application: the Terminal tab the preview names,
 * or empty while none is open. EDT only.
 *
 * @param preview the tab the preview uses now
 */
public record TabNameChoices(Supplier<Optional<Values>> preview) {
    /** No Terminal tab, for pages built without an application: the preview uses sample values. */
    public static final TabNameChoices NONE = new TabNameChoices(Optional::empty);

    public TabNameChoices { Objects.requireNonNull(preview, "preview"); }

    /**
     * One tab's values.
     *
     * @param pieces each piece's value by name, without braces
     * @param fallback what a pattern that comes out empty names the tab: its shell's label
     */
    public record Values(Map<String, String> pieces, String fallback) {
        public Values {
            pieces = Map.copyOf(pieces);
            Objects.requireNonNull(fallback, "fallback");
        }
    }
}
