package dev.jasper.ui.icons;

import dev.jasper.ui.model.Nodes;
import java.util.Objects;

/** Immutable authored pixels, semantic backdrop and explicit selected-tile ink policy. */
public record IdentityArtwork(EncodedArtwork artwork, Nodes.Hue tileHue, SelectedInk selectedInk) {
    /** Selected tiles preserve authored RGBA unless the author explicitly requests a foreground silhouette. */
    public enum SelectedInk { AUTHORED, FOREGROUND }
    /** Retains authored ink in every state for existing callers. */
    public IdentityArtwork(EncodedArtwork artwork, Nodes.Hue tileHue) { this(artwork, tileHue, SelectedInk.AUTHORED); }
    public IdentityArtwork {
        Objects.requireNonNull(artwork); Objects.requireNonNull(tileHue); Objects.requireNonNull(selectedInk);
        if (tileHue == Nodes.Hue.NONE) throw new IllegalArgumentException("Identity hue must name a tile backdrop");
    }
}
