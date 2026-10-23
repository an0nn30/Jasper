package dev.jasper.app.plugins;

import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
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
        var p = theme.semantic();
        return new WorkspacePalette(p.color(SemanticColor.SURFACE_PANEL), p.color(SemanticColor.SURFACE_PANEL_RAISED),
            p.color(SemanticColor.SURFACE_CARD), p.color(SemanticColor.TEXT_PRIMARY), p.color(SemanticColor.TEXT_SECONDARY),
            p.color(SemanticColor.BORDER_WINDOW), p.color(SemanticColor.SELECTION_BG), p.color(SemanticColor.TEXT_ONSELECTION),
            p.color(SemanticColor.SURFACE_CONTROL_HOVER), p.color(SemanticColor.ACCENT), p.color(SemanticColor.STATUS_SUCCESS),
            p.color(SemanticColor.STATUS_WARNING), p.color(SemanticColor.STATUS_DANGER));
    }
    static EditorPalette editor(GeneratedTheme theme) {
        var e = theme.editor();
        return new EditorPalette(e.background(), e.foreground(), e.caret(), e.selection(), e.currentLine(), e.gutter(), e.lineNumbers());
    }
    static SyntaxPalette syntax(GeneratedTheme theme) {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        theme.syntax().forEach((role, value) -> styles.put(SyntaxRole.valueOf(role.name()),
            new SyntaxStyle(value.color(), value.bold(), value.italic())));
        return new SyntaxPalette(styles);
    }
    /** Each of {@code library}'s themes in order, its Dark and then its Light editor scheme, for the variants it has. */
    static java.util.List<dev.jasper.sdk.ui.EditorScheme> editorSchemes(dev.jasper.app.appearance.ThemeLibrary library) {
        var schemes = new java.util.ArrayList<dev.jasper.sdk.ui.EditorScheme>();
        for (var theme : library.themes()) {
            for (boolean dark : new boolean[]{true, false}) {
                if (!theme.has(dark)) continue;
                var generated = theme.generated(dark);
                schemes.add(new dev.jasper.sdk.ui.EditorScheme(theme.id() + (dark ? "/dark" : "/light"), library.label(theme),
                    dark ? dev.jasper.sdk.Variant.DARK : dev.jasper.sdk.Variant.LIGHT, editor(generated), syntax(generated)));
            }
        }
        return java.util.List.copyOf(schemes);
    }
}
