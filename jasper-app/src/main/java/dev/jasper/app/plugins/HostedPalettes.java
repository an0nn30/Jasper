package dev.jasper.app.plugins;

import dev.jasper.app.appearance.GeneratedTheme;
import dev.jasper.app.appearance.Token;
import dev.jasper.sdk.ui.EditorPalette;
import dev.jasper.sdk.ui.WorkspacePalette;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import java.util.EnumMap;

/** Maps immutable app values at the only SDK boundary. */
final class HostedPalettes {
    private HostedPalettes() {}
    static WorkspacePalette workspace(GeneratedTheme theme) {
        var t = theme.tokens();
        return new WorkspacePalette(t.get(Token.SURFACE), t.get(Token.SURFACE_RAISED), t.get(Token.SURFACE_SUNKEN),
            t.get(Token.TEXT), t.get(Token.TEXT_MUTED), t.get(Token.BORDER), t.get(Token.SELECTION),
            t.get(Token.SELECTION_TEXT), t.get(Token.HOVER), t.get(Token.ACCENT), t.get(Token.SUCCESS),
            t.get(Token.WARNING), t.get(Token.ERROR));
    }
    static EditorPalette editor(GeneratedTheme theme) {
        var t = theme.tokens();
        return new EditorPalette(t.get(Token.EDITOR_BACKGROUND), t.get(Token.EDITOR_FOREGROUND), t.get(Token.EDITOR_CARET),
            t.get(Token.EDITOR_SELECTION), t.get(Token.EDITOR_CURRENT_LINE), t.get(Token.EDITOR_GUTTER),
            t.get(Token.EDITOR_LINE_NUMBERS));
    }
    static SyntaxPalette syntax(GeneratedTheme theme) {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        theme.syntax().forEach((role, value) -> styles.put(SyntaxRole.valueOf(role.name()),
            new SyntaxStyle(value.color(), value.bold(), value.italic())));
        return new SyntaxPalette(styles);
    }
}
