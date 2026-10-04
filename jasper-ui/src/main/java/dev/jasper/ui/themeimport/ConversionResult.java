package dev.jasper.ui.themeimport;

import dev.jasper.ui.theme.ThemeDocument;
import java.util.List;
import java.util.Objects;

/** One standalone native theme and the fallbacks used to convert its provider. */
public record ConversionResult(ThemeDocument document, List<String> warnings, String sourceEntry) {
    public ConversionResult { Objects.requireNonNull(document); Objects.requireNonNull(sourceEntry); warnings = List.copyOf(warnings); }
}
