package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;

/**
 * A dialog the application builds over one of its windows.
 *
 * @param title non-blank title
 * @param owner the Jasper window, content tab or anchor the dialog belongs to
 * @param presentation explicit presentation; attached sheets and overlays require modality, and attached sheets a live
 *     checked owner at show
 * @param modal whether the dialog blocks its owner; {@link WindowSurface#show()} then returns only after it closes
 * @param swatch a small square of this hue drawn before the title wherever Jasper draws the dialog's title, as in a
 *     login dialog's coloured square; a title bar the system draws shows none, and {@link Nodes.Hue#NONE} draws none
 *     (since 0.18.0)
 */
public record DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation, Nodes.Hue swatch) {
    /** Placement, independent of the form's content kind; a hint the presentation may place another way. */
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
    /**
     * A dialog with no swatch.
     *
     * @param title non-blank title
     * @param owner the window the dialog belongs to
     * @param modal whether the dialog blocks its owner
     * @param presentation explicit presentation
     */
    public DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation) {
        this(title, owner, modal, presentation, Nodes.Hue.NONE);
    }
    /** Validates the title and owner. */
    public DialogSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A dialog needs a title");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(presentation, "presentation");
        Objects.requireNonNull(swatch, "swatch");
        if (presentation == Presentation.ATTACHED_SHEET && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
        if (presentation == Presentation.OVERLAY && !modal) throw new IllegalArgumentException("Overlays must be modal");
    }
}
