package dev.jasper.sdk.ui;

import java.awt.Color;
import java.util.Objects;

/** Semantic colors for workspace controls.
 * @param background background color
 * @param surfaceRaised raised surface background
 * @param surfaceSunken recessed surface background
 * @param text text color
 * @param textMuted secondary text color
 * @param border border color
 * @param selection selection color
 * @param selectionText selected text foreground
 * @param hover hover color
 * @param accent accent color
 * @param success success color
 * @param warning warning color
 * @param error error color
 * @since 0.12.0
 */
public record WorkspacePalette(Color background, Color surfaceRaised, Color surfaceSunken, Color text,
                               Color textMuted, Color border, Color selection, Color selectionText, Color hover,
                               Color accent, Color success, Color warning, Color error) {
    /** Validates every color. */
    public WorkspacePalette {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(surfaceRaised, "surfaceRaised");
        Objects.requireNonNull(surfaceSunken, "surfaceSunken");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(textMuted, "textMuted");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(selectionText, "selectionText");
        Objects.requireNonNull(hover, "hover");
        Objects.requireNonNull(accent, "accent");
        Objects.requireNonNull(success, "success");
        Objects.requireNonNull(warning, "warning");
        Objects.requireNonNull(error, "error");
    }

}
