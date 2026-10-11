package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.Objects;

/** Immutable choice, with optional semantic artwork, supporting text and an authored theme preview. */
public record Option(String id, String label, Optional<Nodes.Hue> hue, String icon, String description, Optional<Preview> preview) {
    public Option {
        ModelChecks.id(id);
        ModelChecks.label(label, "label"); Objects.requireNonNull(hue);
        Objects.requireNonNull(icon); Objects.requireNonNull(description); Objects.requireNonNull(preview);
    }
    /** Ordinary text choice. */
    public Option(String id, String label, Optional<Nodes.Hue> hue) { this(id,label,hue,"","",Optional.empty()); }
    /** Choice with semantic artwork and supporting text. */
    public Option(String id, String label, String icon, String description) { this(id,label,Optional.empty(),icon,description,Optional.empty()); }
    /** Colors sampled from one authored source; values are ARGB. Geometry belongs to the renderer. */
    public record Palette(int background, int sidebar, int control, int text, int accent) { }
    /** A single source preview or two actual sources shown side by side for a system policy. */
    public record Preview(Palette primary, Optional<Palette> alternate) {
        public Preview { Objects.requireNonNull(primary); Objects.requireNonNull(alternate); }
    }
}
