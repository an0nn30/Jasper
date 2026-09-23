package dev.jasper.sdk.ui;

import dev.jasper.sdk.terminal.WindowHandle;
import java.util.Objects;

/**
 * Centered, non-movable progress content inside a terminal window; no native window is created.
 * @param title nonblank accessible title
 * @param owner an open terminal window from this host
 * @since 0.7.3
 */
public record OverlaySpec(String title, WindowHandle owner) {
    /** Validates the title and owner. */
    public OverlaySpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("An overlay needs a title");
        Objects.requireNonNull(owner, "owner");
    }
}
