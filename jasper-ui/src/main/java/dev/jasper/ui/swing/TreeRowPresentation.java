package dev.jasper.ui.swing;

import java.util.Objects;
import java.util.Optional;

/** App-owned contextual and physical-key metadata for a stable tree item. */
public record TreeRowPresentation(String context,boolean locked,Optional<KeycapSequence> keycaps) {
    public TreeRowPresentation{Objects.requireNonNull(context);Objects.requireNonNull(keycaps);}
}
