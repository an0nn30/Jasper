package dev.jasper.browser.api;

import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.TerminalTypography;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.util.Objects;

/**
 * A preview's look.
 *
 * @param variant whether the look is dark or light
 * @param palette the document's colours
 * @param syntax the colours and styles of code's tokens
 * @param textFamily the font family of the document's text; not blank
 * @param typography the code's font, with its fallback fonts and ligatures, and the size of the document's text
 */
public record PreviewTheme(Variant variant, WorkspacePalette palette, SyntaxPalette syntax, String textFamily,
        TerminalTypography typography) {
    /** Rejects a null value and a blank text family. */
    public PreviewTheme {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(palette, "palette");
        Objects.requireNonNull(syntax, "syntax");
        Objects.requireNonNull(textFamily, "textFamily");
        Objects.requireNonNull(typography, "typography");
        if (textFamily.isBlank()) throw new IllegalArgumentException("A preview's text family must not be blank");
    }
}
