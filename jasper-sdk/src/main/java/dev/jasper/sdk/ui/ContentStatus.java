package dev.jasper.sdk.ui;

import java.util.Objects;

/** An item rendered in the host status bar while its content is selected.
 * @param text literal visible text
 * @param tooltip literal descriptive text
 * @param run optional UI-thread action, null for plain text
 * @since 0.8.5
 */
public record ContentStatus(String text,String tooltip,Runnable run) {
    /** Validates text. */
    public ContentStatus {Objects.requireNonNull(text);Objects.requireNonNull(tooltip);}
}
