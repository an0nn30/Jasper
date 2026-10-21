package dev.jasper.sdk.ui;

import java.util.Optional;
import java.util.Objects;

/**
 * Immutable choice, with optional semantic artwork, supporting text and an authored theme preview.
 *
 * @param id the option's id, unique among its {@link Nodes.Choice}'s options; reported in {@link UiEvent.Selected}
 * @param label the option's literal text and accessible name; not blank
 * @param hue present marks the option with that category colour before its label: in an EXCLUSIVE choice (and a TABS
 *     choice in the default look), in a SEARCHABLE choice's search list, and, when the option has no icon or
 *     description, wherever a DROPDOWN shows it and on a SEARCHABLE choice's value
 * @param icon an icon name, drawn before the label where a DROPDOWN shows the option and on a SEARCHABLE choice's value
 *     (its search list shows labels only); empty for none
 * @param description supporting text, drawn muted at the end of the line where a DROPDOWN shows the option and on a
 *     SEARCHABLE choice's value (its search list shows labels only), and a PREVIEWS tile's tooltip and accessible
 *     description; empty for none
 * @param preview present draws the option as a miniature window in a PREVIEWS choice
 */
public record Option(String id, String label, Optional<Nodes.Hue> hue, String icon, String description, Optional<Preview> preview) {
    public Option {
        ModelChecks.id(id);
        ModelChecks.label(label, "label"); Objects.requireNonNull(hue);
        Objects.requireNonNull(icon); Objects.requireNonNull(description); Objects.requireNonNull(preview);
    }
    /**
     * Ordinary text choice.
     *
     * @param id the option's id, unique among its choice's options
     * @param label the option's literal text; not blank
     * @param hue present marks the option with that category colour
     */
    public Option(String id, String label, Optional<Nodes.Hue> hue) { this(id,label,hue,"","",Optional.empty()); }
    /**
     * Choice with semantic artwork and supporting text.
     *
     * @param id the option's id, unique among its choice's options
     * @param label the option's literal text; not blank
     * @param icon an icon name drawn before the label; empty for none
     * @param description supporting text drawn muted at the end of the option's line; empty for none
     */
    public Option(String id, String label, String icon, String description) { this(id,label,Optional.empty(),icon,description,Optional.empty()); }
    /**
     * Colours sampled from one authored source; values are ARGB. Geometry belongs to the renderer.
     *
     * @param background the miniature window's background
     * @param sidebar its sidebar strip
     * @param control its raised panel
     * @param text its text lines, drawn at partial opacity
     * @param accent its selected sidebar item and its button
     */
    public record Palette(int background, int sidebar, int control, int text, int accent) { }
    /**
     * A single source preview or two actual sources shown side by side for a system policy.
     *
     * @param primary the colours of the whole miniature, or of its left half when {@code alternate} is present
     * @param alternate present draws the miniature's right half in these colours, as for an option that follows the
     *     system's light or dark appearance
     */
    public record Preview(Palette primary, Optional<Palette> alternate) {
        public Preview { Objects.requireNonNull(primary); Objects.requireNonNull(alternate); }
    }
}
