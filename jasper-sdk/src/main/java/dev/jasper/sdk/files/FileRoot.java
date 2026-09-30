package dev.jasper.sdk.files;

import java.util.Objects;

/**
 * One root a source offers, such as a saved host.
 *
 * @param id        stable within the source; never contains {@code |}
 * @param label     shown in the chooser's navigation pane
 * @param detail    its tooltip, such as {@code user@host:22}; may be empty
 * @param connected whether opening it needs no connection or sign-in first
 * @since 0.10.0
 */
public record FileRoot(String id, String label, String detail, boolean connected) {
    /** Validates the id and label. */
    public FileRoot {
        if (id == null || id.isBlank() || id.contains("|")) throw new IllegalArgumentException("A root needs an id without |: " + id);
        if (label == null || label.isBlank()) throw new IllegalArgumentException("A root needs a label");
        Objects.requireNonNull(detail, "detail");
    }
}
