package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;

/**
 * A dialog the application builds over one of its windows.
 *
 * @param title non-blank title
 * @param owner the terminal window or plugin window the dialog belongs to
 * @param presentation explicit presentation; attached sheets and overlays require modality, and attached sheets a live
 *     checked owner at show
 * @param modal whether the dialog blocks its owner; {@link WindowSurface#show()} then returns only after it closes
 */
public record DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation) {
    /** Placement, independent of the form's content kind. */
    public enum Presentation {
        /** A native dialog centred over its owner, sized to its content; the user cannot resize it. */
        CENTERED,
        /** A native sheet hanging from the owner's title edge. */
        ATTACHED_SHEET,
        /**
         * Inside the owner's window, with no native window: the rest of the window dims under a scrim and the dialog,
         * centred above it, fades up into place, and rises on as it fades out when it closes (both instant with motion
         * off).
         * Modal only.
         *
         * @since 0.15.4
         */
        OVERLAY
    }
    /** Retains the centered presentation and original JVM constructor. */
    public DialogSpec(String title, WindowOwner owner, boolean modal) { this(title, owner, modal, Presentation.CENTERED); }
    /** Validates the title and owner. */
    public DialogSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A dialog needs a title");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(presentation, "presentation");
        if (presentation == Presentation.ATTACHED_SHEET && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
        if (presentation == Presentation.OVERLAY && !modal) throw new IllegalArgumentException("Overlays must be modal");
    }
}
