package dev.jasper.ui.icons;

import dev.jasper.ui.model.Nodes;
import java.util.Objects;

/** Immutable authored pixels and a declared semantic identity-tile backdrop. Never recolors the glyph. */
public record IdentityArtwork(EncodedArtwork artwork, Nodes.Hue tileHue) {
    public IdentityArtwork {
        Objects.requireNonNull(artwork); Objects.requireNonNull(tileHue);
        if (tileHue == Nodes.Hue.NONE) throw new IllegalArgumentException("Identity hue must name a tile backdrop");
    }
}
