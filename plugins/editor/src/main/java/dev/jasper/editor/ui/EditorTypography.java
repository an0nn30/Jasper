package dev.jasper.editor.ui;

import dev.jasper.sdk.ui.TerminalTypography;

/** Resolves display typography without changing independent Editor settings. */
public final class EditorTypography {
    private EditorTypography() { }

    /** @return saved terminal values when following, otherwise the independent Editor values */
    public static TerminalTypography effective(EditorSettings own, TerminalTypography terminal) {
        return own.followTerminalFont() ? terminal : new TerminalTypography(own.fontFamily(), own.fontSize(),
            own.fontFallback(), own.fontLigatures(), own.fontLineHeight());
    }
}
