package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;

/**
 * A dialog the application builds over one of its windows.
 *
 * @param title non-blank title
 * @param owner the terminal window or plugin window the dialog belongs to
 * @param presentation explicit native presentation; attached sheets require modality and a live checked owner at show
 * @param modal whether the dialog blocks its owner; {@link WindowSurface#show()} then returns only after it closes
 */
public record DialogSpec(String title, WindowOwner owner, boolean modal, Presentation presentation) {
    /** Native placement, independent of the form's content kind. */
    public enum Presentation { CENTERED, ATTACHED_SHEET }
    /** Retains the centered presentation and original JVM constructor. */
    public DialogSpec(String title, WindowOwner owner, boolean modal) { this(title, owner, modal, Presentation.CENTERED); }
    /** Validates the title and owner. */
    public DialogSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A dialog needs a title");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(presentation, "presentation");
        if (presentation == Presentation.ATTACHED_SHEET && !modal) throw new IllegalArgumentException("Attached sheets must be modal");
    }
}
