package dev.jasper.app.settingsui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What the Tab name row previews and lists (tab names spec 5), supplied by the application: the Terminal tab the preview
 * names, or empty while none is open, and the pieces plugins add. EDT only.
 *
 * @param preview the tab the preview uses now
 * @param plugins every piece plugins add now, in name order
 */
public record TabNameChoices(Supplier<Optional<Values>> preview, Supplier<List<Piece>> plugins) {
    /** No Terminal tab and no plugin pieces, for pages built without an application: the preview uses sample values. */
    public static final TabNameChoices NONE = new TabNameChoices(Optional::empty, List::of);

    public TabNameChoices {
        Objects.requireNonNull(preview, "preview");
        Objects.requireNonNull(plugins, "plugins");
    }

    /** A preview with no plugin pieces. */
    public TabNameChoices(Supplier<Optional<Values>> preview) { this(preview, List::of); }

    /**
     * A piece a plugin adds.
     *
     * @param name what users write between braces
     * @param description what it shows
     * @param owner its plugin's name
     */
    public record Piece(String name, String description, String owner) {
        public Piece {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(owner, "owner");
        }
    }

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
