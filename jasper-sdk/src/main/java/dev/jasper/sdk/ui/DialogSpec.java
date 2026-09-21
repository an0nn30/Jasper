package dev.jasper.sdk.ui;

import dev.jasper.sdk.WindowOwner;
import java.util.Objects;

/**
 * A dialog the application builds over one of its windows.
 *
 * @param title non-blank title
 * @param owner the terminal window or plugin window the dialog belongs to
 * @param modal whether the dialog blocks its owner; {@link WindowSurface#show()} then returns only after it closes
 */
public record DialogSpec(String title, WindowOwner owner, boolean modal) {
    /** Validates the title and owner. */
    public DialogSpec {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A dialog needs a title");
        Objects.requireNonNull(owner, "owner");
    }
}
